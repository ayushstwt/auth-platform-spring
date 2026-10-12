-- V3__init_otp_and_shedlock.sql
-- Table: otp_challenges and shedlock

CREATE TABLE IF NOT EXISTS otp_challenges (
    id BIGSERIAL PRIMARY KEY,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT REFERENCES users (id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    consumed_at TIMESTAMP WITH TIME ZONE,
    request_ip VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_otp_dest_purpose_active ON otp_challenges (destination, purpose, is_active, is_deleted);
CREATE INDEX IF NOT EXISTS idx_otp_user_purpose_active ON otp_challenges (user_id, purpose, is_active, is_deleted);
CREATE INDEX IF NOT EXISTS idx_otp_expires_at ON otp_challenges (expires_at);

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
