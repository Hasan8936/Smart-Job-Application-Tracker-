package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name="generated_documents")
public class GeneratedDocument {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    @Column(name="job_posting_id", nullable=false) private Long jobPostingId;
    @Column(nullable=false) private String type;
    @Column(nullable=false, columnDefinition="text") private String content;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt=OffsetDateTime.now();
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt=OffsetDateTime.now();
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public Long getJobPostingId(){return jobPostingId;} public void setJobPostingId(Long v){jobPostingId=v;} public String getType(){return type;} public void setType(String v){type=v;} public String getContent(){return content;} public void setContent(String v){content=v;} public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
}