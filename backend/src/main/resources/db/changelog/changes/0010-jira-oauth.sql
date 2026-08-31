--liquibase formatted sql

--changeset testryn:0010-jira-oauth-auth-type
-- Additive: existing installations keep working unchanged in API_TOKEN mode (ADR 0018).
ALTER TABLE jira_connection_configuration
    ADD COLUMN auth_type VARCHAR(20) NOT NULL DEFAULT 'API_TOKEN';

--changeset testryn:0010-jira-oauth-state
-- Pending OAuth authorization attempts. Only the SHA-256 hash of the random `state`
-- is stored (never the raw value); rows are deleted on consume (single-use) and are
-- TTL-bounded via expires_at.
CREATE TABLE jira_oauth_state (
    state_hash VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

--changeset testryn:0010-jira-oauth-token
-- The single OAuth token set for Testryn's one Jira connection (id = 'default').
-- Access and refresh tokens are stored ONLY as AES-256-GCM ciphertext
-- (base64(iv || ciphertext || tag)); there is deliberately no plaintext column.
CREATE TABLE jira_oauth_token (
    id                       VARCHAR(32) PRIMARY KEY,
    access_token_ciphertext  TEXT NOT NULL,
    refresh_token_ciphertext TEXT NOT NULL,
    access_token_expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    cloud_id                 VARCHAR(64) NOT NULL,
    site_url                 VARCHAR(500) NOT NULL,
    scopes                   VARCHAR(500),
    obtained_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL
);
