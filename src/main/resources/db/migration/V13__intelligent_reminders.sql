ALTER TABLE users ADD COLUMN timezone VARCHAR(100) NOT NULL DEFAULT 'UTC';
ALTER TABLE users ADD COLUMN reminder_preferences TEXT NOT NULL DEFAULT '{}';

ALTER TABLE reminders ADD COLUMN event_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reminders ADD COLUMN trigger_offset_minutes INTEGER;
ALTER TABLE reminders ADD COLUMN timezone VARCHAR(100);
ALTER TABLE reminders ADD COLUMN dedupe_key VARCHAR(255);
ALTER TABLE reminders ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reminders ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reminders ADD COLUMN sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reminders ADD COLUMN last_error TEXT;

UPDATE reminders SET dedupe_key = 'legacy-' || id WHERE dedupe_key IS NULL;
ALTER TABLE reminders ALTER COLUMN dedupe_key SET NOT NULL;
CREATE UNIQUE INDEX uq_reminders_dedupe_key ON reminders (dedupe_key);
CREATE INDEX idx_reminders_delivery_due ON reminders (status, next_attempt_at, remind_at);