--liquibase formatted sql

--changeset testryn:0009-jira-connection-configuration
CREATE TABLE jira_connection_configuration (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    email VARCHAR(320),
    active BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- The API token intentionally remains in external server configuration and is not persisted here.
