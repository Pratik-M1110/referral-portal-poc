-- Referral Portal schema (MySQL 8.0.17+). Source: CLAUDE.md appendix, plus the additions listed in docs/MVP.md section 7.
-- MySQL has no array type, so skills / roles are JSON columns.

-- PEOPLE AND COMPANIES
CREATE TABLE users (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  auth_provider VARCHAR(20) NOT NULL,
  is_admin BOOLEAN NOT NULL DEFAULT FALSE,
  consent_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE companies (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  email_domain VARCHAR(190) NOT NULL UNIQUE,
  booth_label VARCHAR(40) NULL,
  is_sponsor BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE profiles (               -- seeker side
  user_id BIGINT UNSIGNED PRIMARY KEY,
  pitch VARCHAR(200) NULL,
  experience_level ENUM('student','fresher','1-3','3-6','6+') NOT NULL,
  skills JSON NULL,
  roles_wanted JSON NULL,
  resume_url VARCHAR(500) NULL,
  github_url VARCHAR(300) NULL,
  linkedin_url VARCHAR(300) NULL,
  is_seeker BOOLEAN NOT NULL DEFAULT TRUE,
  hide_from_my_company BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_skills ((CAST(skills->'$[*]' AS CHAR(40) ARRAY)))
);

CREATE TABLE referrer_profiles (      -- referrer side
  user_id BIGINT UNSIGNED PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  work_email VARCHAR(190) NOT NULL UNIQUE,
  verified_at TIMESTAMP NULL,
  is_referrer BOOLEAN NOT NULL DEFAULT TRUE,
  open_to_meet BOOLEAN NOT NULL DEFAULT FALSE,
  slot_capacity TINYINT UNSIGNED NOT NULL DEFAULT 3,
  referral_limit TINYINT UNSIGNED NOT NULL DEFAULT 10,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE open_roles (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(150) NOT NULL,
  level VARCHAR(40) NULL,
  skills JSON NULL,
  link VARCHAR(500) NULL,
  FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- Resume bytes live here because the free host's disk is wiped on restart. Swap to S3-compatible storage later.
CREATE TABLE resume_files (
  user_id BIGINT UNSIGNED PRIMARY KEY,
  filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes INT UNSIGNED NOT NULL,
  sha256 CHAR(64) NOT NULL,
  data MEDIUMBLOB NOT NULL,
  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Work-email OTPs: hashed code, 10 minute expiry, max 5 attempts (enforced in app code).
CREATE TABLE email_otps (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  work_email VARCHAR(190) NOT NULL,
  code_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_otp_user (user_id, created_at)
);

-- INTEREST AND ACTIONS
CREATE TABLE wishlists (              -- max 5 per seeker: enforce in app code
  seeker_id BIGINT UNSIGNED NOT NULL,
  company_id BIGINT UNSIGNED NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (seeker_id, company_id),
  FOREIGN KEY (seeker_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE TABLE reviews (
  referrer_id BIGINT UNSIGNED NOT NULL,
  candidate_id BIGINT UNSIGNED NOT NULL,
  action ENUM('save','pass') NOT NULL,
  private_note TEXT NULL,
  PRIMARY KEY (referrer_id, candidate_id),
  FOREIGN KEY (referrer_id) REFERENCES users(id),
  FOREIGN KEY (candidate_id) REFERENCES users(id),
  CHECK (referrer_id <> candidate_id)
);

CREATE TABLE referrals (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  referrer_id BIGINT UNSIGNED NOT NULL,
  candidate_id BIGINT UNSIGNED NOT NULL,
  company_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NULL,
  endorsement_note VARCHAR(500) NULL,
  status ENUM('submitted','under_review','interview','closed') NOT NULL DEFAULT 'submitted',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE (referrer_id, candidate_id, company_id),
  FOREIGN KEY (referrer_id) REFERENCES users(id),
  FOREIGN KEY (candidate_id) REFERENCES users(id),
  FOREIGN KEY (company_id) REFERENCES companies(id),
  FOREIGN KEY (role_id) REFERENCES open_roles(id) ON DELETE SET NULL,
  CHECK (referrer_id <> candidate_id)
);

-- EVENT DAY
CREATE TABLE badges (
  user_id BIGINT UNSIGNED PRIMARY KEY,
  qr_token CHAR(32) NOT NULL UNIQUE,  -- random token, never the user id
  checked_in_at TIMESTAMP NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE scans (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  scanner_id BIGINT UNSIGNED NOT NULL,
  scanned_user_id BIGINT UNSIGNED NULL,
  scanned_company_id BIGINT UNSIGNED NULL,
  type ENUM('referrer_scan','company_interest') NOT NULL,
  scanned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  synced_at TIMESTAMP NULL,           -- set when an offline scan reaches the server
  FOREIGN KEY (scanner_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (scanned_user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (scanned_company_id) REFERENCES companies(id) ON DELETE CASCADE,
  INDEX idx_scanner (scanner_id, scanned_at)
);

CREATE TABLE conversations (          -- a meet request that becomes a chat once accepted
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  seeker_id BIGINT UNSIGNED NOT NULL,
  referrer_id BIGINT UNSIGNED NOT NULL,
  initiated_by ENUM('seeker','referrer') NOT NULL DEFAULT 'seeker',
  first_message VARCHAR(200) NULL,
  status ENUM('pending','accepted','declined','completed') NOT NULL DEFAULT 'pending',
  message_count TINYINT UNSIGNED NOT NULL DEFAULT 0,   -- app bumps it in the same transaction as the message insert
  happened BOOLEAN NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (seeker_id, referrer_id),
  FOREIGN KEY (seeker_id) REFERENCES users(id),        -- no CASCADE: MySQL forbids CHECK on columns with cascading FKs
  FOREIGN KEY (referrer_id) REFERENCES users(id),
  CHECK (seeker_id <> referrer_id),
  CHECK (message_count <= 20)                          -- 20 messages in total per conversation
);

-- Messages may contain personal contacts people choose to share: delete 30-90 days after the event.
CREATE TABLE messages (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT UNSIGNED NOT NULL,
  sender_id BIGINT UNSIGNED NOT NULL,
  body VARCHAR(500) NOT NULL,
  sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_conv (conversation_id, sent_at)
);

-- TRUST, INCENTIVES, ALERTS
CREATE TABLE credit_ledger (          -- request budget = 3 + SUM(delta)
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  delta SMALLINT NOT NULL,
  reason VARCHAR(60) NOT NULL,
  ref_id BIGINT UNSIGNED NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE reports_blocks (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  reporter_id BIGINT UNSIGNED NOT NULL,
  target_id BIGINT UNSIGNED NOT NULL,
  type ENUM('report','block') NOT NULL,
  reason VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (target_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE notifications (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(40) NOT NULL,
  payload JSON NULL,
  read_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_user_unread (user_id, read_at)
);
