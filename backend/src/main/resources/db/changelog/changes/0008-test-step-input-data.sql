--liquibase formatted sql

--changeset testryn:0008-01-test-step-input-data
ALTER TABLE test_steps ADD COLUMN input_data TEXT;
