-- Phase 1: structured candidate profile derived from a user's resume.
-- Additive only. One row per user. The seven extracted groups are stored as
-- JSON arrays in TEXT columns (portable across PostgreSQL and the H2 test DB;
-- the service layer is the only place that (de)serializes them).

CREATE TABLE candidate_profiles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  source_resume_id BIGINT REFERENCES resumes(id) ON DELETE SET NULL,
  skills TEXT,
  programming_languages TEXT,
  frameworks TEXT,
  projects TEXT,
  education TEXT,
  experience TEXT,
  preferred_roles TEXT,
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_candidate_profiles_source_resume_id ON candidate_profiles(source_resume_id);
