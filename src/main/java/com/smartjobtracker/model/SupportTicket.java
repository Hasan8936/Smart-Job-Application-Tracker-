package com.smartjobtracker.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** A user's support request; status OPEN, IN_PROGRESS, RESOLVED or CLOSED. */
@Entity
@Table(name = "support_tickets")
public class SupportTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 200) private String subject;
    @Column(nullable = false, length = 20) private String category;
    @Column(nullable = false, length = 20) private String status = "OPEN";
    @Column(name = "assigned_admin_id") private Long assignedAdminId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getUserId() { return userId; } public void setUserId(Long v) { userId = v; }
    public String getSubject() { return subject; } public void setSubject(String v) { subject = v; }
    public String getCategory() { return category; } public void setCategory(String v) { category = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Long getAssignedAdminId() { return assignedAdminId; } public void setAssignedAdminId(Long v) { assignedAdminId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
}
