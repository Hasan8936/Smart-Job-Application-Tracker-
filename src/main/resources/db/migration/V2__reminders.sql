-- Create reminders table
CREATE TABLE reminders (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  application_id BIGINT REFERENCES applications(id),
  remind_at TIMESTAMP NOT NULL,
  type VARCHAR(50) NOT NULL,
  status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  message VARCHAR(1024),
  created_at TIMESTAMP DEFAULT now()
);
