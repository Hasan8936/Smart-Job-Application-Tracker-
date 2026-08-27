CREATE TABLE deep_match_analyses (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  resume_id BIGINT NOT NULL REFERENCES resumes(id),
  job_description TEXT NOT NULL,
  compatibility_score INTEGER NOT NULL,
  missing_keywords TEXT NOT NULL,
  red_flags TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_deep_match_analyses_user ON deep_match_analyses(user_id, created_at);