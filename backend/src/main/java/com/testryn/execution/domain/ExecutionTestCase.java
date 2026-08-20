package com.testryn.execution.domain;

import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCaseVersion;
import jakarta.persistence.*;

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
}
