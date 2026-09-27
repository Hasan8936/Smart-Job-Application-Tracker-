package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(name = "timezone")
    private String timezone = "UTC";

    @Column(name = "reminder_preferences", columnDefinition = "TEXT")
    private String reminderPreferences = "{}";

    private OffsetDateTime createdAt = OffsetDateTime.now();

    /** USER or ADMIN; resolved from the database on every request. */
    @Column(nullable = false, length = 20)
    private String role = "USER";

    /** Suspended users are refused at login and on every API call. */
    @Column(nullable = false)
    private boolean suspended = false;

    @Column(name = "suspended_reason", length = 500)
    private String suspendedReason;

    /** False for OAuth sign-ups that never chose a password (their hash is random). */
    @Column(name = "password_set", nullable = false)
    private boolean passwordSet = true;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }
    public String getSuspendedReason() { return suspendedReason; }
    public void setSuspendedReason(String suspendedReason) { this.suspendedReason = suspendedReason; }
    public boolean isPasswordSet() { return passwordSet; }
    public void setPasswordSet(boolean passwordSet) { this.passwordSet = passwordSet; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getReminderPreferences() { return reminderPreferences; }
    public void setReminderPreferences(String reminderPreferences) { this.reminderPreferences = reminderPreferences; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
