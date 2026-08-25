CREATE TABLE match_analyses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resume_id BIGINT NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    job_posting_id BIGINT REFERENCES job_postings(id) ON DELETE CASCADE,
    source_hash VARCHAR(64) NOT NULL,
    overall_score DOUBLE PRECISION NOT NULL,
    skill_score DOUBLE PRECISION NOT NULL,
    experience_score DOUBLE PRECISION NOT NULL,
    role_score DOUBLE PRECISION NOT NULL,
    semantic_score DOUBLE PRECISION NOT NULL,
    breakdown_json TEXT NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_match_analysis UNIQUE (user_id, resume_id, source_hash)
);
CREATE INDEX idx_match_analyses_user ON match_analyses(user_id);
CREATE INDEX idx_match_analyses_job ON match_analyses(job_posting_id);