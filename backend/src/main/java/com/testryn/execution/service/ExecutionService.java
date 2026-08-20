package com.testryn.execution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testryn.common.error.ApiError;
import com.testryn.common.error.BadRequestException;
import com.testryn.common.error.BulkValidationException;
import com.testryn.common.error.NotFoundException;
import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.Execution.SnapshotEntry;
import com.testryn.execution.domain.ExecutionResult;
import com.testryn.execution.domain.ExecutionTestCase;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        if (merged.getDurationMs() != null && merged.getDurationMs() < 0) {
            throw new BadRequestException("durationMs must not be negative");
        }

        result.apply(merged.getStatus(), merged.getComment(), merged.getDurationMs(), merged.getExecutor(),
                merged.getActualResult(), merged.getFailureDetails());
        execution.markRunningIfNeeded();
        return result;
    }

    /**
     * Bulk-updates several results of one execution atomically (Abschnitt 4-7): every
     * entry in {@code body.results} follows the same JSON Merge Patch semantics as
     * {@link #patchResult}, and each identifies its target via {@code resultId}
     * and/or {@code automationReference} -- resolved only against test cases that are
     * actually part of THIS execution, never globally, never creating one. If ANY
     * entry fails validation (bad reference, not in this execution, duplicate
     * reference, invalid status, negative duration, ...), the whole request is
     * rejected with every violation listed and NOTHING is changed -- validation runs
     * to completion before any entity is mutated, so there is no window in which a
     * later failure could leave earlier entries applied (ADR 0010).
     */
    public List<ExecutionTestCase> bulkPatchResults(UUID executionId, JsonNode body) {
        Execution execution = getById(executionId);

        JsonNode resultsNode = body == null ? null : body.get("results");
        if (resultsNode == null || !resultsNode.isArray() || resultsNode.isEmpty()) {
            throw new BadRequestException("Bulk result update requires a non-empty 'results' array");
        }

        List<ApiError.FieldViolation> violations = new ArrayList<>();
        List<BulkItem> items = parseBulkItems(resultsNode, violations);
        List<ExecutionTestCase> resolved = resolveBulkItems(execution, items, violations);
        List<ExecutionResultPatchState> mergedStates = validateBulkMerges(items, resolved, violations);

        if (!violations.isEmpty()) {
            throw new BulkValidationException(
                    "Bulk result update request is invalid: " + violations.size()
                            + " of " + items.size() + " entries have a problem",
                    violations);
        }

        for (int i = 0; i < resolved.size(); i++) {
            ExecutionResultPatchState state = mergedStates.get(i);
            resolved.get(i).getResult().apply(state.getStatus(), state.getComment(), state.getDurationMs(),
                    state.getExecutor(), state.getActualResult(), state.getFailureDetails());
        }
        execution.markRunningIfNeeded();
        return resolved;
    }

    private record BulkItem(int index, UUID resultId, String automationReference, ObjectNode patchNode) {
        String label() {
            if (automationReference != null) {
                return automationReference;
            }
            if (resultId != null) {
                return resultId.toString();
            }
            return "results[" + index + "]";
        }
    }

    private List<BulkItem> parseBulkItems(JsonNode resultsNode, List<ApiError.FieldViolation> violations) {
        List<BulkItem> items = new ArrayList<>();
        int index = 0;
        for (JsonNode itemNode : resultsNode) {
            String positionalLabel = "results[" + index + "]";
            if (!itemNode.isObject()) {
                violations.add(new ApiError.FieldViolation(positionalLabel, "must be a JSON object"));
                index++;
                continue;
            }
            ObjectNode obj = ((ObjectNode) itemNode).deepCopy();
            JsonNode resultIdNode = obj.remove("resultId");
            JsonNode automationRefNode = obj.remove("automationReference");

            UUID resultId = null;
            if (resultIdNode != null && !resultIdNode.isNull()) {
                String text = resultIdNode.asText();
                try {
                    resultId = UUID.fromString(text);
                } catch (IllegalArgumentException e) {
                    violations.add(new ApiError.FieldViolation(positionalLabel, "resultId is not a valid UUID: " + text));
                }
            }
            String automationReference = (automationRefNode != null && !automationRefNode.isNull()
                    && !automationRefNode.asText().isBlank()) ? automationRefNode.asText() : null;

            if (resultId == null && automationReference == null) {
                violations.add(new ApiError.FieldViolation(positionalLabel,
                        "either resultId or automationReference is required"));
            }
            items.add(new BulkItem(index, resultId, automationReference, obj));
            index++;
        }
        return items;
    }

    /** Resolves each item to an {@link ExecutionTestCase} within {@code execution}
     * only -- never a repository-wide lookup, so a resultId belonging to a different
     * execution is correctly rejected rather than silently updated. */
    private List<ExecutionTestCase> resolveBulkItems(Execution execution, List<BulkItem> items,
                                                       List<ApiError.FieldViolation> violations) {
        List<ExecutionTestCase> resolved = new ArrayList<>();
        Set<UUID> claimedResultIds = new HashSet<>();
        for (BulkItem item : items) {
            ExecutionTestCase byId = item.resultId() == null ? null
                    : findByResultId(execution, item.resultId());
            ExecutionTestCase byRef = item.automationReference() == null ? null
                    : findByAutomationReference(execution, item.automationReference());

            if (item.resultId() != null && byId == null) {
                violations.add(new ApiError.FieldViolation(item.label(),
                        "no result with resultId " + item.resultId() + " exists in execution " + execution.getId()));
            }
            if (item.automationReference() != null && byRef == null) {
                violations.add(new ApiError.FieldViolation(item.label(),
                        "no test case with automationReference '" + item.automationReference()
                                + "' is part of execution " + execution.getId()));
            }
            if (byId != null && byRef != null && !byId.getResult().getId().equals(byRef.getResult().getId())) {
                violations.add(new ApiError.FieldViolation(item.label(),
                        "resultId and automationReference resolve to different results"));
            }

            ExecutionTestCase target = byId != null ? byId : byRef;
            if (target != null && !claimedResultIds.add(target.getResult().getId())) {
                violations.add(new ApiError.FieldViolation(item.label(),
                        "duplicate reference to the same result within this request"));
            }
            resolved.add(target);
        }
        return resolved;
    }

    /** Merges every successfully-resolved item and validates the result, WITHOUT
     * applying anything yet -- so a late failure never leaves earlier entries
     * half-applied. */
    private List<ExecutionResultPatchState> validateBulkMerges(List<BulkItem> items, List<ExecutionTestCase> resolved,
                                                                 List<ApiError.FieldViolation> violations) {
        List<ExecutionResultPatchState> states = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ExecutionTestCase target = resolved.get(i);
            if (target == null) {
                states.add(null);
                continue;
            }
            BulkItem item = items.get(i);
            try {
                ExecutionResultPatchState state = mergePatch(target.getResult(), item.patchNode());
                if (state.getStatus() == null) {
                    violations.add(new ApiError.FieldViolation(item.label(), "status must not be null"));
                }
                if (state.getDurationMs() != null && state.getDurationMs() < 0) {
                    violations.add(new ApiError.FieldViolation(item.label(), "durationMs must not be negative"));
                }
                states.add(state);
            } catch (BadRequestException e) {
                violations.add(new ApiError.FieldViolation(item.label(), e.getMessage()));
                states.add(null);
            }
        }
        return states;
    }

    private ExecutionTestCase findByResultId(Execution execution, UUID resultId) {
        for (ExecutionTestCase etc : execution.getTestCases()) {
            if (etc.getResult() != null && resultId.equals(etc.getResult().getId())) {
                return etc;
            }
        }
        return null;
    }

    private ExecutionTestCase findByAutomationReference(Execution execution, String automationReference) {
        for (ExecutionTestCase etc : execution.getTestCases()) {
            if (automationReference.equals(etc.getTestCase().getAutomationReference())) {
                return etc;
            }
        }
        return null;
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
