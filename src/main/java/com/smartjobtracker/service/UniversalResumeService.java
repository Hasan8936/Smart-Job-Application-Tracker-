package com.smartjobtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.ResumeBuilderDto;
import com.smartjobtracker.dto.UniversalResumeDtos;
import com.smartjobtracker.model.Resume;
import com.smartjobtracker.model.UniversalResume;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.ResumeRepository;
import com.smartjobtracker.repository.UniversalResumeRepository;
import com.smartjobtracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One editable master resume per user. Stored as the builder's structured data; every save regenerates a plain-text
 * {@link Resume} row (created and owned by this service, never an upload) so matching keeps working by resumeId.
 * Match scores are recomputed from the resume text on every match request, so an edit is reflected on the next match.
 */
@Service
public class UniversalResumeService {
    private static final Logger log = LoggerFactory.getLogger(UniversalResumeService.class);
    /** Used as the resume's name only when neither the resume nor the account has a name. */
    static final String RESUME_FILE_NAME = "Universal resume";

    private final UniversalResumeRepository repository;
    private final ResumeRepository resumes;
    private final ResumeService resumeService;
    private final ResumeBuilderService builderService;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public UniversalResumeService(UniversalResumeRepository repository, ResumeRepository resumes, ResumeService resumeService,
                                  ResumeBuilderService builderService, UserRepository users, ObjectMapper mapper) {
        this.repository = repository;
        this.resumes = resumes;
        this.resumeService = resumeService;
        this.builderService = builderService;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<UniversalResumeDtos.Response> get(Long userId) {
        return repository.findByUserId(userId).map(this::toResponse);
    }

    /** Creates or replaces the universal resume and syncs its matching text. */
    @Transactional
    public UniversalResumeDtos.Response save(Long userId, ResumeBuilderDto dto) {
        String template = ResumeTemplate.fromId(dto.getTemplate())
                .orElseThrow(() -> new IllegalArgumentException("Unknown resume template: " + dto.getTemplate())).id();
        dto.setTemplate(template);

        UniversalResume universal = repository.findByUserId(userId).orElseGet(() -> {
            UniversalResume created = new UniversalResume();
            created.setUserId(userId);
            return created;
        });
        universal.setTemplate(template);
        universal.setDataJson(toJson(dto));
        universal.setUpdatedAt(OffsetDateTime.now());
        universal.setResumeId(syncMatchingText(userId, universal.getResumeId(), displayName(userId, dto), builderService.toResumeText(dto)));
        UniversalResume saved = repository.save(universal);
        log.info("Universal resume saved: userId={} resumeId={} template={}", userId, saved.getResumeId(), template);
        return toResponse(saved);
    }

    /**
     * Starts the universal resume from one of the user's uploaded resumes using the existing import parser.
     * Only fields the parser finds are filled; nothing is added. Refuses to replace an existing one unless asked.
     */
    @Transactional
    public UniversalResumeDtos.Response initFromResume(Long userId, Long resumeId, boolean overwrite) {
        Resume source = resumes.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new UniversalResumeNotFoundException("Resume not found"));
        Optional<UniversalResume> existing = repository.findByUserId(userId);
        if (existing.isPresent() && Objects.equals(existing.get().getResumeId(), source.getId())) {
            throw new IllegalArgumentException("That resume is already your universal resume");
        }
        if (existing.isPresent() && !overwrite) {
            throw new UniversalResumeConflictException("You already have a universal resume; pass overwrite=true to replace it");
        }
        return save(userId, fromImport(userId, builderService.importFromResume(userId, source.getId())));
    }

    /** The resume to match with when none is chosen: the universal resume, else the newest other resume. */
    @Transactional(readOnly = true)
    public UniversalResumeDtos.MatchingResume matchingResume(Long userId) {
        Long universalResumeId = repository.findByUserId(userId).map(UniversalResume::getResumeId).orElse(null);
        if (universalResumeId != null) {
            Optional<Resume> linked = resumes.findByIdAndUserId(universalResumeId, userId);
            if (linked.isPresent()) return new UniversalResumeDtos.MatchingResume(universalResumeId, "UNIVERSAL", linked.get().getFileName());
        }
        return resumes.findByUserIdOrderByUploadedAtDescIdDesc(userId).stream()
                .filter(r -> !Objects.equals(r.getId(), universalResumeId))
                .findFirst()
                .map(r -> new UniversalResumeDtos.MatchingResume(r.getId(), "LATEST_UPLOAD", r.getFileName()))
                .orElse(new UniversalResumeDtos.MatchingResume(null, "NONE", null));
    }

