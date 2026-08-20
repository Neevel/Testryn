package com.testryn.testcase.domain;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A single ordered step of a {@link TestCaseVersion}. Steps belong to exactly one
 * version and are immutable once persisted (a content change creates a new version
 * with new steps, see ADR 0002).
 */
@Entity
@Table(name = "test_steps")
public class TestStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_version_id", nullable = false)
    private TestCaseVersion testCaseVersion;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "action", nullable = false, columnDefinition = "TEXT")
    private String action;

    @Column(name = "expected_result", nullable = false, columnDefinition = "TEXT")
    private String expectedResult;

    protected TestStep() {
        // for JPA
    }

    public TestStep(int stepOrder, String action, String expectedResult) {
        this.stepOrder = stepOrder;
        this.action = action;
        this.expectedResult = expectedResult;
    }

    public UUID getId() {
        return id;
    }

    public TestCaseVersion getTestCaseVersion() {
        return testCaseVersion;
    }

    public void setTestCaseVersion(TestCaseVersion testCaseVersion) {
        this.testCaseVersion = testCaseVersion;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public String getAction() {
        return action;
    }

    public String getExpectedResult() {
        return expectedResult;
    }
}
