package com.testryn.execution.web;

import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.ExecutionResult;
import com.testryn.execution.domain.ExecutionResultStatus;
import com.testryn.execution.domain.ExecutionStatus;
import com.testryn.execution.domain.ExecutionTestCase;
import com.testryn.testcase.domain.TestCaseVersion;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExecutionDtos {

    private ExecutionDtos() {
    }

    public record CreateExecutionRequest(
            String name
    ) {
    }

    public record CreateAdhocExecutionRequest(
            String name,
            @NotNull List<UUID> testCaseIds
    ) {
    }

    /**
     * Documents the JSON Merge Patch (RFC 7396) request shape for springdoc; the
     * controller actually binds the raw {@code JsonNode} to distinguish "field
     * absent" from "field explicitly null" -- see ADR 0006. Every field is
     * optional: omit it to leave the current value untouched, send it as
     * {@code null} to clear it (status excepted -- it may never be null).
     */
    public record ExecutionResultPatchRequest(
            ExecutionResultStatus status,
            String comment,
            Long durationMs,
            String executor,
            String actualResult,
            String failureDetails
    ) {
    }

    /**
     * Documents one bulk-update entry for springdoc (Abschnitt 27); the controller
     * binds the raw JSON array element to distinguish "field absent" from "field
     * explicitly null" for the patchable fields, same as {@link ExecutionResultPatchRequest}
     * -- see ADR 0010. {@code resultId} and/or {@code automationReference} identify
     * the target within this execution: at least one is required; if both are given
     * they must resolve to the same result, or the whole request is rejected.
     */
    public record BulkResultPatchItemRequest(
            UUID resultId,
            String automationReference,
            ExecutionResultStatus status,
            String comment,
            Long durationMs,
            String executor,
            String actualResult,
            String failureDetails
    ) {
    }

    public record BulkResultUpdateRequest(
            @NotEmpty List<BulkResultPatchItemRequest> results
    ) {
    }

    /** Superset of {@link ExecutionResultResponse} purpose-built for the bulk
     * endpoint's response: includes {@code testCaseHumanId}/{@code automationReference}
     * so a CI publisher can log/report per-test-case outcomes without a second
     * round-trip to resolve which test case a {@code resultId} belongs to. */
    public record BulkResultEntryResponse(
            UUID resultId,
            String testCaseHumanId,
            String automationReference,
            ExecutionResultStatus status,
            String comment,
            Long durationMs,
            Instant executedAt,
            String executor,
            String actualResult,
            String failureDetails
    ) {
        public static BulkResultEntryResponse from(ExecutionTestCase etc) {
            ExecutionResult result = etc.getResult();
            return new BulkResultEntryResponse(
                    result.getId(),
                    etc.getTestCase().getHumanId(),
                    etc.getTestCase().getAutomationReference(),
                    result.getStatus(),
                    result.getComment(),
                    result.getDurationMs(),
                    result.getExecutedAt(),
                    result.getExecutor(),
                    result.getActualResult(),
                    result.getFailureDetails()
            );
        }
    }

    public record BulkResultUpdateResponse(
            List<BulkResultEntryResponse> results
    ) {
        public static BulkResultUpdateResponse from(List<ExecutionTestCase> updated) {
            return new BulkResultUpdateResponse(updated.stream().map(BulkResultEntryResponse::from).toList());
        }
    }

    public record ExecutionResultResponse(
            UUID id,
            ExecutionResultStatus status,
            String comment,
            Long durationMs,
            Instant executedAt,
            String executor,
            String actualResult,
            String failureDetails
    ) {
        public static ExecutionResultResponse from(ExecutionResult result) {
            return new ExecutionResultResponse(
                    result.getId(),
                    result.getStatus(),
                    result.getComment(),
                    result.getDurationMs(),
                    result.getExecutedAt(),
                    result.getExecutor(),
                    result.getActualResult(),
                    result.getFailureDetails()
            );
        }
    }

    /** Mirrors {@code com.testryn.testcase.web.TestCaseDtos.StepResponse} on
     * purpose -- the execution module reads the pinned {@link TestCaseVersion}'s
     * own steps, not the testcase module's current ones, and should not depend on
     * another module's web-layer DTOs (module boundaries, AGENTS.md). */
    public record ExecutionStepResponse(
            int order,
            String action,
            String expectedResult
    ) {
    }

    public record ExecutionTestCaseResponse(
            UUID testCaseId,
            String testCaseHumanId,
            int testCaseVersionNumber,
            String title,
            String description,
            String preconditions,
            List<ExecutionStepResponse> steps,
            int position,
            ExecutionResultResponse result
    ) {
        public static ExecutionTestCaseResponse from(ExecutionTestCase etc) {
            TestCaseVersion version = etc.getTestCaseVersion();
            return new ExecutionTestCaseResponse(
                    etc.getTestCase().getId(),
                    etc.getTestCase().getHumanId(),
                    version.getVersionNumber(),
                    version.getTitle(),
                    version.getDescription(),
                    version.getPreconditions(),
                    version.getSteps().stream()
                            .map(s -> new ExecutionStepResponse(s.getStepOrder(), s.getAction(), s.getExpectedResult()))
                            .toList(),
                    etc.getPosition(),
                    ExecutionResultResponse.from(etc.getResult())
            );
        }
    }

    public record ExecutionResponse(
            UUID id,
            String projectKey,
            UUID testPlanId,
            int iterationNumber,
            String name,
            ExecutionStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            List<ExecutionTestCaseResponse> testCases
    ) {
        public static ExecutionResponse from(Execution execution) {
            return new ExecutionResponse(
                    execution.getId(),
                    execution.getProject().getKey(),
                    execution.getTestPlan() == null ? null : execution.getTestPlan().getId(),
                    execution.getIterationNumber(),
                    execution.getName(),
                    execution.getStatus(),
                    execution.getCreatedAt(),
                    execution.getStartedAt(),
                    execution.getFinishedAt(),
                    execution.getTestCases().stream().map(ExecutionTestCaseResponse::from).toList()
            );
        }
    }
}
