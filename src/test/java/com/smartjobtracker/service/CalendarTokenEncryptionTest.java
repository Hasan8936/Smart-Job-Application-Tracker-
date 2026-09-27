package com.smartjobtracker.service;

import com.smartjobtracker.model.GoogleCalendarToken;
import com.smartjobtracker.repository.GoogleCalendarTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Calendar tokens are stored encrypted, read back decrypted, and legacy plaintext rows are encrypted in place. */
@SpringBootTest(properties = "app.gmail.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ActiveProfiles("test")
class CalendarTokenEncryptionTest {
    @Autowired private GoogleCalendarService calendar;
    @Autowired private GoogleCalendarTokenRepository tokens;
    @Autowired private CalendarTokenEncryptionBackfill backfill;
    @Autowired private JdbcTemplate jdbc;

    private String raw(String column, long userId) {
        return jdbc.queryForObject("select " + column + " from google_calendar_tokens where user_id = ?", String.class, userId);
    }

    @Test
    void storedEncryptedAndReadDecrypted() {
        calendar.storeOAuthTokens(9101L, "access-secret", "refresh-secret", 3600);
        assertTrue(raw("access_token", 9101L).startsWith("enc:"));
        assertFalse(raw("refresh_token", 9101L).contains("refresh-secret"));
        GoogleCalendarToken loaded = tokens.findByUserId(9101L).orElseThrow();
        assertEquals("access-secret", loaded.getAccessToken());
        assertEquals("refresh-secret", loaded.getRefreshToken());
    }

    @Test
    void legacyPlaintextRowsAreEncryptedOnceAndStillReadable() {
        jdbc.update("insert into google_calendar_tokens (user_id, access_token, refresh_token, token_expiry, calendar_id, created_at, updated_at) "
                + "values (?, 'old-access', null, ?, 'primary', ?, ?)", 9102L, OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now());
        assertEquals("old-access", tokens.findByUserId(9102L).orElseThrow().getAccessToken(), "plaintext still readable");

        assertTrue(backfill.encryptPlaintextRows() >= 1);
        String encrypted = raw("access_token", 9102L);
        assertTrue(encrypted.startsWith("enc:"));
        assertNull(raw("refresh_token", 9102L));
        assertEquals(0, backfill.encryptPlaintextRows(), "idempotent");
        assertEquals(encrypted, raw("access_token", 9102L));
        assertEquals("old-access", tokens.findByUserId(9102L).orElseThrow().getAccessToken());
    }
}
