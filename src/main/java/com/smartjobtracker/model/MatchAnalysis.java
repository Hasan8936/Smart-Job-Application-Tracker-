package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "match_analyses", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "resume_id", "job_posting_id"}))
public class MatchAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "resume_id", nullable = false) private Long resumeId;
    @Column(name = "job_posting_id") private Long jobPostingId;
    @Column(name = "source_hash", nullable = false) private String sourceHash;
    @Column(name = "overall_score", nullable = false) private double overallScore;
    @Column(name = "skill_score", nullable = false) private double skillScore;
    @Column(name = "experience_score", nullable = false) private double experienceScore;
    @Column(name = "role_score", nullable = false) private double roleScore;
    @Column(name = "semantic_score", nullable = false) private double semanticScore;
    @Column(name = "breakdown_json", nullable = false, columnDefinition = "text") private String breakdownJson;
    @Column(name = "computed_at", nullable = false) private OffsetDateTime computedAt = OffsetDateTime.now();
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public Long getResumeId(){return resumeId;} public void setResumeId(Long v){resumeId=v;} public Long getJobPostingId(){return jobPostingId;} public void setJobPostingId(Long v){jobPostingId=v;} public String getSourceHash(){return sourceHash;} public void setSourceHash(String v){sourceHash=v;}
    public double getOverallScore(){return overallScore;} public void setOverallScore(double v){overallScore=v;} public double getSkillScore(){return skillScore;} public void setSkillScore(double v){skillScore=v;} public double getExperienceScore(){return experienceScore;} public void setExperienceScore(double v){experienceScore=v;} public double getRoleScore(){return roleScore;} public void setRoleScore(double v){roleScore=v;} public double getSemanticScore(){return semanticScore;} public void setSemanticScore(double v){semanticScore=v;} public String getBreakdownJson(){return breakdownJson;} public void setBreakdownJson(String v){breakdownJson=v;} public OffsetDateTime getComputedAt(){return computedAt;} public void setComputedAt(OffsetDateTime v){computedAt=v;}
}