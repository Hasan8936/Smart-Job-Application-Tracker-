package com.smartjobtracker.model;

import jakarta.persistence.*;

@Entity
@Table(name = "job_skills", uniqueConstraints = @UniqueConstraint(columnNames = {"job_posting_id", "normalized_name"}))
public class JobSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "job_posting_id", nullable = false) private Long jobPostingId;
    @Column(nullable = false) private String name;
    @Column(name = "normalized_name", nullable = false) private String normalizedName;
    @Column(nullable = false) private String requirement;
    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public Long getJobPostingId() { return jobPostingId; } public void setJobPostingId(Long value) { jobPostingId = value; }
    public String getName() { return name; } public void setName(String value) { name = value; }
    public String getNormalizedName() { return normalizedName; } public void setNormalizedName(String value) { normalizedName = value; }
    public String getRequirement() { return requirement; } public void setRequirement(String value) { requirement = value; }
}