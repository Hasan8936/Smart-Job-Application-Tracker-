-- The JPA entity ApplicationStatusHistory / its repository were added without a
-- matching migration. With `spring.jpa.hibernate.ddl-auto: validate` and Flyway
-- owning the schema, Hibernate fails fast at startup because this table doesn't
-- exist yet. This migration creates it.
CREATE TABLE application_status_history (
  id BIGSERIAL PRIMARY KEY,
  application_id BIGINT REFERENCES applications(id) ON DELETE CASCADE,
  status VARCHAR(50) NOT NULL,
  changed_at TIMESTAMP DEFAULT now(),
  remark VARCHAR(1024)
);

CREATE INDEX idx_application_status_history_application_id
  ON application_status_history(application_id);
