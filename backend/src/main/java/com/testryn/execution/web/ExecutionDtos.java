package com.testryn.execution.web;

import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.ExecutionResult;
import com.testryn.execution.domain.ExecutionResultStatus;
import com.testryn.execution.domain.ExecutionStatus;
import com.testryn.execution.domain.ExecutionTestCase;
import com.testryn.testcase.domain.TestCaseVersion;
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
