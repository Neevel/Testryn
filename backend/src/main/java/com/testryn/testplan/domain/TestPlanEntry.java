package com.testryn.testplan.domain;

import com.testryn.testcase.domain.TestCase;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * One test case included in a {@link TestPlan}. The plan (and its entries) is a
 * reusable, mutable definition — it is not the historical record of what was
 * actually executed. See ADR 0003.
 */
@Entity
@Table(name = "test_plan_entries")
public class TestPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_plan_id", nullable = false)
    private TestPlan testPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Column(name = "position", nullable = false)
    private int position;

    protected TestPlanEntry() {
        // for JPA
    }

    public TestPlanEntry(TestCase testCase, int position) {
        this.testCase = testCase;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public TestPlan getTestPlan() {
        return testPlan;
    }

    public void setTestPlan(TestPlan testPlan) {
        this.testPlan = testPlan;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public int getPosition() {
        return position;
    }
}
