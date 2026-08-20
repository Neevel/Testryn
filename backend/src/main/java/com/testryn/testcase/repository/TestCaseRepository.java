package com.testryn.testcase.repository;

import com.testryn.testcase.domain.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

    Optional<TestCase> findByHumanId(String humanId);

    List<TestCase> findByProjectIdOrderByHumanIdAsc(UUID projectId);

    @Query("select coalesce(max(tc.sequenceNumber), 0) from TestCase tc where tc.project.id = :projectId")
    int findMaxSequenceNumber(@Param("projectId") UUID projectId);
}
