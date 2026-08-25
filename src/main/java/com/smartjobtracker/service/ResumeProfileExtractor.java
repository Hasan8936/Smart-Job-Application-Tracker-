package com.smartjobtracker.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic, offline resume analysis (Phase 1).
 *
 * <p>Extracts structured information from a resume's already-extracted plain text
 * using a curated technology dictionary (skills / languages / frameworks / roles,
 * matched on word boundaries) plus lightweight section parsing (projects /
 * education / experience). It has no external dependencies and makes no network
 * calls, so it is fully unit-testable.
 *
 * <p><b>Anti-fabrication contract:</b> every returned value is text that literally
 * appears in the resume. Nothing is inferred, invented, or embellished. If a
 * section or term is absent, the corresponding list is empty.
 *
 * <p>This is intentionally a single, replaceable seam: a future AI-backed
 * implementation can produce the same {@link ExtractedProfile} without any change
 * to the service, controller, DTO, or schema.
 */
@Service
public class ResumeProfileExtractor {

    /** Immutable result of extraction — seven parallel lists, never null. */
    public static class ExtractedProfile {
        private final List<String> skills;
        private final List<String> programmingLanguages;
        private final List<String> frameworks;
        private final List<String> projects;
        private final List<String> education;
        private final List<String> experience;
        private final List<String> preferredRoles;

        public ExtractedProfile(List<String> skills, List<String> programmingLanguages, List<String> frameworks,
                                List<String> projects, List<String> education, List<String> experience,
                                List<String> preferredRoles) {
            this.skills = skills;
            this.programmingLanguages = programmingLanguages;
            this.frameworks = frameworks;
            this.projects = projects;
            this.education = education;
            this.experience = experience;
            this.preferredRoles = preferredRoles;
        }

        public List<String> getSkills() { return skills; }
        public List<String> getProgrammingLanguages() { return programmingLanguages; }
        public List<String> getFrameworks() { return frameworks; }
        public List<String> getProjects() { return projects; }
        public List<String> getEducation() { return education; }
        public List<String> getExperience() { return experience; }
        public List<String> getPreferredRoles() { return preferredRoles; }
    }

    // --- Dictionaries (canonical display forms; matched case-insensitively). Order is stable for tests. ---

    private static final List<String> PROGRAMMING_LANGUAGES = Arrays.asList(
            "Java", "Python", "C++", "C#", "C", "JavaScript", "TypeScript", "Go", "Rust", "Kotlin",
            "Swift", "Ruby", "PHP", "Scala", "R", "SQL", "MATLAB", "Bash", "Perl", "Objective-C", "Dart");

    private static final List<String> FRAMEWORKS = Arrays.asList(
            "Spring Boot", "Spring", "Hibernate", "React Native", "React", "Angular", "Vue.js", "Svelte",
            "Next.js", "Express.js", "Node.js", "Django", "Flask", "FastAPI", "ASP.NET", ".NET",
            "Ruby on Rails", "Laravel", "TensorFlow", "PyTorch", "Keras", "Pandas", "NumPy",
            "scikit-learn", "Spark", "Hadoop", "Kafka", "JUnit", "Jest", "Cypress", "Selenium",
            "Bootstrap", "Tailwind", "jQuery", "Flutter");

    private static final List<String> SKILLS = Arrays.asList(
            "Git", "GitHub", "GitLab", "Docker", "Kubernetes", "AWS", "Azure", "GCP", "Google Cloud",
            "Linux", "Unix", "REST", "GraphQL", "gRPC", "CI/CD", "Jenkins", "Terraform", "Ansible",
            "Microservices", "Machine Learning", "Deep Learning", "Data Analysis", "Data Engineering",
            "ETL", "Tableau", "Power BI", "Jira", "Agile", "Scrum", "MongoDB", "PostgreSQL", "MySQL",
            "Oracle", "Redis", "Elasticsearch", "Nginx", "Maven", "Gradle", "HTML", "CSS", "OAuth", "JWT");

    private static final List<String> ROLE_TITLES = Arrays.asList(
            "Software Development Engineer", "Software Engineer", "Software Developer",
            "Full Stack Developer", "Full-Stack Developer", "Backend Developer", "Frontend Developer",
            "Data Engineer", "Data Analyst", "Data Scientist", "Machine Learning Engineer",
            "DevOps Engineer", "Web Developer", "Business Analyst", "Systems Engineer", "QA Engineer");

    // Precompiled boundary-aware patterns, preserving dictionary order.
    private static final Map<String, Pattern> LANG_PATTERNS = compile(PROGRAMMING_LANGUAGES);
    private static final Map<String, Pattern> FRAMEWORK_PATTERNS = compile(FRAMEWORKS);
    private static final Map<String, Pattern> SKILL_PATTERNS = compile(SKILLS);
    private static final Map<String, Pattern> ROLE_PATTERNS = compile(ROLE_TITLES);

    // Section headings. Value = which section body to collect (null = a heading we recognize
    // only so it terminates the previous section, e.g. "Skills", "Summary").
    private enum Section { EDUCATION, EXPERIENCE, PROJECTS }

