package com.smartjobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class MatchRequest {
    @NotNull(message = "resumeId is required")
    private Long resumeId;

    @NotBlank(message = "jobDescriptionText is required")
    @Size(max = 50_000, message = "jobDescriptionText must be 50,000 characters or fewer")
    private String jobDescriptionText;

    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }
    public String getJobDescriptionText() { return jobDescriptionText; }
    public void setJobDescriptionText(String jobDescriptionText) { this.jobDescriptionText = jobDescriptionText; }
}
