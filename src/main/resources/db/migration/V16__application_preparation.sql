CREATE TABLE application_profiles (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE REFERENCES users(id), full_name VARCHAR(255), email VARCHAR(255), phone VARCHAR(50),
  education TEXT, experience TEXT, skills TEXT, github_url VARCHAR(500), linkedin_url VARCHAR(500), portfolio_url VARCHAR(500), resume_id BIGINT REFERENCES resumes(id), updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE TABLE application_preparations (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), job_description TEXT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now());
CREATE INDEX idx_application_preparations_user ON application_preparations(user_id, created_at);
CREATE TABLE application_field_mappings (id BIGSERIAL PRIMARY KEY, preparation_id BIGINT NOT NULL REFERENCES application_preparations(id) ON DELETE CASCADE, external_field VARCHAR(255) NOT NULL, field_type VARCHAR(30) NOT NULL, UNIQUE(preparation_id, external_field));
CREATE TABLE application_suggestions (id BIGSERIAL PRIMARY KEY, preparation_id BIGINT NOT NULL REFERENCES application_preparations(id) ON DELETE CASCADE, mapping_id BIGINT NOT NULL REFERENCES application_field_mappings(id) ON DELETE CASCADE, field_type VARCHAR(30) NOT NULL, suggested_value TEXT NOT NULL, source_evidence TEXT NOT NULL, rationale TEXT NOT NULL, decision VARCHAR(20) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now());
CREATE INDEX idx_application_suggestions_preparation ON application_suggestions(preparation_id, id);