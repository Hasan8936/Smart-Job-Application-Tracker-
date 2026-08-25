package com.smartjobtracker.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the deterministic extractor. No Spring context — pure logic.
 * Covers detection, section parsing, word-boundary correctness, and — importantly —
 * the anti-fabrication contract (nothing is returned that isn't in the text).
 */
public class ResumeProfileExtractorTest {

    private final ResumeProfileExtractor extractor = new ResumeProfileExtractor();

    private static final String SAMPLE =
            "John Doe\n" +
            "Software Engineer\n" +
            "\n" +
            "SKILLS\n" +
            "Java, Python, SQL, Docker, AWS, Git, React, Spring Boot\n" +
            "\n" +
            "EDUCATION\n" +
            "B.E. Computer Engineering, Thapar Institute, 2026\n" +
            "CGPA 8.5\n" +
            "\n" +
            "EXPERIENCE\n" +
            "Data Engineering Intern at Acme Corp\n" +
            "- Built ETL pipelines in Python\n" +
            "\n" +
            "PROJECTS\n" +
            "Job Tracker - a full stack app using Spring Boot and React\n";

    @Test
    public void detects_languages_frameworks_and_skills() {
        ResumeProfileExtractor.ExtractedProfile p = extractor.extract(SAMPLE);

        assertThat(p.getProgrammingLanguages()).contains("Java", "Python", "SQL");
        assertThat(p.getFrameworks()).contains("React", "Spring Boot");
        assertThat(p.getSkills()).contains("Docker", "AWS", "Git");
    }

    @Test
    public void parses_sections_into_entries() {
        ResumeProfileExtractor.ExtractedProfile p = extractor.extract(SAMPLE);

        assertThat(p.getEducation()).anyMatch(e -> e.contains("Thapar"));
        assertThat(p.getExperience()).anyMatch(e -> e.contains("Acme") || e.contains("ETL"));
        assertThat(p.getProjects()).anyMatch(e -> e.contains("Job Tracker"));
        // Bullet marker is stripped, remainder kept verbatim.
        assertThat(p.getExperience()).anyMatch(e -> e.startsWith("Built ETL pipelines"));
    }

    @Test
    public void detects_preferred_role_titles_present_in_text() {
        ResumeProfileExtractor.ExtractedProfile p = extractor.extract(SAMPLE);
        assertThat(p.getPreferredRoles()).contains("Software Engineer");
    }

    @Test
    public void never_fabricates_absent_terms() {
        ResumeProfileExtractor.ExtractedProfile p = extractor.extract(SAMPLE);

        // Not mentioned anywhere in the resume:
        assertThat(p.getSkills()).doesNotContain("Kubernetes", "Azure");
        assertThat(p.getFrameworks()).doesNotContain("Angular", "Django");
        assertThat(p.getProgrammingLanguages()).doesNotContain("Go", "Rust");
    }

    @Test
    public void respects_word_boundaries_for_short_and_symbol_tokens() {
        // "C" must not match "science"/"Computer"; "C++" and "R" must match as whole tokens.
        ResumeProfileExtractor.ExtractedProfile p =
                extractor.extract("Skilled in data science with R and C++ on Linux.");

        assertThat(p.getProgrammingLanguages()).contains("R", "C++");
        assertThat(p.getProgrammingLanguages()).doesNotContain("C");
        assertThat(p.getSkills()).contains("Linux");
    }

    @Test
    public void empty_or_null_text_yields_empty_lists() {
        ResumeProfileExtractor.ExtractedProfile empty = extractor.extract("");
        assertThat(empty.getSkills()).isEmpty();
        assertThat(empty.getProgrammingLanguages()).isEmpty();
        assertThat(empty.getEducation()).isEmpty();

        ResumeProfileExtractor.ExtractedProfile nul = extractor.extract(null);
        assertThat(nul.getFrameworks()).isEmpty();
        assertThat(nul.getExperience()).isEmpty();
        assertThat(nul.getPreferredRoles()).isEmpty();
    }
}
