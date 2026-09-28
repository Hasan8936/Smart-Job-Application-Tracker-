-- What the user wants recommended: asked once in the onboarding popup, editable later. Additive only.
-- One row per user. status = 'SAVED' (preferences filled in) or 'SKIPPED' (popup dismissed; don't ask again).
-- List fields are JSON arrays in TEXT columns (portable across PostgreSQL and the H2 test DB).
CREATE TABLE job_search_preferences (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  status VARCHAR(20) NOT NULL,
  roles TEXT,
  experience_level VARCHAR(20),
  locations TEXT,
  work_modes TEXT,
  job_types TEXT,
  min_salary_lpa INTEGER,
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);
