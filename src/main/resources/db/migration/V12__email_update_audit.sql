ALTER TABLE application_status_history ADD COLUMN IF NOT EXISTS source VARCHAR(60);
ALTER TABLE application_status_history ADD COLUMN IF NOT EXISTS source_email_id BIGINT REFERENCES ingested_emails(id) ON DELETE SET NULL;
ALTER TABLE application_status_history ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION;
ALTER TABLE ingested_emails ADD COLUMN IF NOT EXISTS previous_application_status VARCHAR(30);
ALTER TABLE ingested_emails ADD COLUMN IF NOT EXISTS update_method VARCHAR(50);