package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name="application_suggestions")
public class ApplicationSuggestion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="preparation_id", nullable=false) private Long preparationId;
    @Column(name="mapping_id", nullable=false) private Long mappingId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ApplicationFieldType fieldType;
    @Column(name="suggested_value", nullable=false, columnDefinition="TEXT") private String suggestedValue;
    @Column(name="source_evidence", nullable=false, columnDefinition="TEXT") private String sourceEvidence;
    @Column(nullable=false, columnDefinition="TEXT") private String rationale;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ApplicationSuggestionDecision decision=ApplicationSuggestionDecision.PENDING;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt=OffsetDateTime.now();
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getPreparationId(){return preparationId;} public void setPreparationId(Long v){preparationId=v;}
    public Long getMappingId(){return mappingId;} public void setMappingId(Long v){mappingId=v;} public ApplicationFieldType getFieldType(){return fieldType;} public void setFieldType(ApplicationFieldType v){fieldType=v;}
    public String getSuggestedValue(){return suggestedValue;} public void setSuggestedValue(String v){suggestedValue=v;} public String getSourceEvidence(){return sourceEvidence;} public void setSourceEvidence(String v){sourceEvidence=v;}
    public String getRationale(){return rationale;} public void setRationale(String v){rationale=v;} public ApplicationSuggestionDecision getDecision(){return decision;} public void setDecision(ApplicationSuggestionDecision v){decision=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
}