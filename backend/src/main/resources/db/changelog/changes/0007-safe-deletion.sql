--liquibase formatted sql

--changeset testryn:0007-01-safe-test-case-deletion
ALTER TABLE execution_test_cases DROP CONSTRAINT execution_test_cases_test_case_id_fkey;
ALTER TABLE execution_test_cases ALTER COLUMN test_case_id DROP NOT NULL;
ALTER TABLE execution_test_cases ADD CONSTRAINT fk_execution_test_case_identity FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE SET NULL;
ALTER TABLE test_case_versions DROP CONSTRAINT test_case_versions_test_case_id_fkey;
ALTER TABLE test_case_versions ALTER COLUMN test_case_id DROP NOT NULL;
ALTER TABLE test_case_versions ADD CONSTRAINT fk_version_test_case_identity FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE SET NULL;
ALTER TABLE test_plan_entries DROP CONSTRAINT test_plan_entries_test_case_id_fkey;
ALTER TABLE test_plan_entries ADD CONSTRAINT fk_plan_entry_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE CASCADE;
ALTER TABLE requirement_links DROP CONSTRAINT requirement_links_test_case_id_fkey;
ALTER TABLE requirement_links ADD CONSTRAINT fk_requirement_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE CASCADE;

--changeset testryn:0007-02-safe-plan-deletion
ALTER TABLE executions DROP CONSTRAINT executions_test_plan_id_fkey;
ALTER TABLE executions ADD CONSTRAINT fk_execution_plan FOREIGN KEY (test_plan_id) REFERENCES test_plans(id) ON DELETE SET NULL;
