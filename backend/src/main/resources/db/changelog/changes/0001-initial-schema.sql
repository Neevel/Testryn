--liquibase formatted sql

--changeset testryn:0001-01-projects
CREATE TABLE projects (
    id           UUID PRIMARY KEY,
    key          VARCHAR(50) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL,
    CONSTRAINT uq_projects_key UNIQUE (key)
);

--changeset testryn:0001-02-test-cases
CREATE TABLE test_cases (
    id                  UUID PRIMARY KEY,
    project_id          UUID NOT NULL REFERENCES projects (id),
    human_id            VARCHAR(50) NOT NULL,
    sequence_number     INTEGER NOT NULL,
    status              VARCHAR(30) NOT NULL,
    priority            VARCHAR(20) NOT NULL,
    current_version_id  UUID,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT uq_test_cases_human_id UNIQUE (human_id),
    CONSTRAINT uq_test_cases_project_sequence UNIQUE (project_id, sequence_number)
);
CREATE INDEX idx_test_cases_project ON test_cases (project_id);

--changeset testryn:0001-03-test-case-tags
CREATE TABLE test_case_tags (
    test_case_id UUID NOT NULL REFERENCES test_cases (id) ON DELETE CASCADE,
    tag          VARCHAR(100) NOT NULL,
    CONSTRAINT pk_test_case_tags PRIMARY KEY (test_case_id, tag)
);

--changeset testryn:0001-04-test-case-versions
CREATE TABLE test_case_versions (
    id             UUID PRIMARY KEY,
    test_case_id   UUID NOT NULL REFERENCES test_cases (id),
    version_number INTEGER NOT NULL,
    title          VARCHAR(500) NOT NULL,
    description    TEXT,
    preconditions  TEXT,
    created_at     TIMESTAMP NOT NULL,
    CONSTRAINT uq_test_case_versions_number UNIQUE (test_case_id, version_number)
);
CREATE INDEX idx_test_case_versions_test_case ON test_case_versions (test_case_id);

--changeset testryn:0001-05-test-cases-current-version-fk
ALTER TABLE test_cases
    ADD CONSTRAINT fk_test_cases_current_version
    FOREIGN KEY (current_version_id) REFERENCES test_case_versions (id);

--changeset testryn:0001-06-test-steps
CREATE TABLE test_steps (
    id                    UUID PRIMARY KEY,
    test_case_version_id  UUID NOT NULL REFERENCES test_case_versions (id),
    step_order            INTEGER NOT NULL,
    action                TEXT NOT NULL,
    expected_result       TEXT NOT NULL,
    CONSTRAINT uq_test_steps_order UNIQUE (test_case_version_id, step_order)
);
CREATE INDEX idx_test_steps_version ON test_steps (test_case_version_id);

--changeset testryn:0001-07-requirement-links
CREATE TABLE requirement_links (
    id            UUID PRIMARY KEY,
    test_case_id  UUID NOT NULL REFERENCES test_cases (id),
    provider      VARCHAR(30) NOT NULL,
    external_id   VARCHAR(255),
    external_key  VARCHAR(100) NOT NULL,
    url           VARCHAR(1000) NOT NULL,
    summary       VARCHAR(1000),
    created_at    TIMESTAMP NOT NULL,
    CONSTRAINT uq_requirement_links UNIQUE (test_case_id, provider, external_key)
);
CREATE INDEX idx_requirement_links_test_case ON requirement_links (test_case_id);

--changeset testryn:0001-08-test-plans
CREATE TABLE test_plans (
    id           UUID PRIMARY KEY,
    project_id   UUID NOT NULL REFERENCES projects (id),
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);
CREATE INDEX idx_test_plans_project ON test_plans (project_id);

--changeset testryn:0001-09-test-plan-entries
CREATE TABLE test_plan_entries (
    id            UUID PRIMARY KEY,
    test_plan_id  UUID NOT NULL REFERENCES test_plans (id),
    test_case_id  UUID NOT NULL REFERENCES test_cases (id),
    position      INTEGER NOT NULL,
    CONSTRAINT uq_test_plan_entries UNIQUE (test_plan_id, test_case_id)
);
CREATE INDEX idx_test_plan_entries_plan ON test_plan_entries (test_plan_id);

--changeset testryn:0001-10-executions
CREATE TABLE executions (
    id               UUID PRIMARY KEY,
    project_id       UUID NOT NULL REFERENCES projects (id),
    test_plan_id     UUID REFERENCES test_plans (id),
    iteration_number INTEGER NOT NULL,
    name             VARCHAR(255) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    created_at       TIMESTAMP NOT NULL,
    started_at       TIMESTAMP,
    finished_at      TIMESTAMP
);
CREATE INDEX idx_executions_project ON executions (project_id);
CREATE INDEX idx_executions_test_plan ON executions (test_plan_id);

--changeset testryn:0001-11-execution-test-cases
CREATE TABLE execution_test_cases (
    id                   UUID PRIMARY KEY,
    execution_id         UUID NOT NULL REFERENCES executions (id),
    test_case_id         UUID NOT NULL REFERENCES test_cases (id),
    test_case_version_id UUID NOT NULL REFERENCES test_case_versions (id),
    position             INTEGER NOT NULL,
    CONSTRAINT uq_execution_test_cases UNIQUE (execution_id, test_case_id)
);
CREATE INDEX idx_execution_test_cases_execution ON execution_test_cases (execution_id);

--changeset testryn:0001-12-execution-results
CREATE TABLE execution_results (
    id                      UUID PRIMARY KEY,
    execution_test_case_id  UUID NOT NULL REFERENCES execution_test_cases (id),
    status                  VARCHAR(20) NOT NULL,
    comment                 TEXT,
    duration_ms             BIGINT,
    executed_at             TIMESTAMP,
    executor                VARCHAR(255),
    failure_details         TEXT,
    CONSTRAINT uq_execution_results_etc UNIQUE (execution_test_case_id)
);

--changeset testryn:0001-13-reports
CREATE TABLE reports (
    id            UUID PRIMARY KEY,
    execution_id  UUID NOT NULL REFERENCES executions (id),
    filename      VARCHAR(500) NOT NULL,
    content_type  VARCHAR(255) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    storage_key   VARCHAR(500) NOT NULL,
    checksum      VARCHAR(128),
    uploaded_at   TIMESTAMP NOT NULL
);
CREATE INDEX idx_reports_execution ON reports (execution_id);
