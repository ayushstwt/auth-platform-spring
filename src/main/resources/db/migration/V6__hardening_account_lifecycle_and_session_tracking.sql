-- V6__hardening_account_lifecycle_and_session_tracking.sql
-- Add deleted_at for hard-purge scheduling, device details to refresh_tokens, and ip_address to user_log

ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users (deleted_at);

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS device_name VARCHAR(150);

ALTER TABLE user_log ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);
CREATE INDEX IF NOT EXISTS idx_user_log_created_on ON user_log (created_on DESC);
