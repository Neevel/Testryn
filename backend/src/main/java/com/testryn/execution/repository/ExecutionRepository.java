package com.testryn.execution.repository;

import com.testryn.execution.domain.Execution;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    /**
     * Loads project, testPlan, the testCases snapshot rows, each row's test case,
     * pinned version and result in one query. Deliberately does NOT include
     * {@code testCases.testCaseVersion.steps}: that is a second Hibernate "bag"
     * collection (List without an index column) at a deeper level, and fetching two
     * bags together throws MultipleBagFetchException. Callers that need step data
     * (the execution runner) must initialize it separately -- see
     * {@code ExecutionService#initializeStepsForResponse}.
     */
    @EntityGraph(attributePaths = {
            "project", "testPlan", "testCases", "testCases.testCase", "testCases.testCaseVersion", "testCases.result"
    })
    @Override
    Optional<Execution> findById(UUID id);

    @EntityGraph(attributePaths = {
            "project", "testPlan", "testCases", "testCases.testCase", "testCases.testCaseVersion", "testCases.result"
    })
    List<Execution> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @EntityGraph(attributePaths = {
            "project", "testPlan", "testCases", "testCases.testCase", "testCases.testCaseVersion", "testCases.result"
    })
    List<Execution> findByTestPlanIdOrderByIterationNumberDesc(UUID testPlanId);

    int countByTestPlanId(UUID testPlanId);

    int countByProjectIdAndTestPlanIsNull(UUID projectId);
}
