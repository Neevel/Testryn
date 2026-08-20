package com.testryn.execution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.common.error.BadRequestException;
import com.testryn.common.error.NotFoundException;
import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.Execution.SnapshotEntry;
import com.testryn.execution.domain.ExecutionResult;
import com.testryn.execution.repository.ExecutionRepository;
import com.testryn.execution.repository.ExecutionResultRepository;
import com.testryn.project.domain.Project;
import com.testryn.project.service.ProjectService;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.service.TestCaseService;
import com.testryn.testplan.domain.TestPlan;
import com.testryn.testplan.domain.TestPlanEntry;
import com.testryn.testplan.service.TestPlanService;
import org.hibernate.Hibernate;
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
    private final ObjectMapper objectMapper;

    public ExecutionService(ExecutionRepository executionRepository,
                             ExecutionResultRepository executionResultRepository,
                             TestPlanService testPlanService,
                             ProjectService projectService,
                             TestCaseService testCaseService,
                             ObjectMapper objectMapper) {
        this.executionRepository = executionRepository;
        this.executionResultRepository = executionResultRepository;
        this.testPlanService = testPlanService;
        this.projectService = projectService;
        this.testCaseService = testCaseService;
        this.objectMapper = objectMapper;
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
        execution = executionRepository.save(execution);
        initializeStepsForResponse(execution);
        return execution;
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
        execution = executionRepository.save(execution);
        initializeStepsForResponse(execution);
        return execution;
    }

    @Transactional(readOnly = true)
    public Execution getById(UUID id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Execution", id));
        initializeStepsForResponse(execution);
        return execution;
    }

    @Transactional(readOnly = true)
    public List<Execution> findByProjectKey(String projectKey) {
        Project project = projectService.getByKey(projectKey);
        List<Execution> executions = executionRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        executions.forEach(this::initializeStepsForResponse);
        return executions;
    }

    @Transactional(readOnly = true)
    public List<Execution> findByTestPlan(UUID testPlanId) {
        testPlanService.getById(testPlanId);
        List<Execution> executions = executionRepository.findByTestPlanIdOrderByIterationNumberDesc(testPlanId);
        executions.forEach(this::initializeStepsForResponse);
        return executions;
    }

    /**
     * JSON Merge Patch (RFC 7396) semantics -- see ADR 0006. A field absent from
     * {@code patch} keeps its current value; a field present with JSON {@code null}
     * clears it; a field present with a value overwrites it. {@code status} may not
     * be cleared (an ExecutionResult always has one).
     */
    public ExecutionResult patchResult(UUID executionId, UUID resultId, JsonNode patch) {
        Execution execution = getById(executionId);
        ExecutionResult result = executionResultRepository
                .findByIdAndExecutionTestCase_Execution_Id(resultId, executionId)
                .orElseThrow(() -> NotFoundException.of("ExecutionResult", resultId));

        ExecutionResultPatchState merged = mergePatch(result, patch);
        if (merged.getStatus() == null) {
            throw new BadRequestException("status must not be null");
        }

        result.apply(merged.getStatus(), merged.getComment(), merged.getDurationMs(), merged.getExecutor(),
                merged.getActualResult(), merged.getFailureDetails());
        execution.markRunningIfNeeded();
        return result;
    }

    private ExecutionResultPatchState mergePatch(ExecutionResult current, JsonNode patch) {
        ExecutionResultPatchState state = ExecutionResultPatchState.seedFrom(current);
        try {
            return objectMapper.readerForUpdating(state).readValue(patch);
        } catch (java.io.IOException e) {
            throw new BadRequestException("Invalid result patch: " + e.getMessage());
        }
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

    /**
     * {@code Execution.testCases} and {@code TestCaseVersion.steps} are both
     * Hibernate "bag" collections (unindexed {@code List}s), so they cannot be
     * fetch-joined together in the same query (MultipleBagFetchException) --
     * {@link com.testryn.execution.repository.ExecutionRepository} eager-fetches
     * everything except the steps; this initializes that one remaining collection
     * explicitly, still inside the current transaction, so the response mapper in
     * the web layer can safely read it afterwards.
     */
    private void initializeStepsForResponse(Execution execution) {
        for (var executionTestCase : execution.getTestCases()) {
            Hibernate.initialize(executionTestCase.getTestCaseVersion().getSteps());
        }
    }
}
