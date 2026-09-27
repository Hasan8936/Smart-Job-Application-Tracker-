package com.smartjobtracker.service;

import com.smartjobtracker.dto.ResumeBuilderDto;
import com.smartjobtracker.dto.ResumeBuilderDto.EducationEntry;
import com.smartjobtracker.dto.ResumeBuilderDto.ExperienceEntry;
import com.smartjobtracker.dto.ResumeBuilderDto.PersonalInfo;
import com.smartjobtracker.dto.ResumeBuilderDto.ProjectEntry;
import com.smartjobtracker.dto.ResumeBuilderDto.Skills;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Renders builder form data into a resume PDF or Overleaf-ready LaTeX for a chosen {@link ResumeTemplate}.
 *
 * <p>Works directly from the structured DTO — company, role, dates and bullets stay separate fields all the
 * way to the page — instead of flattening to text and re-parsing it. Every layout is single-column real text
 * in a standard PDF font, so ATS parsers read it in reading order. User text is printed as entered: nothing is
 * reworded, reformatted or invented.
 */
@Component
public class ResumeTemplateRenderer {

    private static final String EN_DASH = "\u2013";

    // ─── PDF ────────────────────────────────────────────────────────────────────

    /** Per-template typography and spacing for the PDF layout. */
    private record Style(PDType1Font regular, PDType1Font bold, PDType1Font italic,
                         float margin, float nameSize, boolean centeredHeader, float contactSize,
                         float headingSize, float bodySize, float leading, float sectionGap, float entryGap) {}

    private Style style(ResumeTemplate t) {
        return switch (t) {
            case JAKES -> new Style(PDType1Font.TIMES_ROMAN, PDType1Font.TIMES_BOLD, PDType1Font.TIMES_ITALIC,
                    36f, 24f, true, 10f, 12.5f, 10.5f, 1.22f, 9f, 5f);
            case SB2NOV -> new Style(PDType1Font.HELVETICA, PDType1Font.HELVETICA_BOLD, PDType1Font.HELVETICA_OBLIQUE,
                    40f, 18f, false, 9.5f, 11.5f, 9.8f, 1.25f, 9f, 5f);
            case COMPACT -> new Style(PDType1Font.HELVETICA, PDType1Font.HELVETICA_BOLD, PDType1Font.HELVETICA_OBLIQUE,
                    32f, 16f, false, 8.8f, 10f, 9f, 1.2f, 6f, 3f);
        };
    }

    public byte[] renderPdf(ResumeBuilderDto dto, ResumeTemplate template) {
        Style s = style(template);
        try (PDDocument document = new PDDocument()) {
            PDDocumentInformation info = document.getDocumentInformation();
            String name = personalName(dto);
            info.setTitle(name.isEmpty() ? "Resume" : name + " - Resume");
            if (!name.isEmpty()) info.setAuthor(name);

            Canvas c = new Canvas(document, PDRectangle.LETTER, s.margin);
            header(c, s, dto.getPersonalInfo());

            if (notBlank(dto.getSummary())) {
                heading(c, s, "Summary");
                c.paragraph(s.regular, s.bodySize, s.bodySize * s.leading, c.margin, c.width, dto.getSummary().trim());
            }

            List<ExperienceEntry> experience = nonEmptyExperience(dto);
            if (!experience.isEmpty()) {
                heading(c, s, "Experience");
                for (int i = 0; i < experience.size(); i++) {
                    if (i > 0) c.y -= s.entryGap;
                    experienceEntry(c, s, template, experience.get(i));
                }
            }

            List<ProjectEntry> projects = nonEmptyProjects(dto);
            if (!projects.isEmpty()) {
                heading(c, s, "Projects");
                for (int i = 0; i < projects.size(); i++) {
                    if (i > 0) c.y -= s.entryGap;
                    projectEntry(c, s, projects.get(i));
                }
            }

            List<EducationEntry> education = nonEmptyEducation(dto);
            if (!education.isEmpty()) {
                heading(c, s, "Education");
                for (int i = 0; i < education.size(); i++) {
                    if (i > 0) c.y -= s.entryGap;
                    educationEntry(c, s, template, education.get(i));
                }
            }

            List<String[]> skills = skillLines(dto.getSkills());
            if (!skills.isEmpty()) {
                heading(c, s, template == ResumeTemplate.JAKES ? "Technical Skills" : "Skills");
                for (String[] line : skills) c.labeled(s.bold, s.regular, s.bodySize, s.bodySize * s.leading, line[0], line[1]);
            }

            c.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not generate PDF", ex);
        }
    }

