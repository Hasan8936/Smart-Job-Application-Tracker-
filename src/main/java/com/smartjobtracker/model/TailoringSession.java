package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "resume_tailoring_sessions")
public class TailoringSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "source_resume_id", nullable = false) private Long sourceResumeId;
    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT") private String jobDescription;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public Long getUserId() { return userId; } public void setUserId(Long value) { userId = value; }
    public Long getSourceResumeId() { return sourceResumeId; } public void setSourceResumeId(Long value) { sourceResumeId = value; }
    public String getJobDescription() { return jobDescription; } public void setJobDescription(String value) { jobDescription = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
}