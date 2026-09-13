package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.ResumeTailoringDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResumeTailoringServiceTest {
    @Test
    void analysisOnlyCreatesGroundedSuggestionsAndExtractsAtsKeywords() {
        Resume resume = resume("SKILLS\nJava, Git\nPROJECTS\nJob Tracker with Java");
        ResumeRepository resumes = mock(ResumeRepository.class); when(resumes.findById(4L)).thenReturn(Optional.of(resume));
        TailoringSessionRepository sessions = mock(TailoringSessionRepository.class); when(sessions.save(any())).thenAnswer(invocation -> { TailoringSession value = invocation.getArgument(0); value.setId(8L); return value; });
        TailoringSuggestionRepository suggestions = mock(TailoringSuggestionRepository.class); when(suggestions.save(any())).thenAnswer(invocation -> { TailoringSuggestion value = invocation.getArgument(0); value.setId(9L); return value; });
        when(suggestions.findBySessionIdOrderByIdAsc(8L)).thenAnswer(invocation -> List.of(savedSuggestion()));
        ResumeTailoringService service = service(resumes, sessions, suggestions, mock(ResumeVersionRepository.class), new RuleBasedResumeTailoringProvider());

        ResumeTailoringDtos.Analysis result = service.analyze(3L, new ResumeTailoringDtos.AnalyzeRequest(4L, "Java and Docker required", null));

        assertTrue(result.atsKeywords().contains("Java"));
        assertTrue(result.highlightedProjects().get(0).contains("Job Tracker"));
        verify(suggestions, atLeastOnce()).save(any(TailoringSuggestion.class));
    }

    @Test
    void newVersionContainsOnlyAcceptedEditsAndLeavesOriginalUntouched() {
        Resume resume = resume("Experience\nBuilt APIs in Java");
        ResumeRepository resumes = mock(ResumeRepository.class); when(resumes.findById(4L)).thenReturn(Optional.of(resume));
        TailoringSession session = new TailoringSession(); session.setId(8L); session.setUserId(3L); session.setSourceResumeId(4L); session.setJobDescription("Java engineer");
        TailoringSessionRepository sessions = mock(TailoringSessionRepository.class); when(sessions.findByIdAndUserId(8L, 3L)).thenReturn(Optional.of(session));
        TailoringSuggestion accepted = savedSuggestion(); accepted.setId(9L); accepted.setSessionId(8L); accepted.setDecision(TailoringSuggestionDecision.ACCEPTED); accepted.setBeforeText("Built APIs in Java"); accepted.setAfterText("Built Java APIs");
        TailoringSuggestion rejected = savedSuggestion(); rejected.setId(10L); rejected.setSessionId(8L); rejected.setDecision(TailoringSuggestionDecision.REJECTED); rejected.setBeforeText("Experience"); rejected.setAfterText("Senior Experience");
        TailoringSuggestionRepository suggestions = mock(TailoringSuggestionRepository.class); when(suggestions.findBySessionIdOrderByIdAsc(8L)).thenReturn(List.of(accepted, rejected));
        ResumeVersionRepository versions = mock(ResumeVersionRepository.class); when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResumeTailoringDtos.Version result = service(resumes, sessions, suggestions, versions, new RuleBasedResumeTailoringProvider()).createVersion(3L, 8L);

        assertEquals("Experience\nBuilt Java APIs", result.content());
        assertEquals(List.of(9L), result.acceptedSuggestionIds());
        assertEquals("Experience\nBuilt APIs in Java", resume.getExtractedText());
    }

    @Test
    void renderPdfProducesAValidPdfForRealisticContent() throws java.io.IOException {
        String content = new String(getClass().getClassLoader().getResourceAsStream("real-tailored-resume.txt").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        ResumeVersion version = new ResumeVersion(); version.setId(2L); version.setUserId(3L);
        version.setContent(content);
        ResumeVersionRepository versions = mock(ResumeVersionRepository.class); when(versions.findByIdAndUserId(2L, 3L)).thenReturn(Optional.of(version));
        ResumeTailoringService service = service(mock(ResumeRepository.class), mock(TailoringSessionRepository.class), mock(TailoringSuggestionRepository.class), versions, new RuleBasedResumeTailoringProvider());

        byte[] pdf = service.renderPdf(3L, 2L);

        assertTrue(pdf.length > 100);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void renderPdfSucceedsWithAC1ControlCharacterMisextractedFromAnIconFont() {
        // The actual live failure: U+0087, a C1 control character PDFBox's standard Helvetica
        // has no glyph for. These come from icon glyphs (phone/link symbols) that survive PDF
        // text extraction as garbage control characters -- not a hypothetical input. Built via
        // a numeric (char) cast rather than a literal escape or pasted character: both kept
        // getting silently stripped somewhere in this authoring pipeline, itself a small
        // illustration of exactly the kind of character that's easy to lose or mangle in transit.
        String phoneIconGlyph = String.valueOf((char) 0x87);
        ResumeVersion version = new ResumeVersion(); version.setId(2L); version.setUserId(3L);
        version.setContent("JOHN DOE\n" + phoneIconGlyph + " +1 555-0100  john@example.com\nExperience\nBuilt things.");
        ResumeVersionRepository versions = mock(ResumeVersionRepository.class); when(versions.findByIdAndUserId(2L, 3L)).thenReturn(Optional.of(version));
        ResumeTailoringService service = service(mock(ResumeRepository.class), mock(TailoringSessionRepository.class), mock(TailoringSuggestionRepository.class), versions, new RuleBasedResumeTailoringProvider());

        byte[] pdf = service.renderPdf(3L, 2L);

        assertTrue(pdf.length > 100);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    private ResumeTailoringService service(ResumeRepository resumes, TailoringSessionRepository sessions, TailoringSuggestionRepository suggestions, ResumeVersionRepository versions, ResumeTailoringProvider fallback) {
        return new ResumeTailoringService(resumes, new ResumeProfileExtractor(), sessions, suggestions, versions, new ObjectMapper(), fallback, mock(ResumeTailoringProvider.class), new com.smartjobtracker.jobs.discovery.JobSkillExtractor(), new com.smartjobtracker.config.AiMatchingConfig());
    }

    @Test
    void renderPdfPreservesBulletsAndDashesInsteadOfReplacingThemWithQuestionMarks() throws java.io.IOException {
        // This is the actual reported bug: sanitize() used to treat any Unicode code point above 255 as
        // unsupported and replace it with '?', which wrongly caught bullet/dash/smart-quote characters that
        // PDFBox's standard fonts render fine. Render real smart-typography characters and read the PDF's
        // own text back out with PDFBox's stripper to prove they survive as themselves, not as '?'.
        String content = "JOHN DOE\njohn@example.com\nExperience\n\u2022 Cut latency 40% \u2013 shipped end\u2011to\u2011end.\n\u2022 Said it was \u201Cgreat\u201D and it\u2019s true.";
        ResumeVersion version = new ResumeVersion(); version.setId(2L); version.setUserId(3L); version.setContent(content);
        ResumeVersionRepository versions = mock(ResumeVersionRepository.class); when(versions.findByIdAndUserId(2L, 3L)).thenReturn(Optional.of(version));
        ResumeTailoringService service = service(mock(ResumeRepository.class), mock(TailoringSessionRepository.class), mock(TailoringSuggestionRepository.class), versions, new RuleBasedResumeTailoringProvider());

        byte[] pdf = service.renderPdf(3L, 2L);

        String extracted;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            extracted = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
        }
        assertTrue(extracted.contains("\u2022"), "bullet should render as itself, not '?': " + extracted);
        assertTrue(extracted.contains("\u2013"), "en dash should render as itself, not '?': " + extracted);
        assertTrue(extracted.contains("\u201Cgreat\u201D"), "curly quotes should render as themselves: " + extracted);
        assertFalse(extracted.contains("? Cut"), "bullet must not degrade to a literal '?': " + extracted);
    }

    @Test
    void renderPdfRightAlignsATrailingDateOnAnEntryHeaderLine() throws java.io.IOException {
        String content = "JOHN DOE\njohn@example.com\nExperience\nSenior Engineer \u2013 Acme Corp Mar 2022 \u2013 Present\nTech Stack: Java, Spring\n\u2022 Did things.";
        ResumeVersion version = new ResumeVersion(); version.setId(2L); version.setUserId(3L); version.setContent(content);
        ResumeVersionRepository versions = mock(ResumeVersionRepository.class); when(versions.findByIdAndUserId(2L, 3L)).thenReturn(Optional.of(version));
        ResumeTailoringService service = service(mock(ResumeRepository.class), mock(TailoringSessionRepository.class), mock(TailoringSuggestionRepository.class), versions, new RuleBasedResumeTailoringProvider());

        byte[] pdf = service.renderPdf(3L, 2L);

        String extracted;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            extracted = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
        }
        assertTrue(extracted.contains("Senior Engineer"), extracted);
        assertTrue(extracted.contains("Present"), extracted);
        assertTrue(extracted.contains("Tech Stack"), extracted);
    }

    @Test
    void groundedFilterAcceptsProposalWhereAfterTextUsesWordsNotInSource() {
        // H1: Gemini-style proposal with a synonym in afterText must now pass grounding.
        // Before the fix, allWordsFromSource would reject "Developed" because it isn't a
        // token in a source that only contains "Built". The real anti-fabrication guard is
        // that beforeText and evidenceText must be literal source excerpts.
        String resumeText = "SKILLS\nJava, Git\nEXPERIENCE\nBuilt REST APIs in Java using Spring Boot";
        String jd = "Looking for a developer with Java experience";

        // Provider returns a proposal whose afterText rewords "Built" as "Developed" —
        // a valid rewrite that would have been blocked by allWordsFromSource.
        ResumeTailoringProvider geminiLike = (rt, jdesc, kw) -> List.of(
            new ResumeTailoringProvider.Proposal("IMPACT",
                "Built REST APIs in Java using Spring Boot",
                "Developed and deployed REST APIs in Java using Spring Boot",
                "Stronger action verb",
                "Built REST APIs in Java using Spring Boot")
        );

        Resume resume = resume(resumeText);
        ResumeRepository resumes = mock(ResumeRepository.class); when(resumes.findById(4L)).thenReturn(Optional.of(resume));
        TailoringSessionRepository sessions = mock(TailoringSessionRepository.class); when(sessions.save(any())).thenAnswer(inv -> { TailoringSession v = inv.getArgument(0); v.setId(8L); return v; });
        TailoringSuggestionRepository suggestions = mock(TailoringSuggestionRepository.class); when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(suggestions.findBySessionIdOrderByIdAsc(8L)).thenReturn(List.of());

        var config = new com.smartjobtracker.config.AiMatchingConfig(); config.setProvider("gemini"); config.setApiKey("fake");
        ResumeTailoringService service = new ResumeTailoringService(resumes, new ResumeProfileExtractor(), sessions, suggestions,
                mock(ResumeVersionRepository.class), new ObjectMapper(), new RuleBasedResumeTailoringProvider(), geminiLike,
                new com.smartjobtracker.jobs.discovery.JobSkillExtractor(), config);

        service.analyze(3L, new ResumeTailoringDtos.AnalyzeRequest(4L, jd, null));

        // The proposal must have been saved (not silently dropped by allWordsFromSource)
        verify(suggestions, atLeastOnce()).save(argThat(s ->
            "Developed and deployed REST APIs in Java using Spring Boot".equals(s.getAfterText())
        ));
    }

    @Test
    void groundedFilterRejectsProposalWhereBeforeTextIsNotInSource() {
        // A proposal whose beforeText doesn't appear verbatim in the resume must still be rejected.
        String resumeText = "SKILLS\nJava, Git";
        ResumeTailoringProvider badProvider = (rt, jd, kw) -> List.of(
            new ResumeTailoringProvider.Proposal("IMPACT", "Led a team of 10 engineers",
                "Led a team of 10 engineers at scale", "fabricated", "Led a team")
        );

        Resume resume = resume(resumeText);
        ResumeRepository resumes = mock(ResumeRepository.class); when(resumes.findById(4L)).thenReturn(Optional.of(resume));
        TailoringSessionRepository sessions = mock(TailoringSessionRepository.class); when(sessions.save(any())).thenAnswer(inv -> { TailoringSession v = inv.getArgument(0); v.setId(8L); return v; });
        TailoringSuggestionRepository suggestions = mock(TailoringSuggestionRepository.class);
        when(suggestions.findBySessionIdOrderByIdAsc(8L)).thenReturn(List.of());

        ResumeTailoringService service = new ResumeTailoringService(resumes, new ResumeProfileExtractor(), sessions, suggestions,
                mock(ResumeVersionRepository.class), new ObjectMapper(), badProvider, mock(ResumeTailoringProvider.class),
                new com.smartjobtracker.jobs.discovery.JobSkillExtractor(), new com.smartjobtracker.config.AiMatchingConfig());

        service.analyze(3L, new ResumeTailoringDtos.AnalyzeRequest(4L, "Java engineer", null));

        verify(suggestions, never()).save(any(TailoringSuggestion.class));
    }

    @Test
    void analyzeRunsFallbackWhenGeminiProposalsAllFailGrounding() {
        // H2: When Gemini is the primary provider but all its proposals fail grounding (because
        // their beforeText isn't in the source), the service must fall back to the rule-based
        // provider rather than silently returning zero suggestions.
        String resumeText = "SKILLS\nJava, Spring Boot, Git\nEXPERIENCE\nBuilt REST APIs";

        ResumeTailoringProvider alwaysInvalid = (rt, jd, kw) -> List.of(
            new ResumeTailoringProvider.Proposal("IMPACT", "text not in resume at all",
                "rewrite", "rationale", "text not in resume at all")
        );
        // Rule-based fallback will find at least the keyword lines and produce proposals
        ResumeTailoringProvider fallbackProvider = new RuleBasedResumeTailoringProvider();

        Resume resume = resume(resumeText);
        ResumeRepository resumes = mock(ResumeRepository.class); when(resumes.findById(4L)).thenReturn(Optional.of(resume));
        TailoringSessionRepository sessions = mock(TailoringSessionRepository.class); when(sessions.save(any())).thenAnswer(inv -> { TailoringSession v = inv.getArgument(0); v.setId(8L); return v; });
        TailoringSuggestionRepository suggestions = mock(TailoringSuggestionRepository.class); when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(suggestions.findBySessionIdOrderByIdAsc(8L)).thenReturn(List.of());

        var config = new com.smartjobtracker.config.AiMatchingConfig(); config.setProvider("gemini"); config.setApiKey("fake");
        ResumeTailoringService service = new ResumeTailoringService(resumes, new ResumeProfileExtractor(), sessions, suggestions,
                mock(ResumeVersionRepository.class), new ObjectMapper(), fallbackProvider, alwaysInvalid,
                new com.smartjobtracker.jobs.discovery.JobSkillExtractor(), config);

        service.analyze(3L, new ResumeTailoringDtos.AnalyzeRequest(4L, "Java and Spring Boot required", null));

        // Fallback must have been consulted, producing at least one saved suggestion
        verify(suggestions, atLeastOnce()).save(any(TailoringSuggestion.class));
    }

    private Resume resume(String text) { Resume resume = new Resume(); resume.setId(4L); resume.setUserId(3L); resume.setExtractedText(text); return resume; }
    private TailoringSuggestion savedSuggestion() { TailoringSuggestion suggestion = new TailoringSuggestion(); suggestion.setSessionId(8L); suggestion.setCategory("ATS_KEYWORD"); suggestion.setBeforeText("Java"); suggestion.setAfterText("Java"); suggestion.setRationale("Existing evidence"); suggestion.setEvidenceText("Java"); return suggestion; }
}
