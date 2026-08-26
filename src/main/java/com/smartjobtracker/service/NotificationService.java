package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartjobtracker.config.MetaWhatsAppConfig;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.NotificationDeliveryRepository;
import com.smartjobtracker.repository.NotificationPreferenceRepository;
import com.smartjobtracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ATTEMPTS = 5;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryRepository deliveries;
    private final UserRepository users;
    private final NotificationProvider provider;
    private final MetaWhatsAppConfig config;
    private final SecureRandom random = new SecureRandom();

    public NotificationService(NotificationPreferenceRepository preferences, NotificationDeliveryRepository deliveries,
                                UserRepository users, NotificationProvider provider, MetaWhatsAppConfig config) {
        this.preferences = preferences; this.deliveries = deliveries; this.users = users; this.provider = provider; this.config = config;
    }

    @Transactional
    public NotificationPreference savePreference(Long userId, String phone, boolean optIn, String source) {
        NotificationPreference preference = preferences.findByUserId(userId).orElseGet(NotificationPreference::new);
        preference.setUserId(userId); preference.setChannel(NotificationChannel.WHATSAPP); preference.setPhoneE164(phone);
        preference.setWhatsappOptIn(optIn);
        preference.setConsentSource(source == null || source.isBlank() ? "settings" : source);
        preference.setConsentedAt(optIn ? OffsetDateTime.now() : null);
        if (!optIn) preference.setVerifiedAt(null);
        return preferences.save(preference);
    }

    public Optional<NotificationPreference> getPreference(Long userId) { return preferences.findByUserId(userId); }

    @Transactional
    public Optional<NotificationDelivery> enqueueWhatsApp(Long userId, String eventKey, String message) {
        NotificationPreference preference = preferences.findByUserId(userId).orElse(null);
        if (preference == null || !preference.isWhatsappOptIn() || preference.getVerifiedAt() == null || preference.getPhoneE164() == null) return Optional.empty();
        String dedupeKey = userId + ":WHATSAPP:" + eventKey;
        Optional<NotificationDelivery> existing = deliveries.findByDedupeKey(dedupeKey);
        if (existing.isPresent()) return existing;
        NotificationDelivery delivery = new NotificationDelivery(); delivery.setUserId(userId);
        delivery.setChannel(NotificationChannel.WHATSAPP); delivery.setDedupeKey(dedupeKey); delivery.setMessage(message);
        delivery.setNextAttemptAt(OffsetDateTime.now());
        return Optional.of(deliveries.save(delivery));
    }

    @Transactional
    public void startVerification(Long userId) {
        NotificationPreference preference = preferences.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("Save WhatsApp consent first"));
        if (!preference.isWhatsappOptIn() || preference.getPhoneE164() == null) throw new IllegalArgumentException("WhatsApp opt-in and phone are required");
        String code = String.format("%06d", random.nextInt(1_000_000));
        preference.setVerificationCodeHash(hash(code));
        preference.setVerificationExpiresAt(OffsetDateTime.now().plusMinutes(10));
        preferences.save(preference);
        provider.send(preference.getPhoneE164(), "Smart Job Tracker verification code: " + code);
    }

    @Transactional
    public void verifyPhone(Long userId, String code) {
        NotificationPreference preference = preferences.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("WhatsApp preference not found"));
        if (preference.getVerificationExpiresAt() == null || preference.getVerificationExpiresAt().isBefore(OffsetDateTime.now())
                || preference.getVerificationCodeHash() == null || !MessageDigest.isEqual(preference.getVerificationCodeHash().getBytes(StandardCharsets.UTF_8), hash(code).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        preference.setVerifiedAt(OffsetDateTime.now());
        preference.setVerificationCodeHash(null);
        preference.setVerificationExpiresAt(null);
        preferences.save(preference);
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Unable to hash verification code", ex); }
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.whatsapp.poll-interval-ms:60000}")
    public void deliverDue() {
        OffsetDateTime now = OffsetDateTime.now();
        List<NotificationDelivery> due = deliveries.findByStatusInAndNextAttemptAtBefore(
                List.of(NotificationDeliveryStatus.QUEUED, NotificationDeliveryStatus.FAILED), now);
        for (NotificationDelivery delivery : due) {
            if (delivery.getStatus() == NotificationDeliveryStatus.FAILED && delivery.getAttempts() >= MAX_ATTEMPTS) continue;
            send(delivery, now);
        }
    }

    private void send(NotificationDelivery delivery, OffsetDateTime now) {
        NotificationPreference preference = preferences.findByUserId(delivery.getUserId()).orElse(null);
        if (preference == null || !preference.isWhatsappOptIn() || preference.getPhoneE164() == null) {
            delivery.setStatus(NotificationDeliveryStatus.FAILED); delivery.setLastError("WhatsApp consent or phone is unavailable");
            delivery.setAttempts(delivery.getAttempts() + 1); deliveries.save(delivery); return;
        }
        try {
            NotificationProvider.Submission submission = provider.send(preference.getPhoneE164(), delivery.getMessage());
            delivery.setProviderMessageId(submission.providerMessageId()); delivery.setStatus(NotificationDeliveryStatus.SUBMITTED);
            delivery.setSubmittedAt(now); delivery.setAttempts(delivery.getAttempts() + 1); delivery.setLastError(null);
        } catch (Exception ex) {
            delivery.setStatus(NotificationDeliveryStatus.FAILED); delivery.setAttempts(delivery.getAttempts() + 1);
            delivery.setLastError(ex.getMessage() == null ? "Provider submission failed" : ex.getMessage());
            delivery.setNextAttemptAt(now.plusMinutes(1L << Math.min(delivery.getAttempts(), 6)));
            log.warn("WhatsApp notification {} submission attempt {} failed", delivery.getId(), delivery.getAttempts());
        }
        deliveries.save(delivery);
    }

    @Transactional
    public void applyProviderStatus(String providerMessageId, String status) {
        NotificationDelivery delivery = deliveries.findByProviderMessageId(providerMessageId).orElse(null);
        if (delivery == null) return;
        NotificationDeliveryStatus next = switch (status.toLowerCase()) {
            case "sent" -> NotificationDeliveryStatus.SENT;
            case "delivered" -> NotificationDeliveryStatus.DELIVERED;
            case "read" -> NotificationDeliveryStatus.READ;
            case "failed" -> NotificationDeliveryStatus.FAILED;
            default -> null;
        };
        if (next == null || (delivery.getStatus() == NotificationDeliveryStatus.DELIVERED && next == NotificationDeliveryStatus.SENT)) return;
        delivery.setStatus(next);
        if (next == NotificationDeliveryStatus.DELIVERED || next == NotificationDeliveryStatus.READ) delivery.setConfirmedAt(OffsetDateTime.now());
        deliveries.save(delivery);
    }

    public List<NotificationDelivery> history(Long userId) { return deliveries.findByUserIdOrderByCreatedAtDesc(userId); }

    public boolean verifyWebhookToken(String token) {
        return token != null && config.getVerifyToken() != null
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), config.getVerifyToken().getBytes(StandardCharsets.UTF_8));
    }

    public boolean validWebhookSignature(String signature, String body) {
        if (signature == null || !signature.startsWith("sha256=") || config.getAppSecret() == null || config.getAppSecret().isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) { return false; }
    }

    @Transactional
    public void processWebhook(JsonNode payload) {
        for (JsonNode entry : payload.path("entry")) for (JsonNode change : entry.path("changes"))
            for (JsonNode status : change.path("value").path("statuses")) {
                String id = status.path("id").asText(null); String state = status.path("status").asText(null);
                if (id != null && state != null) applyProviderStatus(id, state);
            }
    }
}