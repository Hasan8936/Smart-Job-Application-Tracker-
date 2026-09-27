package com.smartjobtracker.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** An admin's flag on a user account (category ABUSE/BILLING/BUG/DATA_ISSUE/OTHER, status OPEN/RESOLVED). */
@Entity
@Table(name = "user_flags")
public class UserFlag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 20) private String category;
    @Column(nullable = false, length = 10) private String severity;
    @Column(columnDefinition = "text") private String note;
    @Column(nullable = false, length = 10) private String status = "OPEN";
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "resolved_by") private Long resolvedBy;
    @Column(name = "resolved_at") private OffsetDateTime resolvedAt;

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getUserId() { return userId; } public void setUserId(Long v) { userId = v; }
    public String getCategory() { return category; } public void setCategory(String v) { category = v; }
    public String getSeverity() { return severity; } public void setSeverity(String v) { severity = v; }
    public String getNote() { return note; } public void setNote(String v) { note = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Long getCreatedBy() { return createdBy; } public void setCreatedBy(Long v) { createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    public Long getResolvedBy() { return resolvedBy; } public void setResolvedBy(Long v) { resolvedBy = v; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; } public void setResolvedAt(OffsetDateTime v) { resolvedAt = v; }
}