    private static final Map<String, Section> HEADINGS = new LinkedHashMap<>();
    static {
        HEADINGS.put("education", Section.EDUCATION);
        HEADINGS.put("academic", Section.EDUCATION);
        HEADINGS.put("academics", Section.EDUCATION);
        HEADINGS.put("qualifications", Section.EDUCATION);
        HEADINGS.put("experience", Section.EXPERIENCE);
        HEADINGS.put("work experience", Section.EXPERIENCE);
        HEADINGS.put("professional experience", Section.EXPERIENCE);
        HEADINGS.put("employment", Section.EXPERIENCE);
        HEADINGS.put("work history", Section.EXPERIENCE);
        HEADINGS.put("internship", Section.EXPERIENCE);
        HEADINGS.put("internships", Section.EXPERIENCE);
        HEADINGS.put("projects", Section.PROJECTS);
        HEADINGS.put("academic projects", Section.PROJECTS);
        HEADINGS.put("personal projects", Section.PROJECTS);
        HEADINGS.put("key projects", Section.PROJECTS);
        // Recognized-but-ignored headings (terminate a running section):
        for (String other : Arrays.asList("skills", "technical skills", "summary", "objective", "profile",
                "certifications", "certification", "achievements", "awards", "contact", "interests",
                "hobbies", "languages", "references", "publications", "coursework", "activities",
                "extracurricular", "volunteer", "links", "about")) {
            HEADINGS.put(other, null);
        }
    }

    private static final int MAX_ENTRIES_PER_SECTION = 40;
    private static final int MAX_ENTRY_LENGTH = 500;

    public ExtractedProfile extract(String resumeText) {
        String text = resumeText == null ? "" : resumeText;

        List<String> languages = matchDictionary(text, LANG_PATTERNS);
        List<String> frameworks = matchDictionary(text, FRAMEWORK_PATTERNS);
        List<String> skills = matchDictionary(text, SKILL_PATTERNS);
        List<String> roles = matchDictionary(text, ROLE_PATTERNS);

        Map<Section, List<String>> sections = parseSections(text);

        return new ExtractedProfile(
                skills,
                languages,
                frameworks,
                sections.getOrDefault(Section.PROJECTS, new ArrayList<>()),
                sections.getOrDefault(Section.EDUCATION, new ArrayList<>()),
                sections.getOrDefault(Section.EXPERIENCE, new ArrayList<>()),
                roles);
    }

    /** Returns dictionary terms whose whole-word/phrase pattern is found in the text, in dictionary order. */
    private List<String> matchDictionary(String text, Map<String, Pattern> patterns) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : patterns.entrySet()) {
            if (e.getValue().matcher(text).find()) {
                found.add(e.getKey());
            }
        }
        return found;
    }

    private Map<Section, List<String>> parseSections(String text) {
        Map<Section, List<String>> result = new LinkedHashMap<>();
        String[] lines = text.split("\\r?\\n");
        Section current = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            Section headingMatch = headingFor(line);
            boolean isRecognizedHeading = isHeadingLine(line);
            if (isRecognizedHeading) {
                // Entering a new (possibly ignored) section: stop collecting the previous one.
                current = headingMatch; // null when heading is recognized-but-ignored
                continue;
            }

            if (current != null) {
                List<String> bucket = result.computeIfAbsent(current, k -> new ArrayList<>());
                if (bucket.size() >= MAX_ENTRIES_PER_SECTION) continue;
                String entry = cleanEntry(line);
                if (entry.length() >= 2) bucket.add(entry);
            }
        }
        return result;
    }

    /** A short line that matches a known heading keyword (equals or "keyword" + separator). */
    private boolean isHeadingLine(String line) {
        return headingKey(line) != null;
    }

    private Section headingFor(String line) {
        String key = headingKey(line);
        return key == null ? null : HEADINGS.get(key);
    }

    private String headingKey(String line) {
        if (line.length() > 40) return null;
        String norm = line.toLowerCase()
                .replaceAll("[^a-z& ]", " ")   // drop bullets, digits, punctuation
                .replaceAll("\\s+", " ")
                .trim();
        if (norm.isEmpty()) return null;
        for (String key : HEADINGS.keySet()) {
            if (norm.equals(key)) return key;
            // "work experience & internships" style — heading followed by a separator word/&
            if (norm.startsWith(key + " ") || norm.startsWith(key + "&")) return key;
        }
        return null;
    }

    private String cleanEntry(String line) {
        // Strip common leading bullet markers, keep the rest verbatim.
        String e = line.replaceFirst("^[\\-\\*\\u2022\\u25CF\\u25AA\\u00B7\\uF0B7\\s]+", "").trim();
        if (e.length() > MAX_ENTRY_LENGTH) e = e.substring(0, MAX_ENTRY_LENGTH).trim();
        return e;
    }

    /** Builds boundary-aware, case-insensitive patterns; internal spaces allow any whitespace run. */
    private static Map<String, Pattern> compile(List<String> terms) {
        Map<String, Pattern> map = new LinkedHashMap<>();
        // De-duplicate while preserving order.
        for (String term : new LinkedHashSet<>(terms)) {
            String[] parts = term.split(" ");
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) body.append("\\s+");
                body.append(Pattern.quote(parts[i]));
            }
            // Boundary rule: a term must not be glued to an alphanumeric or a tech-token
            // char (+ #) on either side, so "c" can't match inside "science" and "spring"
            // can't match inside "springboard". A '.' is treated as a boundary ONLY when it
            // is not itself part of a dotted token: we reject a match preceded by "<alnum>."
            // or followed by ".<alnum>" so "node" won't match inside "node.js" and ".net"
            // won't match inside "asp.net" — yet a term ending a sentence ("Linux.") still
            // matches. Both lookbehinds are fixed-length (Java requires bounded lookbehind).
            String regex = "(?<![A-Za-z0-9+#])(?<![A-Za-z0-9]\\.)" + body + "(?![A-Za-z0-9+#])(?!\\.[A-Za-z0-9])";
            map.put(term, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }
        return map;
    }
}
