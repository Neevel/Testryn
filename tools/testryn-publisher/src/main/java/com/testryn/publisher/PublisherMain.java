package com.testryn.publisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entry point. {@link #main} is a thin wrapper around {@link #run} so the actual
 * logic stays unit-testable without ever calling {@code System.exit} from a test
 * (Abschnitt 31).
 *
 * <p>Exit codes: {@code 0} success, {@code 1} the request reached Testryn but failed
 * (validation error, HTTP error, transport failure), {@code 2} usage error (bad/
 * missing arguments) -- the standard Unix convention for "you used the tool wrong".
 */
public final class PublisherMain {

    private PublisherMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
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
