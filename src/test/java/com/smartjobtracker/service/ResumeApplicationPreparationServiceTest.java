package com.smartjobtracker.service;

import com.smartjobtracker.dto.ApplicationPreparationDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResumeApplicationPreparationServiceTest {

    @Test
    void portfolioExtractionIgnoresTheUsersOwnEmailDomain() {
        Resume resume = new Resume(); resume.setId(2L); resume.setUserId(3L); resume.setFileName("resume.pdf");
        resume.setExtractedText("Jane Doe\n+91 8922024316  jane.doe@gmail.com LinkedIn GitHub\nEducation\nBE Computer Science");
        ResumeRepository resumes = mock(ResumeRepository.class); when(resumes.findById(2L)).thenReturn(Optional.of(resume));
        User user = new User(); user.setId(3L); user.setName("Jane Doe"); user.setEmail("jane.doe@gmail.com");
        UserRepository users = mock(UserRepository.class); when(users.findById(3L)).thenReturn(Optional.of(user));
        ApplicationPreparation saved = new ApplicationPreparation(); saved.setId(5L);
        ApplicationPreparationRepository preparations = mock(ApplicationPreparationRepository.class); when(preparations.save(any())).thenReturn(saved);
        ApplicationFieldMappingRepository mappings = mock(ApplicationFieldMappingRepository.class); when(mappings.save(any())).thenAnswer(invocation -> { ApplicationFieldMapping m = invocation.getArgument(0); m.setId(1L); return m; });
        ApplicationSuggestionRepository suggestions = mock(ApplicationSuggestionRepository.class); when(suggestions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(suggestions.findByPreparationIdOrderByIdAsc(5L)).thenReturn(java.util.List.of());
        when(mappings.findByPreparationId(5L)).thenReturn(java.util.List.of());

        ResumeApplicationPreparationService service = new ResumeApplicationPreparationService(
                mock(ApplicationProfileRepository.class), preparations, mappings, suggestions, resumes, users,
                mock(CandidateProfileRepository.class), new RuleBasedApplicationPreparationProvider(), mock(ApplicationPreparationProvider.class),
                new com.smartjobtracker.config.AiMatchingConfig(), new ResumeProfileExtractor());

        service.prepare(3L, new ApplicationPreparationDtos.PrepareRequest(2L, "Software Engineer role"));

        verify(suggestions, never()).save(argThat(s -> s.getFieldType() == ApplicationFieldType.PORTFOLIO && "gmail.com".equalsIgnoreCase(s.getSuggestedValue())));
    }
}
