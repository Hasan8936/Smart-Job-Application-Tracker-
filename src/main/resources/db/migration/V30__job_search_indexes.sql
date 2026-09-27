-- Job board search matches words against job skills (EXISTS subquery per job) and orders by posted date.
-- Additive: indexes only, no table or column changes.
CREATE INDEX IF NOT EXISTS idx_job_skills_normalized_name ON job_skills(normalized_name);
CREATE INDEX IF NOT EXISTS idx_job_postings_country_posted ON job_postings(country_code, posted_at DESC);
