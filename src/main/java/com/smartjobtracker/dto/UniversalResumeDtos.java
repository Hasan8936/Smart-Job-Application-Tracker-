package com.smartjobtracker.dto;

import java.time.OffsetDateTime;

public final class UniversalResumeDtos {
    private UniversalResumeDtos() {}

    /** The user's universal resume; {@code resumeId} is the Resume row used for matching. */
    public record Response(ResumeBuilderDto resume, String template, Long resumeId,
                           OffsetDateTime updatedAt, boolean usedForMatching) {}

    /**
     * The resume matching should use when the caller doesn't pick one.
     * {@code source}: UNIVERSAL, LATEST_UPLOAD or NONE (resumeId null).
     */
    public record MatchingResume(Long resumeId, String source, String fileName) {}
}
