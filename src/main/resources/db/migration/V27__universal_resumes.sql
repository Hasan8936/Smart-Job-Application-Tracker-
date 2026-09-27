-- One editable master ("universal") resume per user, stored as the builder's structured data (JSON in TEXT for
-- H2/PostgreSQL portability). resume_id points at the plain-text Resume row kept in sync for matching; it is
-- created by this feature and is never one of the user's uploaded resumes.
CREATE TABLE universal_resumes (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    resume_id   BIGINT       REFERENCES resumes(id) ON DELETE SET NULL,
    template    VARCHAR(20)  NOT NULL DEFAULT 'jakes',
    data_json   TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
