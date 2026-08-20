package com.testryn.testcase.web;

import com.testryn.common.error.BadRequestException;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.service.TestCaseCommands;
import com.testryn.testcase.service.TestCaseExportService;
import com.testryn.testcase.service.TestCaseService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.testryn.testcase.web.TestCaseDtos.*;

@RestController
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final TestCaseExportService testCaseExportService;

    public TestCaseController(TestCaseService testCaseService, TestCaseExportService testCaseExportService) {
        this.testCaseService = testCaseService;
        this.testCaseExportService = testCaseExportService;
    }

    @PostMapping("/api/v1/projects/{projectKey}/test-cases")
    public ResponseEntity<TestCaseResponse> create(@PathVariable String projectKey,
                                                     @Valid @RequestBody CreateTestCaseRequest request) {
        var command = new TestCaseCommands.CreateTestCaseCommand(
                request.title(), request.description(), request.preconditions(),
                request.priority(), request.tags(), toStepCommands(request.steps()));
        TestCase created = testCaseService.create(projectKey, command);
        TestCaseResponse body = TestCaseResponse.from(created);
        return ResponseEntity.created(URI.create("/api/v1/test-cases/" + created.getId())).body(body);
    }

    @GetMapping("/api/v1/projects/{projectKey}/test-cases")
    public List<TestCaseResponse> listForProject(@PathVariable String projectKey) {
        return testCaseService.findByProjectKey(projectKey).stream().map(TestCaseResponse::from).toList();
    }

    @GetMapping("/api/v1/test-cases/{id}")
    public TestCaseResponse getById(@PathVariable UUID id) {
        return TestCaseResponse.from(testCaseService.getById(id));
    }

    @PutMapping("/api/v1/test-cases/{id}")
    public TestCaseResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTestCaseRequest request) {
        var command = new TestCaseCommands.UpdateTestCaseCommand(
                request.title(), request.description(), request.preconditions(),
                toStepCommands(request.steps()), request.status(), request.priority(), request.tags());
        return TestCaseResponse.from(testCaseService.update(id, command));
    }

    @GetMapping("/api/v1/test-cases/{id}/versions")
    public List<TestCaseVersionResponse> versions(@PathVariable UUID id) {
        return testCaseService.findVersions(id).stream().map(TestCaseVersionResponse::from).toList();
    }

    @GetMapping("/api/v1/projects/{projectKey}/test-cases/export")
    public ResponseEntity<String> export(@PathVariable String projectKey,
                                          @RequestParam(defaultValue = "json") String format) {
        String content;
        MediaType mediaType;
        String extension;
        switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> {
                content = testCaseExportService.exportJson(projectKey);
                mediaType = MediaType.APPLICATION_JSON;
                extension = "json";
            }
            case "csv" -> {
                content = testCaseExportService.exportCsv(projectKey);
                mediaType = MediaType.parseMediaType("text/csv");
                extension = "csv";
            }
            case "markdown", "md" -> {
                content = testCaseExportService.exportMarkdown(projectKey);
                mediaType = MediaType.parseMediaType("text/markdown");
                extension = "md";
            }
            default -> throw new BadRequestException("Unsupported export format: " + format);
        }
        String filename = projectKey + "-test-cases." + extension;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    private List<TestCaseCommands.StepCommand> toStepCommands(List<StepRequest> steps) {
        return steps.stream().map(s -> new TestCaseCommands.StepCommand(s.action(), s.expectedResult())).toList();
    }
}
