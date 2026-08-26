CREATE TABLE notification_preferences (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
  channel VARCHAR(50) NOT NULL DEFAULT 'WHATSAPP',
  phone_e164 VARCHAR(30),
  whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
  consented_at TIMESTAMP WITH TIME ZONE,
  consent_source VARCHAR(100),
  verified_at TIMESTAMP WITH TIME ZONE,
  verification_code_hash VARCHAR(128),
  verification_expires_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE notification_deliveries (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  channel VARCHAR(50) NOT NULL,
  dedupe_key VARCHAR(255) NOT NULL UNIQUE,
  message TEXT NOT NULL,
  provider_message_id VARCHAR(255),
  status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
  submitted_at TIMESTAMP WITH TIME ZONE,
  confirmed_at TIMESTAMP WITH TIME ZONE,
  last_error TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_delivery_due ON notification_deliveries(status, next_attempt_at);
CREATE INDEX idx_notification_delivery_provider ON notification_deliveries(provider_message_id);