    /** The universal resume is named after the person: the name on the resume, else the account name. */
    private String displayName(Long userId, ResumeBuilderDto dto) {
        String name = dto.getPersonalInfo() == null ? null : dto.getPersonalInfo().name();
        if (name == null || name.isBlank()) name = users.findById(userId).map(User::getName).orElse(null);
        if (name == null || name.isBlank()) return RESUME_FILE_NAME;
        name = name.trim().replaceAll("\\s+", " ");
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    /** Updates the linked text-only Resume, or creates it. Uploaded resumes are never touched. */
    private Long syncMatchingText(Long userId, Long linkedResumeId, String fileName, String text) {
        if (linkedResumeId != null) {
            Optional<Resume> linked = resumes.findByIdAndUserId(linkedResumeId, userId);
            if (linked.isPresent()) {
                Resume r = linked.get();
                r.setFileName(fileName);
                r.setExtractedText(text);
                r.setUploadedAt(OffsetDateTime.now());
                return resumes.save(r).getId();
            }
        }
        return resumeService.saveFromContent(userId, fileName, text).getId();
    }

    @SuppressWarnings("unchecked")
    private ResumeBuilderDto fromImport(Long userId, Map<String, Object> imported) {
        ResumeBuilderDto dto = new ResumeBuilderDto();
        User user = users.findById(userId).orElse(null);
        dto.setPersonalInfo(new ResumeBuilderDto.PersonalInfo(user == null ? null : user.getName(), user == null ? null : user.getEmail(),
                null, null, null, null, null, null));

        Map<String, List<String>> skills = (Map<String, List<String>>) imported.getOrDefault("skills", Map.of());
        dto.setSkills(new ResumeBuilderDto.Skills(list(skills.get("languages")), list(skills.get("frameworks")),
                list(skills.get("tools")), list(skills.get("other"))));

        List<ResumeBuilderDto.ExperienceEntry> experience = new ArrayList<>();
        for (Map<String, Object> e : (List<Map<String, Object>>) imported.getOrDefault("experience", List.of())) {
            experience.add(new ResumeBuilderDto.ExperienceEntry(str(e.get("company")), str(e.get("role")), str(e.get("startDate")),
                    str(e.get("endDate")), Boolean.TRUE.equals(e.get("current")), list((List<String>) e.get("bullets"))));
        }
        dto.setExperience(experience);

        List<ResumeBuilderDto.EducationEntry> education = new ArrayList<>();
        for (Map<String, Object> e : (List<Map<String, Object>>) imported.getOrDefault("education", List.of())) {
            education.add(new ResumeBuilderDto.EducationEntry(str(e.get("institution")), str(e.get("degree")), str(e.get("field")),
                    str(e.get("startYear")), str(e.get("endYear")), str(e.get("gpa"))));
        }
        dto.setEducation(education);

        // The parser only yields project lines, not structured projects; keep each line verbatim as a description.
        List<ResumeBuilderDto.ProjectEntry> projects = new ArrayList<>();
        for (String hint : list((List<String>) imported.get("projectHints"))) {
            projects.add(new ResumeBuilderDto.ProjectEntry(null, List.of(hint), List.of(), null, null, null));
        }
        dto.setProjects(projects);
        return dto;
    }

    private UniversalResumeDtos.Response toResponse(UniversalResume u) {
        ResumeBuilderDto dto;
        try {
            dto = mapper.readValue(u.getDataJson(), ResumeBuilderDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored universal resume is unreadable (id=" + u.getId() + ")", e);
        }
        return new UniversalResumeDtos.Response(dto, u.getTemplate(), u.getResumeId(), u.getUpdatedAt(), u.getResumeId() != null);
    }

    private String toJson(ResumeBuilderDto dto) {
        try {
            return mapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize universal resume", e);
        }
    }

    private static List<String> list(List<String> values) {
        return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).toList();
    }

    private static String str(Object value) { return value == null ? "" : value.toString(); }
}
