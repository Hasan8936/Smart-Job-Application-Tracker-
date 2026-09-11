-- Performance indexes: reduce full-table scans on the most common query paths.
-- All indexes are created with IF NOT EXISTS so re-running is safe.

-- applications: every query filters by user_id; status is a common secondary filter
CREATE INDEX IF NOT EXISTS idx_applications_user_id ON applications(user_id);
CREATE INDEX IF NOT EXISTS idx_applications_user_status ON applications(user_id, status);
CREATE INDEX IF NOT EXISTS idx_applications_created_at ON applications(created_at DESC);

-- resumes: nearly every resume query is by user_id
CREATE INDEX IF NOT EXISTS idx_resumes_user_id ON resumes(user_id);

-- job_postings: text search is the hot path; add an index on title for ORDER BY and equality
CREATE INDEX IF NOT EXISTS idx_job_postings_title ON job_postings(title);
CREATE INDEX IF NOT EXISTS idx_job_postings_employment_type ON job_postings(employment_type);
CREATE INDEX IF NOT EXISTS idx_job_postings_location ON job_postings(location);
CREATE INDEX IF NOT EXISTS idx_job_postings_provider ON job_postings(provider);
CREATE INDEX IF NOT EXISTS idx_job_postings_created_at ON job_postings(created_at DESC);

-- reminders: delivery poller queries pending reminders by remind_at; already indexed in V13
-- but user-facing list by user_id is not covered
CREATE INDEX IF NOT EXISTS idx_reminders_user_id ON reminders(user_id);

-- match_analyses and deep_match_analyses: always queried by user_id
CREATE INDEX IF NOT EXISTS idx_match_analyses_user_id ON match_analyses(user_id);

-- application_status_history: always joined by application_id
CREATE INDEX IF NOT EXISTS idx_app_status_history_application_id ON application_status_history(application_id);
