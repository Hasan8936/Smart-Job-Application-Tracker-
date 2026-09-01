package com.smartjobtracker.dto;

import com.smartjobtracker.model.ApplicationFieldType;
import com.smartjobtracker.model.ApplicationSuggestionDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class ApplicationPreparationDtos {
    private ApplicationPreparationDtos() {}
    public record ProfileRequest(@Size(max=255) String fullName, @Size(max=255) String email, @Size(max=50) String phone,
                                 @Size(max=10000) String education, @Size(max=10000) String experience, @Size(max=10000) String skills,
                                 @Size(max=500) String githubUrl, @Size(max=500) String linkedinUrl, @Size(max=500) String portfolioUrl, Long resumeId) {}
    public record MappingRequest(@NotBlank @Size(max=255) String externalField, @NotNull ApplicationFieldType fieldType) {}
    /** No mappings field: prepare() derives the standard field set and every fact directly from the resume + account, nothing manually entered. */
    public record PrepareRequest(@NotNull Long resumeId, @NotBlank @Size(max=100000) String jobDescription) {}
    public record DecisionRequest(@NotNull ApplicationSuggestionDecision decision) {}
    public record Profile(Long id, String fullName, String email, String phone, String education, String experience, String skills, String githubUrl, String linkedinUrl, String portfolioUrl, Long resumeId, OffsetDateTime updatedAt) {}
    public record Mapping(Long id, String externalField, ApplicationFieldType fieldType) {}
    public record Suggestion(Long id, Long mappingId, String externalField, ApplicationFieldType fieldType, String suggestedValue, String sourceEvidence, String rationale, ApplicationSuggestionDecision decision) {}
    public record Preparation(Long id, String jobDescription, Profile profile, List<Mapping> mappings, List<Suggestion> suggestions, boolean submissionRequired) {}
}