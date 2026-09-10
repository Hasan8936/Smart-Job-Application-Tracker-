package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name = "interview_prep_sessions")
public class InterviewPrepSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "resume_id") private Long resumeId;
    @Column(name = "application_id") private Long applicationId;
    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT") private String jobDescription;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InterviewPrepSource source;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getUserId() { return userId; } public void setUserId(Long v) { userId = v; }
    public Long getResumeId() { return resumeId; } public void setResumeId(Long v) { resumeId = v; }
    public Long getApplicationId() { return applicationId; } public void setApplicationId(Long v) { applicationId = v; }
    public String getJobDescription() { return jobDescription; } public void setJobDescription(String v) { jobDescription = v; }
    public InterviewPrepSource getSource() { return source; } public void setSource(InterviewPrepSource v) { source = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
