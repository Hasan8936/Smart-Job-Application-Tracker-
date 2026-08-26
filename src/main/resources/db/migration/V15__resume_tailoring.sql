CREATE TABLE resume_tailoring_sessions (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  source_resume_id BIGINT NOT NULL REFERENCES resumes(id),
  job_description TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_tailoring_sessions_user ON resume_tailoring_sessions(user_id, created_at);

CREATE TABLE resume_tailoring_suggestions (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES resume_tailoring_sessions(id) ON DELETE CASCADE,
  category VARCHAR(80) NOT NULL,
  before_text TEXT NOT NULL,
  after_text TEXT NOT NULL,
  rationale TEXT NOT NULL,
  evidence_text TEXT NOT NULL,
  decision VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_tailoring_suggestions_session ON resume_tailoring_suggestions(session_id, id);

CREATE TABLE resume_versions (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  source_resume_id BIGINT NOT NULL REFERENCES resumes(id),
  tailoring_session_id BIGINT NOT NULL REFERENCES resume_tailoring_sessions(id),
  job_description TEXT NOT NULL,
  content TEXT NOT NULL,
  accepted_suggestion_ids TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_resume_versions_user ON resume_versions(user_id, created_at);