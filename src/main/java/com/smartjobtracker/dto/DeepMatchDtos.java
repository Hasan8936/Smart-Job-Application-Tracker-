package com.smartjobtracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class DeepMatchDtos {

    public record DeepMatchRequest(
            @jakarta.validation.constraints.NotNull Long resumeId,
            @NotBlank @Size(max = 100_000) String jobDescription
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecruiterTestResult(
            int compatibilityScore,
            List<String> missingKeywords,
            List<String> redFlags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record XyzRewriteResult(
            String rewrittenExperience,
            String rewrittenProjects,
            String rewrittenSkills
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AtsFilterResult(
            List<String> flaggedSections,
            List<String> fixedSections
    ) {}

    public record DeepMatchResult(
            Long analysisId,
            Long resumeId,
            String jobDescription,
            RecruiterTestResult recruiterTest,
            XyzRewriteResult xyzRewrite,
            AtsFilterResult atsFilter
    ) {}
}