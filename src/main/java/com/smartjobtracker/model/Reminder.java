package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reminders")
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "remind_at")
    private OffsetDateTime remindAt;

    @Enumerated(EnumType.STRING)
    private ReminderType type;

    @Enumerated(EnumType.STRING)
    private ReminderStatus status = ReminderStatus.PENDING;

    private String message;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public OffsetDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(OffsetDateTime remindAt) { this.remindAt = remindAt; }
    public ReminderType getType() { return type; }
    public void setType(ReminderType type) { this.type = type; }
    public ReminderStatus getStatus() { return status; }
    public void setStatus(ReminderStatus status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
