package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationChannel channel;
    @Column(name = "dedupe_key", nullable = false, unique = true) private String dedupeKey;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;
    @Column(name = "provider_message_id") private String providerMessageId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationDeliveryStatus status = NotificationDeliveryStatus.QUEUED;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at") private OffsetDateTime nextAttemptAt;
    @Column(name = "submitted_at") private OffsetDateTime submittedAt;
    @Column(name = "confirmed_at") private OffsetDateTime confirmedAt;
    @Column(name = "last_error", columnDefinition = "TEXT") private String lastError;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long value) { userId = value; }
    public NotificationChannel getChannel() { return channel; } public void setChannel(NotificationChannel value) { channel = value; }
    public String getDedupeKey() { return dedupeKey; } public void setDedupeKey(String value) { dedupeKey = value; }
    public String getMessage() { return message; } public void setMessage(String value) { message = value; }
    public String getProviderMessageId() { return providerMessageId; } public void setProviderMessageId(String value) { providerMessageId = value; }
    public NotificationDeliveryStatus getStatus() { return status; } public void setStatus(NotificationDeliveryStatus value) { status = value; }
    public int getAttempts() { return attempts; } public void setAttempts(int value) { attempts = value; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; } public void setNextAttemptAt(OffsetDateTime value) { nextAttemptAt = value; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; } public void setSubmittedAt(OffsetDateTime value) { submittedAt = value; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; } public void setConfirmedAt(OffsetDateTime value) { confirmedAt = value; }
    public String getLastError() { return lastError; } public void setLastError(String value) { lastError = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
}