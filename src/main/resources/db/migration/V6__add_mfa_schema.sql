-- V6__add_mfa_schema.sql
-- Add MFA columns to users table and create user_recovery_codes table

ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN totp_secret_encrypted VARCHAR(512);
ALTER TABLE users ADD COLUMN totp_key_id VARCHAR(50);
ALTER TABLE users ADD COLUMN totp_last_timestep BIGINT;

CREATE TABLE IF NOT EXISTS user_recovery_codes (
    id BIGSERIAL PRIMARY KEY,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_user_recovery_code_hash UNIQUE (user_id, code_hash)
);

CREATE INDEX IF NOT EXISTS idx_user_recovery_codes_user_id ON user_recovery_codes (user_id);
