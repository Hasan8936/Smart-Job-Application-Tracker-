CREATE TABLE job_skills (
    id BIGSERIAL PRIMARY KEY,
    job_posting_id BIGINT NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    requirement VARCHAR(20) NOT NULL,
    CONSTRAINT uq_job_skill UNIQUE (job_posting_id, normalized_name)
);
CREATE INDEX idx_job_skills_posting ON job_skills(job_posting_id);