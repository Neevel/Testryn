package com.testryn.testcase.web;

import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCasePriority;
import com.testryn.testcase.domain.TestCaseStatus;
import com.testryn.testcase.domain.TestCaseVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TestCaseDtos {

    private TestCaseDtos() {
    }

    public record StepRequest(
            @NotBlank String action,
            @NotBlank String expectedResult
    ) {
    }

    public record StepResponse(
            int order,
            String action,
            String expectedResult
    ) {
        public static StepResponse from(com.testryn.testcase.domain.TestStep step) {
            return new StepResponse(step.getStepOrder(), step.getAction(), step.getExpectedResult());
        }
    }

    public record CreateTestCaseRequest(
            @NotBlank String title,
            String description,
            String preconditions,
            @NotNull TestCasePriority priority,
            Set<String> tags,
            @NotEmpty @Valid List<StepRequest> steps,
            String automationReference
    ) {
    }

    public record UpdateTestCaseRequest(
            @NotBlank String title,
            String description,
            String preconditions,
            @NotEmpty @Valid List<StepRequest> steps,
            @NotNull TestCaseStatus status,
            @NotNull TestCasePriority priority,
            Set<String> tags,
            String automationReference
    ) {
    }

    public record TestCaseVersionResponse(
            UUID id,
            int versionNumber,
            String title,
            String description,
            String preconditions,
            List<StepResponse> steps,
            Instant createdAt
    ) {
        public static TestCaseVersionResponse from(TestCaseVersion version) {
            return new TestCaseVersionResponse(
                    version.getId(),
                    version.getVersionNumber(),
                    version.getTitle(),
                    version.getDescription(),
                    version.getPreconditions(),
                    version.getSteps().stream().map(StepResponse::from).toList(),
                    version.getCreatedAt()
            );
        }
    }

    public record TestCaseResponse(
            UUID id,
            String humanId,
            String projectKey,
            TestCaseStatus status,
            TestCasePriority priority,
            Set<String> tags,
            String automationReference,
            TestCaseVersionResponse currentVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static TestCaseResponse from(TestCase testCase) {
            return new TestCaseResponse(
                    testCase.getId(),
                    testCase.getHumanId(),
                    testCase.getProject().getKey(),
                    testCase.getStatus(),
                    testCase.getPriority(),
                    testCase.getTags(),
                    testCase.getAutomationReference(),
                    testCase.getCurrentVersion() == null ? null : TestCaseVersionResponse.from(testCase.getCurrentVersion()),
                    testCase.getCreatedAt(),
                    testCase.getUpdatedAt()
            );
        }
    }
}
