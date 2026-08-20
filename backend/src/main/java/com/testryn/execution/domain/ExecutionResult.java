package com.testryn.execution.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The outcome of exactly one {@link ExecutionTestCase} (1:1). Starts as
 * {@code NOT_RUN} when the execution is created and is updated as the test case is
 * actually executed (manually or by an external automation pipeline reporting back).
 */
@Entity
@Table(name = "execution_results")
public class ExecutionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_test_case_id", nullable = false, unique = true)
    private ExecutionTestCase executionTestCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionResultStatus status;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "executor")
    private String executor;

    /** What actually happened, as observed by the tester -- distinct from the
     * test case's expected result. Free text, optional. */
    @Column(name = "actual_result", columnDefinition = "TEXT")
    private String actualResult;

    @Column(name = "failure_details", columnDefinition = "TEXT")
    private String failureDetails;

    protected ExecutionResult() {
        // for JPA
    }

    public static ExecutionResult createNotRun(ExecutionTestCase executionTestCase) {
        ExecutionResult result = new ExecutionResult();
        result.executionTestCase = executionTestCase;
        result.status = ExecutionResultStatus.NOT_RUN;
        return result;
    }

    public void apply(ExecutionResultStatus status, String comment, Long durationMs, String executor,
                       String actualResult, String failureDetails) {
        this.status = status;
        this.comment = comment;
        this.durationMs = durationMs;
        this.executor = executor;
        this.actualResult = actualResult;
        this.failureDetails = failureDetails;
        this.executedAt = status == ExecutionResultStatus.NOT_RUN ? null : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ExecutionTestCase getExecutionTestCase() {
        return executionTestCase;
    }

    public ExecutionResultStatus getStatus() {
        return status;
    }

    public String getComment() {
        return comment;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public String getExecutor() {
        return executor;
    }

    public String getActualResult() {
        return actualResult;
    }

    public String getFailureDetails() {
        return failureDetails;
    }
}
