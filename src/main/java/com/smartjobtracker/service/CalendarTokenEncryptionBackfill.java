package com.smartjobtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Encrypts Google Calendar tokens saved before encryption existed. Runs at startup; rows already encrypted are
 * skipped, so it is safe to run on every start. Logs counts only, never token values.
 */
@Component
public class CalendarTokenEncryptionBackfill implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CalendarTokenEncryptionBackfill.class);

    private final JdbcTemplate jdbc;
    private final CalendarTokenCrypto converter;

    public CalendarTokenEncryptionBackfill(JdbcTemplate jdbc, CalendarTokenCrypto converter) {
        this.jdbc = jdbc; this.converter = converter;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            encryptPlaintextRows();
        } catch (RuntimeException e) {
            log.warn("Calendar token encryption backfill skipped ({})", e.getClass().getSimpleName());
        }
    }

    /** Each row is updated on its own; a partial run is simply finished by the next start. */
    public int encryptPlaintextRows() {
        if (!converter.canEncrypt()) return 0;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, access_token, refresh_token from google_calendar_tokens "
                        + "where access_token not like 'enc:%' or (refresh_token is not null and refresh_token not like 'enc:%')");
        for (Map<String, Object> row : rows) {
            jdbc.update("update google_calendar_tokens set access_token = ?, refresh_token = ? where id = ?",
                    converter.encrypt((String) row.get("access_token")),
                    converter.encrypt((String) row.get("refresh_token")),
                    row.get("id"));
        }
        if (!rows.isEmpty()) log.info("Encrypted {} stored Google Calendar token row(s)", rows.size());
        return rows.size();
    }
}
