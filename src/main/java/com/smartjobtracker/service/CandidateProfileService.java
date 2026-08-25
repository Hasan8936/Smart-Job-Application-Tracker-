package com.smartjobtracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.CandidateProfileDto;
import com.smartjobtracker.model.CandidateProfile;
import com.smartjobtracker.model.Resume;
import com.smartjobtracker.repository.CandidateProfileRepository;
import com.smartjobtracker.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reads/writes the per-user {@link CandidateProfile}.
 *
 * <p>Responsibilities: return the current profile, (re)build it from a resume via
 * the deterministic {@link ResumeProfileExtractor}, and persist manual edits.
 * The seven extracted groups are stored as JSON text on the entity; this service
 * is the single place that (de)serializes them, so callers only ever deal with
 * {@link CandidateProfileDto}.
 *
 * <p>Extraction is idempotent: the same resume always yields the same profile
 * (one row per user, upserted). Re-running extraction intentionally replaces the
 * extracted fields — manual edits are preserved only until the next extract.
 *
 * <p>Logging records ids and counts only — never resume text or profile content.
 */
@Service
public class CandidateProfileService {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfileService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final CandidateProfileRepository profileRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeProfileExtractor extractor;
    private final ObjectMapper objectMapper;

    public CandidateProfileService(CandidateProfileRepository profileRepository,
                                   ResumeRepository resumeRepository,
                                   ResumeProfileExtractor extractor,
                                   ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.resumeRepository = resumeRepository;
        this.extractor = extractor;
        this.objectMapper = objectMapper;
    }

    /** Current user's profile, if one exists. */
    @Transactional(readOnly = true)
    public Optional<CandidateProfileDto> getProfile(Long userId) {
        return profileRepository.findByUserId(userId).map(this::toDto);
    }

    /**
     * (Re)build the profile from a resume and persist it.
     *
     * @param resumeId a specific resume to analyze, or {@code null} to use the user's most recent upload
     * @throws ProfileNotFoundException if the user has no usable resume (or the id isn't theirs)
     */
    @Transactional
    public CandidateProfileDto extractFromResume(Long userId, Long resumeId) {
        Resume resume = resolveResume(userId, resumeId);

        ResumeProfileExtractor.ExtractedProfile extracted = extractor.extract(resume.getExtractedText());

        CandidateProfile profile = profileRepository.findByUserId(userId).orElseGet(CandidateProfile::new);
        OffsetDateTime now = OffsetDateTime.now();
        if (profile.getId() == null) {
            profile.setUserId(userId);
            profile.setCreatedAt(now);
        }
        profile.setSourceResumeId(resume.getId());
        profile.setSkills(toJson(extracted.getSkills()));
        profile.setProgrammingLanguages(toJson(extracted.getProgrammingLanguages()));
        profile.setFrameworks(toJson(extracted.getFrameworks()));
        profile.setProjects(toJson(extracted.getProjects()));
        profile.setEducation(toJson(extracted.getEducation()));
        profile.setExperience(toJson(extracted.getExperience()));
        profile.setPreferredRoles(toJson(extracted.getPreferredRoles()));
        profile.setUpdatedAt(now);

        CandidateProfile saved = profileRepository.save(profile);
        log.info("Extracted candidate profile userId={} resumeId={} skills={} languages={} frameworks={} projects={} education={} experience={} roles={}",
                userId, resume.getId(), extracted.getSkills().size(), extracted.getProgrammingLanguages().size(),
                extracted.getFrameworks().size(), extracted.getProjects().size(), extracted.getEducation().size(),
                extracted.getExperience().size(), extracted.getPreferredRoles().size());
        return toDto(saved);
    }

    /** Persist manually edited profile data (creates the profile if none exists yet). */
    @Transactional
    public CandidateProfileDto saveEdits(Long userId, CandidateProfileDto dto) {
        CandidateProfile profile = profileRepository.findByUserId(userId).orElseGet(CandidateProfile::new);
        OffsetDateTime now = OffsetDateTime.now();
        if (profile.getId() == null) {
            profile.setUserId(userId);
            profile.setCreatedAt(now);
        }
        profile.setSkills(toJson(clean(dto.getSkills())));
        profile.setProgrammingLanguages(toJson(clean(dto.getProgrammingLanguages())));
        profile.setFrameworks(toJson(clean(dto.getFrameworks())));
        profile.setProjects(toJson(clean(dto.getProjects())));
        profile.setEducation(toJson(clean(dto.getEducation())));
        profile.setExperience(toJson(clean(dto.getExperience())));
        profile.setPreferredRoles(toJson(clean(dto.getPreferredRoles())));
        profile.setUpdatedAt(now);

        CandidateProfile saved = profileRepository.save(profile);
        log.info("Saved candidate profile edits userId={} skills={} languages={} frameworks={} projects={} education={} experience={} roles={}",
                userId, size(dto.getSkills()), size(dto.getProgrammingLanguages()), size(dto.getFrameworks()),
                size(dto.getProjects()), size(dto.getEducation()), size(dto.getExperience()), size(dto.getPreferredRoles()));
        return toDto(saved);
    }

    // --- helpers ---

    private Resume resolveResume(Long userId, Long resumeId) {
        if (resumeId != null) {
            // Ownership check: a resume that isn't the caller's is reported as not-found (no info leak).
            Resume r = resumeRepository.findById(resumeId).orElse(null);
            if (r == null || r.getUserId() == null || !r.getUserId().equals(userId)) {
                throw new ProfileNotFoundException("Resume not found");
            }
            return r;
        }
        List<Resume> resumes = resumeRepository.findByUserId(userId);
        return resumes.stream()
                .max(Comparator.comparing(Resume::getUploadedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Resume::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow(() -> new ProfileNotFoundException("No resume found to analyze. Upload a resume first."));
    }

    private CandidateProfileDto toDto(CandidateProfile p) {
        CandidateProfileDto dto = new CandidateProfileDto();
        dto.setSourceResumeId(p.getSourceResumeId());
        dto.setUpdatedAt(p.getUpdatedAt());
        dto.setSkills(fromJson(p.getSkills()));
        dto.setProgrammingLanguages(fromJson(p.getProgrammingLanguages()));
        dto.setFrameworks(fromJson(p.getFrameworks()));
        dto.setProjects(fromJson(p.getProjects()));
        dto.setEducation(fromJson(p.getEducation()));
        dto.setExperience(fromJson(p.getExperience()));
        dto.setPreferredRoles(fromJson(p.getPreferredRoles()));
        return dto;
    }

    /** Trim, drop null/blank, de-duplicate (case-insensitive) while preserving order. */
    private List<String> clean(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        List<String> seenLower = new ArrayList<>();
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            String lower = t.toLowerCase();
            if (seenLower.contains(lower)) continue;
            seenLower.add(lower);
            out.add(t);
        }
        return out;
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? new ArrayList<>() : list);
        } catch (Exception e) {
            // List<String> is always serializable; treat any failure as a bug, not a silent data change.
            throw new IllegalStateException("Failed to serialize profile field", e);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<String> parsed = objectMapper.readValue(json, STRING_LIST);
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception e) {
            // Defensive: never fail a read because a row holds unexpected content.
            log.warn("Could not parse stored profile field as a string list; returning empty list");
            return new ArrayList<>();
        }
    }

    private int size(List<String> l) { return l == null ? 0 : l.size(); }
}
