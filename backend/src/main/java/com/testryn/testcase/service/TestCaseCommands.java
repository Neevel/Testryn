package com.testryn.testcase.service;

import com.testryn.testcase.domain.TestCasePriority;
import com.testryn.testcase.domain.TestCaseStatus;

import java.util.List;
import java.util.Set;

public final class TestCaseCommands {

    private TestCaseCommands() {
    }

    public record StepCommand(String action, String expectedResult) {
    }

    public record CreateTestCaseCommand(
            String title,
            String description,
            String preconditions,
            TestCasePriority priority,
            Set<String> tags,
            List<StepCommand> steps
    ) {
    }

    /**
     * A single PUT updates both content and metadata, mirroring how a client edits a
     * test case as one form. Content (title/description/preconditions/steps) and
     * metadata (status/priority/tags) are versioned differently under the hood: a new
     * {@code TestCaseVersion} is only created when the content actually changed (ADR
     * 0002); metadata changes never create a new version.
     */
    public record UpdateTestCaseCommand(
            String title,
            String description,
            String preconditions,
            List<StepCommand> steps,
            TestCaseStatus status,
            TestCasePriority priority,
            Set<String> tags
    ) {
    }
}
