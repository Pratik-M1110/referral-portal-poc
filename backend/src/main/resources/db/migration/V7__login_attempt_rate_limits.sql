CREATE TABLE auth_login_rate_limits (
  bucket_hash CHAR(64) NOT NULL PRIMARY KEY,
  window_started_at DATETIME(6) NOT NULL,
  attempt_count INT UNSIGNED NOT NULL,
  INDEX idx_auth_login_rate_limits_window (window_started_at)
) ENGINE=InnoDB;