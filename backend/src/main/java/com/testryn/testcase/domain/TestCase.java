package com.testryn.testcase.domain;

import com.testryn.project.domain.Project;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The stable identity of a test case within a project. Content (title, description,
 * preconditions, steps) lives in {@link TestCaseVersion} snapshots, not here — see
 * ADR 0002. This entity only holds the identity and metadata that is NOT part of the
 * versioned content (status, priority, tags), plus a pointer to the current version.
 */
@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Human-readable, stable identifier, e.g. {@code BITLESS-TC-42}. */
    @Column(name = "human_id", nullable = false, unique = true, length = 50)
    private String humanId;

    /** Per-project sequence used to derive {@link #humanId}. */
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TestCaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TestCasePriority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private TestCaseVersion currentVersion;

    /**
     * Stable, machine-friendly identifier an external automated test uses to report
     * results back to this test case (e.g. {@code auth.login.valid}), independent of
     * any specific test framework -- see Abschnitt 8 and ADR 0009. Optional, unique
     * per project (enforced at both service and DB level), not part of the versioned
     * content: changing it does not create a new {@link TestCaseVersion}.
     */
    @Column(name = "automation_reference", length = 200)
    private String automationReference;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "test_case_tags", joinColumns = @JoinColumn(name = "test_case_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TestCase() {
        // for JPA
    }

    public static TestCase create(Project project, String humanId, int sequenceNumber,
                                   TestCasePriority priority, Set<String> tags, String automationReference) {
        TestCase testCase = new TestCase();
        testCase.project = project;
        testCase.humanId = humanId;
        testCase.sequenceNumber = sequenceNumber;
        testCase.status = TestCaseStatus.DRAFT;
        testCase.priority = priority;
        testCase.tags = tags == null ? new HashSet<>() : new HashSet<>(tags);
        testCase.automationReference = automationReference;
        Instant now = Instant.now();
        testCase.createdAt = now;
        testCase.updatedAt = now;
        return testCase;
    }

    public void assignVersion(TestCaseVersion version) {
        this.currentVersion = version;
        this.updatedAt = Instant.now();
    }

    public void updateMetadata(TestCaseStatus status, TestCasePriority priority, Set<String> tags,
                                String automationReference) {
        this.status = status;
        this.priority = priority;
        this.tags = tags == null ? new HashSet<>() : new HashSet<>(tags);
        this.automationReference = automationReference;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getHumanId() {
        return humanId;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public TestCaseStatus getStatus() {
        return status;
    }

    public TestCasePriority getPriority() {
        return priority;
    }

    public TestCaseVersion getCurrentVersion() {
        return currentVersion;
    }

    public Set<String> getTags() {
        return tags;
    }

    public String getAutomationReference() {
        return automationReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
