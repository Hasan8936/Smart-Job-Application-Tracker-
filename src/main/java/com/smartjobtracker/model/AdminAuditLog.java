package com.smartjobtracker.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** One admin action. {@code detail} holds ids and short labels only — never user content. */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "admin_id") private Long adminId;
    @Column(name = "target_user_id") private Long targetUserId;
    @Column(nullable = false, length = 40) private String action;
    @Column(length = 500) private String detail;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getAdminId() { return adminId; } public void setAdminId(Long v) { adminId = v; }
    public Long getTargetUserId() { return targetUserId; } public void setTargetUserId(Long v) { targetUserId = v; }
    public String getAction() { return action; } public void setAction(String v) { action = v; }
    public String getDetail() { return detail; } public void setDetail(String v) { detail = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
