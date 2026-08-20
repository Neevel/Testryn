package com.testryn.execution.repository;

import com.testryn.execution.domain.ExecutionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionResultRepository extends JpaRepository<ExecutionResult, UUID> {

    Optional<ExecutionResult> findByIdAndExecutionTestCase_Execution_Id(UUID id, UUID executionId);
}
