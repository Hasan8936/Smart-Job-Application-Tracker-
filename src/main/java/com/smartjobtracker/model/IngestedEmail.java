package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name="ingested_emails", uniqueConstraints=@UniqueConstraint(columnNames={"user_id", "gmail_message_id"}))
public class IngestedEmail {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    @Column(name="gmail_message_id", nullable=false) private String gmailMessageId;
    @Column(name="thread_id") private String threadId;
    @Column(name="from_addr") private String fromAddress;
    private String subject;
    @Column(columnDefinition="text") private String snippet;
    @Column(name="received_at") private OffsetDateTime receivedAt;
    private String category;
    @Column(name="processed_at") private OffsetDateTime processedAt;
    private String company;
    @Column(name="job_title") private String jobTitle;
    @Column(name="application_reference") private String applicationReference;
    @Column(name="extracted_status") private String extractedStatus;
    @Column(name="interview_date") private String interviewDate;
    @Column(name="interview_time") private String interviewTime;
    private String deadline;
    @Column(name="action_required") private String actionRequired;
    private Double confidence;
    @Column(name="review_status") private String reviewStatus;
    @Column(name="matched_application_id") private Long matchedApplicationId;
    @Column(name="previous_application_status") private String previousApplicationStatus;
    @Column(name="update_method") private String updateMethod;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getGmailMessageId(){return gmailMessageId;} public void setGmailMessageId(String v){gmailMessageId=v;}
    public String getThreadId(){return threadId;} public void setThreadId(String v){threadId=v;}
    public String getFromAddress(){return fromAddress;} public void setFromAddress(String v){fromAddress=v;}
    public String getSubject(){return subject;} public void setSubject(String v){subject=v;}
    public String getSnippet(){return snippet;} public void setSnippet(String v){snippet=v;}
    public OffsetDateTime getReceivedAt(){return receivedAt;} public void setReceivedAt(OffsetDateTime v){receivedAt=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public OffsetDateTime getProcessedAt(){return processedAt;} public void setProcessedAt(OffsetDateTime v){processedAt=v;}
    public String getCompany(){return company;} public void setCompany(String v){company=v;}
    public String getJobTitle(){return jobTitle;} public void setJobTitle(String v){jobTitle=v;}
    public String getApplicationReference(){return applicationReference;} public void setApplicationReference(String v){applicationReference=v;}
    public String getExtractedStatus(){return extractedStatus;} public void setExtractedStatus(String v){extractedStatus=v;}
    public String getInterviewDate(){return interviewDate;} public void setInterviewDate(String v){interviewDate=v;}
    public String getInterviewTime(){return interviewTime;} public void setInterviewTime(String v){interviewTime=v;}
    public String getDeadline(){return deadline;} public void setDeadline(String v){deadline=v;}
    public String getActionRequired(){return actionRequired;} public void setActionRequired(String v){actionRequired=v;}
    public Double getConfidence(){return confidence;} public void setConfidence(Double v){confidence=v;}
    public String getReviewStatus(){return reviewStatus;} public void setReviewStatus(String v){reviewStatus=v;}
    public Long getMatchedApplicationId(){return matchedApplicationId;} public void setMatchedApplicationId(Long v){matchedApplicationId=v;}
    public String getPreviousApplicationStatus(){return previousApplicationStatus;} public void setPreviousApplicationStatus(String v){previousApplicationStatus=v;}
    public String getUpdateMethod(){return updateMethod;} public void setUpdateMethod(String v){updateMethod=v;}
}
