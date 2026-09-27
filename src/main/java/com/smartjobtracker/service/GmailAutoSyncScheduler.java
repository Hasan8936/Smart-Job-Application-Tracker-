package com.smartjobtracker.service;

import com.smartjobtracker.config.GmailConfig;
import com.smartjobtracker.model.GmailConnection;
import com.smartjobtracker.repository.GmailConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Syncs every connected Gmail account in the background, so application emails update the tracker without the
 * user pressing Sync. Idempotent: {@link GmailService#sync} skips message ids it has already stored (backed by the
 * ingested_emails (user_id, gmail_message_id) unique constraint), so overlapping runs never double-process.
 * One user's failure (revoked token, quota) is logged and never stops the others.
 */
@Component
public class GmailAutoSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(GmailAutoSyncScheduler.class);

    private final GmailConnectionRepository connections;
    private final GmailService gmail;
    private final GmailConfig config;
    private final boolean enabled;

    public GmailAutoSyncScheduler(GmailConnectionRepository connections, GmailService gmail, GmailConfig config,
                                  @Value("${app.gmail.auto-sync.enabled:true}") boolean enabled) {
        this.connections = connections;
        this.gmail = gmail;
        this.config = config;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.gmail.auto-sync.interval-ms:900000}",
            initialDelayString = "${app.gmail.auto-sync.initial-delay-ms:120000}")
    public void syncConnectedAccounts() {
        if (!enabled || !config.isEnabled() || config.configurationError() != null) return;
        int users = 0, processed = 0, failed = 0;
        for (GmailConnection connection : connections.findByStatus("CONNECTED")) {
            users++;
            try {
                processed += gmail.sync(connection.getUserId());
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Gmail auto-sync failed for userId={}: {}", connection.getUserId(), ex.getClass().getSimpleName());
            }
        }
        if (users > 0) log.info("Gmail auto-sync: users={} newEmails={} failed={}", users, processed, failed);
    }
}
