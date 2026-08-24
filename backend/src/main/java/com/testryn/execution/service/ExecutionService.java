package com.testryn.execution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testryn.common.error.ApiError;
import com.testryn.common.error.BadRequestException;
import com.testryn.common.error.BulkValidationException;
import com.testryn.common.error.ConflictException;
import com.testryn.common.error.NotFoundException;
import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.Execution.SnapshotEntry;
import com.testryn.execution.domain.ExecutionResult;
import com.testryn.execution.domain.ExecutionResultStatus;
import com.testryn.execution.domain.ExecutionStatus;
import com.testryn.execution.domain.ExecutionStepResult;
import com.testryn.execution.domain.ExecutionTestCase;
import com.testryn.execution.repository.ExecutionRepository;
import com.testryn.execution.repository.ExecutionResultRepository;
import com.testryn.execution.repository.ExecutionStepResultRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final ExecutionResultRepository executionResultRepository;
    private final ExecutionStepResultRepository executionStepResultRepository;
    private final TestPlanService testPlanService;
    private final ProjectService projectService;
    private final TestCaseService testCaseService;
    private final ObjectMapper objectMapper;

    public ExecutionService(ExecutionRepository executionRepository,
                             ExecutionResultRepository executionResultRepository,
                             ExecutionStepResultRepository executionStepResultRepository,
                             TestPlanService testPlanService,
                             ProjectService projectService,
                             TestCaseService testCaseService,
                             ObjectMapper objectMapper) {
        this.executionRepository = executionRepository;
        this.executionResultRepository = executionResultRepository;
        this.executionStepResultRepository = executionStepResultRepository;
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
        requireWritable(execution);
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
        requireWritable(execution);

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

    /**
     * Same JSON Merge Patch semantics as {@link #patchResult}, one level deeper --
     * see {@link ExecutionStepResultPatchState}. After applying, the owning test
     * case's testcase-level status is re-derived from ALL of its step results (ADR
     * 0015, Abschnitt 9) and applied via {@link ExecutionResult#deriveStatus}, which
     * touches only {@code status}/{@code executedAt} -- any comment or CI-reported
     * duration already on that testcase-level result survives untouched.
     */
    public ExecutionStepResult patchStepResult(UUID executionId, UUID stepResultId, JsonNode patch) {
        Execution execution = getById(executionId);
        requireWritable(execution);
        ExecutionStepResult stepResult = executionStepResultRepository
                .findByIdAndExecutionTestCase_Execution_Id(stepResultId, executionId)
                .orElseThrow(() -> NotFoundException.of("ExecutionStepResult", stepResultId));

        ExecutionStepResultPatchState merged = mergeStepPatch(stepResult, patch);
        if (merged.getStatus() == null) {
            throw new BadRequestException("status must not be null");
        }
        stepResult.apply(merged.getStatus(), merged.getActualResult(), merged.getComment(),
                merged.getFailureDetails(), merged.getExecutor());
        execution.markRunningIfNeeded();
        deriveAndApplyTestCaseStatus(stepResult.getExecutionTestCase());
        return stepResult;
    }

    /**
     * Atomic bulk update of several step results in one request (Abschnitt 19):
     * every entry identifies its target by {@code stepResultId} alone -- the id
     * already returned by every read of an execution's step results, so there is
     * exactly one way to address a given row, not two (contrast with the
     * testcase-level bulk endpoint's resultId-or-automationReference duality, which
     * exists there only because automated callers may not know the internal id
     * yet). Same all-or-nothing validation-before-mutation shape as
     * {@link #bulkPatchResults} (ADR 0010): nothing is written if any entry fails.
     * Every distinct test case touched by at least one entry has its testcase-level
     * status re-derived exactly once after all step writes are applied.
     */
    public List<ExecutionStepResult> bulkPatchStepResults(UUID executionId, JsonNode body) {
        Execution execution = getById(executionId);
        requireWritable(execution);

        JsonNode resultsNode = body == null ? null : body.get("results");
        if (resultsNode == null || !resultsNode.isArray() || resultsNode.isEmpty()) {
            throw new BadRequestException("Bulk step result update requires a non-empty 'results' array");
        }

        List<ApiError.FieldViolation> violations = new ArrayList<>();
        List<StepBulkItem> items = parseStepBulkItems(resultsNode, violations);
        List<ExecutionStepResult> resolved = resolveStepBulkItems(execution, items, violations);
        List<ExecutionStepResultPatchState> mergedStates = validateStepBulkMerges(items, resolved, violations);

        if (!violations.isEmpty()) {
            throw new BulkValidationException(
                    "Bulk step result update request is invalid: " + violations.size()
                            + " of " + items.size() + " entries have a problem", violations);
        }

        Set<ExecutionTestCase> touchedTestCases = new LinkedHashSet<>();
        for (int i = 0; i < resolved.size(); i++) {
            ExecutionStepResultPatchState state = mergedStates.get(i);
            ExecutionStepResult stepResult = resolved.get(i);
            stepResult.apply(state.getStatus(), state.getActualResult(), state.getComment(),
                    state.getFailureDetails(), state.getExecutor());
            touchedTestCases.add(stepResult.getExecutionTestCase());
        }
        for (ExecutionTestCase touched : touchedTestCases) {
            deriveAndApplyTestCaseStatus(touched);
        }
        execution.markRunningIfNeeded();
        return resolved;
    }

    private void deriveAndApplyTestCaseStatus(ExecutionTestCase executionTestCase) {
        ExecutionResultStatus derived = executionTestCase.deriveStatusFromSteps();
        executionTestCase.getResult().deriveStatus(derived);
    }

    private record StepBulkItem(int index, UUID stepResultId, ObjectNode patchNode) {
        String label() {
            return stepResultId != null ? stepResultId.toString() : "results[" + index + "]";
        }
    }

    private List<StepBulkItem> parseStepBulkItems(JsonNode resultsNode, List<ApiError.FieldViolation> violations) {
        List<StepBulkItem> items = new ArrayList<>();
        int index = 0;
        for (JsonNode itemNode : resultsNode) {
            String positionalLabel = "results[" + index + "]";
            if (!itemNode.isObject()) {
                violations.add(new ApiError.FieldViolation(positionalLabel, "must be a JSON object"));
                index++;
                continue;
            }
            ObjectNode obj = ((ObjectNode) itemNode).deepCopy();
            JsonNode stepResultIdNode = obj.remove("stepResultId");

            UUID stepResultId = null;
            if (stepResultIdNode == null || stepResultIdNode.isNull() || stepResultIdNode.asText().isBlank()) {
                violations.add(new ApiError.FieldViolation(positionalLabel, "stepResultId is required"));
            } else {
                String text = stepResultIdNode.asText();
                try {
                    stepResultId = UUID.fromString(text);
                } catch (IllegalArgumentException e) {
                    violations.add(new ApiError.FieldViolation(positionalLabel, "stepResultId is not a valid UUID: " + text));
                }
            }
            items.add(new StepBulkItem(index, stepResultId, obj));
            index++;
        }
        return items;
    }

    private List<ExecutionStepResult> resolveStepBulkItems(Execution execution, List<StepBulkItem> items,
                                                             List<ApiError.FieldViolation> violations) {
        List<ExecutionStepResult> resolved = new ArrayList<>();
        Set<UUID> claimed = new HashSet<>();
        for (StepBulkItem item : items) {
            if (item.stepResultId() == null) {
                resolved.add(null);
                continue;
            }
            ExecutionStepResult found = findStepResultById(execution, item.stepResultId());
            if (found == null) {
                violations.add(new ApiError.FieldViolation(item.label(),
                        "no step result with id " + item.stepResultId() + " exists in execution " + execution.getId()));
            } else if (!claimed.add(item.stepResultId())) {
                violations.add(new ApiError.FieldViolation(item.label(),
                        "duplicate reference to the same step result within this request"));
            }
            resolved.add(found);
        }
        return resolved;
    }

    private List<ExecutionStepResultPatchState> validateStepBulkMerges(List<StepBulkItem> items,
                                                                         List<ExecutionStepResult> resolved,
                                                                         List<ApiError.FieldViolation> violations) {
        List<ExecutionStepResultPatchState> states = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ExecutionStepResult target = resolved.get(i);
            if (target == null) {
                states.add(null);
                continue;
            }
            StepBulkItem item = items.get(i);
            try {
                ExecutionStepResultPatchState state = mergeStepPatch(target, item.patchNode());
                if (state.getStatus() == null) {
                    violations.add(new ApiError.FieldViolation(item.label(), "status must not be null"));
                }
                states.add(state);
            } catch (BadRequestException e) {
                violations.add(new ApiError.FieldViolation(item.label(), e.getMessage()));
                states.add(null);
            }
        }
        return states;
    }

    private ExecutionStepResult findStepResultById(Execution execution, UUID stepResultId) {
        for (ExecutionTestCase etc : execution.getTestCases()) {
            for (ExecutionStepResult stepResult : etc.getStepResults()) {
                if (stepResultId.equals(stepResult.getId())) {
                    return stepResult;
                }
            }
        }
        return null;
    }

    private ExecutionStepResultPatchState mergeStepPatch(ExecutionStepResult current, JsonNode patch) {
        ExecutionStepResultPatchState state = ExecutionStepResultPatchState.seedFrom(current);
        try {
            return objectMapper.readerForUpdating(state).readValue(patch);
        } catch (java.io.IOException e) {
            throw new BadRequestException("Invalid step result patch: " + e.getMessage());
        }
    }

    /** Abschnitt 50: RUNNING (and CREATED, which a first write transitions to
     * RUNNING anyway via {@link Execution#markRunningIfNeeded}) accept result
     * writes; COMPLETED/ABORTED do not -- for testcase-level results, step-level
     * results, and both of their bulk variants alike. The CI publisher needs no
     * separate change: it calls this same bulk testcase-level endpoint. */
    private void requireWritable(Execution execution) {
        ExecutionStatus status = execution.getStatus();
        if (status == ExecutionStatus.COMPLETED || status == ExecutionStatus.ABORTED) {
            throw new ConflictException(
                    "Execution " + execution.getId() + " is " + status + " and no longer accepts result writes");
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
     * {@code Execution.testCases}, {@code TestCaseVersion.steps}, and now
     * {@code ExecutionTestCase.stepResults} are all Hibernate "bag" collections
     * (unindexed {@code List}s), so at most one of them can be fetch-joined in the
     * same query (MultipleBagFetchException) --
     * {@link com.testryn.execution.repository.ExecutionRepository} eager-fetches
     * {@code testCases} itself; this initializes the remaining two explicitly,
     * still inside the current transaction (response mapping happens in the web
     * layer, after the transaction/session has already closed, {@code open-in-view}
     * is off). Each step result's own {@code step} association is also a lazy
     * proxy and is initialized here too, so its action/expectedResult/stepOrder
     * stay readable afterwards.
     */
    private void initializeStepsForResponse(Execution execution) {
        for (var executionTestCase : execution.getTestCases()) {
            Hibernate.initialize(executionTestCase.getTestCaseVersion().getSteps());
            Hibernate.initialize(executionTestCase.getStepResults());
            for (var stepResult : executionTestCase.getStepResults()) {
                Hibernate.initialize(stepResult.getStep());
            }
        }
    }
}
