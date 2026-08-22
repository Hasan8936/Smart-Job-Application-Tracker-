-- Initial schema for Smart Job Tracker (MVP)

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE resumes (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  file_name VARCHAR(512),
  extracted_text TEXT,
  uploaded_at TIMESTAMP DEFAULT now()
);

CREATE TABLE applications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  company_name VARCHAR(255),
  role_title VARCHAR(255),
  job_description TEXT,
  status VARCHAR(50),
  applied_date DATE,
  created_at TIMESTAMP DEFAULT now()
);
