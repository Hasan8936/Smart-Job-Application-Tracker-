package com.smartjobtracker.service;

import com.smartjobtracker.dto.CandidateProfileDto;
import com.smartjobtracker.dto.ResumeBuilderDto;
import com.smartjobtracker.model.Resume;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResumeBuilderService {

    private final ResumeTailoringService tailoringService;
    private final ResumeService resumeService;
    private final CandidateProfileService profileService;
    private final UserRepository userRepository;

    public ResumeBuilderService(ResumeTailoringService tailoringService,
                                ResumeService resumeService,
                                CandidateProfileService profileService,
                                UserRepository userRepository) {
        this.tailoringService = tailoringService;
        this.resumeService = resumeService;
        this.profileService = profileService;
        this.userRepository = userRepository;
    }

    /** Returns pre-fill data from CandidateProfile + user email/name for the builder form. */
    @Transactional(readOnly = true)
    public Map<String, Object> prefill(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Basic user info
        User user = userRepository.findById(userId).orElse(null);
        Map<String, String> personal = new LinkedHashMap<>();
        if (user != null) personal.put("email", user.getEmail());
        result.put("personalInfo", personal);

        // Skills from CandidateProfile (if one exists)
        profileService.getProfile(userId).ifPresent(profile -> {
            Map<String, List<String>> skills = new LinkedHashMap<>();
            skills.put("languages", safe(profile.getProgrammingLanguages()));
            skills.put("frameworks", safe(profile.getFrameworks()));
            skills.put("tools", safe(profile.getSkills()));
            skills.put("other", List.of());
            result.put("skills", skills);
            result.put("experienceHints", safe(profile.getExperience()));
            result.put("educationHints", safe(profile.getEducation()));
            result.put("preferredRoles", safe(profile.getPreferredRoles()));
        });

        return result;
    }

    /** Converts builder form data to formatted resume text, renders PDF, saves Resume entity. */
    @Transactional
    public Map<String, Object> export(Long userId, ResumeBuilderDto dto) {
        String content = toResumeText(dto);
        byte[] pdf = tailoringService.renderContent(content);

        String role = dto.getTargetRole() == null || dto.getTargetRole().isBlank()
                ? "resume" : dto.getTargetRole().toLowerCase().replace(" ", "-");
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String fileName = role + "-resume-" + date + ".pdf";

        Resume saved = resumeService.saveFromContent(userId, fileName, content);

        return Map.of("resumeId", saved.getId(), "fileName", fileName, "pdf", pdf);
    }

    /** Generates PDF bytes from builder form data (for preview/download without saving). */
    public byte[] renderOnly(ResumeBuilderDto dto) {
        return tailoringService.renderContent(toResumeText(dto));
    }

    // ─── Text formatter ──────────────────────────────────────────────────────

    private String toResumeText(ResumeBuilderDto dto) {
        StringBuilder sb = new StringBuilder();
        ResumeBuilderDto.PersonalInfo pi = dto.getPersonalInfo();

        // Header: name
        String name = pi != null && pi.name() != null ? pi.name().trim() : "Your Name";
        sb.append(name).append("\n");

        // Contact line
        List<String> contact = new ArrayList<>();
        if (pi != null) {
            add(contact, pi.email());
            add(contact, pi.phone());
            add(contact, pi.location());
            if (pi.linkedin() != null && !pi.linkedin().isBlank())
                contact.add(stripped(pi.linkedin()));
            if (pi.github() != null && !pi.github().isBlank())
                contact.add(stripped(pi.github()));
            if (pi.website() != null && !pi.website().isBlank())
                contact.add(stripped(pi.website()));
        }
        sb.append(String.join(" | ", contact)).append("\n\n");

        // Summary
        if (dto.getSummary() != null && !dto.getSummary().isBlank()) {
            sb.append("SUMMARY\n").append(dto.getSummary().trim()).append("\n\n");
        }

        // Experience
        if (dto.getExperience() != null && !dto.getExperience().isEmpty()) {
            sb.append("EXPERIENCE\n");
            for (ResumeBuilderDto.ExperienceEntry e : dto.getExperience()) {
                String end = Boolean.TRUE.equals(e.current()) ? "Present" :
                             (e.endDate() != null && !e.endDate().isBlank() ? e.endDate() : "Present");
                String headline = parts(e.company(), e.role(),
                        (e.startDate() != null && !e.startDate().isBlank() ? e.startDate() + " – " + end : end));
                sb.append(headline).append("\n");
                if (e.bullets() != null) {
                    for (String b : e.bullets()) {
                        if (b != null && !b.isBlank()) sb.append("• ").append(b.trim()).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        // Education
        if (dto.getEducation() != null && !dto.getEducation().isEmpty()) {
            sb.append("EDUCATION\n");
            for (ResumeBuilderDto.EducationEntry e : dto.getEducation()) {
                String degreeField = join(e.degree(), e.field() != null && !e.field().isBlank() ? "in " + e.field() : null);
                String years = join(e.startYear(), e.endYear() != null && !e.endYear().isBlank() ? "– " + e.endYear() : null);
                sb.append(parts(e.institution(), degreeField, years)).append("\n");
                if (e.gpa() != null && !e.gpa().isBlank()) sb.append("GPA: ").append(e.gpa()).append("\n");
                sb.append("\n");
            }
        }

        // Skills
        if (dto.getSkills() != null) {
            ResumeBuilderDto.Skills s = dto.getSkills();
            boolean hasSkills = !safe(s.languages()).isEmpty() || !safe(s.frameworks()).isEmpty()
                    || !safe(s.tools()).isEmpty() || !safe(s.other()).isEmpty();
            if (hasSkills) {
                sb.append("SKILLS\n");
                appendSkillLine(sb, "Languages", s.languages());
                appendSkillLine(sb, "Frameworks", s.frameworks());
                appendSkillLine(sb, "Tools", s.tools());
                appendSkillLine(sb, "Other", s.other());
            }
        }

        return sb.toString().stripTrailing();
    }

    private void appendSkillLine(StringBuilder sb, String label, List<String> items) {
        if (items == null || items.isEmpty()) return;
        sb.append(label).append(": ").append(String.join(", ", items)).append("\n");
    }

    private String parts(String... values) {
        return Arrays.stream(values)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private String join(String a, String b) {
        if (a == null || a.isBlank()) return b == null ? "" : b;
        if (b == null || b.isBlank()) return a;
        return a + " " + b;
    }

    private void add(List<String> list, String value) {
        if (value != null && !value.isBlank()) list.add(value.trim());
    }

    private String stripped(String url) {
        return url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
    }

    private List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }
}
