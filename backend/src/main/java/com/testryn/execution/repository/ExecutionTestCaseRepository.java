package com.testryn.execution.repository;

import com.testryn.execution.domain.ExecutionTestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExecutionTestCaseRepository extends JpaRepository<ExecutionTestCase, UUID> {

    /**
     * For each given test case, the single most recent {@link ExecutionTestCase} it
     * appears in (by its {@code Execution}'s {@code createdAt}) -- "most recently
     * added to an execution", regardless of whether that execution has actually been
     * run yet (Abschnitt 14: an execution that exists but is still {@code NOT_RUN} is
     * a real execution, not "no execution yet"). Backs the Jira Forge requirement
     * coverage view (ADR 0014) and is written as one query for the whole batch of
     * test case ids, not one query per test case (Abschnitt 22).
     *
     * <p>The correlated subquery is portable JPQL (no native/Postgres-only SQL) even
     * though this project only ever runs against Postgres -- simpler to keep that way
     * unless a real performance need says otherwise. In the pathological case of two
     * executions containing the same test case with the exact same {@code createdAt}
     * instant, more than one row could come back for that test case; callers should
     * treat the first one they see as authoritative and ignore the rest.
     */
    /**
     * Also fetch-joins {@code stepResults} and each one's {@code step} (Abschnitt
     * 24/25: the Jira Forge coverage view needs step-level detail for the latest
     * execution too, still in this same single query). Deliberately does NOT also
     * join {@code testCaseVersion.steps} -- that is a second Hibernate "bag"
     * collection alongside {@code stepResults} and would throw
     * MultipleBagFetchException; every step's action/expectedResult is available
     * via {@code stepResult.getStep()} instead (the same {@link
     * com.testryn.testcase.domain.TestStep} row, reached through the result rather
     * than through the version), so nothing is actually lost.
     */
    @Query("""
            select etc from ExecutionTestCase etc
            join fetch etc.execution e
            join fetch etc.testCase tc
            left join fetch etc.result r
            left join fetch etc.stepResults sr
            left join fetch sr.step
            where tc.id in :testCaseIds
            and e.createdAt = (
                select max(etc2.execution.createdAt)
                from ExecutionTestCase etc2
                where etc2.testCase.id = etc.testCase.id
            )
            """)
    List<ExecutionTestCase> findLatestByTestCaseIds(@Param("testCaseIds") List<UUID> testCaseIds);
}
