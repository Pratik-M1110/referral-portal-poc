CREATE TABLE referral_request_resumes (
  request_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  data LONGBLOB NOT NULL,
  uploaded_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_referral_request_resumes_request
    FOREIGN KEY (request_id) REFERENCES company_referral_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB;