package com.testryn.execution.web;

import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.ExecutionResult;
import com.testryn.execution.domain.ExecutionResultStatus;
import com.testryn.execution.domain.ExecutionStatus;
import com.testryn.execution.domain.ExecutionStepResult;
import com.testryn.execution.domain.ExecutionTestCase;
import com.testryn.testcase.domain.TestCaseVersion;
import com.testryn.testcase.domain.TestStep;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * Documents the JSON Merge Patch request shape for a single step result for
     * springdoc -- the controller binds the raw {@code JsonNode}, same reason as
     * {@link ExecutionResultPatchRequest} (ADR 0006/0015). No {@code durationMs}:
     * not a step-level field (Abschnitt 5).
     */
    public record StepResultPatchRequest(
            ExecutionResultStatus status,
            String actualResult,
            String comment,
            String failureDetails,
            String executor
    ) {
    }

    /** Documents one bulk step-result-update entry for springdoc (Abschnitt 19) --
     * addressed by {@code stepResultId} alone, the id every read of an execution's
     * step results already returns. */
    public record BulkStepResultPatchItemRequest(
            UUID stepResultId,
            ExecutionResultStatus status,
            String actualResult,
            String comment,
            String failureDetails,
            String executor
    ) {
    }

    public record BulkStepResultUpdateRequest(
            @NotEmpty List<BulkStepResultPatchItemRequest> results
    ) {
    }

    public record StepResultResponse(
            UUID id,
            ExecutionResultStatus status,
            String actualResult,
            String comment,
            String failureDetails,
            Instant executedAt,
            String executor
    ) {
        public static StepResultResponse from(ExecutionStepResult stepResult) {
            return new StepResultResponse(
                    stepResult.getId(),
                    stepResult.getStatus(),
                    stepResult.getActualResult(),
                    stepResult.getComment(),
                    stepResult.getFailureDetails(),
                    stepResult.getExecutedAt(),
                    stepResult.getExecutor()
            );
        }
    }

    public record BulkStepResultUpdateResponse(
            List<StepResultResponse> results
    ) {
        public static BulkStepResultUpdateResponse from(List<ExecutionStepResult> updated) {
            return new BulkStepResultUpdateResponse(updated.stream().map(StepResultResponse::from).toList());
        }
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
     * another module's web-layer DTOs (module boundaries, AGENTS.md). {@code result}
     * is {@code null} for an execution created before the Step-Level Execution
     * Results block (ADR 0015) -- no step results were ever backfilled for old
     * executions (Abschnitt 37), so callers must treat {@code null} as "not
     * available for this execution", not as NOT_RUN. */
    public record ExecutionStepResponse(
            int order,
            String action,
            String inputData,
            String expectedResult,
            StepResultResponse result
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
            Map<UUID, ExecutionStepResult> stepResultsByStepId = etc.getStepResults().stream()
                    .collect(Collectors.toMap(sr -> sr.getStep().getId(), Function.identity()));
            List<ExecutionStepResponse> steps = version.getSteps().stream()
                    .map(s -> toStepResponse(s, stepResultsByStepId.get(s.getId())))
                    .toList();
            return new ExecutionTestCaseResponse(
                    etc.getTestCase().getId(),
                    etc.getTestCase().getHumanId(),
                    version.getVersionNumber(),
                    version.getTitle(),
                    version.getDescription(),
                    version.getPreconditions(),
                    steps,
                    etc.getPosition(),
                    ExecutionResultResponse.from(etc.getResult())
            );
        }

        private static ExecutionStepResponse toStepResponse(TestStep step, ExecutionStepResult stepResult) {
            return new ExecutionStepResponse(step.getStepOrder(), step.getAction(), step.getInputData(), step.getExpectedResult(),
                    stepResult == null ? null : StepResultResponse.from(stepResult));
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
