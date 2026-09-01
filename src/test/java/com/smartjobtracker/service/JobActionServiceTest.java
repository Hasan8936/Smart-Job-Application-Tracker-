package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobActionServiceTest {
    @Test
    void markAppliedReusesExistingApplication() {
        JobPosting job = job();
        JobPostingRepository jobs = mock(JobPostingRepository.class); when(jobs.findById(3L)).thenReturn(Optional.of(job));
        JobApplication application = new JobApplication(); application.setId(8L);
        JobApplicationRepository applications = mock(JobApplicationRepository.class);
        when(applications.findFirstByUserIdAndCompanyNameIgnoreCaseAndRoleTitleIgnoreCase(5L, "Acme", "Engineer")).thenReturn(Optional.of(application));
        SavedJob saved = new SavedJob(); saved.setId(9L);
        SavedJobRepository savedJobs = mock(SavedJobRepository.class); when(savedJobs.findByUserIdAndJobPostingId(5L, 3L)).thenReturn(Optional.of(saved)); when(savedJobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        JobActionService service = service(savedJobs, jobs, applications, mock(JobApplicationService.class), mock(GeneratedDocumentRepository.class), mock(CandidateProfileRepository.class), mock(ResumeRepository.class));
        SavedJob result = service.markApplied(5L, 3L);
        assertEquals(8L, result.getApplicationId());
        verify(applications, never()).save(any());
    }

    @Test
    void generateWithoutAResumeFailsInsteadOfInventingContent() {
        JobPosting job = job(); JobPostingRepository jobs = mock(JobPostingRepository.class); when(jobs.findById(3L)).thenReturn(Optional.of(job));
        ResumeRepository resumes = mock(ResumeRepository.class); when(resumes.findByUserId(5L)).thenReturn(List.of());
        JobActionService service = service(mock(SavedJobRepository.class), jobs, mock(JobApplicationRepository.class), mock(JobApplicationService.class), mock(GeneratedDocumentRepository.class), mock(CandidateProfileRepository.class), resumes);
        assertThrows(IllegalArgumentException.class, () -> service.generate(5L, 3L, "COVER_LETTER"));
    }

    @Test
    void generatedDocumentFallsBackToResumeGroundedTemplateWhenGeminiFails() {
        JobPosting job = job(); JobPostingRepository jobs = mock(JobPostingRepository.class); when(jobs.findById(3L)).thenReturn(Optional.of(job));
        Resume resume = new Resume(); resume.setExtractedText("Experienced in Java and Spring Boot.");
        ResumeRepository resumes = mock(ResumeRepository.class); when(resumes.findByUserId(5L)).thenReturn(List.of(resume));
        GeneratedDocumentRepository documents = mock(GeneratedDocumentRepository.class); when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GeminiClient gemini = mock(GeminiClient.class); when(gemini.complete(any(), any(), anyInt())).thenThrow(new RuntimeException("Gemini unavailable"));
        JobActionService service = new JobActionService(mock(SavedJobRepository.class), jobs, mock(JobApplicationRepository.class), mock(JobApplicationService.class), documents, mock(CandidateProfileRepository.class), resumes, gemini, new ObjectMapper());
        GeneratedDocument result = service.generate(5L, 3L, "COVER_LETTER");
        assertEquals("COVER_LETTER", result.getType());
        org.junit.jupiter.api.Assertions.assertTrue(result.getContent().contains("Java"));
        verify(documents).save(any(GeneratedDocument.class));
    }

    private JobActionService service(SavedJobRepository savedJobs, JobPostingRepository jobs, JobApplicationRepository applications,
            JobApplicationService applicationService, GeneratedDocumentRepository documents, CandidateProfileRepository profiles, ResumeRepository resumes) {
        return new JobActionService(savedJobs, jobs, applications, applicationService, documents, profiles, resumes, mock(GeminiClient.class), new ObjectMapper());
    }

    private JobPosting job() { JobPosting job = new JobPosting(); job.setId(3L); job.setCompany("Acme"); job.setTitle("Engineer"); job.setDescription("Build software"); return job; }
}
