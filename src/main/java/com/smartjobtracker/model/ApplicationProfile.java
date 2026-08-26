package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "application_profiles")
public class ApplicationProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, unique = true) private Long userId;
    @Column(name = "full_name") private String fullName;
    private String email;
    @Column(name = "phone") private String phone;
    @Column(columnDefinition = "TEXT") private String education;
    @Column(columnDefinition = "TEXT") private String experience;
    @Column(columnDefinition = "TEXT") private String skills;
    @Column(name = "github_url") private String githubUrl;
    @Column(name = "linkedin_url") private String linkedinUrl;
    @Column(name = "portfolio_url") private String portfolioUrl;
    @Column(name = "resume_id") private Long resumeId;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;} public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;} public String getEducation(){return education;} public void setEducation(String v){education=v;}
    public String getExperience(){return experience;} public void setExperience(String v){experience=v;} public String getSkills(){return skills;} public void setSkills(String v){skills=v;}
    public String getGithubUrl(){return githubUrl;} public void setGithubUrl(String v){githubUrl=v;} public String getLinkedinUrl(){return linkedinUrl;} public void setLinkedinUrl(String v){linkedinUrl=v;}
    public String getPortfolioUrl(){return portfolioUrl;} public void setPortfolioUrl(String v){portfolioUrl=v;} public Long getResumeId(){return resumeId;} public void setResumeId(Long v){resumeId=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
}