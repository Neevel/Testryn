package com.testryn.requirement.web;

import com.testryn.execution.domain.ExecutionResultStatus;
import com.testryn.execution.domain.ExecutionTestCase;
import com.testryn.requirement.domain.RequirementProviderType;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCasePriority;
import com.testryn.testcase.domain.TestCaseStatus;
import com.testryn.testcase.domain.TestCaseVersion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response shapes for the requirement-coverage read view (ADR 0014) -- "which test
 * cases cover this requirement, with their steps and latest result", built for the
 * Jira Forge issue panel but provider-neutral: nothing here is Jira-specific.
 * Deliberately small (Abschnitt 21): only the fields the panel actually renders.
 */
public final class RequirementCoverageDtos {

    private RequirementCoverageDtos() {
    }

    public record RequirementRef(
            RequirementProviderType provider,
            String externalKey
    ) {
    }

    /** Mirrors {@code TestCaseDtos.StepResponse}/{@code ExecutionDtos.ExecutionStepResponse}
     * on purpose -- same field names, another module's own copy (module boundaries). */
    public record CoverageStepResponse(
            int order,
            String action,
            String inputData,
            String expectedResult
    ) {
    }

    /** No {@code executor}/{@code executedAt} at the step level here (Abschnitt 26:
     * "Response nicht unnötig gigantisch machen") -- the panel's step display never
     * shows those for an individual step. {@code comment} was added in the Forge
     * Panel UX Refinement block, deliberately additive and narrow: it is the only
     * field carrying a human-written reason for a BLOCKED step (the same field the
     * Runner already writes to, e.g. "environment down"), and the panel has nowhere
     * else to read that from (Abschnitt 17/31 -- no domain/migration/aggregation
     * change, one existing field exposed on one existing read DTO). */
    public record CoverageStepResultResponse(
            ExecutionResultStatus status,
            String actualResult,
            String failureDetails,
            String comment
    ) {
        public static CoverageStepResultResponse from(com.testryn.execution.domain.ExecutionStepResult stepResult) {
            return new CoverageStepResultResponse(stepResult.getStatus(), stepResult.getActualResult(),
                    stepResult.getFailureDetails(), stepResult.getComment());
        }
    }

    /** The step as it actually was in the latest execution's own pinned snapshot
     * (Abschnitt 22: the execution-level snapshot stays authoritative for history),
     * together with its result -- distinct from {@link CoverageStepResponse}, which
     * is the test case's CURRENT steps with no result at all, used as a preview when
     * a test case has no execution yet (Abschnitt 17). */
    public record LatestExecutionStepResponse(
            int position,
            String action,
            String inputData,
            String expectedResult,
            CoverageStepResultResponse result
    ) {
        public static LatestExecutionStepResponse from(com.testryn.execution.domain.ExecutionStepResult stepResult) {
            var step = stepResult.getStep();
            return new LatestExecutionStepResponse(step.getStepOrder(), step.getAction(), step.getInputData(), step.getExpectedResult(),
                    CoverageStepResultResponse.from(stepResult));
        }
    }

    public record LatestExecutionResponse(
            UUID executionId,
            String executionName,
            ExecutionResultStatus status,
            Instant executedAt,
            Long durationMs,
            String executor,
            /** Empty for an execution that predates the Step-Level Execution
             * Results block (ADR 0015) -- never fabricated (Abschnitt 35/36), and
             * for a JUnit-imported result specifically (Abschnitt 20/36: automation
             * without step reporting stays testcase-level only). */
            List<LatestExecutionStepResponse> steps
    ) {
        public static LatestExecutionResponse from(ExecutionTestCase etc) {
            var result = etc.getResult();
            List<LatestExecutionStepResponse> steps = etc.getStepResults().stream()
                    .sorted(java.util.Comparator.comparingInt(sr -> sr.getStep().getStepOrder()))
                    .map(LatestExecutionStepResponse::from)
                    .toList();
            return new LatestExecutionResponse(
                    etc.getExecution().getId(),
                    etc.getExecution().getName(),
                    result.getStatus(),
                    result.getExecutedAt(),
                    result.getDurationMs(),
                    result.getExecutor(),
                    steps
            );
        }
    }

    public record CoverageTestCaseResponse(
            UUID id,
            String humanId,
            /** The owning project's key. Additive, provider-neutral: a caller
             * starting an execution needs to know which project each linked test
             * case belongs to (the ad-hoc execution endpoint is project-scoped),
             * and {@code project} is already fetched by the coverage query's entity
             * graph, so this costs no extra round trip. */
            String projectKey,
            String title,
            TestCaseStatus status,
            TestCasePriority priority,
            int version,
            String description,
            String preconditions,
            List<CoverageStepResponse> steps,
            LatestExecutionResponse latestExecution
    ) {
        /** {@code latestExecutionByTestCaseId} intentionally excludes an entry for a
         * test case that has never been added to any execution -- distinct from one
         * added but not yet run (Abschnitt 14, which still has a real, NOT_RUN
         * result). */
        public static CoverageTestCaseResponse from(TestCase testCase,
                                                      Map<UUID, ExecutionTestCase> latestExecutionByTestCaseId) {
            TestCaseVersion version = testCase.getCurrentVersion();
            ExecutionTestCase latest = latestExecutionByTestCaseId.get(testCase.getId());
            return new CoverageTestCaseResponse(
                    testCase.getId(),
                    testCase.getHumanId(),
                    testCase.getProject().getKey(),
                    version == null ? null : version.getTitle(),
                    testCase.getStatus(),
                    testCase.getPriority(),
                    version == null ? 0 : version.getVersionNumber(),
                    version == null ? null : version.getDescription(),
                    version == null ? null : version.getPreconditions(),
                    version == null ? List.of() : version.getSteps().stream()
                            .map(s -> new CoverageStepResponse(s.getStepOrder(), s.getAction(), s.getInputData(), s.getExpectedResult()))
                            .toList(),
                    latest == null ? null : LatestExecutionResponse.from(latest)
            );
        }
    }

    public record RequirementCoverageResponse(
            RequirementRef requirement,
            List<CoverageTestCaseResponse> testCases,
            int totalCount
    ) {
    }
}
