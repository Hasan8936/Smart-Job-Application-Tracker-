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

    @Column(name = "event_at")
    private OffsetDateTime eventAt;

    @Column(name = "trigger_offset_minutes")
    private Integer triggerOffsetMinutes;

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "dedupe_key", nullable = false, unique = true)
    private String dedupeKey;

    private int attempts;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

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
    public OffsetDateTime getEventAt() { return eventAt; }
    public void setEventAt(OffsetDateTime eventAt) { this.eventAt = eventAt; }
    public Integer getTriggerOffsetMinutes() { return triggerOffsetMinutes; }
    public void setTriggerOffsetMinutes(Integer triggerOffsetMinutes) { this.triggerOffsetMinutes = triggerOffsetMinutes; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
