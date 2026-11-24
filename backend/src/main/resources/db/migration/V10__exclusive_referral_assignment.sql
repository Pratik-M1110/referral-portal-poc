ALTER TABLE referral_request_responses
  MODIFY COLUMN status ENUM(
    'pending',
    'accepted',
    'declined',
    'expired',
    'submitted',
    'confirmed',
    'closed',
    'accepted_elsewhere'
  ) NOT NULL DEFAULT 'pending';