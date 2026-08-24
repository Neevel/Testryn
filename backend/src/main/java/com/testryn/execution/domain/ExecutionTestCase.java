package com.testryn.execution.domain;

import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCaseVersion;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One row of an {@link Execution}'s immutable snapshot: which test case, pinned to
 * exactly which {@link TestCaseVersion}, at which position. This — not the current
 * state of the originating {@code TestPlan} — is the historical truth of what an
 * execution covered (ADR 0003).
 */
@Entity
@Table(name = "execution_test_cases")
public class ExecutionTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_version_id", nullable = false)
    private TestCaseVersion testCaseVersion;

    @Column(name = "position", nullable = false)
    private int position;

    @OneToOne(mappedBy = "executionTestCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private ExecutionResult result;

    // Deliberately NOT fetch-joined together with Execution.testCases in the same
    // query (both are Hibernate "bag" collections -> MultipleBagFetchException,
    // same reason TestCaseVersion.steps is initialized separately, see
    // ExecutionService#initializeStepsForResponse).
    @OneToMany(mappedBy = "executionTestCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExecutionStepResult> stepResults = new ArrayList<>();

    protected ExecutionTestCase() {
        // for JPA
    }

    public ExecutionTestCase(TestCase testCase, TestCaseVersion testCaseVersion, int position) {
        this.testCase = testCase;
        this.testCaseVersion = testCaseVersion;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public Execution getExecution() {
        return execution;
    }

    public void setExecution(Execution execution) {
        this.execution = execution;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public TestCaseVersion getTestCaseVersion() {
        return testCaseVersion;
    }

    public int getPosition() {
        return position;
    }

    public ExecutionResult getResult() {
        return result;
    }

    public void setResult(ExecutionResult result) {
        this.result = result;
    }

    public List<ExecutionStepResult> getStepResults() {
        return stepResults;
    }

    /** Called once, at execution-creation time, one {@link ExecutionStepResult} per
     * step of the pinned {@link #testCaseVersion} (Abschnitt 8: eagerly initialized
     * as NOT_RUN, no lazy auto-creation on first click -- see ADR 0015 for why). */
    public void initializeStepResults() {
        for (var step : testCaseVersion.getSteps()) {
            ExecutionStepResult stepResult = ExecutionStepResult.createNotRun(this, step);
            stepResults.add(stepResult);
        }
    }

    /**
     * The testcase-level status implied by the current step results alone (ADR
     * 0015, Abschnitt 9's "Empfohlene Semantik", applied literally):
     * <ol>
     *   <li>any step FAILED -&gt; FAILED</li>
     *   <li>else any step BLOCKED -&gt; BLOCKED</li>
     *   <li>else every step PASSED -&gt; PASSED</li>
     *   <li>else every step that has actually been run (not NOT_RUN) is SKIPPED,
     *       and at least one is -&gt; SKIPPED</li>
     *   <li>else -&gt; NOT_RUN (still incomplete)</li>
     * </ol>
     * Callers decide whether/when to actually apply this to {@link #result} -- see
     * {@code ExecutionStepResultService}, which applies it only as a side effect of
     * a step-level write, never from the independent testcase-level patch path
     * (Abschnitt 9/20: JUnit/CI results stay exactly as reported, steps untouched).
     */
    public ExecutionResultStatus deriveStatusFromSteps() {
        if (stepResults.isEmpty()) {
            return ExecutionResultStatus.NOT_RUN;
        }
        boolean anyFailed = false;
        boolean anyBlocked = false;
        boolean allPassed = true;
        boolean anySkipped = false;
        boolean allExecutedAreSkipped = true;
        for (ExecutionStepResult stepResult : stepResults) {
            ExecutionResultStatus status = stepResult.getStatus();
            if (status == ExecutionResultStatus.FAILED) {
                anyFailed = true;
            }
            if (status == ExecutionResultStatus.BLOCKED) {
                anyBlocked = true;
            }
            if (status != ExecutionResultStatus.PASSED) {
                allPassed = false;
            }
            if (status == ExecutionResultStatus.SKIPPED) {
                anySkipped = true;
            } else if (status != ExecutionResultStatus.NOT_RUN) {
                allExecutedAreSkipped = false;
            }
        }
        if (anyFailed) {
            return ExecutionResultStatus.FAILED;
        }
        if (anyBlocked) {
            return ExecutionResultStatus.BLOCKED;
        }
        if (allPassed) {
            return ExecutionResultStatus.PASSED;
        }
        if (anySkipped && allExecutedAreSkipped) {
            return ExecutionResultStatus.SKIPPED;
        }
        return ExecutionResultStatus.NOT_RUN;
    }
}