    private void header(Canvas c, Style s, PersonalInfo pi) throws IOException {
        String name = pi == null || blank(pi.name()) ? "Your Name" : pi.name().trim();
        float nameLeading = s.nameSize * 1.1f;
        c.ensure(nameLeading);
        c.y -= s.nameSize * 0.8f;
        c.text(s.bold, s.nameSize, s.centeredHeader ? c.centerX(s.bold, s.nameSize, name) : c.margin, name);
        c.y -= s.nameSize * 0.45f;

        List<Link> contacts = contactLinks(pi);
        if (!contacts.isEmpty()) {
            c.y -= s.contactSize * 1.1f;
            c.linkRow(s.regular, s.contactSize, s.contactSize * 1.35f, contacts, s.centeredHeader);
        }
    }

    /** Section title with a rule underneath; the rule sits in the gap below the title, never through text. */
    private void heading(Canvas c, Style s, String title) throws IOException {
        c.y -= s.sectionGap;
        c.ensure(s.headingSize + s.bodySize * 3);
        c.y -= s.headingSize;
        String label = title.toUpperCase(java.util.Locale.ROOT);
        c.text(s.bold, s.headingSize, c.margin, label);
        float ruleY = c.y - s.headingSize * 0.3f;
        c.rule(ruleY);
        c.y = ruleY - 3f;
    }

    private void experienceEntry(Canvas c, Style s, ResumeTemplate t, ExperienceEntry e) throws IOException {
        String dates = dateRange(e.startDate(), e.endDate(), e.current());
        float lead = s.bodySize * s.leading;
        switch (t) {
            case JAKES -> {
                c.twoColumnLine(s.bold, s.regular, s.bodySize, lead, orEmpty(e.role(), e.company()), dates);
                if (notBlank(e.role()) && notBlank(e.company())) c.twoColumnLine(s.italic, s.italic, s.bodySize * 0.95f, lead, e.company().trim(), "");
            }
            case SB2NOV -> {
                c.twoColumnLine(s.bold, s.regular, s.bodySize, lead, orEmpty(e.company(), e.role()), "");
                if (notBlank(e.role()) && notBlank(e.company())) c.twoColumnLine(s.italic, s.italic, s.bodySize * 0.95f, lead, e.role().trim(), dates);
                else if (!dates.isEmpty()) c.twoColumnLine(s.italic, s.italic, s.bodySize * 0.95f, lead, "", dates);
            }
            case COMPACT -> c.twoColumnLine(s.bold, s.regular, s.bodySize, lead, joinNonBlank(" \u2014 ", e.role(), e.company()), dates);
        }
        for (String b : nonBlank(e.bullets())) c.bullet(s.regular, s.bodySize, lead, b);
    }

    private void projectEntry(Canvas c, Style s, ProjectEntry p) throws IOException {
        float lead = s.bodySize * s.leading;
        String name = blank(p.name()) ? "" : p.name().trim();
        String tech = String.join(", ", nonBlank(p.techStack()));
        String date = blank(p.date()) ? "" : p.date().trim();
        c.projectLine(s.bold, s.italic, s.regular, s.bodySize, lead, name, tech, date);
        for (String b : nonBlank(p.description())) c.bullet(s.regular, s.bodySize, lead, b);
        List<Link> links = new ArrayList<>();
        if (notBlank(p.githubUrl())) links.add(Link.of("GitHub: ", p.githubUrl()));
        if (notBlank(p.liveUrl())) links.add(Link.of("Live: ", p.liveUrl()));
        if (!links.isEmpty()) {
            c.ensure(lead);
            c.y -= lead;
            c.linkRow(s.regular, s.bodySize * 0.95f, lead, links, false);
        }
    }

    private void educationEntry(Canvas c, Style s, ResumeTemplate t, EducationEntry e) throws IOException {
        float lead = s.bodySize * s.leading;
        String years = dateRange(e.startYear(), e.endYear(), false);
        String degree = degreeLine(e);
        if (t == ResumeTemplate.COMPACT) {
            c.twoColumnLine(s.bold, s.regular, s.bodySize, lead, joinNonBlank(" — ", e.institution(), degree), years);
            return;
        }
        String institution = blank(e.institution()) ? degree : e.institution().trim();
        c.twoColumnLine(s.bold, s.regular, s.bodySize, lead, institution, t == ResumeTemplate.JAKES ? "" : years);
        if (notBlank(e.institution()) && !degree.isEmpty()) {
            c.twoColumnLine(s.italic, s.italic, s.bodySize * 0.95f, lead, degree, t == ResumeTemplate.JAKES ? years : "");
        } else if (t == ResumeTemplate.JAKES && !years.isEmpty()) {
            c.twoColumnLine(s.italic, s.italic, s.bodySize * 0.95f, lead, "", years);
        }
    }

    // ─── LaTeX ──────────────────────────────────────────────────────────────────

