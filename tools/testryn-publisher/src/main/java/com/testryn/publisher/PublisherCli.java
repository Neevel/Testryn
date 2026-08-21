package com.testryn.publisher;

import java.util.ArrayList;
import java.util.List;

/** Minimal, hand-rolled argument parsing (Abschnitt 16 of the original CI-publisher
 * block: not a full framework; AGENTS.md #8: no new dependency without a concrete
 * reason -- a CLI-parsing library is not worth it for a handful of flags and two
 * subcommands). */
public final class PublisherCli {

    static final String USAGE = """
            Usage:
              testryn-publisher publish --base-url <url> [--execution-id <id>] --results <file|->
              testryn-publisher publish-junit --base-url <url> --execution-id <id> --results <file-or-dir>... [--dry-run]

            Commands:
              publish        Reads Testryn's own JSON result format (a file or stdin).
              publish-junit  Reads one or more JUnit/Surefire/Failsafe XML report files
                             and/or directories of them (not recursive).

            Options:
              --base-url       Testryn base URL, e.g. http://localhost:8080 (required)
              --execution-id   Execution to update. Optional for 'publish' if the input JSON
                               has an "executionId"; required for 'publish-junit' (JUnit XML
                               never carries one).
              --results        'publish': path to a results JSON file, or "-"/omitted for stdin.
                               'publish-junit': one or more XML files and/or directories --
                               repeat the flag, or list several values after one occurrence
                               (so a shell-expanded glob like target/surefire-reports/*.xml
                               is captured in full either way).
              --dry-run        'publish-junit' only: parse and print a summary, send nothing.

            Reads TESTRYN_API_TOKEN from the environment if set (never as a CLI argument).
            """;

    private PublisherCli() {
    }

    public static final class UsageException extends RuntimeException {
        public UsageException(String message) {
            super(message);
        }
    }

    public static PublisherOptions parse(String[] args) {
        return parse(args, System.getenv("TESTRYN_API_TOKEN"));
    }

    /** Package-private overload so tests can inject a token without touching real
     * environment variables. */
    static PublisherOptions parse(String[] args, String apiToken) {
        String baseUrl = null;
        String executionId = null;
        String resultsPath = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--base-url" -> baseUrl = requireValue(args, ++i, arg);
                case "--execution-id" -> executionId = requireValue(args, ++i, arg);
                case "--results" -> resultsPath = requireValue(args, ++i, arg);
                default -> throw new UsageException("Unknown option '" + arg + "'.\n\n" + USAGE);
            }
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new UsageException("--base-url is required.\n\n" + USAGE);
        }
        return new PublisherOptions(baseUrl, executionId, resultsPath, apiToken);
    }

    public static JUnitPublishOptions parseJUnit(String[] args) {
        return parseJUnit(args, System.getenv("TESTRYN_API_TOKEN"));
    }

    static JUnitPublishOptions parseJUnit(String[] args, String apiToken) {
        String baseUrl = null;
        String executionId = null;
        List<String> resultsPaths = new ArrayList<>();
        boolean dryRun = false;
        int i = 1;
        while (i < args.length) {
            String arg = args[i];
            switch (arg) {
                case "--base-url" -> {
                    baseUrl = requireValue(args, ++i, arg);
                    i++;
                }
                case "--execution-id" -> {
                    executionId = requireValue(args, ++i, arg);
                    i++;
                }
                case "--dry-run" -> {
                    dryRun = true;
                    i++;
                }
                case "--results" -> {
                    i++;
                    int consumed = 0;
                    // Greedily consume every following non-flag token: this is what
                    // lets a shell-expanded glob (target/surefire-reports/*.xml ->
                    // many space-separated argv entries after ONE --results) be
                    // captured in full, not just its first match.
                    while (i < args.length && !args[i].startsWith("--")) {
                        resultsPaths.add(args[i]);
                        i++;
                        consumed++;
                    }
                    if (consumed == 0) {
                        throw new UsageException("--results requires at least one value.\n\n" + USAGE);
                    }
                }
                default -> throw new UsageException("Unknown option '" + arg + "'.\n\n" + USAGE);
            }
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new UsageException("--base-url is required.\n\n" + USAGE);
        }
        if (executionId == null || executionId.isBlank()) {
            throw new UsageException("--execution-id is required for publish-junit.\n\n" + USAGE);
        }
        if (resultsPaths.isEmpty()) {
            throw new UsageException("--results is required for publish-junit.\n\n" + USAGE);
        }
        return new JUnitPublishOptions(baseUrl, executionId, resultsPaths, apiToken, dryRun);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new UsageException(option + " requires a value.\n\n" + USAGE);
        }
        return args[index];
    }
}
