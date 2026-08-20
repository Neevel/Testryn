--liquibase formatted sql

--changeset testryn:0004-01-test-cases-automation-reference
ALTER TABLE test_cases ADD COLUMN automation_reference VARCHAR(200);

--changeset testryn:0004-02-test-cases-automation-reference-unique-per-project
-- Project-scoped uniqueness: the same automationReference string is fine in two
-- different projects, never twice in the same one (Abschnitt 10). Partial index so
-- NULL (the common case -- most test cases are not automated) never collides.
CREATE UNIQUE INDEX uq_test_cases_project_automation_reference
    ON test_cases (project_id, automation_reference)
    WHERE automation_reference IS NOT NULL;
