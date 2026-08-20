package com.testryn.execution.repository;

import com.testryn.execution.domain.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    List<Execution> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Execution> findByTestPlanIdOrderByIterationNumberDesc(UUID testPlanId);

    int countByTestPlanId(UUID testPlanId);

    int countByProjectIdAndTestPlanIsNull(UUID projectId);
}
