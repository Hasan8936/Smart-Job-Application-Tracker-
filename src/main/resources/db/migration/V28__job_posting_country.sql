-- ISO country detected from the posting's location (CountryDetector); null when unknown (e.g. plain "Remote").
-- Additive; existing rows are filled in at startup by JobPostingBackfill. Used to list the preferred country
-- (app.job-discovery.preferred-country, default IN) first and to filter by country.
ALTER TABLE job_postings ADD COLUMN IF NOT EXISTS country_code VARCHAR(2);
CREATE INDEX IF NOT EXISTS idx_job_postings_country_code ON job_postings(country_code);
