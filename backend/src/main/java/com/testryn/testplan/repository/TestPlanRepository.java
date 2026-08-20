package com.testryn.testplan.repository;

import com.testryn.testplan.domain.TestPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestPlanRepository extends JpaRepository<TestPlan, UUID> {

    List<TestPlan> findByProjectIdOrderByNameAsc(UUID projectId);
}
