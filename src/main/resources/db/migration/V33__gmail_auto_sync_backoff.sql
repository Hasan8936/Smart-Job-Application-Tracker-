ALTER TABLE gmail_connections
    ADD COLUMN auto_sync_failures INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN auto_sync_next_attempt_at TIMESTAMPTZ,
    ADD COLUMN auto_sync_disabled_reason VARCHAR(120);

CREATE INDEX idx_gmail_connection_auto_sync_next_attempt
    ON gmail_connections(status, auto_sync_next_attempt_at);
