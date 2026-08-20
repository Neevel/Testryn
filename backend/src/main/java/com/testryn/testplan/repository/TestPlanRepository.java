package com.testryn.testplan.repository;

import com.testryn.testplan.domain.TestPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestPlanRepository extends JpaRepository<TestPlan, UUID> {

    /**
     * Loads everything a {@code TestPlanResponse} needs (project, entries, each
     * entry's test case and its current version) in one query. Only a single
     * "bag" collection (entries) is fetched, so this is safe from
     * MultipleBagFetchException.
     */
    @EntityGraph(attributePaths = {"project", "entries", "entries.testCase", "entries.testCase.currentVersion"})
    @Override
    Optional<TestPlan> findById(UUID id);

    @EntityGraph(attributePaths = {"project", "entries", "entries.testCase", "entries.testCase.currentVersion"})
    List<TestPlan> findByProjectIdOrderByNameAsc(UUID projectId);
}
