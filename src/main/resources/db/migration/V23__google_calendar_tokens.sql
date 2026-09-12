CREATE TABLE google_calendar_tokens (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_token  TEXT         NOT NULL,
    refresh_token TEXT,
    token_expiry  TIMESTAMPTZ,
    calendar_id   TEXT         NOT NULL DEFAULT 'primary',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(user_id)
);
