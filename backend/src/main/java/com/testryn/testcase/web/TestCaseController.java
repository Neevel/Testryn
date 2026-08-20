package com.testryn.testcase.web;

import com.testryn.common.error.BadRequestException;
import com.testryn.common.web.PageResponse;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCasePriority;
import com.testryn.testcase.domain.TestCaseStatus;
import com.testryn.testcase.service.TestCaseCommands;
import com.testryn.testcase.service.TestCaseExportService;
import com.testryn.testcase.service.TestCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @Operation(
            summary = "Create a test case",
            description = """
                    Creates a new test case in DRAFT status with an initial version (v1) built from the given
                    title/description/preconditions/steps. Before creating, consider searching first
                    (`GET /api/v1/projects/{projectKey}/test-cases?query=...`) to avoid near-duplicates.
                    """
    )
    @PostMapping("/api/v1/projects/{projectKey}/test-cases")
    public ResponseEntity<TestCaseResponse> create(@PathVariable String projectKey,
                                                     @Valid @RequestBody CreateTestCaseRequest request) {
        var command = new TestCaseCommands.CreateTestCaseCommand(
                request.title(), request.description(), request.preconditions(),
                request.priority(), request.tags(), toStepCommands(request.steps()), request.automationReference());
        TestCase created = testCaseService.create(projectKey, command);
        TestCaseResponse body = TestCaseResponse.from(created);
        return ResponseEntity.created(URI.create("/api/v1/test-cases/" + created.getId())).body(body);
    }

    @Operation(
            summary = "Search/list test cases in a project (paginated)",
            description = """
                    All filters are optional and combine with AND: `query` matches the human-readable ID or
                    current title (case-insensitive substring), `tag` an exact tag, `requirementKey` an exact
                    (case-insensitive) linked Jira/requirement key, `status`/`priority` exact match,
                    `automationReference` an exact (case-sensitive) match -- no fuzzy matching for it, since it
                    is a machine identifier, not free text. Useful for an AI/CI agent checking for
                    near-duplicate test cases before creating a new one (`?query=login`) or resolving which
                    test case an automated test result belongs to (`?automationReference=auth.login.valid`).
                    Response is a page envelope, not a bare array -- see PageResponse.
                    """
    )
    @GetMapping("/api/v1/projects/{projectKey}/test-cases")
    public PageResponse<TestCaseResponse> listForProject(
            @PathVariable String projectKey,
            @Parameter(description = "Free-text match against human-readable ID or current title") @RequestParam(required = false) String query,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String requirementKey,
            @RequestParam(required = false) TestCaseStatus status,
            @RequestParam(required = false) TestCasePriority priority,
            @Parameter(description = "Exact, case-sensitive match against automationReference") @RequestParam(required = false) String automationReference,
            @Parameter(description = "0-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, max 100") @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by("humanId").ascending());
        Page<TestCase> result = testCaseService.search(
                projectKey, query, tag, requirementKey, status, priority, automationReference, pageable);
        return PageResponse.from(result, result.getContent().stream().map(TestCaseResponse::from).toList());
    }

    @GetMapping("/api/v1/test-cases/{id}")
    public TestCaseResponse getById(@PathVariable UUID id) {
        return TestCaseResponse.from(testCaseService.getById(id));
    }

    @PutMapping("/api/v1/test-cases/{id}")
    public TestCaseResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTestCaseRequest request) {
        var command = new TestCaseCommands.UpdateTestCaseCommand(
                request.title(), request.description(), request.preconditions(),
                toStepCommands(request.steps()), request.status(), request.priority(), request.tags(),
                request.automationReference());
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
