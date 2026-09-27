package com.smartjobtracker.service;

import com.smartjobtracker.dto.ResumeBuilderDto;
import com.smartjobtracker.dto.ResumeBuilderDto.EducationEntry;
import com.smartjobtracker.dto.ResumeBuilderDto.ExperienceEntry;
import com.smartjobtracker.dto.ResumeBuilderDto.PersonalInfo;
import com.smartjobtracker.dto.ResumeBuilderDto.ProjectEntry;
import com.smartjobtracker.dto.ResumeBuilderDto.Skills;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Checks each template's PDF the way an ATS would read it (extracted text, reading order, fonts) and
 * that the generated LaTeX is structurally sound, escaped, and keeps the template's license notice.
 */
class ResumeTemplateRendererTest {

    private final ResumeTemplateRenderer renderer = new ResumeTemplateRenderer();

    private static ResumeBuilderDto sample() {
        ResumeBuilderDto d = new ResumeBuilderDto();
        d.setTargetRole("Backend Engineer");
        d.setPersonalInfo(new PersonalInfo("Aisha Khan", "aisha.khan@example.com", "+91 98765 43210", "Bengaluru, India",
                "https://linkedin.com/in/aishakhan", "github.com/aishak", null, null));
        d.setSummary("Backend engineer with 2 years of experience building Spring Boot services.");
        d.setExperience(List.of(
                new ExperienceEntry("Acme Fintech", "Software Engineer", "Jul 2023", "", true,
                        List.of("Built a payment reconciliation service", "Cut p95 latency by 70% with Redis & indexes")),
                new ExperienceEntry("Beta Labs", "Backend Intern", "01/2023", "06/2023", false,
                        List.of("Implemented JWT authentication for an admin API"))));
        d.setProjects(List.of(new ProjectEntry("Smart Job Tracker", List.of("Job tracker with resume matching"),
                List.of("Java", "Spring Boot"), "https://github.com/aishak/sjt", null, "2024")));
        d.setEducation(List.of(new EducationEntry("VIT University", "B.Tech", "Computer Science", "2019", "2023", "8.6/10")));
        d.setSkills(new Skills(List.of("Java", "SQL"), List.of("Spring Boot"), List.of("Docker"), List.of()));
        return d;
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void pdf_is_single_page_readable_text_in_resume_order(ResumeTemplate template) throws Exception {
        byte[] pdf = renderer.renderPdf(sample(), template);
        String text = text(pdf);

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(doc.getDocumentInformation().getTitle()).isEqualTo("Aisha Khan - Resume");
            // email, LinkedIn, GitHub and the project repo are clickable
            assertThat(doc.getPage(0).getAnnotations()).hasSize(4);
        }

        assertThat(text).startsWith("Aisha Khan");
        int summary = text.indexOf("SUMMARY");
        int experience = text.indexOf("EXPERIENCE");
        int projects = text.indexOf("PROJECTS");
        int education = text.indexOf("EDUCATION");
        int skills = text.indexOf("SKILLS");
        assertThat(summary).isPositive();
        assertThat(experience).isGreaterThan(summary);
        assertThat(projects).isGreaterThan(experience);
        assertThat(education).isGreaterThan(projects);
        assertThat(skills).isGreaterThan(education);

        // user values survive verbatim, including numeric date formats
        assertThat(text).contains("Jul 2023 \u2013 Present", "01/2023 \u2013 06/2023", "2019 \u2013 2023",
                "Acme Fintech", "Software Engineer", "Beta Labs", "Backend Intern",
                "Cut p95 latency by 70% with Redis & indexes", "GPA: 8.6/10", "linkedin.com/in/aishakhan");
        // no dangling separators left over from joining empty fields
        for (String line : text.split("\\R")) {
            assertThat(line.strip()).doesNotEndWith("|").doesNotStartWith("|");
        }
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void pdf_uses_only_standard_fonts(ResumeTemplate template) throws Exception {
        try (PDDocument doc = PDDocument.load(renderer.renderPdf(sample(), template))) {
            PDPage page = doc.getPage(0);
            List<String> fonts = new ArrayList<>();
            for (COSName name : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(name);
                assertThat(font).isInstanceOf(PDType1Font.class);
                assertThat(((PDType1Font) font).isStandard14()).isTrue();
                fonts.add(font.getName());
            }
            assertThat(fonts).isNotEmpty();
        }
    }

    @Test
    void long_resume_paginates_and_unsupported_glyphs_do_not_break_export() throws Exception {
        ResumeBuilderDto d = sample();
        List<String> bullets = new ArrayList<>(Collections.nCopies(80, "Shipped a feature \uD83D\uDE80 with measurable impact"));
        d.setExperience(List.of(new ExperienceEntry("Acme", "Engineer", "2020", "2024", false, bullets)));

        for (ResumeTemplate t : ResumeTemplate.values()) {
            byte[] pdf = renderer.renderPdf(d, t);
            try (PDDocument doc = PDDocument.load(pdf)) {
                assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            }
            assertThat(text(pdf)).contains("Shipped a feature").doesNotContain("\uD83D\uDE80");
        }
    }

    @Test
    void empty_entries_are_skipped_and_missing_name_uses_placeholder() throws Exception {
        ResumeBuilderDto d = new ResumeBuilderDto();
        d.setExperience(List.of(new ExperienceEntry("", "", "", "", false, List.of(""))));
        String text = text(renderer.renderPdf(d, ResumeTemplate.JAKES));
        assertThat(text).contains("Your Name").doesNotContain("EXPERIENCE");
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void latex_is_structurally_balanced_and_escaped(ResumeTemplate template) {
        String tex = renderer.renderLatex(sample(), template);

        assertThat(tex).contains("\\begin{document}", "\\end{document}", "\\pdfgentounicode=1");
        assertThat(count(tex, "\\begin{itemize}")).isEqualTo(count(tex, "\\end{itemize}"));
        assertThat(count(tex, "ListStart")).isEqualTo(count(tex, "ListEnd"));
        int open = count(tex, "{") - count(tex, "\\{");
        int close = count(tex, "}") - count(tex, "\\}");
        assertThat(open).isEqualTo(close);
        // special characters escaped; full link targets preserved
        assertThat(tex).contains("Redis \\& indexes", "70\\%", "\\href{https://linkedin.com/in/aishakhan}",
                "\\href{https://github.com/aishak}", "\\href{mailto:aisha.khan@example.com}");
        assertThat(tex).contains("Jul 2023 -- Present", "01/2023 -- 06/2023");
        // GPA stays with the degree instead of becoming its own section
        assertThat(tex).doesNotContainIgnoringCase("\\section{GPA");
        assertThat(tex).contains("GPA: 8.6/10");
    }

    @Test
    void latex_keeps_license_notices_and_template_macros() {
        assertThat(renderer.renderLatex(sample(), ResumeTemplate.JAKES))
                .contains("Jake Gutierrez", "License : MIT", "\\resumeSubheading\n      {Software Engineer}{Jul 2023 -- Present}\n      {Acme Fintech}{}");
        assertThat(renderer.renderLatex(sample(), ResumeTemplate.SB2NOV))
                .contains("Sourabh Bajaj", "License : MIT", "\\resumeSubheading\n      {Acme Fintech}{}\n      {Software Engineer}{Jul 2023 -- Present}");
        assertThat(renderer.renderLatex(sample(), ResumeTemplate.COMPACT))
                .contains("\\resumeEntry{Software Engineer --- Acme Fintech}{Jul 2023 -- Present}")
                .doesNotContain("MIT");
    }

    @Test
    void tex_escapes_every_special_character() {
        assertThat(ResumeTemplateRenderer.tex("a&b%c$d#e_f{g}h~i^j\\k|l"))
                .isEqualTo("a\\&b\\%c\\$d\\#e\\_f\\{g\\}h\\textasciitilde{}i\\textasciicircum{}j\\textbackslash{}k\\textbar{}l");
    }

    @Test
    void template_ids_resolve_case_insensitively_with_default() {
        assertThat(ResumeTemplate.fromId(null)).contains(ResumeTemplate.JAKES);
        assertThat(ResumeTemplate.fromId(" SB2NOV ")).contains(ResumeTemplate.SB2NOV);
        assertThat(ResumeTemplate.fromId("compact")).contains(ResumeTemplate.COMPACT);
        assertThat(ResumeTemplate.fromId("fancy-two-column")).isEmpty();
        assertThatCode(() -> ResumeTemplate.valueOf("JAKES")).doesNotThrowAnyException();
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) n++;
        return n;
    }
}
