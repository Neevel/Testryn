package com.testryn.execution.web;

import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.ExecutionResult;
import com.testryn.execution.domain.ExecutionResultStatus;
import com.testryn.execution.domain.ExecutionStatus;
import com.testryn.execution.domain.ExecutionTestCase;
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

    public record UpdateExecutionResultRequest(
            @NotNull ExecutionResultStatus status,
            String comment,
            Long durationMs,
            String executor,
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
                    result.getFailureDetails()
            );
        }
    }

    public record ExecutionTestCaseResponse(
            UUID testCaseId,
            String testCaseHumanId,
            int testCaseVersionNumber,
            String title,
            int position,
            ExecutionResultResponse result
    ) {
        public static ExecutionTestCaseResponse from(ExecutionTestCase etc) {
            return new ExecutionTestCaseResponse(
                    etc.getTestCase().getId(),
                    etc.getTestCase().getHumanId(),
                    etc.getTestCaseVersion().getVersionNumber(),
                    etc.getTestCaseVersion().getTitle(),
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
