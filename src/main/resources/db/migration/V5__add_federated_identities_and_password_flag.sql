-- V5__add_federated_identities_and_password_flag.sql
-- Add has_password column to users table and create federated_identities table

ALTER TABLE users ADD COLUMN has_password BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS federated_identities (
    id BIGSERIAL PRIMARY KEY,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email_at_link VARCHAR(255) NOT NULL,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_federated_identity_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX IF NOT EXISTS idx_federated_identities_user_id ON federated_identities (user_id);
CREATE INDEX IF NOT EXISTS idx_federated_identities_provider_sub ON federated_identities (provider, provider_subject);
