package com.testryn.testplan.web;

import com.testryn.testplan.domain.TestPlan;
import com.testryn.testplan.domain.TestPlanEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TestPlanDtos {

    private TestPlanDtos() {
    }

    public record CreateTestPlanRequest(
            @NotBlank String name,
            String description
    ) {
    }

    public record AddTestCaseRequest(
            @NotNull UUID testCaseId
    ) {
    }

    public record TestPlanEntryResponse(
            UUID testCaseId,
            String testCaseHumanId,
            String testCaseTitle,
            int position
    ) {
        public static TestPlanEntryResponse from(TestPlanEntry entry) {
            String title = entry.getTestCase().getCurrentVersion() == null
                    ? null
                    : entry.getTestCase().getCurrentVersion().getTitle();
            return new TestPlanEntryResponse(
                    entry.getTestCase().getId(),
                    entry.getTestCase().getHumanId(),
                    title,
                    entry.getPosition()
            );
        }
    }

    public record TestPlanResponse(
            UUID id,
            String projectKey,
            String name,
            String description,
            List<TestPlanEntryResponse> testCases,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static TestPlanResponse from(TestPlan plan) {
            return new TestPlanResponse(
                    plan.getId(),
                    plan.getProject().getKey(),
                    plan.getName(),
                    plan.getDescription(),
                    plan.getEntries().stream().map(TestPlanEntryResponse::from).toList(),
                    plan.getCreatedAt(),
                    plan.getUpdatedAt()
            );
        }
    }
}
