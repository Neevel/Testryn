package com.testryn.execution.service;

import com.testryn.common.error.BadRequestException;
import com.testryn.common.error.NotFoundException;
import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.Execution.SnapshotEntry;
import com.testryn.execution.domain.ExecutionResult;
import com.testryn.execution.domain.ExecutionResultStatus;
import com.testryn.execution.repository.ExecutionRepository;
import com.testryn.execution.repository.ExecutionResultRepository;
import com.testryn.project.domain.Project;
import com.testryn.project.service.ProjectService;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.service.TestCaseService;
import com.testryn.testplan.domain.TestPlan;
import com.testryn.testplan.domain.TestPlanEntry;
import com.testryn.testplan.service.TestPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final ExecutionResultRepository executionResultRepository;
    private final TestPlanService testPlanService;
    private final ProjectService projectService;
    private final TestCaseService testCaseService;

    public ExecutionService(ExecutionRepository executionRepository,
                             ExecutionResultRepository executionResultRepository,
                             TestPlanService testPlanService,
                             ProjectService projectService,
                             TestCaseService testCaseService) {
        this.executionRepository = executionRepository;
        this.executionResultRepository = executionResultRepository;
        this.testPlanService = testPlanService;
        this.projectService = projectService;
        this.testCaseService = testCaseService;
    }

    /** Creates a new, independent iteration of {@code testPlanId} (ADR 0003). */
    public Execution createFromPlan(UUID testPlanId, String name) {
        TestPlan plan = testPlanService.getById(testPlanId);
        if (plan.getEntries().isEmpty()) {
            throw new BadRequestException("Cannot create an execution from an empty test plan");
        }
        List<SnapshotEntry> snapshot = new ArrayList<>();
        for (TestPlanEntry entry : plan.getEntries()) {
            TestCase testCase = entry.getTestCase();
            requireVersioned(testCase);
            snapshot.add(new SnapshotEntry(testCase, testCase.getCurrentVersion()));
        }
        int iterationNumber = executionRepository.countByTestPlanId(plan.getId()) + 1;
        String resolvedName = (name == null || name.isBlank())
                ? plan.getName() + " – Iteration " + iterationNumber
                : name;
        Execution execution = Execution.createSnapshot(plan.getProject(), plan, iterationNumber, resolvedName, snapshot);
        return executionRepository.save(execution);
    }

    /** Creates an ad-hoc execution (no test plan) from an explicit set of test cases. */
    public Execution createAdhoc(String projectKey, String name, List<UUID> testCaseIds) {
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            throw new BadRequestException("An execution requires at least one test case");
        }
        Project project = projectService.getByKey(projectKey);
        List<SnapshotEntry> snapshot = new ArrayList<>();
        for (UUID testCaseId : testCaseIds) {
            TestCase testCase = testCaseService.getById(testCaseId);
            requireVersioned(testCase);
            snapshot.add(new SnapshotEntry(testCase, testCase.getCurrentVersion()));
        }
        int iterationNumber = executionRepository.countByProjectIdAndTestPlanIsNull(project.getId()) + 1;
        Execution execution = Execution.createSnapshot(project, null, iterationNumber, name, snapshot);
        return executionRepository.save(execution);
    }

    @Transactional(readOnly = true)
    public Execution getById(UUID id) {
        return executionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Execution", id));
    }

    @Transactional(readOnly = true)
    public List<Execution> findByProjectKey(String projectKey) {
        Project project = projectService.getByKey(projectKey);
        return executionRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
    }

    @Transactional(readOnly = true)
    public List<Execution> findByTestPlan(UUID testPlanId) {
        testPlanService.getById(testPlanId);
        return executionRepository.findByTestPlanIdOrderByIterationNumberDesc(testPlanId);
    }

    public ExecutionResult updateResult(UUID executionId, UUID resultId, ExecutionResultStatus status,
                                         String comment, Long durationMs, String executor, String failureDetails) {
        Execution execution = getById(executionId);
        ExecutionResult result = executionResultRepository
                .findByIdAndExecutionTestCase_Execution_Id(resultId, executionId)
                .orElseThrow(() -> NotFoundException.of("ExecutionResult", resultId));

        result.apply(status, comment, durationMs, executor, failureDetails);
        execution.markRunningIfNeeded();
        return result;
    }

    public Execution complete(UUID executionId) {
        Execution execution = getById(executionId);
        execution.complete();
        return execution;
    }

    public Execution abort(UUID executionId) {
        Execution execution = getById(executionId);
        execution.abort();
        return execution;
    }

    private void requireVersioned(TestCase testCase) {
        if (testCase.getCurrentVersion() == null) {
            throw new BadRequestException("Test case " + testCase.getHumanId() + " has no version yet");
        }
    }
}
