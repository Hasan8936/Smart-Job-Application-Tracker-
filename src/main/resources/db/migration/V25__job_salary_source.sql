-- Where a job's salary came from and what period it covers (additive, nullable).
--   salary_source: PROVIDER (job source API), DESCRIPTION (stated in the posting text),
--                  ESTIMATE (median of reported salaries for similar postings)
--   salary_period: YEAR, MONTH, WEEK, DAY, HOUR
ALTER TABLE job_postings ADD COLUMN IF NOT EXISTS salary_period VARCHAR(10);
ALTER TABLE job_postings ADD COLUMN IF NOT EXISTS salary_source VARCHAR(20);
ALTER TABLE job_postings ADD COLUMN IF NOT EXISTS salary_sample_size INTEGER;
