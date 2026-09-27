package com.smartjobtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Encrypts Google Calendar OAuth tokens at rest with the same AES-GCM key as Gmail tokens (GMAIL_TOKEN_ENCRYPTION_KEY).
 * Encrypted values carry the {@code enc:} prefix; values without it are legacy plaintext and are read as-is (see
 * {@link CalendarTokenEncryptionBackfill}). If the key isn't configured, tokens stay plaintext rather than breaking
 * Calendar, and a warning is logged at startup. Used by {@link CalendarTokenConverter} on the entity.
 */
@Component
public class CalendarTokenCrypto {
    private static final Logger log = LoggerFactory.getLogger(CalendarTokenCrypto.class);
    static final String PREFIX = "enc:";
    private static volatile CalendarTokenCrypto current;

    private final GmailTokenCipher cipher;

    public CalendarTokenCrypto(GmailTokenCipher cipher) {
        this.cipher = cipher;
        if (!cipher.isConfigured()) {
            log.warn("GMAIL_TOKEN_ENCRYPTION_KEY is not set: Google Calendar tokens will be stored unencrypted");
        }
        current = this;
    }

    /** The application's instance; null outside a full application context (e.g. JPA-only test slices). */
    static CalendarTokenCrypto current() { return current; }

    public String encrypt(String value) {
        if (value == null || value.startsWith(PREFIX) || !cipher.isConfigured()) return value;
        return PREFIX + cipher.encrypt(value);
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        return cipher.decrypt(stored.substring(PREFIX.length()));
    }

    public boolean canEncrypt() { return cipher.isConfigured(); }
}
