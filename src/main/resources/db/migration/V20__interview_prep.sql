CREATE TABLE interview_prep_sessions (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  resume_id BIGINT REFERENCES resumes(id),
  application_id BIGINT REFERENCES applications(id),
  job_description TEXT NOT NULL,
  source VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_interview_prep_sessions_user ON interview_prep_sessions(user_id, created_at);

CREATE TABLE interview_prep_questions (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES interview_prep_sessions(id) ON DELETE CASCADE,
  position INT NOT NULL,
  category VARCHAR(30) NOT NULL,
  question TEXT NOT NULL,
  suggested_answer TEXT NOT NULL,
  source_evidence TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_interview_prep_questions_session ON interview_prep_questions(session_id, position);
