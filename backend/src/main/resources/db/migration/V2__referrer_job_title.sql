-- Referrer job title shown next to the name ("Asha, Engineering Manager").
ALTER TABLE referrer_profiles ADD COLUMN job_title VARCHAR(100) NULL;
