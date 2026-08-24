package com.testryn.execution.domain;

import com.testryn.testcase.domain.TestStep;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The outcome of exactly one step within one {@link ExecutionTestCase} (Step-Level
 * Execution Results block, ADR 0015). Mirrors {@link ExecutionResult} deliberately
 * closely -- same {@link ExecutionResultStatus} enum (Abschnitt 4: no parallel status
 * model), same "starts NOT_RUN, gets applied later" shape -- just one level deeper.
 *
 * <p><b>Historical accuracy without a redundant snapshot table</b> (ADR 0015): this
 * references {@link TestStep} directly, not a copy of its content. That is safe
 * specifically because {@link TestStep} rows are themselves already immutable once
 * persisted (see its own javadoc / ADR 0002) and permanently tied to one specific,
 * immutable {@link com.testryn.testcase.domain.TestCaseVersion} -- exactly the
 * version this step result's {@link ExecutionTestCase} already pinned at execution
 * creation time. A separate "ExecutionStepSnapshot" copying action/expectedResult
 * text would duplicate data that can never change out from under this reference.</p>
 */
@Entity
@Table(name = "execution_step_results")
public class ExecutionStepResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_test_case_id", nullable = false)
    private ExecutionTestCase executionTestCase;

    /** The immutable step this result is for -- see class javadoc for why this is
     * already a stable historical reference without needing its own snapshot copy. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false)
    private TestStep step;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionResultStatus status;

    @Column(name = "actual_result", columnDefinition = "TEXT")
    private String actualResult;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "failure_details", columnDefinition = "TEXT")
    private String failureDetails;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "executor")
    private String executor;

    protected ExecutionStepResult() {
        // for JPA
    }

    public static ExecutionStepResult createNotRun(ExecutionTestCase executionTestCase, TestStep step) {
        ExecutionStepResult result = new ExecutionStepResult();
        result.executionTestCase = executionTestCase;
        result.step = step;
        result.status = ExecutionResultStatus.NOT_RUN;
        return result;
    }

    /** Full apply, same shape as {@link ExecutionResult#apply} -- used by the
     * step-result PATCH/bulk-PATCH endpoints (a real, explicit edit of this step). */
    public void apply(ExecutionResultStatus status, String actualResult, String comment, String failureDetails,
                       String executor) {
        this.status = status;
        this.actualResult = actualResult;
        this.comment = comment;
        this.failureDetails = failureDetails;
        this.executor = executor;
        this.executedAt = status == ExecutionResultStatus.NOT_RUN ? null : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ExecutionTestCase getExecutionTestCase() {
        return executionTestCase;
    }

    public TestStep getStep() {
        return step;
    }

    public ExecutionResultStatus getStatus() {
        return status;
    }

    public String getActualResult() {
        return actualResult;
    }

    public String getComment() {
        return comment;
    }

    public String getFailureDetails() {
        return failureDetails;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public String getExecutor() {
        return executor;
    }
}
