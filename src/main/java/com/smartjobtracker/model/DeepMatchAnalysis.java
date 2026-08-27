package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "deep_match_analyses")
public class DeepMatchAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "resume_id", nullable = false) private Long resumeId;
    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT") private String jobDescription;
    @Column(name = "compatibility_score", nullable = false) private int compatibilityScore;
    @Column(name = "missing_keywords", nullable = false, columnDefinition = "TEXT") private String missingKeywords;
    @Column(name = "red_flags", nullable = false, columnDefinition = "TEXT") private String redFlags;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public Long getUserId() { return userId; } public void setUserId(Long value) { userId = value; }
    public Long getResumeId() { return resumeId; } public void setResumeId(Long value) { resumeId = value; }
    public String getJobDescription() { return jobDescription; } public void setJobDescription(String value) { jobDescription = value; }
    public int getCompatibilityScore() { return compatibilityScore; } public void setCompatibilityScore(int value) { compatibilityScore = value; }
    public String getMissingKeywords() { return missingKeywords; } public void setMissingKeywords(String value) { missingKeywords = value; }
    public String getRedFlags() { return redFlags; } public void setRedFlags(String value) { redFlags = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
}