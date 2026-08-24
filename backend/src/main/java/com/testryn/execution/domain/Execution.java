package com.testryn.execution.domain;

import com.testryn.project.domain.Project;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCaseVersion;
import com.testryn.testplan.domain.TestPlan;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A concrete test run: an immutable snapshot of the test cases (pinned to specific
 * {@link TestCaseVersion}s) that were relevant at the moment the execution was
 * created, plus their results. Optionally tied to a {@link TestPlan}; each execution
 * of the same plan gets its own, independent {@code iterationNumber} (ADR 0003).
 */
@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_plan_id")
    private TestPlan testPlan;

    @Column(name = "iteration_number", nullable = false)
    private int iterationNumber;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ExecutionTestCase> testCases = new ArrayList<>();

    protected Execution() {
        // for JPA
    }

    public record SnapshotEntry(TestCase testCase, TestCaseVersion version) {
    }

    /**
     * Creates the execution together with its immutable snapshot, one
     * {@link ExecutionTestCase} (+ a {@code NOT_RUN} result) per entry, in order.
     */
    public static Execution createSnapshot(Project project, TestPlan testPlan, int iterationNumber, String name,
                                            List<SnapshotEntry> snapshot) {
        Execution execution = new Execution();
        execution.project = project;
        execution.testPlan = testPlan;
        execution.iterationNumber = iterationNumber;
        execution.name = name;
        execution.status = ExecutionStatus.CREATED;
        execution.createdAt = Instant.now();

        int position = 1;
        for (SnapshotEntry entry : snapshot) {
            ExecutionTestCase etc = new ExecutionTestCase(entry.testCase(), entry.version(), position++);
            etc.setExecution(execution);
            etc.setResult(ExecutionResult.createNotRun(etc));
            // One ExecutionStepResult per step of the pinned version, all NOT_RUN
            // (Abschnitt 8/ADR 0015) -- requires entry.version().getSteps() to
            // already be loaded, which it is: TestCaseService's entity graph always
            // includes currentVersion.steps.
            etc.initializeStepResults();
            execution.testCases.add(etc);
        }
        return execution;
    }

    public void markRunningIfNeeded() {
        if (this.status == ExecutionStatus.CREATED) {
            this.status = ExecutionStatus.RUNNING;
            this.startedAt = Instant.now();
        }
    }

    public void complete() {
        this.status = ExecutionStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public void abort() {
        this.status = ExecutionStatus.ABORTED;
        this.finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public TestPlan getTestPlan() {
        return testPlan;
    }

    public int getIterationNumber() {
        return iterationNumber;
    }

    public String getName() {
        return name;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public List<ExecutionTestCase> getTestCases() {
        return testCases;
    }
}
