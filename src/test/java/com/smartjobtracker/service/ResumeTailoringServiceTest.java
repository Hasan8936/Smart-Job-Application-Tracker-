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

        ResumeTailoringDtos.Analysis result = service.analyze(3L, new ResumeTailoringDtos.AnalyzeRequest(4L, "Java and Docker required"));

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

    private ResumeTailoringService service(ResumeRepository resumes, TailoringSessionRepository sessions, TailoringSuggestionRepository suggestions, ResumeVersionRepository versions, ResumeTailoringProvider fallback) {
        return new ResumeTailoringService(resumes, new ResumeProfileExtractor(), sessions, suggestions, versions, new ObjectMapper(), fallback, mock(ResumeTailoringProvider.class), new com.smartjobtracker.jobs.discovery.JobSkillExtractor(), new com.smartjobtracker.config.AiMatchingConfig());
    }

    private Resume resume(String text) { Resume resume = new Resume(); resume.setId(4L); resume.setUserId(3L); resume.setExtractedText(text); return resume; }
    private TailoringSuggestion savedSuggestion() { TailoringSuggestion suggestion = new TailoringSuggestion(); suggestion.setSessionId(8L); suggestion.setCategory("ATS_KEYWORD"); suggestion.setBeforeText("Java"); suggestion.setAfterText("Java"); suggestion.setRationale("Existing evidence"); suggestion.setEvidenceText("Java"); return suggestion; }
}