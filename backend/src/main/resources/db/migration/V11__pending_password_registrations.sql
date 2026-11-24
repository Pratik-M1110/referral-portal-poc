CREATE TABLE pending_password_registrations (
  email VARCHAR(190) NOT NULL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  code_hash CHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  INDEX idx_pending_password_registration_expiry (expires_at)
) ENGINE=InnoDB;