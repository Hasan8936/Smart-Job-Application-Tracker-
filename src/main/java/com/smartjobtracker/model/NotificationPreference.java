package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private NotificationChannel channel = NotificationChannel.WHATSAPP;
    @Column(name = "phone_e164") private String phoneE164;
    @Column(name = "whatsapp_opt_in", nullable = false) private boolean whatsappOptIn;
    @Column(name = "consented_at") private OffsetDateTime consentedAt;
    @Column(name = "consent_source") private String consentSource;
    @Column(name = "verified_at") private OffsetDateTime verifiedAt;
    @Column(name = "verification_code_hash") private String verificationCodeHash;
    @Column(name = "verification_expires_at") private OffsetDateTime verificationExpiresAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public NotificationChannel getChannel() { return channel; } public void setChannel(NotificationChannel channel) { this.channel = channel; }
    public String getPhoneE164() { return phoneE164; } public void setPhoneE164(String phoneE164) { this.phoneE164 = phoneE164; }
    public boolean isWhatsappOptIn() { return whatsappOptIn; } public void setWhatsappOptIn(boolean value) { whatsappOptIn = value; }
    public OffsetDateTime getConsentedAt() { return consentedAt; } public void setConsentedAt(OffsetDateTime value) { consentedAt = value; }
    public String getConsentSource() { return consentSource; } public void setConsentSource(String value) { consentSource = value; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; } public void setVerifiedAt(OffsetDateTime value) { verifiedAt = value; }
    public String getVerificationCodeHash() { return verificationCodeHash; } public void setVerificationCodeHash(String value) { verificationCodeHash = value; }
    public OffsetDateTime getVerificationExpiresAt() { return verificationExpiresAt; } public void setVerificationExpiresAt(OffsetDateTime value) { verificationExpiresAt = value; }
}