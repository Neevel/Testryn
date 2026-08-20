package com.testryn.testcase.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An immutable, fully self-contained content snapshot of a {@link TestCase}: title,
 * description, preconditions and steps. Once created, a version and its steps are
 * never modified or deleted (ADR 0002) — Executions reference a specific version to
 * stay historically stable regardless of later Test Case edits.
 */
@Entity
@Table(name = "test_case_versions")
public class TestCaseVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "preconditions", columnDefinition = "TEXT")
    private String preconditions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "testCaseVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<TestStep> steps = new ArrayList<>();

    protected TestCaseVersion() {
        // for JPA
    }

    public static TestCaseVersion create(TestCase testCase, int versionNumber, String title,
                                          String description, String preconditions,
                                          List<TestStep> steps) {
        TestCaseVersion version = new TestCaseVersion();
        version.testCase = testCase;
        version.versionNumber = versionNumber;
        version.title = title;
        version.description = description;
        version.preconditions = preconditions;
        version.createdAt = Instant.now();
        for (TestStep step : steps) {
            step.setTestCaseVersion(version);
            version.steps.add(step);
        }
        return version;
    }

    public UUID getId() {
        return id;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPreconditions() {
        return preconditions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<TestStep> getSteps() {
        return steps;
    }
}
