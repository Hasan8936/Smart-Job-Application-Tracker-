-- Account self-service, super-admin console and support tickets. Additive only: new columns and new tables;
-- no existing column or foreign-key rule is changed.

-- users: role, suspension, whether the user ever chose a password (OAuth sign-ups get a random hash), last login
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended_reason VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_set BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

-- profile details not already on candidate_profiles (phone and linkedin_url exist since V24)
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS headline VARCHAR(200);
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS location VARCHAR(200);
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS github_url VARCHAR(500);
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS website_url VARCHAR(500);

-- profile photo, re-encoded server-side (max 512x512, metadata stripped)
CREATE TABLE user_photos (
    user_id      BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    content_type VARCHAR(20) NOT NULL,
    data         BYTEA       NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- admin flags on a user
CREATE TABLE user_flags (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category    VARCHAR(20)  NOT NULL,
    severity    VARCHAR(10)  NOT NULL,
    note        TEXT,
    status      VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    created_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_by BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_user_flags_user ON user_flags(user_id, created_at);

-- support tickets and their conversation
CREATE TABLE support_tickets (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject           VARCHAR(200) NOT NULL,
    category          VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    assigned_admin_id BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_support_tickets_user ON support_tickets(user_id, created_at);
CREATE INDEX idx_support_tickets_status ON support_tickets(status, updated_at);

CREATE TABLE support_ticket_messages (
    id          BIGSERIAL   PRIMARY KEY,
    ticket_id   BIGINT      NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    author_id   BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    from_admin  BOOLEAN     NOT NULL DEFAULT FALSE,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_support_ticket_messages_ticket ON support_ticket_messages(ticket_id, created_at);

-- every admin action; target_user_id has no FK so the trail survives account deletion
CREATE TABLE admin_audit_log (
    id             BIGSERIAL    PRIMARY KEY,
    admin_id       BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    target_user_id BIGINT,
    action         VARCHAR(40)  NOT NULL,
    detail         VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_admin_audit_log_created ON admin_audit_log(created_at);
CREATE INDEX idx_admin_audit_log_target ON admin_audit_log(target_user_id);
