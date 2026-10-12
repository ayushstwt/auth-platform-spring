-- V7__add_indexes_for_performance_and_audit.sql
-- Missing indexes for session lookup by user and active state, and audit log queries

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_expires ON refresh_tokens (user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_user_log_action_created ON user_log (action, created_on DESC);
