package com.testryn.testplan.domain;

import com.testryn.project.domain.Project;
import com.testryn.testcase.domain.TestCase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable, mutable collection of test cases (e.g. "Regression Login &amp;
 * Account"). Executing a plan creates an immutable snapshot ({@code Execution} +
 * {@code ExecutionTestCase}) — the plan itself is never a historical record, see ADR
 * 0003.
 */
@Entity
@Table(name = "test_plans")
public class TestPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "testPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TestPlanEntry> entries = new ArrayList<>();

    protected TestPlan() {
        // for JPA
    }

    public static TestPlan create(Project project, String name, String description) {
        TestPlan plan = new TestPlan();
        plan.project = project;
        plan.name = name;
        plan.description = description;
        Instant now = Instant.now();
        plan.createdAt = now;
        plan.updatedAt = now;
        return plan;
    }

    public TestPlanEntry addTestCase(TestCase testCase) {
        boolean alreadyPresent = entries.stream().anyMatch(e -> e.getTestCase().getId().equals(testCase.getId()));
        if (alreadyPresent) {
            throw new IllegalStateException("Test case " + testCase.getHumanId() + " is already part of this plan");
        }
        int nextPosition = entries.size() + 1;
        TestPlanEntry entry = new TestPlanEntry(testCase, nextPosition);
        entry.setTestPlan(this);
        entries.add(entry);
        this.updatedAt = Instant.now();
        return entry;
    }

    public void removeTestCase(UUID testCaseId) {
        boolean removed = entries.removeIf(e -> e.getTestCase().getId().equals(testCaseId));
        if (!removed) {
            throw new IllegalStateException("Test case " + testCaseId + " is not part of this plan");
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<TestPlanEntry> getEntries() {
        return entries;
    }
}
