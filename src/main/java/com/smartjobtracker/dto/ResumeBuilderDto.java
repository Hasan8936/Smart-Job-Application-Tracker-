package com.smartjobtracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ResumeBuilderDto {

    /** Resume layout id (see ResumeTemplate); optional, defaults to Jake's Resume. */
    @Pattern(regexp = "(?i)jakes|sb2nov|compact", message = "must be one of: jakes, sb2nov, compact")
    private String template;
    @Size(max = 200) private String goal;
    @Size(max = 200) private String targetRole;
    @Valid private PersonalInfo personalInfo;
    @Size(max = 3000) private String summary;
    @Valid @Size(max = 30) private List<ExperienceEntry> experience;
    @Valid @Size(max = 30) private List<ProjectEntry> projects;
    @Valid @Size(max = 20) private List<EducationEntry> education;
    @Valid private Skills skills;

    public record PersonalInfo(@Size(max = 200) String name, @Size(max = 320) String email, @Size(max = 50) String phone,
                               @Size(max = 200) String location, @Size(max = 500) String linkedin, @Size(max = 500) String github,
                               @Size(max = 500) String website, @Size(max = 500) String leetcode) {}

    public record ExperienceEntry(@Size(max = 200) String company, @Size(max = 200) String role,
                                  @Size(max = 50) String startDate, @Size(max = 50) String endDate,
                                  boolean current, @Size(max = 30) List<@Size(max = 1000) String> bullets) {}

    public record ProjectEntry(@Size(max = 200) String name, @Size(max = 30) List<@Size(max = 1000) String> description,
                               @Size(max = 40) List<@Size(max = 100) String> techStack,
                               @Size(max = 500) String githubUrl, @Size(max = 500) String liveUrl, @Size(max = 50) String date) {}

    public record EducationEntry(@Size(max = 200) String institution, @Size(max = 200) String degree, @Size(max = 200) String field,
                                 @Size(max = 20) String startYear, @Size(max = 20) String endYear, @Size(max = 30) String gpa) {}

    public record Skills(@Size(max = 100) List<@Size(max = 100) String> languages, @Size(max = 100) List<@Size(max = 100) String> frameworks,
                         @Size(max = 100) List<@Size(max = 100) String> tools, @Size(max = 100) List<@Size(max = 100) String> other) {}

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public PersonalInfo getPersonalInfo() { return personalInfo; }
    public void setPersonalInfo(PersonalInfo personalInfo) { this.personalInfo = personalInfo; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<ExperienceEntry> getExperience() { return experience; }
    public void setExperience(List<ExperienceEntry> experience) { this.experience = experience; }
    public List<ProjectEntry> getProjects() { return projects; }
    public void setProjects(List<ProjectEntry> projects) { this.projects = projects; }
    public List<EducationEntry> getEducation() { return education; }
    public void setEducation(List<EducationEntry> education) { this.education = education; }
    public Skills getSkills() { return skills; }
    public void setSkills(Skills skills) { this.skills = skills; }
}
