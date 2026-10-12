-- V4__add_user_phone.sql
-- Add phone_e164 and phone_verified columns to users table

ALTER TABLE users ADD COLUMN phone_e164 VARCHAR(20);
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN DEFAULT FALSE NOT NULL;

CREATE UNIQUE INDEX idx_users_phone_e164 ON users (phone_e164);
