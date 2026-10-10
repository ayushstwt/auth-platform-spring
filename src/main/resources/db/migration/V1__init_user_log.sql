-- V1__init_user_log.sql
-- Base audit log table and indexes for user-service

CREATE TABLE IF NOT EXISTS user_log (
    id BIGSERIAL PRIMARY KEY,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT,
    action VARCHAR(50) NOT NULL,
    sub_action VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_user_log_user_created ON user_log (user_id, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_user_log_action ON user_log (action);
