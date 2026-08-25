package com.smartjobtracker.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * API representation of a {@link com.smartjobtracker.model.CandidateProfile}.
 *
 * <p>The entity stores each group as JSON text; this DTO exposes real lists so
 * the entity is never serialized directly (keeps the JSON contract in one place
 * and avoids leaking storage details). Used for both responses and PUT edits.
 *
 * <p>{@code sourceResumeId} and {@code updatedAt} are read-only metadata for the
 * UI; the service ignores them on write.
 */
public class CandidateProfileDto {

    private Long sourceResumeId;
    private OffsetDateTime updatedAt;

    @Size(max = 300, message = "too many skills")
    private List<@Size(max = 200, message = "skill too long") String> skills = new ArrayList<>();

    @Size(max = 300, message = "too many programming languages")
    private List<@Size(max = 200, message = "value too long") String> programmingLanguages = new ArrayList<>();

    @Size(max = 300, message = "too many frameworks")
    private List<@Size(max = 200, message = "value too long") String> frameworks = new ArrayList<>();

    @Size(max = 300, message = "too many projects")
    private List<@Size(max = 2000, message = "entry too long") String> projects = new ArrayList<>();

    @Size(max = 300, message = "too many education entries")
    private List<@Size(max = 2000, message = "entry too long") String> education = new ArrayList<>();

    @Size(max = 300, message = "too many experience entries")
    private List<@Size(max = 2000, message = "entry too long") String> experience = new ArrayList<>();

    @Size(max = 100, message = "too many preferred roles")
    private List<@Size(max = 200, message = "value too long") String> preferredRoles = new ArrayList<>();

    public Long getSourceResumeId() { return sourceResumeId; }
    public void setSourceResumeId(Long sourceResumeId) { this.sourceResumeId = sourceResumeId; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public List<String> getProgrammingLanguages() { return programmingLanguages; }
    public void setProgrammingLanguages(List<String> programmingLanguages) { this.programmingLanguages = programmingLanguages; }
    public List<String> getFrameworks() { return frameworks; }
    public void setFrameworks(List<String> frameworks) { this.frameworks = frameworks; }
    public List<String> getProjects() { return projects; }
    public void setProjects(List<String> projects) { this.projects = projects; }
    public List<String> getEducation() { return education; }
    public void setEducation(List<String> education) { this.education = education; }
    public List<String> getExperience() { return experience; }
    public void setExperience(List<String> experience) { this.experience = experience; }
    public List<String> getPreferredRoles() { return preferredRoles; }
    public void setPreferredRoles(List<String> preferredRoles) { this.preferredRoles = preferredRoles; }
}
