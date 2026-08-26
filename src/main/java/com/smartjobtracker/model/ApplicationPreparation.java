package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name="application_preparations")
public class ApplicationPreparation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    @Column(name="job_description", nullable=false, columnDefinition="TEXT") private String jobDescription;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt=OffsetDateTime.now();
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getJobDescription(){return jobDescription;} public void setJobDescription(String v){jobDescription=v;} public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
}