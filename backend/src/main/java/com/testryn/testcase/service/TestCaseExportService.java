package com.testryn.testcase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCaseVersion;
import com.testryn.testcase.domain.TestStep;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Exports a project's test cases (including steps and expected results) in a few
 * simple, dependency-light formats. Deliberately independent of the web-layer DTOs —
 * export is a presentation concern of the {@code testcase} module itself, not of a
 * particular controller.
 */
@Service
@Transactional(readOnly = true)
public class TestCaseExportService {

    private final TestCaseService testCaseService;
    private final ObjectMapper objectMapper;

    public TestCaseExportService(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .findAndRegisterModules();
    }

    public record ExportStep(int order, String action, String inputData, String expectedResult) {
        static ExportStep from(TestStep step) {
            return new ExportStep(step.getStepOrder(), step.getAction(), step.getInputData(), step.getExpectedResult());
        }
    }

    public record ExportTestCase(String humanId, String title, String description, String preconditions,
                                  String status, String priority, Set<String> tags, int version,
                                  List<ExportStep> steps, Instant updatedAt) {
        static ExportTestCase from(TestCase testCase) {
            TestCaseVersion v = testCase.getCurrentVersion();
            return new ExportTestCase(
                    testCase.getHumanId(),
                    v == null ? null : v.getTitle(),
                    v == null ? null : v.getDescription(),
                    v == null ? null : v.getPreconditions(),
                    testCase.getStatus().name(),
                    testCase.getPriority().name(),
                    testCase.getTags(),
                    v == null ? 0 : v.getVersionNumber(),
                    v == null ? List.of() : v.getSteps().stream().map(ExportStep::from).toList(),
                    testCase.getUpdatedAt()
            );
        }
    }

    public String exportJson(String projectKey) {
        List<ExportTestCase> testCases = testCaseService.findByProjectKey(projectKey).stream()
                .map(ExportTestCase::from).toList();
        try {
            return objectMapper.writeValueAsString(testCases);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize test cases to JSON", e);
        }
    }

    public String exportCsv(String projectKey) {
        List<ExportTestCase> testCases = testCaseService.findByProjectKey(projectKey).stream()
                .map(ExportTestCase::from).toList();
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", "humanId", "title", "status", "priority", "tags", "version",
                "preconditions", "steps")).append("\r\n");
        for (ExportTestCase tc : testCases) {
            String steps = tc.steps().stream()
                    .map(s -> "%d) %s%s -> %s".formatted(s.order(), s.action(),
                            s.inputData() == null || s.inputData().isBlank() ? "" : " [Input: " + s.inputData() + "]",
                            s.expectedResult()))
                    .reduce((a, b) -> a + " | " + b).orElse("");
            csv.append(csvField(tc.humanId())).append(',')
                    .append(csvField(tc.title())).append(',')
                    .append(csvField(tc.status())).append(',')
                    .append(csvField(tc.priority())).append(',')
                    .append(csvField(String.join(";", tc.tags()))).append(',')
                    .append(tc.version()).append(',')
                    .append(csvField(tc.preconditions())).append(',')
                    .append(csvField(steps))
                    .append("\r\n");
        }
        return csv.toString();
    }

    public String exportMarkdown(String projectKey) {
        List<ExportTestCase> testCases = testCaseService.findByProjectKey(projectKey).stream()
                .map(ExportTestCase::from).toList();
        StringBuilder md = new StringBuilder();
        md.append("# Test Cases: ").append(projectKey).append("\n\n");
        for (ExportTestCase tc : testCases) {
            md.append("## ").append(tc.humanId()).append(" — ").append(nullToEmpty(tc.title())).append("\n\n");
            md.append("- Status: ").append(tc.status()).append("\n");
            md.append("- Priority: ").append(tc.priority()).append("\n");
            md.append("- Version: ").append(tc.version()).append("\n");
            if (!tc.tags().isEmpty()) {
                md.append("- Tags: ").append(String.join(", ", tc.tags())).append("\n");
            }
            md.append("\n");
            if (tc.description() != null && !tc.description().isBlank()) {
                md.append("**Description:** ").append(tc.description()).append("\n\n");
            }
            if (tc.preconditions() != null && !tc.preconditions().isBlank()) {
                md.append("**Preconditions:** ").append(tc.preconditions()).append("\n\n");
            }
            if (!tc.steps().isEmpty()) {
                md.append("| # | Action | Input / Data | Expected Result |\n|---|---|---|---|\n");
                for (ExportStep step : tc.steps()) {
                    md.append("| ").append(step.order()).append(" | ")
                            .append(step.action().replace("|", "\\|")).append(" | ")
                            .append(nullToEmpty(step.inputData()).replace("|", "\\|")).append(" | ")
                            .append(step.expectedResult().replace("|", "\\|")).append(" |\n");
                }
                md.append("\n");
            }
        }
        return md.toString();
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
