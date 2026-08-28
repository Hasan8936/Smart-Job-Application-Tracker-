package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.DeepMatchDtos.DeepMatchRequest;
import com.smartjobtracker.dto.DeepMatchDtos.DeepMatchResult;
import com.smartjobtracker.dto.DeepMatchDtos.RecruiterTestResult;
import com.smartjobtracker.model.DeepMatchAnalysis;
import com.smartjobtracker.model.Resume;
import com.smartjobtracker.repository.DeepMatchAnalysisRepository;
import com.smartjobtracker.repository.ResumeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResumeDeepMatchServiceTest {

    private ResumeDeepMatchService service(AnthropicClient client) {
        return new ResumeDeepMatchService(client, new ObjectMapper(), mock(ResumeRepository.class), mock(DeepMatchAnalysisRepository.class));
    }

    @Test
    void boundClampsScoreAndTrimsListsToMaximums() {
        ResumeDeepMatchService service = service(mock(AnthropicClient.class));

        RecruiterTestResult oversized = new RecruiterTestResult(
                150,
                List.of("a", "b", "c", "d", "e", "f", "g"),
                List.of("flag1", "flag2", "flag3", "flag4", "flag5"));

        RecruiterTestResult bounded = service.bound(oversized);

        assertEquals(100, bounded.compatibilityScore());
        assertEquals(5, bounded.missingKeywords().size());
        assertEquals(List.of("a", "b", "c", "d", "e"), bounded.missingKeywords());
        assertEquals(3, bounded.redFlags().size());
        assertEquals(List.of("flag1", "flag2", "flag3"), bounded.redFlags());
    }

    @Test
    void boundClampsNegativeScoreToZeroAndHandlesNullLists() {
        ResumeDeepMatchService service = service(mock(AnthropicClient.class));

        RecruiterTestResult negative = new RecruiterTestResult(-20, null, null);

        RecruiterTestResult bounded = service.bound(negative);

        assertEquals(0, bounded.compatibilityScore());
        assertTrue(bounded.missingKeywords().isEmpty());
        assertTrue(bounded.redFlags().isEmpty());
    }

    @Test
    void boundLeavesInRangeResultUnchanged() {
        ResumeDeepMatchService service = service(mock(AnthropicClient.class));

        RecruiterTestResult inRange = new RecruiterTestResult(72, List.of("Kubernetes", "Terraform"), List.of("No leadership experience listed"));

        RecruiterTestResult bounded = service.bound(inRange);

        assertEquals(72, bounded.compatibilityScore());
        assertEquals(List.of("Kubernetes", "Terraform"), bounded.missingKeywords());
        assertEquals(List.of("No leadership experience listed"), bounded.redFlags());
    }

    @Test
    void analyzeParsesStructuredJsonResponseAndAppliesBounds() {
        Resume resume = new Resume();
        resume.setId(4L);
        resume.setUserId(3L);
        resume.setExtractedText("Java, Spring Boot, PostgreSQL");

        ResumeRepository resumes = mock(ResumeRepository.class);
        when(resumes.findById(4L)).thenReturn(Optional.of(resume));

        DeepMatchAnalysisRepository analyses = mock(DeepMatchAnalysisRepository.class);
        when(analyses.save(any(DeepMatchAnalysis.class))).thenAnswer(invocation -> {
            DeepMatchAnalysis value = invocation.getArgument(0);
            value.setId(9L);
            return value;
        });

        AnthropicClient client = mock(AnthropicClient.class);
        // Oversized response: score above 100, 6 missing keywords, 4 red flags.
        when(client.complete(any(), any(), anyInt())).thenReturn(
                "{\"compatibilityScore\": 130, "
                        + "\"missingKeywords\": [\"Docker\", \"Kubernetes\", \"AWS\", \"CI/CD\", \"GraphQL\", \"Kafka\"], "
                        + "\"redFlags\": [\"No leadership\", \"Short tenure\", \"No testing mentioned\", \"Career gap\"]}");

        ResumeDeepMatchService service = new ResumeDeepMatchService(client, new ObjectMapper(), resumes, analyses);

        DeepMatchResult result = service.analyze(3L, new DeepMatchRequest(4L, "Backend engineer role"));

        assertEquals(100, result.recruiterTest().compatibilityScore());
        assertEquals(5, result.recruiterTest().missingKeywords().size());
        assertEquals(3, result.recruiterTest().redFlags().size());
        verify(analyses).save(any(DeepMatchAnalysis.class));
    }
}