    public String renderLatex(ResumeBuilderDto dto, ResumeTemplate template) {
        StringBuilder sb = new StringBuilder();
        sb.append(switch (template) {
            case JAKES -> JAKES_PREAMBLE;
            case SB2NOV -> SB2NOV_PREAMBLE;
            case COMPACT -> COMPACT_PREAMBLE;
        });
        sb.append("\n\\begin{document}\n\n");

        PersonalInfo pi = dto.getPersonalInfo();
        String name = pi == null || blank(pi.name()) ? "Your Name" : pi.name().trim();
        String contacts = String.join(" $|$ ", contactLinks(pi).stream().map(this::latexLink).toList());
        if (template == ResumeTemplate.JAKES) {
            sb.append("\\begin{center}\n    \\textbf{\\Huge \\scshape ").append(tex(name)).append("} \\\\ \\vspace{1pt}\n");
            if (!contacts.isEmpty()) sb.append("    \\small ").append(contacts).append("\n");
            sb.append("\\end{center}\n\n");
        } else {
            sb.append("\\noindent{\\").append(template == ResumeTemplate.COMPACT ? "Large" : "LARGE")
              .append(" \\textbf{").append(tex(name)).append("}}");
            sb.append("\n\n");
            if (!contacts.isEmpty()) sb.append("\\noindent{\\small ").append(contacts).append("\\par}\n\n");
        }

        if (notBlank(dto.getSummary())) {
            sb.append("\\section{Summary}\n{\\small ").append(tex(dto.getSummary().trim())).append("\\par}\n\n");
        }

        List<ExperienceEntry> experience = nonEmptyExperience(dto);
        if (!experience.isEmpty()) {
            sb.append("\\section{Experience}\n  \\resumeSubHeadingListStart\n");
            for (ExperienceEntry e : experience) {
                String dates = tex(dateRange(e.startDate(), e.endDate(), e.current())).replace(EN_DASH, "--");
                switch (template) {
                    case JAKES -> sb.append("    \\resumeSubheading\n      {").append(tex(orEmpty(e.role(), e.company())))
                            .append("}{").append(dates).append("}\n      {")
                            .append(notBlank(e.role()) ? tex(safeTrim(e.company())) : "").append("}{}\n");
                    case SB2NOV -> sb.append("    \\resumeSubheading\n      {").append(tex(orEmpty(e.company(), e.role())))
                            .append("}{}\n      {").append(notBlank(e.company()) ? tex(safeTrim(e.role())) : "")
                            .append("}{").append(dates).append("}\n");
                    case COMPACT -> sb.append("    \\resumeEntry{").append(tex(joinNonBlank(" --- ", e.role(), e.company())))
                            .append("}{").append(dates).append("}\n");
                }
                appendLatexBullets(sb, e.bullets());
            }
            sb.append("  \\resumeSubHeadingListEnd\n\n");
        }

        List<ProjectEntry> projects = nonEmptyProjects(dto);
        if (!projects.isEmpty()) {
            sb.append("\\section{Projects}\n  \\resumeSubHeadingListStart\n");
            for (ProjectEntry p : projects) {
                String tech = String.join(", ", nonBlank(p.techStack()));
                String title = "\\textbf{" + tex(safeTrim(p.name())) + "}" + (tech.isEmpty() ? "" : " $|$ \\emph{" + tex(tech) + "}");
                String date = tex(safeTrim(p.date()));
                sb.append(template == ResumeTemplate.COMPACT ? "    \\resumeEntry{" : "    \\resumeProjectHeading\n      {")
                  .append(title).append("}{").append(date).append("}\n");
                List<String> items = new ArrayList<>(nonBlank(p.description()));
                List<String> links = new ArrayList<>();
                if (notBlank(p.githubUrl())) links.add("GitHub: " + latexLink(Link.of("", p.githubUrl())));
                if (notBlank(p.liveUrl())) links.add("Live: " + latexLink(Link.of("", p.liveUrl())));
                appendLatexBullets(sb, items, links.isEmpty() ? null : String.join(" $|$ ", links));
            }
            sb.append("  \\resumeSubHeadingListEnd\n\n");
        }

        List<EducationEntry> education = nonEmptyEducation(dto);
        if (!education.isEmpty()) {
            sb.append("\\section{Education}\n  \\resumeSubHeadingListStart\n");
            for (EducationEntry e : education) {
                String years = tex(dateRange(e.startYear(), e.endYear(), false)).replace(EN_DASH, "--");
                String degree = tex(degreeLine(e));
                String institution = tex(safeTrim(e.institution()));
                switch (template) {
                    case JAKES -> sb.append("    \\resumeSubheading\n      {").append(institution).append("}{}\n      {")
                            .append(degree).append("}{").append(years).append("}\n");
                    case SB2NOV -> sb.append("    \\resumeSubheading\n      {").append(institution).append("}{").append(years)
                            .append("}\n      {").append(degree).append("}{}\n");
                    case COMPACT -> sb.append("    \\resumeEntry{").append(tex(joinNonBlank(" --- ", e.institution(), degreeLine(e))))
                            .append("}{").append(years).append("}\n");
                }
            }
            sb.append("  \\resumeSubHeadingListEnd\n\n");
        }

        List<String[]> skills = skillLines(dto.getSkills());
        if (!skills.isEmpty()) {
            sb.append("\\section{").append(template == ResumeTemplate.JAKES ? "Technical Skills" : "Skills").append("}\n")
              .append(" \\begin{itemize}[leftmargin=0.15in, label={}]\n    \\small{\\item{\n");
            for (int i = 0; i < skills.size(); i++) {
                sb.append("     \\textbf{").append(tex(skills.get(i)[0])).append("}{: ").append(tex(skills.get(i)[1])).append("}")
                  .append(i < skills.size() - 1 ? " \\\\\n" : "\n");
            }
            sb.append("    }}\n \\end{itemize}\n\n");
        }

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    private void appendLatexBullets(StringBuilder sb, List<String> bullets) {
        appendLatexBullets(sb, nonBlank(bullets), null);
    }

    /** {@code rawLine} is already-escaped LaTeX appended as a final item (used for project links). */
    private void appendLatexBullets(StringBuilder sb, List<String> bullets, String rawLine) {
        if (bullets.isEmpty() && rawLine == null) return;
        sb.append("      \\resumeItemListStart\n");
        for (String b : bullets) sb.append("        \\resumeItem{").append(tex(b.trim())).append("}\n");
        if (rawLine != null) sb.append("        \\resumeItem{").append(rawLine).append("}\n");
        sb.append("      \\resumeItemListEnd\n");
    }

    private String latexLink(Link link) {
        String label = tex(link.prefix() + link.display());
        if (link.href() == null) return label;
        return "\\href{" + link.href().replace("\\", "").replace("%", "\\%").replace("#", "\\#")
                .replace("{", "").replace("}", "") + "}{\\underline{" + label + "}}";
    }

    /** Escapes LaTeX special characters in user text; en dashes and other Unicode pass through (UTF-8 input). */
    static String tex(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '\\' -> out.append("\\textbackslash{}");
                case '&', '%', '$', '#', '_', '{', '}' -> out.append('\\').append(ch);
                case '~' -> out.append("\\textasciitilde{}");
                case '^' -> out.append("\\textasciicircum{}");
                case '<' -> out.append("\\textless{}");
                case '>' -> out.append("\\textgreater{}");
                case '|' -> out.append("\\textbar{}");
                case '\n', '\r' -> out.append(' ');
                default -> out.append(ch);
            }
        }
        return out.toString();
    }

    // Jake Gutierrez's template (MIT), unchanged apart from the header comment and the example content.
    private static final String JAKES_PREAMBLE = """
            %-------------------------
            % Resume in Latex
            % Template: Jake's Resume by Jake Gutierrez -- https://github.com/jakegut/resume
            % Based off of: https://github.com/sb2nov/resume
            % License : MIT (Copyright (c) 2020 Jake Gutierrez)
            % Generated by Smart Job Tracker -- compile with pdfLaTeX on Overleaf
            %------------------------

            \\documentclass[letterpaper,11pt]{article}

            \\usepackage{latexsym}
            \\usepackage[empty]{fullpage}
            \\usepackage{titlesec}
            \\usepackage{marvosym}
            \\usepackage[usenames,dvipsnames]{color}
            \\usepackage{verbatim}
            \\usepackage{enumitem}
            \\usepackage[hidelinks]{hyperref}
            \\usepackage{fancyhdr}
            \\usepackage[english]{babel}
            \\usepackage{tabularx}
            \\input{glyphtounicode}

            \\pagestyle{fancy}
            \\fancyhf{} % clear all header and footer fields
            \\fancyfoot{}
            \\renewcommand{\\headrulewidth}{0pt}
            \\renewcommand{\\footrulewidth}{0pt}

            % Adjust margins
            \\addtolength{\\oddsidemargin}{-0.5in}
            \\addtolength{\\evensidemargin}{-0.5in}
            \\addtolength{\\textwidth}{1in}
            \\addtolength{\\topmargin}{-.5in}
            \\addtolength{\\textheight}{1.0in}

            \\urlstyle{same}

            \\raggedbottom
            \\raggedright
            \\setlength{\\tabcolsep}{0in}

            % Sections formatting
            \\titleformat{\\section}{
              \\vspace{-4pt}\\scshape\\raggedright\\large
            }{}{0em}{}[\\color{black}\\titlerule \\vspace{-5pt}]

            % Ensure that generate pdf is machine readable/ATS parsable
            \\pdfgentounicode=1

            %-------------------------
            % Custom commands
            \\newcommand{\\resumeItem}[1]{
              \\item\\small{
                {#1 \\vspace{-2pt}}
              }
            }

            \\newcommand{\\resumeSubheading}[4]{
              \\vspace{-2pt}\\item
                \\begin{tabular*}{0.97\\textwidth}[t]{l@{\\extracolsep{\\fill}}r}
                  \\textbf{#1} & #2 \\\\
                  \\textit{\\small#3} & \\textit{\\small #4} \\\\
                \\end{tabular*}\\vspace{-7pt}
            }

            \\newcommand{\\resumeProjectHeading}[2]{
                \\item
                \\begin{tabular*}{0.97\\textwidth}{l@{\\extracolsep{\\fill}}r}
                  \\small#1 & #2 \\\\
                \\end{tabular*}\\vspace{-7pt}
            }

            \\renewcommand\\labelitemii{$\\vcenter{\\hbox{\\tiny$\\bullet$}}$}

            \\newcommand{\\resumeSubHeadingListStart}{\\begin{itemize}[leftmargin=0.15in, label={}]}
            \\newcommand{\\resumeSubHeadingListEnd}{\\end{itemize}}
            \\newcommand{\\resumeItemListStart}{\\begin{itemize}}
            \\newcommand{\\resumeItemListEnd}{\\end{itemize}\\vspace{-5pt}}
            """;

    // Sourabh Bajaj's template (MIT); \\resumeItem takes one argument here (plain bullets) and a
    // \\resumeProjectHeading macro is added so projects can carry a tech stack and date.
    private static final String SB2NOV_PREAMBLE = """
            %-------------------------
            % Resume in LaTeX
            % Template: by Sourabh Bajaj -- https://github.com/sb2nov/resume
            % License : MIT (Copyright (c) Sourabh Bajaj)
            % Generated by Smart Job Tracker -- compile with pdfLaTeX on Overleaf
            %------------------------

            \\documentclass[letterpaper,11pt]{article}

            \\usepackage[empty]{fullpage}
            \\usepackage{titlesec}
            \\usepackage[usenames,dvipsnames]{color}
            \\usepackage{enumitem}
            \\usepackage[hidelinks]{hyperref}
            \\usepackage{fancyhdr}
            \\usepackage[english]{babel}
            \\usepackage{tabularx}
            \\usepackage[scaled]{helvet}
            \\renewcommand{\\familydefault}{\\sfdefault}
            \\usepackage[T1]{fontenc}
            \\input{glyphtounicode}

            \\pagestyle{fancy}
            \\fancyhf{} % Clear all header and footer fields
            \\fancyfoot{}
            \\renewcommand{\\headrulewidth}{0pt}
            \\renewcommand{\\footrulewidth}{0pt}

            % Adjust margins
            \\addtolength{\\oddsidemargin}{-0.5in}
            \\addtolength{\\evensidemargin}{-0.5in}
            \\addtolength{\\textwidth}{1in}
            \\addtolength{\\topmargin}{-.5in}
            \\addtolength{\\textheight}{1.0in}

            \\urlstyle{same}

            \\raggedbottom
            \\raggedright
            \\setlength{\\tabcolsep}{0in}

            % Sections formatting
            \\titleformat{\\section}{
              \\vspace{-4pt}\\scshape\\raggedright\\large
            }{}{0em}{}[\\color{black}\\titlerule \\vspace{-5pt}]

            % Ensure that generated PDF is machine readable/ATS parsable
            \\pdfgentounicode=1

            %-------------------------
            % Custom commands
            \\newcommand{\\resumeItem}[1]{
              \\item\\small{
                {#1 \\vspace{-2pt}}
              }
            }

            \\newcommand{\\resumeSubheading}[4]{
              \\vspace{-1pt}\\item
                \\begin{tabular*}{0.97\\textwidth}[t]{l@{\\extracolsep{\\fill}}r}
                  \\textbf{#1} & #2 \\\\
                  \\textit{\\small #3} & \\textit{\\small #4} \\\\
                \\end{tabular*}\\vspace{-5pt}
            }

            \\newcommand{\\resumeProjectHeading}[2]{
                \\item
                \\begin{tabular*}{0.97\\textwidth}{l@{\\extracolsep{\\fill}}r}
                  \\small#1 & #2 \\\\
                \\end{tabular*}\\vspace{-5pt}
            }

            \\renewcommand{\\labelitemii}{$\\circ$}

            \\newcommand{\\resumeSubHeadingListStart}{\\begin{itemize}[leftmargin=*]}
            \\newcommand{\\resumeSubHeadingListEnd}{\\end{itemize}}
            \\newcommand{\\resumeItemListStart}{\\begin{itemize}}
            \\newcommand{\\resumeItemListEnd}{\\end{itemize}\\vspace{-5pt}}
            """;

    private static final String COMPACT_PREAMBLE = """
            %-------------------------
            % Resume in LaTeX -- Minimal Compact template
            % Generated by Smart Job Tracker -- compile with pdfLaTeX on Overleaf
            %------------------------

            \\documentclass[letterpaper,10pt]{article}

            \\usepackage[margin=0.45in]{geometry}
            \\usepackage{titlesec}
            \\usepackage{enumitem}
            \\usepackage[hidelinks]{hyperref}
            \\usepackage[english]{babel}
            \\usepackage[scaled]{helvet}
            \\renewcommand{\\familydefault}{\\sfdefault}
            \\usepackage[T1]{fontenc}
            \\input{glyphtounicode}
            \\pdfgentounicode=1

            \\pagestyle{empty}
            \\urlstyle{same}
            \\raggedbottom
            \\raggedright
            \\setlength{\\parindent}{0pt}
            \\setlength{\\tabcolsep}{0in}

            \\titleformat{\\section}{\\bfseries\\normalsize}{}{0em}{\\MakeUppercase}[\\titlerule]
            \\titlespacing*{\\section}{0pt}{6pt}{3pt}

            \\newcommand{\\resumeItem}[1]{\\item\\small{#1}}
            \\newcommand{\\resumeEntry}[2]{\\item\\textbf{#1}\\hfill #2}
            \\newcommand{\\resumeSubHeadingListStart}{\\begin{itemize}[leftmargin=0in, label={}, itemsep=2pt, topsep=0pt]}
            \\newcommand{\\resumeSubHeadingListEnd}{\\end{itemize}}
            \\newcommand{\\resumeItemListStart}{\\begin{itemize}[leftmargin=0.18in, itemsep=0pt, topsep=1pt, parsep=0pt]}
            \\newcommand{\\resumeItemListEnd}{\\end{itemize}}
            """;

    // ─── Shared data shaping ────────────────────────────────────────────────────

    /** Contact item: prefix + display text, with an optional link target. */
    private record Link(String prefix, String display, String href) {
        static Link of(String prefix, String url) {
            String u = url.trim();
            String href = u.matches("(?i)^[a-z][a-z0-9+.-]*:.*") ? u : "https://" + u;
            return new Link(prefix, u.replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "").replaceFirst("/$", ""), href);
        }
    }

    private List<Link> contactLinks(PersonalInfo pi) {
        List<Link> out = new ArrayList<>();
        if (pi == null) return out;
        if (notBlank(pi.phone())) out.add(new Link("", pi.phone().trim(), null));
        if (notBlank(pi.email())) out.add(new Link("", pi.email().trim(), "mailto:" + pi.email().trim()));
        if (notBlank(pi.location())) out.add(new Link("", pi.location().trim(), null));
        if (notBlank(pi.linkedin())) out.add(Link.of("", pi.linkedin()));
        if (notBlank(pi.github())) out.add(Link.of("", pi.github()));
        if (notBlank(pi.leetcode())) out.add(Link.of("", pi.leetcode()));
        if (notBlank(pi.website())) out.add(Link.of("", pi.website()));
        return out;
    }

    private String dateRange(String start, String end, boolean current) {
        String s = safeTrim(start);
        String e = current ? "Present" : safeTrim(end);
        if (s.isEmpty()) return e;
        if (e.isEmpty()) return s;
        return s + " " + EN_DASH + " " + e;
    }

    private String degreeLine(EducationEntry e) {
        String degree = safeTrim(e.degree());
        String field = safeTrim(e.field());
        String base = degree.isEmpty() ? field : (field.isEmpty() ? degree : degree + " in " + field);
        return notBlank(e.gpa()) ? joinNonBlank("; ", base, "GPA: " + e.gpa().trim()) : base;
    }

    private List<String[]> skillLines(Skills skills) {
        List<String[]> lines = new ArrayList<>();
        if (skills == null) return lines;
        addSkillLine(lines, "Languages", skills.languages());
        addSkillLine(lines, "Frameworks", skills.frameworks());
        addSkillLine(lines, "Developer Tools", skills.tools());
        addSkillLine(lines, "Other", skills.other());
        return lines;
    }

    private void addSkillLine(List<String[]> lines, String label, List<String> items) {
        List<String> values = nonBlank(items);
        if (!values.isEmpty()) lines.add(new String[]{label, String.join(", ", values)});
    }

    private List<ExperienceEntry> nonEmptyExperience(ResumeBuilderDto dto) {
        if (dto.getExperience() == null) return List.of();
        return dto.getExperience().stream().filter(e -> e != null
                && (notBlank(e.company()) || notBlank(e.role()) || !nonBlank(e.bullets()).isEmpty())).toList();
    }

    private List<ProjectEntry> nonEmptyProjects(ResumeBuilderDto dto) {
        if (dto.getProjects() == null) return List.of();
        return dto.getProjects().stream().filter(p -> p != null
                && (notBlank(p.name()) || !nonBlank(p.description()).isEmpty())).toList();
    }

    private List<EducationEntry> nonEmptyEducation(ResumeBuilderDto dto) {
        if (dto.getEducation() == null) return List.of();
        return dto.getEducation().stream().filter(e -> e != null
                && (notBlank(e.institution()) || notBlank(e.degree()) || notBlank(e.field()))).toList();
    }

    private static String personalName(ResumeBuilderDto dto) {
        PersonalInfo pi = dto.getPersonalInfo();
        return pi == null || pi.name() == null ? "" : pi.name().trim();
    }

    private static List<String> nonBlank(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(ResumeTemplateRenderer::notBlank).map(String::trim).toList();
    }

    private static String orEmpty(String preferred, String fallback) {
        return notBlank(preferred) ? preferred.trim() : safeTrim(fallback);
    }

    private static String joinNonBlank(String sep, String... values) {
        List<String> parts = new ArrayList<>();
        for (String v : values) if (notBlank(v)) parts.add(v.trim());
        return String.join(sep, parts);
    }

    private static String safeTrim(String s) { return s == null ? "" : s.trim(); }
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static boolean blank(String s) { return !notBlank(s); }

    // ─── Drawing primitives ─────────────────────────────────────────────────────

    /** Page cursor: y is the baseline of the most recently drawn line; callers step y down before drawing. */
    private static final class Canvas {
        final PDDocument document; final PDRectangle size; final float margin; final float width;
        PDPage page; PDPageContentStream stream; float y;

        Canvas(PDDocument document, PDRectangle size, float margin) throws IOException {
            this.document = document; this.size = size; this.margin = margin; this.width = size.getWidth() - 2 * margin;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            page = new PDPage(size);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = size.getHeight() - margin;
        }

        void close() throws IOException { stream.close(); }

        /** Starts a new page if the next {@code needed} points would run into the bottom margin. */
        void ensure(float needed) throws IOException {
            if (y - needed < margin) newPage();
        }

        void text(PDType1Font font, float fontSize, float x, String text) throws IOException {
            if (text.isEmpty()) return;
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(x, y);
            stream.showText(safe(text, font));
            stream.endText();
        }

        float centerX(PDType1Font font, float fontSize, String text) {
            return margin + Math.max(0, (width - textWidth(text, font, fontSize)) / 2f);
        }

        void rule(float atY) throws IOException {
            stream.setStrokingColor(0f, 0f, 0f);
            stream.setLineWidth(0.6f);
            stream.moveTo(margin, atY);
            stream.lineTo(margin + width, atY);
            stream.stroke();
        }

        /** Left text and right-aligned text on one line; the left side wraps if they would collide. */
        void twoColumnLine(PDType1Font leftFont, PDType1Font rightFont, float fontSize, float leading,
                           String left, String right) throws IOException {
            float rightWidth = right.isEmpty() ? 0 : textWidth(right, rightFont, fontSize);
            float leftMax = right.isEmpty() ? width : width - rightWidth - 12f;
            List<String> lines = wrap(left, leftFont, fontSize, Math.max(80f, leftMax));
            for (int i = 0; i < lines.size(); i++) {
                ensure(leading);
                y -= leading;
                text(leftFont, fontSize, margin, lines.get(i));
                if (i == 0 && !right.isEmpty()) text(rightFont, fontSize, margin + width - rightWidth, right);
            }
        }

        /** "Name | tech stack" with the name bold and the stack italic, date right-aligned. */
        void projectLine(PDType1Font bold, PDType1Font italic, PDType1Font regular, float fontSize, float leading,
                         String name, String tech, String date) throws IOException {
            String sep = tech.isEmpty() || name.isEmpty() ? "" : " | ";
            float dateWidth = date.isEmpty() ? 0 : textWidth(date, regular, fontSize);
            float available = width - (date.isEmpty() ? 0 : dateWidth + 12f);
            float nameWidth = textWidth(name + sep, bold, fontSize);
            if (nameWidth + textWidth(tech, italic, fontSize) > available) {
                // Too long for one line: name (+ date) first, then the stack on its own wrapped line(s).
                twoColumnLine(bold, regular, fontSize, leading, name, date);
                if (!tech.isEmpty()) for (String line : wrap(tech, italic, fontSize, width)) {
                    ensure(leading); y -= leading; text(italic, fontSize, margin, line);
                }
                return;
            }
            ensure(leading);
            y -= leading;
            text(bold, fontSize, margin, name + sep);
            text(italic, fontSize, margin + nameWidth, tech);
            if (!date.isEmpty()) text(regular, fontSize, margin + width - dateWidth, date);
        }

        void paragraph(PDType1Font font, float fontSize, float leading, float x, float maxWidth, String text) throws IOException {
            for (String line : wrap(text, font, fontSize, maxWidth)) {
                ensure(leading); y -= leading; text(font, fontSize, x, line);
            }
        }

        /** Bullet glyph with a hanging indent so wrapped lines align under the text. */
        void bullet(PDType1Font font, float fontSize, float leading, String text) throws IOException {
            float indent = fontSize * 1.3f;
            List<String> lines = wrap(text, font, fontSize, width - indent - 4f);
            for (int i = 0; i < lines.size(); i++) {
                ensure(leading);
                y -= leading;
                if (i == 0) text(font, fontSize, margin + 4f, "\u2022");
                text(font, fontSize, margin + 4f + indent, lines.get(i));
            }
        }

        /** "Label: value" with a bold label and a hanging indent for wrapped values. */
        void labeled(PDType1Font bold, PDType1Font regular, float fontSize, float leading, String label, String value) throws IOException {
            String prefix = label + ": ";
            float prefixWidth = textWidth(prefix, bold, fontSize);
            List<String> lines = wrap(value, regular, fontSize, width - prefixWidth);
            for (int i = 0; i < lines.size(); i++) {
                ensure(leading);
                y -= leading;
                if (i == 0) text(bold, fontSize, margin, prefix);
                text(regular, fontSize, margin + prefixWidth, lines.get(i));
            }
        }

        /** Items joined by " | ", wrapping between items, each linked item clickable. */
        void linkRow(PDType1Font font, float fontSize, float leading, List<Link> items, boolean centered) throws IOException {
            String sep = "  |  ";
            float sepWidth = textWidth(sep, font, fontSize);
            List<List<Link>> rows = new ArrayList<>();
            List<Link> row = new ArrayList<>();
            float rowWidth = 0;
            for (Link item : items) {
                float w = textWidth(item.prefix() + item.display(), font, fontSize);
                float add = row.isEmpty() ? w : sepWidth + w;
                if (!row.isEmpty() && rowWidth + add > width) { rows.add(row); row = new ArrayList<>(); rowWidth = 0; add = w; }
                row.add(item); rowWidth += add;
            }
            if (!row.isEmpty()) rows.add(row);

            for (int r = 0; r < rows.size(); r++) {
                List<Link> current = rows.get(r);
                float total = 0;
                for (int i = 0; i < current.size(); i++) {
                    total += textWidth(current.get(i).prefix() + current.get(i).display(), font, fontSize) + (i > 0 ? sepWidth : 0);
                }
                if (r > 0) { ensure(leading); y -= leading; }
                float x = centered ? margin + Math.max(0, (width - total) / 2f) : margin;
                for (int i = 0; i < current.size(); i++) {
                    if (i > 0) { text(font, fontSize, x, sep); x += sepWidth; }
                    Link item = current.get(i);
                    String label = item.prefix() + item.display();
                    float w = textWidth(label, font, fontSize);
                    text(font, fontSize, x, label);
                    if (item.href() != null) annotate(x, w, fontSize, item.href());
                    x += w;
                }
            }
        }

        private void annotate(float x, float w, float fontSize, String href) throws IOException {
            PDAnnotationLink link = new PDAnnotationLink();
            PDBorderStyleDictionary border = new PDBorderStyleDictionary();
            border.setWidth(0);
            link.setBorderStyle(border);
            link.setRectangle(new PDRectangle(x, y - fontSize * 0.25f, w, fontSize * 1.15f));
            PDActionURI action = new PDActionURI();
            action.setURI(href);
            link.setAction(action);
            page.getAnnotations().add(link);
        }

        static List<String> wrap(String text, PDType1Font font, float fontSize, float maxWidth) {
            List<String> out = new ArrayList<>();
            if (text == null || text.isBlank()) { out.add(""); return out; }
            StringBuilder line = new StringBuilder();
            for (String word : text.trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && textWidth(candidate, font, fontSize) > maxWidth) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            out.add(line.toString());
            return out;
        }

        static float textWidth(String text, PDType1Font font, float fontSize) {
            try { return font.getStringWidth(safe(text, font)) / 1000f * fontSize; }
            catch (IOException | IllegalArgumentException ex) { return text.length() * fontSize * 0.5f; }
        }

        private static final Set<Character> WINANSI_EXTRAS = Set.of(
                '\u2018', '\u2019', '\u201C', '\u201D', '\u2013', '\u2014', '\u2022', '\u2026', '\u2122', '\u20AC');

        /**
         * The standard 14 fonts only cover WinAnsiEncoding; anything else would throw in showText. Map common
         * look-alikes to ASCII and replace the rest with '?', so a stray emoji never breaks the whole export.
         */
        static String safe(String text, PDType1Font font) {
            StringBuilder out = new StringBuilder(text.length());
            for (char ch : text.toCharArray()) {
                if (ch == '\t') out.append(' ');
                else if (ch < 32 || (ch >= 127 && ch <= 159)) continue;
                else if (ch <= 255 || WINANSI_EXTRAS.contains(ch)) out.append(ch);
                else if (ch == '\u2212' || ch == '\u2010' || ch == '\u2011') out.append('-');
                else if (ch == '\u00A0' || ch == '\u2009' || ch == '\u202F') out.append(' ');
                else out.append('?');
            }
            return out.toString();
        }
    }
}
