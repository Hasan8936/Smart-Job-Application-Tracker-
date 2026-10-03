CREATE TABLE job_discovery_tasks (
    id VARCHAR(36) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    query_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    total_saved INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(500)
);

CREATE INDEX idx_job_discovery_task_ready
    ON job_discovery_tasks(status, available_at, created_at);
