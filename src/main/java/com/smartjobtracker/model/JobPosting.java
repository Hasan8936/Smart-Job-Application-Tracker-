package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "job_postings", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "external_id"}))
public class JobPosting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String provider;
    @Column(name = "external_id", nullable = false) private String externalId;
    @Column(name = "dedupe_hash", nullable = false) private String dedupeHash;
    @Column(nullable = false) private String company;
    private String title; private String location;
    @Column(name = "employment_type") private String employmentType;
    @Column(name = "work_mode") private String workMode;
    @Column(name = "apply_url", nullable = false, length = 2000) private String applyUrl;
    @Column(name = "posted_at") private OffsetDateTime postedAt;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "logo_url", length = 2000) private String logoUrl;
    @Column(name = "salary_min") private Integer salaryMin; @Column(name = "salary_max") private Integer salaryMax;
    @Column(name = "salary_currency") private String salaryCurrency;
    @Column(name = "raw_json", columnDefinition = "text") private String rawJson;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getProvider(){return provider;} public void setProvider(String v){provider=v;}
    public String getExternalId(){return externalId;} public void setExternalId(String v){externalId=v;} public String getDedupeHash(){return dedupeHash;} public void setDedupeHash(String v){dedupeHash=v;}
    public String getCompany(){return company;} public void setCompany(String v){company=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getLocation(){return location;} public void setLocation(String v){location=v;}
    public String getEmploymentType(){return employmentType;} public void setEmploymentType(String v){employmentType=v;} public String getWorkMode(){return workMode;} public void setWorkMode(String v){workMode=v;} public String getApplyUrl(){return applyUrl;} public void setApplyUrl(String v){applyUrl=v;}
    public OffsetDateTime getPostedAt(){return postedAt;} public void setPostedAt(OffsetDateTime v){postedAt=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;} public String getLogoUrl(){return logoUrl;} public void setLogoUrl(String v){logoUrl=v;}
    public Integer getSalaryMin(){return salaryMin;} public void setSalaryMin(Integer v){salaryMin=v;} public Integer getSalaryMax(){return salaryMax;} public void setSalaryMax(Integer v){salaryMax=v;} public String getSalaryCurrency(){return salaryCurrency;} public void setSalaryCurrency(String v){salaryCurrency=v;} public String getRawJson(){return rawJson;} public void setRawJson(String v){rawJson=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
}