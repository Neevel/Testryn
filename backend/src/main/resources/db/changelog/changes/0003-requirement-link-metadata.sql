--liquibase formatted sql

--changeset testryn:0003-01-requirement-links-metadata
ALTER TABLE requirement_links ADD COLUMN issue_type VARCHAR(100);
ALTER TABLE requirement_links ADD COLUMN status VARCHAR(100);
ALTER TABLE requirement_links ADD COLUMN description TEXT;
