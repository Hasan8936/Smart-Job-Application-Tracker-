CREATE TABLE job_postings (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(100) NOT NULL,
    external_id VARCHAR(500) NOT NULL,
    dedupe_hash VARCHAR(64) NOT NULL,
    company VARCHAR(500) NOT NULL,
    title VARCHAR(500) NOT NULL,
    location VARCHAR(500),
    employment_type VARCHAR(100),
    work_mode VARCHAR(100),
    apply_url VARCHAR(2000) NOT NULL,
    posted_at TIMESTAMPTZ,
    description TEXT,
    logo_url VARCHAR(2000),
    salary_min INTEGER,
    salary_max INTEGER,
    salary_currency VARCHAR(10),
    raw_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_provider_external UNIQUE (provider, external_id)
);
CREATE INDEX idx_job_postings_dedupe_hash ON job_postings(dedupe_hash);
CREATE INDEX idx_job_postings_posted_at ON job_postings(posted_at);
CREATE INDEX idx_job_postings_company ON job_postings(company);

CREATE TABLE job_provider_syncs (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(100) NOT NULL,
    query_key VARCHAR(1000) NOT NULL,
    cursor VARCHAR(1000),
    last_synced_at TIMESTAMPTZ,
    status VARCHAR(30),
    CONSTRAINT uq_job_provider_sync UNIQUE (provider, query_key)
);
CREATE INDEX idx_job_provider_sync_provider ON job_provider_syncs(provider);

