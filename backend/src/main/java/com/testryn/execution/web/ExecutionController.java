package com.testryn.execution.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.testryn.common.error.BadRequestException;
import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.ExecutionStatus;
import com.testryn.execution.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.testryn.execution.web.ExecutionDtos.*;

@RestController
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @Operation(
            summary = "Start an execution from a test plan (a new iteration)",
            description = "Takes an immutable snapshot of the current version of every test case in the plan "
                    + "at this moment (title/description/preconditions/steps). Editing the plan or its test "
                    + "cases afterwards never changes this execution's snapshot. `name` is optional; if "
                    + "omitted, an iteration name/number is generated."
    )
    @PostMapping("/api/v1/test-plans/{testPlanId}/executions")
    public ResponseEntity<ExecutionResponse> createFromPlan(@PathVariable UUID testPlanId,
                                                              @RequestBody(required = false) CreateExecutionRequest request) {
        String name = request == null ? null : request.name();
        Execution execution = executionService.createFromPlan(testPlanId, name);
        return created(execution);
    }

    @PostMapping("/api/v1/projects/{projectKey}/executions")
    public ResponseEntity<ExecutionResponse> createAdhoc(@PathVariable String projectKey,
                                                           @Valid @RequestBody CreateAdhocExecutionRequest request) {
        Execution execution = executionService.createAdhoc(projectKey, request.name(), request.testCaseIds());
        return created(execution);
    }

    @GetMapping("/api/v1/projects/{projectKey}/executions")
    public List<ExecutionResponse> listForProject(@PathVariable String projectKey) {
        return executionService.findByProjectKey(projectKey).stream().map(ExecutionResponse::from).toList();
    }

    @GetMapping("/api/v1/test-plans/{testPlanId}/executions")
    public List<ExecutionResponse> listForTestPlan(@PathVariable UUID testPlanId) {
        return executionService.findByTestPlan(testPlanId).stream().map(ExecutionResponse::from).toList();
    }

    @GetMapping("/api/v1/executions/{id}")
    public ExecutionResponse getById(@PathVariable UUID id) {
        return ExecutionResponse.from(executionService.getById(id));
    }

    @PatchMapping("/api/v1/executions/{id}")
    public ExecutionResponse updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String statusValue = body.get("status");
        ExecutionStatus target = parseStatus(statusValue);
        Execution execution = switch (target) {
            case COMPLETED -> executionService.complete(id);
            case ABORTED -> executionService.abort(id);
            default -> throw new BadRequestException("Unsupported status transition: " + statusValue);
        };
        return ExecutionResponse.from(execution);
    }

    @Operation(
            summary = "Partially update an execution result (JSON Merge Patch, RFC 7396)",
            description = """
                    A field absent from the request body keeps its current value. A field present with JSON
                    null clears it (except `status`, which must never be null). A field present with a value
                    overwrites it. This lets a manual tester update only `comment` without erasing `durationMs`/
                    `executor` a CI pipeline had already reported, and vice versa -- see ADR 0006.
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = ExecutionResultPatchRequest.class))
    )
    @PatchMapping(value = "/api/v1/executions/{executionId}/results/{resultId}", consumes = "application/json")
    public ExecutionResultResponse updateResult(@PathVariable UUID executionId, @PathVariable UUID resultId,
                                                 @RequestBody JsonNode patch) {
        var result = executionService.patchResult(executionId, resultId, patch);
        return ExecutionResultResponse.from(result);
    }

    @Operation(
            summary = "Bulk-update multiple execution results in one atomic request (for CI pipelines)",
            description = """
                    Same JSON Merge Patch semantics as the single-result PATCH, applied to every entry in
                    `results`. Each entry identifies its target via `resultId` and/or `automationReference`
                    (at least one required; if both are given, they must resolve to the same result). An
                    `automationReference` is resolved only against test cases that are actually part of THIS
                    execution -- never globally, and never by creating a test case. Atomic: if any entry fails
                    validation (unknown reference, result not in this execution, duplicate reference within the
                    request, invalid status, negative duration, ...), the entire request is rejected and no
                    result is changed. The error response lists every problem found, not just the first, so a
                    CI caller can fix everything in one round trip -- see ADR 0010.
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = BulkResultUpdateRequest.class))
    )
    @PatchMapping(value = "/api/v1/executions/{executionId}/results", consumes = "application/json")
    public BulkResultUpdateResponse bulkUpdateResults(@PathVariable UUID executionId, @RequestBody JsonNode body) {
        var updated = executionService.bulkPatchResults(executionId, body);
        return BulkResultUpdateResponse.from(updated);
    }

    private ExecutionStatus parseStatus(String value) {
        if (value == null) {
            throw new BadRequestException("status is required");
        }
        try {
            return ExecutionStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown execution status: " + value);
        }
    }

    private ResponseEntity<ExecutionResponse> created(Execution execution) {
        return ResponseEntity.created(URI.create("/api/v1/executions/" + execution.getId()))
                .body(ExecutionResponse.from(execution));
    }
}
