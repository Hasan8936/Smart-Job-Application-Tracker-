package com.smartjobtracker.service;

import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        JobActionService service = new JobActionService(savedJobs, jobs, applications, mock(JobApplicationService.class), mock(GeneratedDocumentRepository.class), mock(CandidateProfileRepository.class));
        SavedJob result = service.markApplied(5L, 3L);
        assertEquals(8L, result.getApplicationId());
        verify(applications, never()).save(any());
    }

    @Test
    void generatedDocumentUsesVerifiedProfileFieldsAndIsStored() {
        JobPosting job = job(); JobPostingRepository jobs = mock(JobPostingRepository.class); when(jobs.findById(3L)).thenReturn(Optional.of(job));
        CandidateProfile profile = new CandidateProfile(); profile.setSkills("[\"Java\"]");
        CandidateProfileRepository profiles = mock(CandidateProfileRepository.class); when(profiles.findByUserId(5L)).thenReturn(Optional.of(profile));
        GeneratedDocumentRepository documents = mock(GeneratedDocumentRepository.class); when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        JobActionService service = new JobActionService(mock(SavedJobRepository.class), jobs, mock(JobApplicationRepository.class), mock(JobApplicationService.class), documents, profiles);
        GeneratedDocument result = service.generate(5L, 3L, "COVER_LETTER");
        assertEquals("COVER_LETTER", result.getType());
        org.junit.jupiter.api.Assertions.assertTrue(result.getContent().contains("Java"));
        verify(documents).save(any(GeneratedDocument.class));
    }

    private JobPosting job() { JobPosting job = new JobPosting(); job.setId(3L); job.setCompany("Acme"); job.setTitle("Engineer"); job.setDescription("Build software"); return job; }
}