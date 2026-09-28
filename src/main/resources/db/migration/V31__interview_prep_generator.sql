-- Which generator produced a session's answers: 'AI' (Gemini), 'OFFLINE' (resume-grounded drafts), or
-- 'MIXED' (some AI batches failed and were filled offline). NULL for sessions created before this column.
ALTER TABLE interview_prep_sessions ADD COLUMN generator VARCHAR(20);
