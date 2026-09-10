package com.smartjobtracker.dto;

import com.smartjobtracker.model.InterviewPrepSource;
import com.smartjobtracker.model.InterviewQuestionCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class InterviewPrepDtos {
    private InterviewPrepDtos() {}

    /**
     * Exactly one of jobDescriptionText / jobDescriptionUrl / applicationId must be provided,
     * matching {@code source}. The service resolves whichever one applies and rejects the rest.
     */
    public record GenerateRequest(@NotNull Long resumeId, @NotNull InterviewPrepSource source,
                                   @Size(max = 100000) String jobDescriptionText,
                                   @Size(max = 2000) String jobDescriptionUrl,
                                   Long applicationId,
                                   Integer questionCount) {}

    public record Question(Long id, int position, InterviewQuestionCategory category, String question,
                            String suggestedAnswer, String sourceEvidence) {}

    public record Session(Long id, String jobDescription, InterviewPrepSource source, Long resumeId,
                           Long applicationId, OffsetDateTime createdAt, List<Question> questions) {}

    public record SessionSummary(Long id, String jobDescriptionPreview, InterviewPrepSource source,
                                  int questionCount, OffsetDateTime createdAt) {}
}
