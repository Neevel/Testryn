package com.testryn.testcase.repository;

import com.testryn.testcase.domain.TestCase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID>, JpaSpecificationExecutor<TestCase> {

    /**
     * Loads everything a {@code TestCaseResponse} needs to be built from (project,
     * current version, its steps, tags) in one query, so the mapping can safely
     * happen in the web layer after this (read-only) transaction has ended.
     */
    @EntityGraph(attributePaths = {"project", "currentVersion", "currentVersion.steps", "tags"})
    @Override
    Optional<TestCase> findById(UUID id);

    @EntityGraph(attributePaths = {"project", "currentVersion", "currentVersion.steps", "tags"})
    Optional<TestCase> findByHumanId(String humanId);

    @EntityGraph(attributePaths = {"project", "currentVersion", "currentVersion.steps", "tags"})
    List<TestCase> findByProjectIdOrderByHumanIdAsc(UUID projectId);

    /** Batch variant of {@link #findById(UUID)} -- same entity graph, one query for a
     * whole page of test cases (e.g. the Jira Forge requirement-coverage lookup)
     * instead of one call per id. Order is not guaranteed by the database; callers
     * that need a specific order re-sort the result themselves. */
    @EntityGraph(attributePaths = {"project", "currentVersion", "currentVersion.steps", "tags"})
    List<TestCase> findByIdIn(List<UUID> ids);

    @Query("select coalesce(max(tc.sequenceNumber), 0) from TestCase tc where tc.project.id = :projectId")
    int findMaxSequenceNumber(@Param("projectId") UUID projectId);

    /** Backs the project-scoped uniqueness check for {@code automationReference} (Abschnitt 10). */
    boolean existsByProject_IdAndAutomationReferenceAndIdNot(UUID projectId, String automationReference, UUID id);

    boolean existsByProject_IdAndAutomationReference(UUID projectId, String automationReference);
}
