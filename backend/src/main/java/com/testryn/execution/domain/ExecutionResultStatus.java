package com.testryn.execution.domain;

/**
 * Outcome of a single test case within an execution. Manual and automated results
 * share this same model — the execution path differs, the outcome does not.
 */
public enum ExecutionResultStatus {
    NOT_RUN,
    PASSED,
    FAILED,
    SKIPPED,
    BLOCKED
}
