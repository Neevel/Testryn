package com.testryn.testplan.service;

import com.testryn.common.error.NotFoundException;
import com.testryn.project.domain.Project;
import com.testryn.project.service.ProjectService;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.service.TestCaseService;
import com.testryn.testplan.domain.TestPlan;
import com.testryn.testplan.domain.TestPlanEntry;
import com.testryn.testplan.repository.TestPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TestPlanService {

    private final TestPlanRepository testPlanRepository;
    private final ProjectService projectService;
    private final TestCaseService testCaseService;

    public TestPlanService(TestPlanRepository testPlanRepository, ProjectService projectService,
                            TestCaseService testCaseService) {
        this.testPlanRepository = testPlanRepository;
        this.projectService = projectService;
        this.testCaseService = testCaseService;
    }

    public TestPlan create(String projectKey, String name, String description) {
        Project project = projectService.getByKey(projectKey);
        TestPlan plan = TestPlan.create(project, name, description);
        return testPlanRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public TestPlan getById(UUID id) {
        return testPlanRepository.findById(id).orElseThrow(() -> NotFoundException.of("TestPlan", id));
    }

    @Transactional(readOnly = true)
    public List<TestPlan> findByProjectKey(String projectKey) {
        Project project = projectService.getByKey(projectKey);
        return testPlanRepository.findByProjectIdOrderByNameAsc(project.getId());
    }

    public TestPlanEntry addTestCase(UUID testPlanId, UUID testCaseId) {
        TestPlan plan = getById(testPlanId);
        TestCase testCase = testCaseService.getById(testCaseId);
        return plan.addTestCase(testCase);
    }

    public void removeTestCase(UUID testPlanId, UUID testCaseId) {
        TestPlan plan = getById(testPlanId);
        plan.removeTestCase(testCaseId);
    }
}
