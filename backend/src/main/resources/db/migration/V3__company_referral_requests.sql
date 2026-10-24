ALTER TABLE referrer_profiles
  ADD COLUMN accepts_referral_requests BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN referral_role_preferences JSON NULL;

CREATE INDEX idx_referrer_referral_opt_in
  ON referrer_profiles(company_id, verified_at, accepts_referral_requests);

ALTER TABLE profiles ADD COLUMN phone_number VARCHAR(32) NULL;

CREATE TABLE company_referral_requests (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  seeker_id BIGINT UNSIGNED NOT NULL,
  company_id BIGINT UNSIGNED NOT NULL,
  role_title VARCHAR(160) NOT NULL,
  job_url VARCHAR(500) NULL,
  seeker_note VARCHAR(1000) NULL,
  share_profile BOOLEAN NOT NULL DEFAULT FALSE,
  share_seeker_email BOOLEAN NOT NULL DEFAULT FALSE,
  share_seeker_phone BOOLEAN NOT NULL DEFAULT FALSE,
  base_charge BOOLEAN NOT NULL DEFAULT TRUE,
  status ENUM('open','closed','expired') NOT NULL DEFAULT 'open',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  closed_at TIMESTAMP NULL,
  FOREIGN KEY (seeker_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  INDEX idx_company_referral_seeker_created (seeker_id, created_at),
  INDEX idx_company_referral_expiry (status, expires_at),
  INDEX idx_company_referral_company (company_id, status)
);

CREATE TABLE referral_request_responses (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT UNSIGNED NOT NULL,
  referrer_id BIGINT UNSIGNED NOT NULL,
  status ENUM('pending','accepted','declined','expired','submitted','confirmed','closed') NOT NULL DEFAULT 'pending',
  share_referrer_email BOOLEAN NOT NULL DEFAULT FALSE,
  share_referrer_phone BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  accepted_at TIMESTAMP NULL,
  submitted_at TIMESTAMP NULL,
  confirmed_at TIMESTAMP NULL,
  closed_at TIMESTAMP NULL,
  reminders_sent TINYINT UNSIGNED NOT NULL DEFAULT 0,
  last_reminded_at TIMESTAMP NULL,
  FOREIGN KEY (request_id) REFERENCES company_referral_requests(id) ON DELETE CASCADE,
  FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE KEY uq_referral_request_referrer (request_id, referrer_id),
  INDEX idx_referral_response_inbox (referrer_id, status, created_at),
  INDEX idx_referral_response_reminders (status, accepted_at, reminders_sent),
  INDEX idx_referral_response_capacity (referrer_id, status, submitted_at)
);

CREATE TABLE referral_request_credits (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  source_response_id BIGINT UNSIGNED NOT NULL,
  consumed_by_request_id BIGINT UNSIGNED NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (source_response_id) REFERENCES referral_request_responses(id) ON DELETE CASCADE,
  FOREIGN KEY (consumed_by_request_id) REFERENCES company_referral_requests(id) ON DELETE SET NULL,
  UNIQUE KEY uq_referral_credit_source (source_response_id),
  UNIQUE KEY uq_referral_credit_request (consumed_by_request_id),
  INDEX idx_referral_credit_available (user_id, expires_at, consumed_by_request_id)
);