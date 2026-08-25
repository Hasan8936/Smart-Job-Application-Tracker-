package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Structured candidate profile derived from a user's resume (Phase 1).
 *
 * One row per user. The seven extracted groups (skills, programming languages,
 * frameworks, projects, education, experience, preferred roles) are each stored
 * as a JSON array in a {@code text} column. The service layer (Jackson) is the
 * only place that reads/writes that JSON — callers always see real lists via
 * {@code CandidateProfileDto}, never this entity.
 *
 * JSON-in-text is used deliberately so the mapping is identical on H2 (tests,
 * ddl-auto) and PostgreSQL (prod, Flyway). The original {@link Resume} is never
 * modified; this profile only references it via {@code sourceResumeId}.
 */
@Entity
@Table(name = "candidate_profiles")
public class CandidateProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner. Unique — one profile per user. */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** Resume the extracted data last came from (nullable; resume is never mutated). */
    @Column(name = "source_resume_id")
    private Long sourceResumeId;

    @Column(name = "skills", columnDefinition = "text")
    private String skills;

    @Column(name = "programming_languages", columnDefinition = "text")
    private String programmingLanguages;

    @Column(name = "frameworks", columnDefinition = "text")
    private String frameworks;

    @Column(name = "projects", columnDefinition = "text")
    private String projects;

    @Column(name = "education", columnDefinition = "text")
    private String education;

    @Column(name = "experience", columnDefinition = "text")
    private String experience;

    @Column(name = "preferred_roles", columnDefinition = "text")
    private String preferredRoles;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSourceResumeId() { return sourceResumeId; }
    public void setSourceResumeId(Long sourceResumeId) { this.sourceResumeId = sourceResumeId; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getProgrammingLanguages() { return programmingLanguages; }
    public void setProgrammingLanguages(String programmingLanguages) { this.programmingLanguages = programmingLanguages; }
    public String getFrameworks() { return frameworks; }
    public void setFrameworks(String frameworks) { this.frameworks = frameworks; }
    public String getProjects() { return projects; }
    public void setProjects(String projects) { this.projects = projects; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getPreferredRoles() { return preferredRoles; }
    public void setPreferredRoles(String preferredRoles) { this.preferredRoles = preferredRoles; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
