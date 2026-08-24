--liquibase formatted sql

--changeset testryn:0006-01-execution-step-results
-- Step-Level Execution Results (ADR 0015). References test_steps directly rather
-- than a separate snapshot copy: test_steps rows are themselves already immutable
-- once persisted (ADR 0002) and permanently scoped to one specific, already-pinned
-- test_case_version -- exactly the historical accuracy this table needs, without
-- duplicating action/expected_result text that can never change underneath it.
-- No FK ON DELETE CASCADE: nothing in the application ever deletes a test_steps or
-- execution_test_cases row, matching every other snapshot reference in this schema.
CREATE TABLE execution_step_results (
    id                      UUID PRIMARY KEY,
    execution_test_case_id  UUID NOT NULL REFERENCES execution_test_cases (id),
    step_id                 UUID NOT NULL REFERENCES test_steps (id),
    status                  VARCHAR(20) NOT NULL,
    actual_result           TEXT,
    comment                 TEXT,
    failure_details         TEXT,
    executed_at             TIMESTAMP,
    executor                VARCHAR(255),
    -- Abschnitt 7: a step reference must be unique within its ExecutionTestCase --
    -- this is also what makes "duplicate step result" impossible at the DB level,
    -- not just in application code.
    CONSTRAINT uq_execution_step_results_etc_step UNIQUE (execution_test_case_id, step_id)
);
CREATE INDEX idx_execution_step_results_etc ON execution_step_results (execution_test_case_id);
