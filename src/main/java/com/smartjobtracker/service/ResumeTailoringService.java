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
import java.util.stream.Collectors;

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
        List<ResumeTailoringProvider.Proposal> proposals;
        try { proposals = "gemini".equalsIgnoreCase(aiConfig.getProvider()) ? gemini.suggest(resumeText, providerJobDescription, atsKeywords) : fallback.suggest(resumeText, providerJobDescription, atsKeywords); }
        catch (RuntimeException ex) { proposals = fallback.suggest(resumeText, providerJobDescription, atsKeywords); }
        for (ResumeTailoringProvider.Proposal proposal : proposals) {
            if (!grounded(proposal, resumeText)) continue;
            TailoringSuggestion suggestion = new TailoringSuggestion(); suggestion.setSessionId(session.getId()); suggestion.setCategory(proposal.category()); suggestion.setBeforeText(proposal.beforeText()); suggestion.setAfterText(proposal.afterText()); suggestion.setRationale(proposal.rationale()); suggestion.setEvidenceText(proposal.evidenceText()); suggestions.save(suggestion);
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
     * Renders plain resume text into a simple, readable PDF -- no LaTeX toolchain is available
     * in this deployment, so this is a direct PDFBox layout rather than compiling toLatex()'s
     * output. Wraps lines to the page width and paginates when content overflows a page.
     */
    private byte[] renderPdf(String content) {
        float margin = 50f;
        float fontSize = 10.5f;
        float leading = fontSize * 1.35f;
        PDType1Font font = PDType1Font.HELVETICA;
        PDType1Font bold = PDType1Font.HELVETICA_BOLD;
        try (PDDocument document = new PDDocument()) {
            PDRectangle pageSize = PDRectangle.LETTER;
            float printableWidth = pageSize.getWidth() - margin * 2;
            PDPage page = new PDPage(pageSize);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float y = pageSize.getHeight() - margin;
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(margin, y);
            boolean textOpen = true;
            for (String rawLine : content.split("\\r?\\n")) {
                boolean heading = !rawLine.isBlank() && rawLine.trim().equals(rawLine.trim().toUpperCase(Locale.ROOT)) && rawLine.trim().length() > 2;
                PDType1Font lineFont = heading ? bold : font;
                for (String wrapped : wrap(rawLine, lineFont, fontSize, printableWidth)) {
                    if (y <= margin) {
                        stream.endText(); stream.close();
                        page = new PDPage(pageSize); document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        y = pageSize.getHeight() - margin;
                        stream.beginText(); stream.setFont(font, fontSize); stream.newLineAtOffset(margin, y);
                        textOpen = true;
                    }
                    if (lineFont != font) stream.setFont(lineFont, fontSize);
                    try { stream.showText(sanitize(wrapped)); }
                    catch (IllegalArgumentException undefinedGlyph) { stream.showText(asciiOnly(wrapped)); }
                    if (lineFont != font) stream.setFont(font, fontSize);
                    stream.newLineAtOffset(0, -leading);
                    y -= leading;
                }
            }
            if (textOpen) stream.endText();
            stream.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate PDF", ex);
        }
    }

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

    /** PDFBox's standard 14 fonts only support WinAnsiEncoding -- strip anything outside it so showText() doesn't throw. */
    /**
     * PDFBox's standard 14 fonts only support WinAnsiEncoding, and the excluded range must cover
     * more than "outside Latin-1": the C1 control block (0x80-0x9F / 128-159) sits inside 0-255
     * but several of those code points (confirmed live: 0x87) have no glyph and make showText()
     * throw. Icon-font glyphs mis-extracted from a PDF resume (phone/link icons etc.) land
     * exactly in this range, so this isn't a hypothetical edge case.
     */
    private String sanitize(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) result.append(c < 32 || c > 255 || (c >= 128 && c <= 159) ? '?' : c);
        return result.toString();
    }

    /** Strips to plain ASCII -- the guaranteed-safe fallback when even sanitize() misses an undefined glyph. */
    private String asciiOnly(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) result.append(c < 32 || c > 126 ? '?' : c);
        return result.toString();
    }

    private boolean grounded(ResumeTailoringProvider.Proposal proposal, String source) {
        return nonBlank(proposal.beforeText()) && source.contains(proposal.beforeText()) && source.contains(proposal.evidenceText()) && nonBlank(proposal.afterText()) && allWordsFromSource(proposal.afterText(), source);
    }
    private boolean allWordsFromSource(String candidate, String source) { Set<String> words = Arrays.stream(source.toLowerCase().split("[^a-z0-9+#.]+" )).filter(word -> !word.isBlank()).collect(Collectors.toSet()); return Arrays.stream(candidate.toLowerCase().split("[^a-z0-9+#.]+" )).filter(word -> !word.isBlank()).allMatch(words::contains); }
    private ResumeTailoringDtos.Analysis analysis(TailoringSession session, ResumeProfileExtractor.ExtractedProfile profile, List<String> keywords) { return new ResumeTailoringDtos.Analysis(session.getId(), session.getSourceResumeId(), keywords, profile.getSkills(), profile.getProjects(), suggestions.findBySessionIdOrderByIdAsc(session.getId()).stream().map(this::toSuggestion).toList(), session.getCreatedAt()); }
    private ResumeTailoringDtos.Suggestion toSuggestion(TailoringSuggestion value) { return new ResumeTailoringDtos.Suggestion(value.getId(), value.getCategory(), value.getBeforeText(), value.getAfterText(), value.getRationale(), value.getEvidenceText(), value.getDecision()); }
    private ResumeTailoringDtos.Version toVersion(ResumeVersion value) { return new ResumeTailoringDtos.Version(value.getId(), value.getSourceResumeId(), value.getTailoringSessionId(), value.getJobDescription(), value.getContent(), fromJson(value.getAcceptedSuggestionIds()), toLatex(value.getContent()), value.getCreatedAt()); }
    private String toJson(List<Long> values) { try { return mapper.writeValueAsString(values); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private List<Long> fromJson(String value) { try { return mapper.readValue(value, IDS); } catch (Exception ex) { return List.of(); } }
    private List<String> fromJsonStrings(String value) { try { return mapper.readValue(value, new TypeReference<List<String>>() {}); } catch (Exception ex) { return List.of(); } }
    private String toLatex(String content) {
        StringBuilder latex = new StringBuilder("\\documentclass[10pt]{article}\n\\usepackage[margin=0.5in]{geometry}\n\\usepackage{enumitem}\n\\usepackage{hyperref}\n\\pagestyle{empty}\n\\setlist[itemize]{leftmargin=*,itemsep=0.8pt,topsep=2pt}\n\\setlength{\\parindent}{0pt}\n\\begin{document}\n");
        for (String line : content.split("\\r?\\n")) {
            String escaped = line.replace("\\", "\\textbackslash{}").replace("&", "\\&").replace("%", "\\%").replace("#", "\\#").replace("{", "\\{").replace("}", "\\}");
            latex.append(escaped.isBlank() ? "\\par" : escaped).append("\\\\\n");
        }
        return latex.append("\\end{document}\n").toString();
    }
    private boolean nonBlank(String value) { return value != null && !value.isBlank(); }
}