package com.smartjobtracker.dto;

import jakarta.validation.constraints.Pattern;

import java.util.List;

public class ResumeBuilderDto {

    /** Resume layout id (see ResumeTemplate); optional, defaults to Jake's Resume. */
    @Pattern(regexp = "(?i)jakes|sb2nov|compact", message = "must be one of: jakes, sb2nov, compact")
    private String template;
    private String goal;
    private String targetRole;
    private PersonalInfo personalInfo;
    private String summary;
    private List<ExperienceEntry> experience;
    private List<ProjectEntry> projects;
    private List<EducationEntry> education;
    private Skills skills;

    public record PersonalInfo(String name, String email, String phone, String location,
                               String linkedin, String github, String website, String leetcode) {}

    public record ExperienceEntry(String company, String role, String startDate, String endDate,
                                  boolean current, List<String> bullets) {}

    public record ProjectEntry(String name, List<String> description, List<String> techStack,
                               String githubUrl, String liveUrl, String date) {}

    public record EducationEntry(String institution, String degree, String field,
                                 String startYear, String endYear, String gpa) {}

    public record Skills(List<String> languages, List<String> frameworks,
                         List<String> tools, List<String> other) {}

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
