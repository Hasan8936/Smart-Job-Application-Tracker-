package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "resume_tailoring_suggestions")
public class TailoringSuggestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false) private Long sessionId;
    @Column(nullable = false) private String category;
    @Column(name = "before_text", nullable = false, columnDefinition = "TEXT") private String beforeText;
    @Column(name = "after_text", nullable = false, columnDefinition = "TEXT") private String afterText;
    @Column(nullable = false, columnDefinition = "TEXT") private String rationale;
    @Column(name = "evidence_text", nullable = false, columnDefinition = "TEXT") private String evidenceText;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TailoringSuggestionDecision decision = TailoringSuggestionDecision.PENDING;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public Long getSessionId() { return sessionId; } public void setSessionId(Long value) { sessionId = value; }
    public String getCategory() { return category; } public void setCategory(String value) { category = value; }
    public String getBeforeText() { return beforeText; } public void setBeforeText(String value) { beforeText = value; }
    public String getAfterText() { return afterText; } public void setAfterText(String value) { afterText = value; }
    public String getRationale() { return rationale; } public void setRationale(String value) { rationale = value; }
    public String getEvidenceText() { return evidenceText; } public void setEvidenceText(String value) { evidenceText = value; }
    public TailoringSuggestionDecision getDecision() { return decision; } public void setDecision(TailoringSuggestionDecision value) { decision = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
}