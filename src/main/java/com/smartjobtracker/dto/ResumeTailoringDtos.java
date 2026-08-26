package com.smartjobtracker.dto;

import com.smartjobtracker.model.TailoringSuggestionDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class ResumeTailoringDtos {
    private ResumeTailoringDtos() {}
    public record AnalyzeRequest(@NotNull Long resumeId, @NotBlank @Size(max = 100000) String jobDescription) {}
    public record DecisionRequest(@NotNull TailoringSuggestionDecision decision) {}
    public record Suggestion(Long id, String category, String beforeText, String afterText, String rationale, String evidenceText, TailoringSuggestionDecision decision) {}
    public record Analysis(Long sessionId, Long sourceResumeId, List<String> atsKeywords, List<String> highlightedSkills, List<String> highlightedProjects, List<Suggestion> suggestions, OffsetDateTime createdAt) {}
    public record Version(Long id, Long sourceResumeId, Long tailoringSessionId, String jobDescription, String content, List<Long> acceptedSuggestionIds, OffsetDateTime createdAt) {}
}