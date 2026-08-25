package com.smartjobtracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class HybridMatchDtos {
    private HybridMatchDtos() {}
    public record Request(@NotNull Long resumeId, Long jobId, @Size(max = 100000) String jobDescriptionText) {}
    public record Response(double overallMatch, double skillMatch, double experienceMatch,
                           double roleMatch, double semanticSimilarity, String semanticProvider,
                           List<String> missingRequiredSkills, List<String> missingPreferredSkills,
                           List<String> strongMatches, List<String> partialMatches,
                           List<String> recommendations, Breakdown breakdown, OffsetDateTime computedAt) {}
    public record Breakdown(double requiredSkillWeight, double preferredSkillWeight,
                            int requiredSkillCount, int preferredSkillCount,
                            int matchedRequiredCount, int matchedPreferredCount,
                            String explanation) {}
}