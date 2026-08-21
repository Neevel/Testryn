--liquibase formatted sql

--changeset testryn:0005-01-service-tokens
-- Machine-to-machine credentials (ADR 0012). The raw token is never stored -- only
-- lookup_id (non-secret public half, used for O(1) lookup) and token_hash (SHA-256 of
-- the secret half). revoked_at is nullable and never cleared once set: revocation
-- keeps the audit row (Abschnitt 17), it does not delete it.
CREATE TABLE service_tokens (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    lookup_id VARCHAR(32) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    revoked_at TIMESTAMP
);

CREATE UNIQUE INDEX uq_service_tokens_lookup_id ON service_tokens (lookup_id);

--changeset testryn:0005-02-service-token-scopes
-- Normalized scope collection (same pattern as test_case_tags) rather than a
-- serialized column -- keeps scope checks simple SQL, no JSON parsing needed if a
-- future admin query needs to filter by scope.
CREATE TABLE service_token_scopes (
    service_token_id UUID NOT NULL REFERENCES service_tokens(id) ON DELETE CASCADE,
    scope VARCHAR(20) NOT NULL,
    PRIMARY KEY (service_token_id, scope)
);
