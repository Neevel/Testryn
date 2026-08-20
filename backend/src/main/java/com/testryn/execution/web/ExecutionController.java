package com.testryn.execution.web;

import com.testryn.common.error.BadRequestException;
import com.testryn.execution.domain.Execution;
import com.testryn.execution.domain.ExecutionStatus;
import com.testryn.execution.service.ExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @PatchMapping("/api/v1/executions/{executionId}/results/{resultId}")
    public ExecutionResultResponse updateResult(@PathVariable UUID executionId, @PathVariable UUID resultId,
                                                 @Valid @RequestBody UpdateExecutionResultRequest request) {
        var result = executionService.updateResult(executionId, resultId, request.status(), request.comment(),
                request.durationMs(), request.executor(), request.actualResult(), request.failureDetails());
        return ExecutionResultResponse.from(result);
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
