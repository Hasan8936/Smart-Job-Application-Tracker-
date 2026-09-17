-- Mark AI-estimated salary rows so the UI can label them differently
ALTER TABLE job_postings ADD COLUMN IF NOT EXISTS salary_estimated BOOLEAN NOT NULL DEFAULT FALSE;

-- Add phone and LinkedIn URL to candidate profiles (used by auto-apply)
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS linkedin_url VARCHAR(500);

-- Track auto-apply submissions per user per job
CREATE TABLE auto_apply_log (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_posting_id   BIGINT       NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    skyvern_task_id  VARCHAR(200),
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auto_apply_user_job UNIQUE(user_id, job_posting_id)
);
CREATE INDEX idx_auto_apply_log_user ON auto_apply_log(user_id);
