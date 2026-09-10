package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name = "interview_prep_questions")
public class InterviewPrepQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false) private Long sessionId;
    @Column(nullable = false) private int position;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private InterviewQuestionCategory category;
    @Column(nullable = false, columnDefinition = "TEXT") private String question;
    @Column(name = "suggested_answer", nullable = false, columnDefinition = "TEXT") private String suggestedAnswer;
    @Column(name = "source_evidence", columnDefinition = "TEXT") private String sourceEvidence;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getSessionId() { return sessionId; } public void setSessionId(Long v) { sessionId = v; }
    public int getPosition() { return position; } public void setPosition(int v) { position = v; }
    public InterviewQuestionCategory getCategory() { return category; } public void setCategory(InterviewQuestionCategory v) { category = v; }
    public String getQuestion() { return question; } public void setQuestion(String v) { question = v; }
    public String getSuggestedAnswer() { return suggestedAnswer; } public void setSuggestedAnswer(String v) { suggestedAnswer = v; }
    public String getSourceEvidence() { return sourceEvidence; } public void setSourceEvidence(String v) { sourceEvidence = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
