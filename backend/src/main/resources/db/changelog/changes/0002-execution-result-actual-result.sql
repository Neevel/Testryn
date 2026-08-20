--liquibase formatted sql

--changeset testryn:0002-01-execution-results-actual-result
ALTER TABLE execution_results ADD COLUMN actual_result TEXT;
