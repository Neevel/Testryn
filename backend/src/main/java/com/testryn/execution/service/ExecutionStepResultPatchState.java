package com.testryn.execution.service;

import com.testryn.execution.domain.ExecutionResultStatus;
import com.testryn.execution.domain.ExecutionStepResult;

/**
 * Purely technical merge target for JSON Merge Patch (RFC 7396) semantics on step
 * result PATCH endpoints -- mirrors {@link ExecutionResultPatchState} exactly, minus
 * {@code durationMs} (not a step-level field, Abschnitt 5). See that class's javadoc
 * for the full rationale; no behavior of its own here either.
 */
public class ExecutionStepResultPatchState {

    private ExecutionResultStatus status;
    private String actualResult;
    private String comment;
    private String failureDetails;
    private String executor;

    public static ExecutionStepResultPatchState seedFrom(ExecutionStepResult result) {
        ExecutionStepResultPatchState state = new ExecutionStepResultPatchState();
        state.status = result.getStatus();
        state.actualResult = result.getActualResult();
        state.comment = result.getComment();
        state.failureDetails = result.getFailureDetails();
        state.executor = result.getExecutor();
        return state;
    }

    public ExecutionResultStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionResultStatus status) {
        this.status = status;
    }

    public String getActualResult() {
        return actualResult;
    }

    public void setActualResult(String actualResult) {
        this.actualResult = actualResult;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getFailureDetails() {
        return failureDetails;
    }

    public void setFailureDetails(String failureDetails) {
        this.failureDetails = failureDetails;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }
}
