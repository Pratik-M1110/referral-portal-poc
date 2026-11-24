-- Email/password sign-in (#21). OAuth accounts keep password_hash NULL; only
-- password accounts (auth_provider = 'password') have a bcrypt hash here.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100) NULL AFTER auth_provider;
