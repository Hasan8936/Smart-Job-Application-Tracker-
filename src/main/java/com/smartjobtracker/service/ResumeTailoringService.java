package com.smartjobtracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.ResumeTailoringDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeTailoringService {
    private static final TypeReference<List<Long>> IDS = new TypeReference<>() {};
    private final ResumeRepository resumes;
    private final ResumeProfileExtractor extractor;
    private final TailoringSessionRepository sessions;
    private final TailoringSuggestionRepository suggestions;
    private final ResumeVersionRepository versions;
    private final ObjectMapper mapper;
    private final ResumeTailoringProvider fallback;
    private final ResumeTailoringProvider gemini;
    private final com.smartjobtracker.jobs.discovery.JobSkillExtractor skillExtractor;
    private final com.smartjobtracker.config.AiMatchingConfig aiConfig;
    private final DeepMatchAnalysisRepository deepMatches;

    @Autowired
    public ResumeTailoringService(ResumeRepository resumes, ResumeProfileExtractor extractor, TailoringSessionRepository sessions,
                                  TailoringSuggestionRepository suggestions, ResumeVersionRepository versions, ObjectMapper mapper,
                                  @Qualifier("ruleBasedResumeTailoringProvider") ResumeTailoringProvider fallback,
                                  @Qualifier("geminiResumeTailoringProvider") ResumeTailoringProvider gemini,
                                  com.smartjobtracker.jobs.discovery.JobSkillExtractor skillExtractor,
                                  com.smartjobtracker.config.AiMatchingConfig aiConfig,
                                  DeepMatchAnalysisRepository deepMatches) {
        this.resumes = resumes; this.extractor = extractor; this.sessions = sessions; this.suggestions = suggestions; this.versions = versions;
        this.mapper = mapper; this.fallback = fallback; this.gemini = gemini; this.skillExtractor = skillExtractor; this.aiConfig = aiConfig;
        this.deepMatches = deepMatches;
    }

    public ResumeTailoringService(ResumeRepository resumes, ResumeProfileExtractor extractor, TailoringSessionRepository sessions,
                                  TailoringSuggestionRepository suggestions, ResumeVersionRepository versions, ObjectMapper mapper,
                                  ResumeTailoringProvider fallback, ResumeTailoringProvider gemini,
                                  com.smartjobtracker.jobs.discovery.JobSkillExtractor skillExtractor,
                                  com.smartjobtracker.config.AiMatchingConfig aiConfig) {
        this(resumes, extractor, sessions, suggestions, versions, mapper, fallback, gemini, skillExtractor, aiConfig, null);
    }

    @Transactional
    public ResumeTailoringDtos.Analysis analyze(Long userId, ResumeTailoringDtos.AnalyzeRequest request) {
        Resume resume = resumes.findById(request.resumeId()).filter(item -> Objects.equals(item.getUserId(), userId)).orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        String resumeText = resume.getExtractedText() == null ? "" : resume.getExtractedText();
        List<String> atsKeywords = skillExtractor.extract(null, request.jobDescription()).stream().map(com.smartjobtracker.model.JobSkill::getName).distinct().toList();
        String providerJobDescription = request.jobDescription();
        if (request.deepMatchAnalysisId() != null && deepMatches != null) {
            DeepMatchAnalysis deepMatch = deepMatches.findByIdAndUserId(request.deepMatchAnalysisId(), userId)
                .filter(item -> Objects.equals(item.getResumeId(), resume.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Deep match analysis not found"));
            atsKeywords = new ArrayList<>(atsKeywords);
            atsKeywords.addAll(fromJsonStrings(deepMatch.getMissingKeywords()));
            providerJobDescription += "\nRECRUITER RED FLAGS TO ADDRESS:\n" + String.join(", ", fromJsonStrings(deepMatch.getRedFlags()));
        }
        ResumeProfileExtractor.ExtractedProfile profile = extractor.extract(resumeText);
        TailoringSession session = new TailoringSession(); session.setUserId(userId); session.setSourceResumeId(resume.getId()); session.setJobDescription(request.jobDescription());
        session = sessions.save(session);
        boolean geminiWasPrimary = "gemini".equalsIgnoreCase(aiConfig.getProvider());
        List<ResumeTailoringProvider.Proposal> proposals;
        try { proposals = geminiWasPrimary ? gemini.suggest(resumeText, providerJobDescription, atsKeywords) : fallback.suggest(resumeText, providerJobDescription, atsKeywords); }
        catch (RuntimeException ex) { proposals = fallback.suggest(resumeText, providerJobDescription, atsKeywords); geminiWasPrimary = false; }
        int saved = saveGrounded(session.getId(), proposals, resumeText);
        // H2: Gemini responded but every proposal failed the grounding check — use rule-based fallback
        if (saved == 0 && geminiWasPrimary) {
            saveGrounded(session.getId(), fallback.suggest(resumeText, providerJobDescription, atsKeywords), resumeText);
        }
        return analysis(session, profile, atsKeywords);
    }

    @Transactional
    public ResumeTailoringDtos.Suggestion decide(Long userId, Long suggestionId, TailoringSuggestionDecision decision) {
        TailoringSuggestion suggestion = suggestions.findById(suggestionId).orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));
        TailoringSession session = sessions.findByIdAndUserId(suggestion.getSessionId(), userId).orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));
        suggestion.setDecision(decision); return toSuggestion(suggestions.save(suggestion));
    }

    @Transactional
    public ResumeTailoringDtos.Version createVersion(Long userId, Long sessionId) {
        TailoringSession session = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(() -> new IllegalArgumentException("Tailoring session not found"));
        Resume resume = resumes.findById(session.getSourceResumeId()).filter(item -> Objects.equals(item.getUserId(), userId)).orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        List<TailoringSuggestion> accepted = suggestions.findBySessionIdOrderByIdAsc(sessionId).stream().filter(item -> item.getDecision() == TailoringSuggestionDecision.ACCEPTED).toList();
        String content = resume.getExtractedText() == null ? "" : resume.getExtractedText();
        for (TailoringSuggestion suggestion : accepted) content = content.replace(suggestion.getBeforeText(), suggestion.getAfterText());
        ResumeVersion version = new ResumeVersion(); version.setUserId(userId); version.setSourceResumeId(resume.getId()); version.setTailoringSessionId(sessionId); version.setJobDescription(session.getJobDescription()); version.setContent(content); version.setAcceptedSuggestionIds(toJson(accepted.stream().map(TailoringSuggestion::getId).toList()));
        return toVersion(versions.save(version));
    }

    @Transactional(readOnly = true)
    public List<ResumeTailoringDtos.Version> versions(Long userId) { return versions.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toVersion).toList(); }

    @Transactional(readOnly = true)
    public byte[] renderPdf(Long userId, Long versionId) {
        ResumeVersion version = versions.findByIdAndUserId(versionId, userId).orElseThrow(() -> new IllegalArgumentException("Resume version not found"));
        return renderPdf(version.getContent() == null ? "" : version.getContent());
    }

    /**
     * Renders plain resume text into a structured, readable PDF -- no LaTeX toolchain is
     * available in this deployment, so this is a direct PDFBox layout rather than compiling
     * toLatex()'s output. The source is unstructured plain text (one line per resume line, no
     * markup), so layout is heuristic: the first two non-blank lines are treated as name/contact,
     * known section-header words (or short ALL-CAPS lines) get a rule under them, "Label: value"
     * lines get a bold label, a trailing date range right-aligns the line it's on, and lines
     * immediately followed by "Tech Stack:" are treated as bold entry titles. Wraps to the page
     * width and paginates when content overflows a page.
     */
    private byte[] renderPdf(String content) {
        float margin = 50f;
        float bodySize = 10.5f;
        float leading = bodySize * 1.32f;
        PDType1Font regular = PDType1Font.HELVETICA;
        PDType1Font bold = PDType1Font.HELVETICA_BOLD;
        List<String> rawLines = new ArrayList<>(Arrays.asList(content.split("\\r?\\n")));
        try (PDDocument document = new PDDocument()) {
            PDRectangle pageSize = PDRectangle.LETTER;
            float printableWidth = pageSize.getWidth() - margin * 2;
            Page page = new Page(document, pageSize, margin);

            int i = 0;
            // First two non-blank lines are the name and the contact line -- true for every
            // standard resume format and true for both source PDFs this was built against.
            while (i < rawLines.size() && rawLines.get(i).isBlank()) i++;
            if (i < rawLines.size()) {
                page = drawCentered(document, page, pageSize, margin, printableWidth, bold, 17.5f, rawLines.get(i).trim());
                i++;
            }
            while (i < rawLines.size() && rawLines.get(i).isBlank()) i++;
            if (i < rawLines.size()) {
                String contact = cleanContactLine(rawLines.get(i));
                page = drawCentered(document, page, pageSize, margin, printableWidth, regular, 9.5f, contact);
                i++;
                page = drawRule(document, page, pageSize, margin, printableWidth, page.y + leading * 0.15f);
                page.y -= leading * 0.35f;
            }

            for (; i < rawLines.size(); i++) {
                String raw = rawLines.get(i);
                String trimmed = raw.trim();
                if (trimmed.isEmpty()) { page.y -= leading * 0.45f; continue; }

                Matcher dateMatch = TRAILING_DATE.matcher(trimmed);
                boolean dated = dateMatch.find();
                String nextTrimmed = i + 1 < rawLines.size() ? rawLines.get(i + 1).trim() : "";
                Matcher labelMatch = LABELED_LINE.matcher(trimmed);

                if (isSectionHeading(trimmed)) {
                    page.y -= leading * 0.25f;
                    page = ensureRoom(document, page, pageSize, margin, leading);
                    page = drawRun(document, page, pageSize, margin, bold, 11f, margin, trimmed.toUpperCase(Locale.ROOT));
                    page = drawRule(document, page, pageSize, margin, printableWidth, page.y + leading * 0.28f);
                    page.y -= leading * 0.12f;
                } else if (trimmed.startsWith("\u2022") || trimmed.startsWith("*") || (trimmed.startsWith("-") && trimmed.length() > 2 && trimmed.charAt(1) == ' ')) {
                    // Checked before the dated-entry regex: an explicit bullet glyph is a stronger, unambiguous
                    // signal than the trailing-date heuristic, which could otherwise misfire on a bullet whose
                    // sentence happens to end in a bare year with no trailing punctuation.
                    String bulletText = trimmed.replaceFirst("^[\u2022*\\-]\\s*", "");
                    page = drawBullet(document, page, pageSize, margin, printableWidth, regular, bodySize, leading, bulletText);
                } else if (dated && !dateMatch.group(1).isBlank()) {
                    String left = trimmed.substring(0, dateMatch.start(1)).trim();
                    String date = dateMatch.group(1).trim();
                    page = drawDatedEntry(document, page, pageSize, margin, printableWidth, bold, regular, bodySize, leading, left, date);
                } else if (labelMatch.matches() && labelMatch.group(1).split("\\s+").length <= 5) {
                    page = drawLabeled(document, page, pageSize, margin, printableWidth, bold, regular, bodySize, leading, labelMatch.group(1), labelMatch.group(2));
                } else if (!nextTrimmed.isEmpty() && TECH_STACK_NEXT.matcher(nextTrimmed).lookingAt()) {
                    for (String wrapped : wrap(trimmed, bold, bodySize, printableWidth)) {
                        page = ensureRoom(document, page, pageSize, margin, leading);
                        page = drawRun(document, page, pageSize, margin, bold, bodySize, margin, wrapped);
                    }
                } else {
                    for (String wrapped : wrap(trimmed, regular, bodySize, printableWidth)) {
                        page = ensureRoom(document, page, pageSize, margin, leading);
                        page = drawRun(document, page, pageSize, margin, regular, bodySize, margin, wrapped);
                    }
                }
            }

            page.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate PDF", ex);
        }
    }

    private static final float LEADING_RATIO = 1.32f;

    /** Tracks the live page/stream/cursor a run of draw* helpers thread through; closed and replaced on overflow. */
    private static final class Page {
        final PDDocument document; PDPage pdPage; PDPageContentStream stream; float y; final float top;
        Page(PDDocument document, PDRectangle size, float margin) throws java.io.IOException {
            this.document = document; this.top = size.getHeight() - margin;
            this.pdPage = new PDPage(size); document.addPage(pdPage);
            this.stream = new PDPageContentStream(document, pdPage); this.y = top;
        }
        void close() throws java.io.IOException { stream.close(); }
    }

    private Page ensureRoom(PDDocument document, Page page, PDRectangle size, float margin, float leading) throws java.io.IOException {
        if (page.y - leading < margin) {
            page.close();
            Page next = new Page(document, size, margin);
            return next;
        }
        return page;
    }

    /** Draws one text run at an absolute (x, current-y) position, then drops y by one leading step. */
    private Page drawRun(PDDocument document, Page page, PDRectangle size, float margin, PDType1Font font, float fontSize, float x, String text) throws java.io.IOException {
        page.stream.beginText();
        page.stream.setFont(font, fontSize);
        page.stream.newLineAtOffset(x, page.y);
        try { page.stream.showText(sanitize(text)); }
        catch (IllegalArgumentException undefinedGlyph) { page.stream.showText(asciiOnly(text)); }
        page.stream.endText();
        page.y -= fontSize * LEADING_RATIO;
        return page;
    }

    /** Draws two runs (e.g. a bold label + its value) on the same visual line before dropping y. */
    private Page drawTwoRuns(Page page, PDType1Font font1, float size1, float x1, String text1, PDType1Font font2, float size2, float x2, String text2, float leading) throws java.io.IOException {
        page.stream.beginText(); page.stream.setFont(font1, size1); page.stream.newLineAtOffset(x1, page.y);
        try { page.stream.showText(sanitize(text1)); } catch (IllegalArgumentException e) { page.stream.showText(asciiOnly(text1)); }
        page.stream.endText();
        if (text2 != null && !text2.isBlank()) {
            page.stream.beginText(); page.stream.setFont(font2, size2); page.stream.newLineAtOffset(x2, page.y);
            try { page.stream.showText(sanitize(text2)); } catch (IllegalArgumentException e) { page.stream.showText(asciiOnly(text2)); }
            page.stream.endText();
        }
        page.y -= leading;
        return page;
    }

    private Page drawCentered(PDDocument document, Page page, PDRectangle size, float margin, float printableWidth, PDType1Font font, float fontSize, String text) throws java.io.IOException {
        page = ensureRoom(document, page, size, margin, fontSize * LEADING_RATIO);
        float width = textWidth(text, font, fontSize);
        float x = margin + Math.max(0, (printableWidth - width) / 2f);
        return drawRun(document, page, size, margin, font, fontSize, x, text);
    }

    private Page drawRule(PDDocument document, Page page, PDRectangle size, float margin, float printableWidth, float y) throws java.io.IOException {
        page.stream.setStrokingColor(0.62f, 0.62f, 0.62f);
        page.stream.setLineWidth(0.6f);
        page.stream.moveTo(margin, y);
        page.stream.lineTo(margin + printableWidth, y);
        page.stream.stroke();
        return page;
    }

    /** Bold title left-aligned, date right-aligned on the same line; falls back to a second line if they'd collide. */
    private Page drawDatedEntry(PDDocument document, Page page, PDRectangle size, float margin, float printableWidth, PDType1Font bold, PDType1Font regular, float fontSize, float leading, String left, String date) throws java.io.IOException {
        page = ensureRoom(document, page, size, margin, leading);
        float leftWidth = textWidth(left, bold, fontSize);
        float dateWidth = textWidth(date, regular, fontSize);
        if (leftWidth + 16f + dateWidth <= printableWidth) {
            return drawTwoRuns(page, bold, fontSize, margin, left, regular, fontSize, margin + printableWidth - dateWidth, date, leading);
        }
        for (String wrapped : wrap(left, bold, fontSize, printableWidth)) {
            page = ensureRoom(document, page, size, margin, leading);
            page = drawRun(document, page, size, margin, bold, fontSize, margin, wrapped);
        }
        page = ensureRoom(document, page, size, margin, leading);
        return drawRun(document, page, size, margin, regular, fontSize, margin + printableWidth - dateWidth, date);
    }

    /** "Label: value" -- bold label, regular value, hanging-indented if the value wraps. */
    private Page drawLabeled(PDDocument document, Page page, PDRectangle size, float margin, float printableWidth, PDType1Font bold, PDType1Font regular, float fontSize, float leading, String label, String value) throws java.io.IOException {
        String prefix = label + ": ";
        float prefixWidth = textWidth(prefix, bold, fontSize);
        List<String> wrapped = wrap(value, regular, fontSize, Math.max(60f, printableWidth - prefixWidth));
        boolean first = true;
        for (String line : wrapped) {
            page = ensureRoom(document, page, size, margin, leading);
            if (first) { page = drawTwoRuns(page, bold, fontSize, margin, prefix, regular, fontSize, margin + prefixWidth, line, leading); first = false; }
            else { page = drawRun(document, page, size, margin, regular, fontSize, margin + prefixWidth, line); }
        }
        return page;
    }

    /** Bullet glyph + hanging-indented wrapped text, so continuation lines align under the text, not the bullet. */
    private Page drawBullet(PDDocument document, Page page, PDRectangle size, float margin, float printableWidth, PDType1Font regular, float fontSize, float leading, String text) throws java.io.IOException {
        float indent = 15f;
        List<String> wrapped = wrap(text, regular, fontSize, printableWidth - indent);
        boolean first = true;
        for (String line : wrapped) {
            page = ensureRoom(document, page, size, margin, leading);
            if (first) { page = drawTwoRuns(page, regular, fontSize, margin, "\u2022", regular, fontSize, margin + indent, line, leading); first = false; }
            else { page = drawRun(document, page, size, margin, regular, fontSize, margin + indent, line); }
        }
        return page;
    }

    private static final Set<String> SECTION_KEYWORDS = Set.of(
            "summary", "objective", "profile", "education", "experience", "work experience", "professional experience",
            "projects", "technical skills", "skills", "core skills", "certifications", "certification",
            "additional information", "achievements", "awards", "publications", "leadership", "extracurricular",
            "activities", "volunteering", "languages", "interests", "references");

    private boolean isSectionHeading(String trimmed) {
        String stripped = trimmed.replaceAll(":\\s*$", "");
        if (SECTION_KEYWORDS.contains(stripped.toLowerCase(Locale.ROOT))) return true;
        return trimmed.equals(trimmed.toUpperCase(Locale.ROOT)) && trimmed.length() > 2 && trimmed.split("\\s+").length <= 5;
    }

    /** Contact/header lines routinely carry icon-font glyphs (phone/link icons) mis-extracted as stray Latin-1
     * letters -- real resumes never legitimately need non-ASCII here, so strip anything outside printable ASCII
     * and turn the resulting whitespace runs (where an icon used to sit) into a clean " | " separator. */
    private String cleanContactLine(String raw) {
        StringBuilder ascii = new StringBuilder();
        for (char c : raw.toCharArray()) if (c >= 32 && c <= 126) ascii.append(c);
        String[] parts = ascii.toString().trim().split("\\s{2,}|\\s*\\|\\s*");
        List<String> cleaned = new ArrayList<>();
        for (String part : parts) { String p = part.trim(); if (!p.isEmpty()) cleaned.add(p); }
        return cleaned.isEmpty() ? ascii.toString().trim() : String.join("  |  ", cleaned);
    }

    private static final String MONTH = "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-zA-Z]*\\.?";
    private static final String DATEPOINT = "(?:" + MONTH + "\\s+\\d{4}|\\d{4})";
    private static final Pattern TRAILING_DATE = Pattern.compile(
            "(" + DATEPOINT + "\\s*[\u2013\u2014\\-]\\s*(?:" + DATEPOINT + "|Present|Current|Now)|" + DATEPOINT + ")\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LABELED_LINE = Pattern.compile("([A-Za-z][A-Za-z /&+.-]{1,40}):\\s+(.+)");
    private static final Pattern TECH_STACK_NEXT = Pattern.compile("tech\\s*stack\\s*:", Pattern.CASE_INSENSITIVE);

    private List<String> wrap(String line, PDType1Font font, float fontSize, float maxWidth) {
        if (line.isBlank()) return List.of("");
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : line.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textWidth(candidate, font, fontSize) > maxWidth && !current.isEmpty()) {
                result.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result.isEmpty() ? List.of("") : result;
    }

    private float textWidth(String text, PDType1Font font, float fontSize) {
        try { return font.getStringWidth(sanitize(text)) / 1000f * fontSize; }
        catch (Exception ex) { return text.length() * fontSize * 0.5f; }
    }

    /**
     * PDFBox's standard 14 fonts use WinAnsiEncoding, which -- despite the name -- covers more than
     * Latin-1: common "smart" typography (bullet, en/em dash, curly quotes, ellipsis) sits at Unicode
     * code points above 255 but maps cleanly to WinAnsiEncoding by glyph name, so PDFBox renders it
     * correctly if it's passed through unchanged. The previous version of this method treated "above
     * 255" as "unsupported" and replaced all of it with '?', which is what actually produced the
     * garbled '?' characters in place of bullets and dashes in exported PDFs -- not a missing-glyph
     * problem, an over-aggressive filter. True control characters and the C1 block (0x80-0x9F / 128-159,
     * confirmed live: 0x87, from icon glyphs mis-extracted from a resume PDF) still have no glyph and
     * are replaced; anything else outside WinAnsiEncoding's actual reach also falls back to '?', with
     * showText()'s IllegalArgumentException as a second safety net at the call site.
     */
    private static final Set<Character> WINANSI_SMART_PUNCTUATION = Set.of(
            '\u2018', '\u2019', '\u201C', '\u201D', '\u2013', '\u2014', '\u2022', '\u2026', '\u2020', '\u2021', '\u2122', '\u20AC');

    private String sanitize(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c < 32) result.append('?');
            else if (c <= 255 && !(c >= 128 && c <= 159)) result.append(c);
            else if (WINANSI_SMART_PUNCTUATION.contains(c)) result.append(c);
            else result.append('?');
        }
        return result.toString();
    }

    /** Guaranteed-safe fallback when even sanitize() misses an undefined glyph -- maps smart punctuation to its
     * plain-ASCII equivalent instead of '?' where possible, since that degrades far more readably. */
    private String asciiOnly(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\u2018': case '\u2019': result.append('\''); break;
                case '\u201C': case '\u201D': result.append('"'); break;
                case '\u2013': case '\u2014': result.append('-'); break;
                case '\u2022': result.append('-'); break;
                case '\u2026': result.append("..."); break;
                default: result.append(c < 32 || c > 126 ? '?' : c);
            }
        }
        return result.toString();
    }

    /** Saves proposals that pass grounding and returns the count saved. */
    private int saveGrounded(Long sessionId, List<ResumeTailoringProvider.Proposal> proposals, String source) {
        int count = 0;
        for (ResumeTailoringProvider.Proposal p : proposals) {
            if (!grounded(p, source)) continue;
            TailoringSuggestion s = new TailoringSuggestion();
            s.setSessionId(sessionId); s.setCategory(p.category()); s.setBeforeText(p.beforeText());
            s.setAfterText(p.afterText()); s.setRationale(p.rationale()); s.setEvidenceText(p.evidenceText());
            suggestions.save(s);
            count++;
        }
        return count;
    }

    /** A proposal is grounded when its edit target and evidence are literal source excerpts. */
    private boolean grounded(ResumeTailoringProvider.Proposal proposal, String source) {
        return nonBlank(proposal.beforeText()) && source.contains(proposal.beforeText())
            && nonBlank(proposal.evidenceText()) && source.contains(proposal.evidenceText())
            && nonBlank(proposal.afterText());
    }
    private ResumeTailoringDtos.Analysis analysis(TailoringSession session, ResumeProfileExtractor.ExtractedProfile profile, List<String> keywords) { return new ResumeTailoringDtos.Analysis(session.getId(), session.getSourceResumeId(), keywords, profile.getSkills(), profile.getProjects(), suggestions.findBySessionIdOrderByIdAsc(session.getId()).stream().map(this::toSuggestion).toList(), session.getCreatedAt()); }
    private ResumeTailoringDtos.Suggestion toSuggestion(TailoringSuggestion value) { return new ResumeTailoringDtos.Suggestion(value.getId(), value.getCategory(), value.getBeforeText(), value.getAfterText(), value.getRationale(), value.getEvidenceText(), value.getDecision()); }
    private ResumeTailoringDtos.Version toVersion(ResumeVersion value) { return new ResumeTailoringDtos.Version(value.getId(), value.getSourceResumeId(), value.getTailoringSessionId(), value.getJobDescription(), value.getContent(), fromJson(value.getAcceptedSuggestionIds()), toLatex(value.getContent()), value.getCreatedAt()); }
    private String toJson(List<Long> values) { try { return mapper.writeValueAsString(values); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private List<Long> fromJson(String value) { try { return mapper.readValue(value, IDS); } catch (Exception ex) { return List.of(); } }
    private List<String> fromJsonStrings(String value) { try { return mapper.readValue(value, new TypeReference<List<String>>() {}); } catch (Exception ex) { return List.of(); } }
    private String toLatex(String content) {
        StringBuilder sb = new StringBuilder();
        // Preamble matching the user's Overleaf template (lato, fontawesome5, Jake's Resume macros)
        sb.append("\\documentclass[letterpaper,11pt]{article}\n\n")
          .append("\\usepackage{latexsym}\n\\usepackage[empty]{fullpage}\n\\usepackage{titlesec}\n")
          .append("\\usepackage{marvosym}\n\\usepackage[usenames,dvipsnames]{color}\n\\usepackage{verbatim}\n")
          .append("\\usepackage{enumitem}\n\\usepackage[hidelinks]{hyperref}\n\\usepackage{fancyhdr}\n")
          .append("\\usepackage[english]{babel}\n\\usepackage{tabularx}\n\\usepackage{fontawesome5}\n")
          .append("\\usepackage[default]{lato}\n\\usepackage[T1]{fontenc}\n\n")
          .append("\\pagestyle{fancy}\n\\fancyhf{}\\fancyfoot{}\n")
          .append("\\renewcommand{\\headrulewidth}{0pt}\n\\renewcommand{\\footrulewidth}{0pt}\n\n")
          .append("\\addtolength{\\oddsidemargin}{-0.5in}\n\\addtolength{\\evensidemargin}{-0.5in}\n")
          .append("\\addtolength{\\textwidth}{1in}\n\\addtolength{\\topmargin}{-.5in}\n")
          .append("\\addtolength{\\textheight}{1.0in}\n\n")
          .append("\\urlstyle{same}\n\\raggedbottom\\raggedright\n\\setlength{\\tabcolsep}{0in}\n\n")
          .append("\\titleformat{\\section}{\\vspace{-4pt}\\scshape\\raggedright\\large}{}{0em}{}[\\color{black}\\titlerule \\vspace{-5pt}]\n\n")
          .append("\\newcommand{\\resumeItem}[1]{\\item\\small{#1 \\vspace{-2pt}}}\n")
          .append("\\newcommand{\\resumeSubheading}[4]{\n")
          .append("  \\vspace{-2pt}\\item\n")
          .append("    \\begin{tabular*}{0.97\\textwidth}[t]{l@{\\extracolsep{\\fill}}r}\n")
          .append("      \\textbf{#1} & #2 \\\\\n")
          .append("      \\textit{\\small#3} & \\textit{\\small #4} \\\\\n")
          .append("    \\end{tabular*}\\vspace{-7pt}\n}\n")
          .append("\\newcommand{\\resumeProjectHeading}[2]{\n")
          .append("    \\item\n")
          .append("    \\begin{tabular*}{0.97\\textwidth}{l@{\\extracolsep{\\fill}}r}\n")
          .append("      \\small#1 & #2 \\\\\n")
          .append("    \\end{tabular*}\\vspace{-7pt}\n}\n")
          .append("\\newcommand{\\resumeSubItem}[1]{\\resumeItem{#1}\\vspace{-4pt}}\n")
          .append("\\renewcommand\\labelitemii{$\\vcenter{\\hbox{\\tiny$\\bullet$}}$}\n")
          .append("\\newcommand{\\resumeSubHeadingListStart}{\\begin{itemize}[leftmargin=0.15in, label={}]}\n")
          .append("\\newcommand{\\resumeSubHeadingListEnd}{\\end{itemize}}\n")
          .append("\\newcommand{\\resumeItemListStart}{\\begin{itemize}}\n")
          .append("\\newcommand{\\resumeItemListEnd}{\\end{itemize}\\vspace{-5pt}}\n\n")
          .append("\\begin{document}\n\n");

        String[] lines = content.split("\\r?\\n");
        int i = 0;

        // Header block: name + contact line
        while (i < lines.length && lines[i].isBlank()) i++;
        sb.append("\\begin{center}\n");
        if (i < lines.length) {
            sb.append("  \\textbf{\\Huge \\scshape ").append(escapeLatex(lines[i].trim())).append("} \\\\ \\vspace{1pt}\n");
            i++;
        }
        while (i < lines.length && lines[i].isBlank()) i++;
        if (i < lines.length && !isSectionHeading(lines[i].trim())) {
            sb.append("  \\small ").append(formatLatexContact(lines[i].trim())).append("\n");
            i++;
        }
        sb.append("\\end{center}\n\n");

        // Section body
        boolean inSubList = false;
        boolean inItemList = false;

        while (i < lines.length) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) { i++; continue; }

            if (isSectionHeading(trimmed)) {
                if (inItemList) { sb.append("    \\resumeItemListEnd\n"); inItemList = false; }
                if (inSubList)  { sb.append("\\resumeSubHeadingListEnd\n\n"); inSubList = false; }
                sb.append("\\section{").append(escapeLatex(toTitleCase(trimmed))).append("}\n");
                sb.append("\\resumeSubHeadingListStart\n");
                inSubList = true;
            } else if (trimmed.startsWith("\u2022") || trimmed.startsWith("* ") || (trimmed.startsWith("- ") && trimmed.length() > 2)) {
                if (!inItemList) { sb.append("    \\resumeItemListStart\n"); inItemList = true; }
                String bullet = trimmed.replaceFirst("^[\u2022*\\-]\\s+", "");
                sb.append("      \\resumeItem{").append(escapeLatex(bullet)).append("}\n");
            } else {
                Matcher dm = TRAILING_DATE.matcher(trimmed);
                if (dm.find() && !dm.group(1).isBlank()) {
                    if (inItemList) { sb.append("    \\resumeItemListEnd\n"); inItemList = false; }
                    String left = trimmed.substring(0, dm.start(1)).trim();
                    String date = dm.group(1).trim();
                    // Look ahead for a subtitle line (role/degree below the org+date line)
                    int j = i + 1;
                    while (j < lines.length && lines[j].isBlank()) j++;
                    String subtitle = "";
                    if (j < lines.length) {
                        String next = lines[j].trim();
                        Matcher nm = TRAILING_DATE.matcher(next);
                        if (!isSectionHeading(next) && !next.startsWith("\u2022") && !next.startsWith("* ")
                                && !(next.startsWith("- ") && next.length() > 2) && !nm.find()) {
                            subtitle = next;
                            i = j;
                        }
                    }
                    sb.append("  \\resumeSubheading{").append(escapeLatex(left)).append("}{").append(escapeLatex(date))
                      .append("}{").append(escapeLatex(subtitle)).append("}{}\n");
                } else {
                    Matcher lm = LABELED_LINE.matcher(trimmed);
                    if (lm.matches() && lm.group(1).split("\\s+").length <= 5) {
                        if (inItemList) { sb.append("    \\resumeItemListEnd\n"); inItemList = false; }
                        sb.append("  \\resumeItem{\\textbf{").append(escapeLatex(lm.group(1)))
                          .append(":} ").append(escapeLatex(lm.group(2))).append("}\n");
                    } else {
                        if (!inItemList) { sb.append("    \\resumeItemListStart\n"); inItemList = true; }
                        sb.append("      \\resumeItem{").append(escapeLatex(trimmed)).append("}\n");
                    }
                }
            }
            i++;
        }

        if (inItemList) sb.append("    \\resumeItemListEnd\n");
        if (inSubList)  sb.append("\\resumeSubHeadingListEnd\n\n");

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    private String formatLatexContact(String contact) {
        String[] parts = contact.split("\\s*\\|\\s*|\\s{2,}");
        List<String> items = new ArrayList<>();
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.contains("@") && p.contains(".")) {
                items.add("\\href{mailto:" + p + "}{\\faEnvelope\\ " + escapeLatex(p) + "}");
            } else if (p.matches("\\+?[\\d][\\d()\\-+. ]{5,}")) {
                items.add("\\faPhone\\ " + escapeLatex(p));
            } else if (p.toLowerCase(Locale.ROOT).contains("linkedin")) {
                items.add("\\href{https://linkedin.com/}{\\faLinkedin\\ " + escapeLatex(p) + "}");
            } else if (p.toLowerCase(Locale.ROOT).contains("github")) {
                items.add("\\href{https://github.com/}{\\faGithub\\ " + escapeLatex(p) + "}");
            } else if (p.startsWith("http") || p.startsWith("www.")) {
                items.add("\\href{" + p + "}{\\faLink\\ " + escapeLatex(p) + "}");
            } else {
                items.add(escapeLatex(p));
            }
        }
        return String.join(" $|$ ", items);
    }

    private String escapeLatex(String text) {
        if (text == null) return "";
        StringBuilder r = new StringBuilder(text.length() + 16);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\\' -> r.append("\\textbackslash{}");
                case '&'  -> r.append("\\&");
                case '%'  -> r.append("\\%");
                case '#'  -> r.append("\\#");
                case '{'  -> r.append("\\{");
                case '}'  -> r.append("\\}");
                case '~'  -> r.append("\\textasciitilde{}");
                case '^'  -> r.append("\\textasciicircum{}");
                case '$'  -> r.append("\\$");
                case '_'  -> r.append("\\_");
                case '<'  -> r.append("\\textless{}");
                case '>'  -> r.append("\\textgreater{}");
                case '\u2022' -> r.append("$\\bullet$");
                case '\u2013' -> r.append("--");
                case '\u2014' -> r.append("---");
                case '\u2018' -> r.append("`");
                case '\u2019' -> r.append("'");
                case '\u201C' -> r.append("``");
                case '\u201D' -> r.append("''");
                default -> r.append(c);
            }
        }
        return r.toString();
    }

    private String toTitleCase(String text) {
        if (text == null || text.isBlank()) return text;
        String[] words = text.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder r = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) { if (r.length() > 0) r.append(' '); r.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)); }
        }
        return r.toString();
    }

    private boolean nonBlank(String value) { return value != null && !value.isBlank(); }
}