package com.testryn.testplan.web;

import com.testryn.testplan.domain.TestPlan;
import com.testryn.testplan.service.TestPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.testryn.testplan.web.TestPlanDtos.*;

@RestController
public class TestPlanController {

    private final TestPlanService testPlanService;

    public TestPlanController(TestPlanService testPlanService) {
        this.testPlanService = testPlanService;
    }

    @PostMapping("/api/v1/projects/{projectKey}/test-plans")
    public ResponseEntity<TestPlanResponse> create(@PathVariable String projectKey,
                                                     @Valid @RequestBody CreateTestPlanRequest request) {
        TestPlan plan = testPlanService.create(projectKey, request.name(), request.description());
        return ResponseEntity.created(URI.create("/api/v1/test-plans/" + plan.getId()))
                .body(TestPlanResponse.from(plan));
    }

    @GetMapping("/api/v1/projects/{projectKey}/test-plans")
    public List<TestPlanResponse> listForProject(@PathVariable String projectKey) {
        return testPlanService.findByProjectKey(projectKey).stream().map(TestPlanResponse::from).toList();
    }

    @GetMapping("/api/v1/test-plans/{id}")
    public TestPlanResponse getById(@PathVariable UUID id) {
        return TestPlanResponse.from(testPlanService.getById(id));
    }

    @PostMapping("/api/v1/test-plans/{id}/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public TestPlanResponse addTestCase(@PathVariable UUID id, @Valid @RequestBody AddTestCaseRequest request) {
        testPlanService.addTestCase(id, request.testCaseId());
        return TestPlanResponse.from(testPlanService.getById(id));
    }

    @DeleteMapping("/api/v1/test-plans/{id}/test-cases/{testCaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTestCase(@PathVariable UUID id, @PathVariable UUID testCaseId) {
        testPlanService.removeTestCase(id, testCaseId);
    }
}
