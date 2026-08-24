package com.testryn.execution.repository;

import com.testryn.execution.domain.ExecutionStepResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionStepResultRepository extends JpaRepository<ExecutionStepResult, UUID> {

    Optional<ExecutionStepResult> findByIdAndExecutionTestCase_Execution_Id(UUID id, UUID executionId);
}
