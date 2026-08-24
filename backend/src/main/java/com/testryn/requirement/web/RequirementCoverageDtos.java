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
            String expectedResult
    ) {
    }

    public record LatestExecutionResponse(
            UUID executionId,
            String executionName,
            ExecutionResultStatus status,
            Instant executedAt,
            Long durationMs
    ) {
        public static LatestExecutionResponse from(ExecutionTestCase etc) {
            var result = etc.getResult();
            return new LatestExecutionResponse(
                    etc.getExecution().getId(),
                    etc.getExecution().getName(),
                    result.getStatus(),
                    result.getExecutedAt(),
                    result.getDurationMs()
            );
        }
    }

    public record CoverageTestCaseResponse(
            UUID id,
            String humanId,
            String title,
            TestCaseStatus status,
            TestCasePriority priority,
            int version,
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
                    version == null ? null : version.getTitle(),
                    testCase.getStatus(),
                    testCase.getPriority(),
                    version == null ? 0 : version.getVersionNumber(),
                    version == null ? null : version.getPreconditions(),
                    version == null ? List.of() : version.getSteps().stream()
                            .map(s -> new CoverageStepResponse(s.getStepOrder(), s.getAction(), s.getExpectedResult()))
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
