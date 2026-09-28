package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * What the user wants recommended, from the onboarding popup (one row per user). List fields are JSON arrays in
 * {@code text} columns; {@code JobPreferenceService} is the only place that reads/writes that JSON.
 * {@code status} is SAVED once filled in, or SKIPPED when the popup was dismissed so it isn't shown again.
 */
@Entity
@Table(name = "job_search_preferences")
public class JobSearchPreference {
    public static final String SAVED = "SAVED";
    public static final String SKIPPED = "SKIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "roles", columnDefinition = "text")
    private String roles;

    @Column(name = "experience_level", length = 20)
    private String experienceLevel;

    @Column(name = "locations", columnDefinition = "text")
    private String locations;

    @Column(name = "work_modes", columnDefinition = "text")
    private String workModes;

    @Column(name = "job_types", columnDefinition = "text")
    private String jobTypes;

    @Column(name = "min_salary_lpa")
    private Integer minSalaryLpa;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }
    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }
    public String getLocations() { return locations; }
    public void setLocations(String locations) { this.locations = locations; }
    public String getWorkModes() { return workModes; }
    public void setWorkModes(String workModes) { this.workModes = workModes; }
    public String getJobTypes() { return jobTypes; }
    public void setJobTypes(String jobTypes) { this.jobTypes = jobTypes; }
    public Integer getMinSalaryLpa() { return minSalaryLpa; }
    public void setMinSalaryLpa(Integer minSalaryLpa) { this.minSalaryLpa = minSalaryLpa; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
