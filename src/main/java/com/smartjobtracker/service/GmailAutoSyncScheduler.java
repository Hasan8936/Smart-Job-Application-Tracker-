package com.smartjobtracker.service;

import com.smartjobtracker.config.GmailConfig;
import com.smartjobtracker.model.GmailConnection;
import com.smartjobtracker.repository.GmailConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Syncs connected Gmail accounts without letting one revoked account consume every scheduler run.
 * Transient failures use persistent exponential backoff; expired/revoked/forbidden tokens are disconnected
 * until the user explicitly reconnects Gmail.
 */
@Component
public class GmailAutoSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(GmailAutoSyncScheduler.class);

    private final GmailConnectionRepository connections;
    private final GmailService gmail;
    private final GmailConfig config;
    private final boolean enabled;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    @Autowired
    public GmailAutoSyncScheduler(GmailConnectionRepository connections, GmailService gmail, GmailConfig config,
                                  @Value("${app.gmail.auto-sync.enabled:true}") boolean enabled,
                                  @Value("${app.gmail.auto-sync.backoff-base-ms:300000}") long baseBackoffMs,
                                  @Value("${app.gmail.auto-sync.backoff-max-ms:21600000}") long maxBackoffMs) {
        this.connections = connections;
        this.gmail = gmail;
        this.config = config;
        this.enabled = enabled;
        this.baseBackoff = Duration.ofMillis(Math.max(1_000L, baseBackoffMs));
        this.maxBackoff = Duration.ofMillis(Math.max(this.baseBackoff.toMillis(), maxBackoffMs));
    }

    // Kept for focused unit tests and callers that do not use Spring property injection.
    GmailAutoSyncScheduler(GmailConnectionRepository connections, GmailService gmail, GmailConfig config,
                           boolean enabled) {
        this(connections, gmail, config, enabled, 300_000L, 21_600_000L);
    }

    @Scheduled(fixedDelayString = "${app.gmail.auto-sync.interval-ms:900000}",
            initialDelayString = "${app.gmail.auto-sync.initial-delay-ms:120000}")
    public void syncConnectedAccounts() {
        if (!enabled || !config.isEnabled() || config.configurationError() != null) return;
        int users = 0, processed = 0, failed = 0, skipped = 0, disabledNow = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (GmailConnection connection : connections.findByStatus("CONNECTED")) {
            users++;
            if (connection.getAutoSyncNextAttemptAt() != null && connection.getAutoSyncNextAttemptAt().isAfter(now)) {
                skipped++;
                continue;
            }
            try {
                processed += gmail.sync(connection.getUserId());
                clearFailureState(connection);
                connections.save(connection);
            } catch (RuntimeException ex) {
                failed++;
                if (isTerminalTokenFailure(ex)) {
                    disable(connection, terminalReason(ex));
                    disabledNow++;
                } else {
                    backoff(connection, now);
                }
                connections.save(connection);
            }
        }
        if (users > 0) {
            log.info("Gmail auto-sync: users={} newEmails={} failed={} skipped={} disabled={}",
                    users, processed, failed, skipped, disabledNow);
        }
    }

    private void clearFailureState(GmailConnection connection) {
        if (connection.getAutoSyncFailures() != 0 || connection.getAutoSyncNextAttemptAt() != null
                || connection.getAutoSyncDisabledReason() != null) {
            connection.setAutoSyncFailures(0);
            connection.setAutoSyncNextAttemptAt(null);
            connection.setAutoSyncDisabledReason(null);
            connection.setUpdatedAt(OffsetDateTime.now());
        }
    }

    private void backoff(GmailConnection connection, OffsetDateTime now) {
        int failures = Math.min(connection.getAutoSyncFailures() + 1, 31);
        connection.setAutoSyncFailures(failures);
        long multiplier = 1L << Math.min(failures - 1, 20);
        long delay = Math.min(maxBackoff.toMillis(), Math.multiplyExact(baseBackoff.toMillis(), multiplier));
        connection.setAutoSyncNextAttemptAt(now.plus(Duration.ofMillis(delay)));
        connection.setUpdatedAt(now);
        log.warn("Gmail auto-sync backing off userId={} failure={} nextAttemptAt={}",
                connection.getUserId(), failures, connection.getAutoSyncNextAttemptAt());
    }

    private void disable(GmailConnection connection, String reason) {
        connection.setStatus("DISCONNECTED");
        connection.setEncryptedAccessToken(null);
        connection.setEncryptedRefreshToken(null);
        connection.setAutoSyncNextAttemptAt(null);
        connection.setAutoSyncDisabledReason(reason);
        connection.setUpdatedAt(OffsetDateTime.now());
        log.warn("Gmail auto-sync disabled for userId={} reason={}; user must reconnect Gmail",
                connection.getUserId(), reason);
    }

    private boolean isTerminalTokenFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof HttpClientErrorException http) {
                int status = http.getStatusCode().value();
                String body = http.getResponseBodyAsString().toLowerCase(Locale.ROOT);
                if (status == 401 || status == 403 || (status == 400 && body.contains("invalid_grant"))) return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String text = message.toLowerCase(Locale.ROOT);
                if (text.contains("invalid_grant") || text.contains("token refresh failed")
                        || text.contains("token expired") || text.contains("unauthorized")
                        || text.contains("forbidden")) return true;
            }
        }
        return false;
    }

    private String terminalReason(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof HttpClientErrorException http) {
                int status = http.getStatusCode().value();
                if (status == 401) return "GOOGLE_TOKEN_UNAUTHORIZED";
                if (status == 403) return "GOOGLE_TOKEN_FORBIDDEN";
                if (status == 400) return "GOOGLE_REFRESH_TOKEN_REVOKED";
            }
        }
        return "GOOGLE_TOKEN_EXPIRED_OR_REVOKED";
    }
}
