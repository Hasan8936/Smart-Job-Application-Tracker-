-- Postings in a blocked script (app.job-discovery.blocked-scripts, default Arabic) are hidden from job lists.
-- Additive: existing rows default to visible; ScriptBlockBackfill sets the flag at startup. Nothing is deleted.
ALTER TABLE job_postings ADD COLUMN IF NOT EXISTS script_blocked BOOLEAN NOT NULL DEFAULT FALSE;
