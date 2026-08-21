package com.testryn.publisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI entry point. {@link #main} is a thin wrapper around {@link #run} so the actual
 * logic stays unit-testable without ever calling {@code System.exit} from a test.
 *
 * <p>Exit codes: {@code 0} success, {@code 1} the request reached Testryn but failed
 * (validation error, HTTP error, transport failure, unresolved automationReference,
 * ...), {@code 2} usage error (bad/missing arguments, unparseable input) -- the
 * standard Unix convention for "you used the tool wrong".
 */
public final class PublisherMain {

    private PublisherMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            err.println(PublisherCli.USAGE);
            return 2;
        }
        return switch (args[0]) {
            case "publish" -> runJsonPublish(args, out, err);
            case "publish-junit" -> runJUnitPublish(args, out, err);
            default -> {
                err.println("Unknown command '" + args[0] + "'.\n\n" + PublisherCli.USAGE);
                yield 2;
            }
        };
    }

    private static int runJsonPublish(String[] args, PrintStream out, PrintStream err) {
        PublisherOptions options;
        try {
            options = PublisherCli.parse(args);
        } catch (PublisherCli.UsageException e) {
            err.println(e.getMessage());
            return 2;
        }

        ResultBatch batch;
        try (InputStream input = openInput(options.resultsPath())) {
            batch = new JsonResultBatchReader().read(input);
        } catch (IOException e) {
            err.println("Could not read results input: " + e.getMessage());
            return 1;
        }

        String executionId;
        try {
            executionId = resolveExecutionId(options.executionId(), batch.executionId());
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 2;
        }

        if (batch.results().isEmpty()) {
            err.println("No results to publish: the input's 'results' array is empty");
            return 1;
        }

        TestrynApiClient client = new TestrynApiClient(
                new HttpUrlConnectionTransport(), options.baseUrl(), options.apiToken());
        PublishOutcome outcome;
        try {
            outcome = client.publish(executionId, batch.results());
        } catch (IOException e) {
            err.println("Could not reach Testryn at " + options.baseUrl() + ": " + e.getMessage());
            return 1;
        }

        return report(outcome, batch.results().size(), out, err);
    }

    private static int runJUnitPublish(String[] args, PrintStream out, PrintStream err) {
        JUnitPublishOptions options;
        try {
            options = PublisherCli.parseJUnit(args);
        } catch (PublisherCli.UsageException e) {
            err.println(e.getMessage());
            return 2;
        }

        JUnitReportImporter.ImportResult imported;
        try {
            imported = new JUnitReportImporter().importFrom(options.resultsPaths());
        } catch (IOException e) {
            err.println("Could not read JUnit XML report(s): " + e.getMessage());
            return 2;
        }

        if (imported.results().isEmpty()) {
            err.println("No <testcase> elements found in the given report(s)");
            return 1;
        }

        Map<String, Long> countsByStatus = countByStatus(imported.results());

        if (options.dryRun()) {
            printJUnitPreview(out, imported, countsByStatus);
            return 0;
        }

        TestrynApiClient client = new TestrynApiClient(
                new HttpUrlConnectionTransport(), options.baseUrl(), options.apiToken());
        PublishOutcome outcome;
        try {
            outcome = client.publish(options.executionId(), imported.results());
        } catch (IOException e) {
            err.println("Could not reach Testryn at " + options.baseUrl() + ": " + e.getMessage());
            return 1;
        }

        out.println("Testryn JUnit Publisher");
        out.println("Execution: " + options.executionId());
        out.println("Files: " + imported.files().size());
        out.println("Results: " + imported.results().size());
        out.println(String.format("%-8s %d", "PASSED", countsByStatus.getOrDefault("PASSED", 0L)));
        out.println(String.format("%-8s %d", "FAILED", countsByStatus.getOrDefault("FAILED", 0L)));
        out.println(String.format("%-8s %d", "SKIPPED", countsByStatus.getOrDefault("SKIPPED", 0L)));

        if (outcome.success()) {
            out.println("Published successfully.");
            return 0;
        }
        err.println("Publishing failed.");
        err.println(outcome.message() + " (HTTP " + outcome.httpStatus() + ")");
        outcome.violations().forEach(v -> err.println("  - " + v));
        return 1;
    }

    private static void printJUnitPreview(PrintStream out, JUnitReportImporter.ImportResult imported,
                                           Map<String, Long> countsByStatus) {
        out.println("JUnit Import Preview");
        out.println("Files:      " + imported.files().size());
        out.println("Tests:      " + imported.results().size());
        out.println("Passed:     " + countsByStatus.getOrDefault("PASSED", 0L));
        out.println("Failed:     " + countsByStatus.getOrDefault("FAILED", 0L));
        out.println("Skipped:    " + countsByStatus.getOrDefault("SKIPPED", 0L));
        out.println("Automation references:");
        for (PublisherResultInput result : imported.results()) {
            // Plain ASCII, deliberately not a unicode checkmark: this tool runs in
            // whatever console encoding a CI agent happens to have (Windows agents
            // commonly default to a non-UTF-8 codepage), where a "✓" would
            // silently mangle into "?" instead of rendering.
            out.println("- " + result.automationReference());
        }
        // Deliberately nothing else: no token, no base URL, no server response --
        // a dry run never talks to the network at all (Abschnitt 24).
    }

    private static Map<String, Long> countByStatus(List<PublisherResultInput> results) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PublisherResultInput result : results) {
            counts.merge(result.status(), 1L, Long::sum);
        }
        return counts;
    }

    static int report(PublishOutcome outcome, int resultCount, PrintStream out, PrintStream err) {
        if (outcome.success()) {
            out.println(outcome.message() + " (" + resultCount + " result(s))");
            outcome.summary().forEach(line -> out.println("  " + line));
            return 0;
        }
        err.println(outcome.message() + " (HTTP " + outcome.httpStatus() + ")");
        outcome.violations().forEach(v -> err.println("  - " + v));
        return 1;
    }

    private static InputStream openInput(String resultsPath) throws IOException {
        if (resultsPath == null || "-".equals(resultsPath)) {
            return System.in;
        }
        return Files.newInputStream(Path.of(resultsPath));
    }

    /** Neither silently prefers one source over the other nor guesses (same
     * principle as resultId/automationReference agreement in ADR 0010). */
    static String resolveExecutionId(String cliExecutionId, String fileExecutionId) {
        boolean cliHas = cliExecutionId != null && !cliExecutionId.isBlank();
        boolean fileHas = fileExecutionId != null && !fileExecutionId.isBlank();
        if (cliHas && fileHas && !cliExecutionId.equals(fileExecutionId)) {
            throw new IllegalArgumentException(
                    "--execution-id ('" + cliExecutionId + "') and the input file's 'executionId' ('"
                            + fileExecutionId + "') disagree -- refusing to guess which one you meant.");
        }
        if (cliHas) {
            return cliExecutionId;
        }
        if (fileHas) {
            return fileExecutionId;
        }
        throw new IllegalArgumentException(
                "No execution id given: pass --execution-id or include \"executionId\" in the input JSON.");
    }
}
