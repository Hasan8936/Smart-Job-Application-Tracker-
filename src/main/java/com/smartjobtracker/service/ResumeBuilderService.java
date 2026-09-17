package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.ResumeBuilderDto;
import com.smartjobtracker.model.Resume;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.ResumeRepository;
import com.smartjobtracker.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
    private final ResumeRepository resumeRepository;
    private final ResumeProfileExtractor profileExtractor;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public ResumeBuilderService(ResumeTailoringService tailoringService,
                                ResumeService resumeService,
                                CandidateProfileService profileService,
                                UserRepository userRepository,
                                ResumeRepository resumeRepository,
                                ResumeProfileExtractor profileExtractor,
                                GeminiClient geminiClient,
                                ObjectMapper objectMapper) {
        this.tailoringService = tailoringService;
        this.resumeService = resumeService;
        this.profileService = profileService;
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.profileExtractor = profileExtractor;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    /** Returns profile-based pre-fill data so the form can start populated. */
    @Transactional(readOnly = true)
    public Map<String, Object> prefill(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        User user = userRepository.findById(userId).orElse(null);
        Map<String, String> personal = new LinkedHashMap<>();
        if (user != null) personal.put("email", user.getEmail());
        result.put("personalInfo", personal);
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

    /** Generates PDF bytes from builder form data without saving. */
    public byte[] renderOnly(ResumeBuilderDto dto) {
        return tailoringService.renderContent(toResumeText(dto));
    }

    /** Generates a LaTeX (.tex) source file for the resume, suitable for Overleaf. */
    public byte[] exportLatex(ResumeBuilderDto dto) {
        String latex = tailoringService.toLatex(toResumeText(dto));
        return latex.getBytes(StandardCharsets.UTF_8);
    }

    /** Parses an existing saved resume into structured builder form data for the import feature. */
    @Transactional(readOnly = true)
    public Map<String, Object> importFromResume(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found or does not belong to this user."));
        String text = resume.getExtractedText() != null ? resume.getExtractedText() : "";
        ResumeProfileExtractor.ExtractedProfile profile = profileExtractor.extract(text);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resumeId", resumeId);
        result.put("fileName", resume.getFileName());

        Map<String, List<String>> skills = new LinkedHashMap<>();
        skills.put("languages", safe(profile.getProgrammingLanguages()));
        skills.put("frameworks", safe(profile.getFrameworks()));
        skills.put("tools", safe(profile.getSkills()));
        skills.put("other", List.of());
        result.put("skills", skills);

        result.put("experience", parseExperienceEntries(safe(profile.getExperience())));
        result.put("education", parseEducationEntries(safe(profile.getEducation())));
        result.put("projectHints", safe(profile.getProjects()));

        return result;
    }

    /** Calls Gemini to improve bullet points, suggest ATS keywords, and score the resume. */
    public Map<String, Object> aiEnhance(Long userId, ResumeBuilderDto dto) {
        String resumeText = toResumeText(dto);
        String targetRole = dto.getTargetRole() != null && !dto.getTargetRole().isBlank()
                ? dto.getTargetRole() : "Software Engineer";

        String systemPrompt = """
                You are an expert resume writer and ATS optimization specialist.

                RULES (follow strictly):
                1. Rewrite bullet points to START with a strong action verb (Designed, Built, Reduced, Led, Implemented, Optimized, Delivered, Architected...).
                2. Do NOT invent new metrics or facts. Keep placeholder values like 'X%', 'X users', 'X ms' unchanged.
                3. Fix grammar and make language concise and professional.
                4. Suggest 5 ATS keywords relevant to the target role that appear to be MISSING from the resume.
                5. Do NOT add new sentences — only rewrite what is already there.
                6. Provide an overall ATS readiness score out of 100.

                Respond ONLY with valid JSON in this exact format (no markdown fences, no extra text):
                {
                  "improvedExperience": [{"index": 0, "bullets": ["bullet 1", "bullet 2"]}],
                  "improvedProjects": [{"index": 0, "description": ["bullet 1"]}],
                  "missingKeywords": ["keyword1", "keyword2", "keyword3", "keyword4", "keyword5"],
                  "sectionFeedback": "Brief one-sentence feedback on section completeness and ordering",
                  "overallScore": 75
                }
                """;

        String userMessage = String.format("Target role: %s\n\nResume:\n%s", targetRole, resumeText);

        try {
            String raw = geminiClient.complete(systemPrompt, userMessage, 2048);
            JsonNode node = objectMapper.readTree(raw);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.convertValue(node, Map.class);
            return parsed;
        } catch (GeminiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiApiException("AI enhancement failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ─── Resume text formatter ───────────────────────────────────────────────────

    String toResumeText(ResumeBuilderDto dto) {
        StringBuilder sb = new StringBuilder();
        ResumeBuilderDto.PersonalInfo pi = dto.getPersonalInfo();

        String name = pi != null && pi.name() != null ? pi.name().trim() : "Your Name";
        sb.append(name).append("\n");

        List<String> contact = new ArrayList<>();
        if (pi != null) {
            add(contact, pi.email());
            add(contact, pi.phone());
            add(contact, pi.location());
            if (pi.linkedin() != null && !pi.linkedin().isBlank()) contact.add(stripped(pi.linkedin()));
            if (pi.github() != null && !pi.github().isBlank()) contact.add(stripped(pi.github()));
            if (pi.leetcode() != null && !pi.leetcode().isBlank()) contact.add(stripped(pi.leetcode()));
            if (pi.website() != null && !pi.website().isBlank()) contact.add(stripped(pi.website()));
        }
        sb.append(String.join(" | ", contact)).append("\n\n");

        if (dto.getSummary() != null && !dto.getSummary().isBlank()) {
            sb.append("SUMMARY\n").append(dto.getSummary().trim()).append("\n\n");
        }

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

        // Projects placed between Experience and Education (standard ATS order)
        if (dto.getProjects() != null && !dto.getProjects().isEmpty()) {
            sb.append("PROJECTS\n");
            for (ResumeBuilderDto.ProjectEntry p : dto.getProjects()) {
                String headline = parts(p.name(), p.date());
                sb.append(headline).append("\n");
                if (p.techStack() != null && !p.techStack().isEmpty()) {
                    sb.append("Tech Stack: ").append(String.join(", ", p.techStack())).append("\n");
                }
                if (p.description() != null) {
                    for (String b : p.description()) {
                        if (b != null && !b.isBlank()) sb.append("• ").append(b.trim()).append("\n");
                    }
                }
                List<String> links = new ArrayList<>();
                if (p.githubUrl() != null && !p.githubUrl().isBlank()) links.add("GitHub: " + stripped(p.githubUrl()));
                if (p.liveUrl() != null && !p.liveUrl().isBlank()) links.add("Live: " + stripped(p.liveUrl()));
                if (!links.isEmpty()) sb.append(String.join(" | ", links)).append("\n");
                sb.append("\n");
            }
        }

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

    // ─── Import parsers ──────────────────────────────────────────────────────────

    private List<Map<String, Object>> parseExperienceEntries(List<String> lines) {
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Object> current = null;
        for (String line : lines) {
            if (line.contains("|")) {
                if (current != null) entries.add(current);
                current = new LinkedHashMap<>();
                String[] ps = line.split("\\|", 3);
                current.put("company", ps[0].trim());
                current.put("role", ps.length > 1 ? ps[1].trim() : "");
                current.put("startDate", "");
                current.put("endDate", "");
                current.put("current", false);
                current.put("bullets", new ArrayList<String>());
                if (ps.length > 2) parseDateRange(current, ps[2].trim());
            } else if (current != null && line.length() > 2) {
                @SuppressWarnings("unchecked")
                List<String> bullets = (List<String>) current.get("bullets");
                if (bullets != null) bullets.add(line.trim());
            } else if (current == null && line.length() > 2) {
                current = new LinkedHashMap<>();
                current.put("company", "");
                current.put("role", "");
                current.put("startDate", "");
                current.put("endDate", "");
                current.put("current", false);
                current.put("bullets", new ArrayList<>(List.of(line.trim())));
            }
        }
        if (current != null) entries.add(current);
        return entries;
    }

    private List<Map<String, Object>> parseEducationEntries(List<String> lines) {
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Object> current = null;
        for (String line : lines) {
            if (line.toLowerCase().startsWith("gpa")) {
                if (current != null) current.put("gpa", line.replaceFirst("(?i)gpa[:\\s]+", "").trim());
            } else if (line.contains("|")) {
                if (current != null) entries.add(current);
                current = new LinkedHashMap<>();
                String[] ps = line.split("\\|", 3);
                current.put("institution", ps[0].trim());
                if (ps.length > 1) parseDegreeField(current, ps[1].trim());
                else { current.put("degree", ""); current.put("field", ""); }
                current.put("startYear", "");
                current.put("endYear", "");
                current.put("gpa", "");
                if (ps.length > 2) parseYears(current, ps[2].trim());
            } else if (current == null) {
                current = new LinkedHashMap<>();
                current.put("institution", line.trim());
                current.put("degree", "");
                current.put("field", "");
                current.put("startYear", "");
                current.put("endYear", "");
                current.put("gpa", "");
            }
        }
        if (current != null) entries.add(current);
        return entries;
    }

    private void parseDateRange(Map<String, Object> entry, String text) {
        String[] ps = text.split("[–\\-]", 2);
        entry.put("startDate", ps[0].trim());
        if (ps.length > 1) {
            String end = ps[1].trim();
            boolean present = end.equalsIgnoreCase("Present") || end.equalsIgnoreCase("Now") || end.equalsIgnoreCase("Current");
            entry.put("current", present);
            entry.put("endDate", present ? "" : end);
        }
    }

    private void parseDegreeField(Map<String, Object> entry, String text) {
        if (text.toLowerCase().contains(" in ")) {
            String[] df = text.split("(?i)\\s+in\\s+", 2);
            entry.put("degree", df[0].trim());
            entry.put("field", df[1].trim());
        } else {
            entry.put("degree", text.trim());
            entry.put("field", "");
        }
    }

    private void parseYears(Map<String, Object> entry, String text) {
        String[] ys = text.split("[–\\-]", 2);
        entry.put("startYear", ys[0].trim());
        if (ys.length > 1) entry.put("endYear", ys[1].trim());
    }

    // ─── Shared helpers ──────────────────────────────────────────────────────────

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
