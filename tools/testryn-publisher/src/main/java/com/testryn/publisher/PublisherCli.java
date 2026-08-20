package com.testryn.publisher;

/** Minimal, hand-rolled argument parsing (Abschnitt 16: not a full framework yet;
 * AGENTS.md #8: no new dependency without a concrete reason -- a CLI-parsing library
 * is not worth it for three flags and one subcommand). */
public final class PublisherCli {

    static final String USAGE = """
            Usage: testryn-publisher publish --base-url <url> [--execution-id <id>] --results <file|->

              --base-url       Testryn base URL, e.g. http://localhost:8080 (required)
              --execution-id   Execution to update (optional if the input JSON has an "executionId")
              --results        Path to a results JSON file, or "-"/omitted to read stdin

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
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            throw new UsageException(USAGE);
        }
        if (!"publish".equals(args[0])) {
            throw new UsageException("Unknown command '" + args[0] + "'.\n\n" + USAGE);
        }

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

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new UsageException(option + " requires a value.\n\n" + USAGE);
        }
        return args[index];
    }
}
