package com.testryn.execution.service;

import com.testryn.execution.domain.ExecutionResult;
import com.testryn.execution.domain.ExecutionResultStatus;

/**
 * Purely technical merge target for JSON Merge Patch (RFC 7396) semantics on
 * {@code PATCH .../results/{resultId}} -- see ADR 0006. Getter/setter pairs only so
 * that {@code ObjectMapper#readerForUpdating} can merge the incoming JSON on top of
 * a snapshot of the current {@link ExecutionResult} state: fields absent from the
 * JSON keep the value this object was seeded with, fields present (including
 * explicit {@code null}) get overwritten. No behavior of its own.
 */
public class ExecutionResultPatchState {

    private ExecutionResultStatus status;
    private String comment;
    private Long durationMs;
    private String executor;
    private String actualResult;
    private String failureDetails;

    public static ExecutionResultPatchState seedFrom(ExecutionResult result) {
        ExecutionResultPatchState state = new ExecutionResultPatchState();
        state.status = result.getStatus();
        state.comment = result.getComment();
        state.durationMs = result.getDurationMs();
        state.executor = result.getExecutor();
        state.actualResult = result.getActualResult();
        state.failureDetails = result.getFailureDetails();
        return state;
    }

    public ExecutionResultStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionResultStatus status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getActualResult() {
        return actualResult;
    }

    public void setActualResult(String actualResult) {
        this.actualResult = actualResult;
    }

    public String getFailureDetails() {
        return failureDetails;
    }

    public void setFailureDetails(String failureDetails) {
        this.failureDetails = failureDetails;
    }
}
