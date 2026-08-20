package com.testryn.publisher;

/**
 * @param baseUrl     Testryn's base URL, e.g. {@code http://localhost:8080}
 * @param executionId from {@code --execution-id}; may be null (then resolved from
 *                     the input file's {@code executionId}, see {@link PublisherMain})
 * @param resultsPath from {@code --results}; null or {@code "-"} means stdin
 * @param apiToken    from the {@code TESTRYN_API_TOKEN} environment variable, never
 *                     a CLI argument (Abschnitt 15: secrets are not command-line
 *                     arguments -- those are visible in shell history and `ps`)
 */
public record PublisherOptions(String baseUrl, String executionId, String resultsPath, String apiToken) {
}
