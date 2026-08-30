CREATE TABLE interview_candidates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(20) NOT NULL,
    calendar_event_id VARCHAR(500) NOT NULL,
    title VARCHAR(1000),
    description TEXT,
    event_start TIMESTAMPTZ,
    event_end TIMESTAMPTZ,
    suggested_application_id BIGINT REFERENCES applications(id) ON DELETE SET NULL,
    match_method VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT uq_interview_candidate UNIQUE(user_id, calendar_event_id)
);
CREATE INDEX idx_interview_candidates_user_status ON interview_candidates(user_id, status);
