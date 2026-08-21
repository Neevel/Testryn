package com.testryn.publisher;

import java.util.List;

/**
 * @param baseUrl      Testryn's base URL, e.g. {@code http://localhost:8080}
 * @param executionId  from {@code --execution-id}; required (JUnit XML never carries
 *                      one, unlike the JSON workflow -- Abschnitt 15)
 * @param resultsPaths from one or more {@code --results} occurrences and/or
 *                      space-separated values after one occurrence (so a
 *                      shell-glob-expanded {@code --results target/surefire-reports/*.xml}
 *                      is captured in full); each entry is a single XML file or a
 *                      directory of them (Abschnitt 12)
 * @param apiToken     from the {@code TESTRYN_API_TOKEN} environment variable, never
 *                      a CLI argument (Abschnitt 26)
 * @param dryRun       from {@code --dry-run}: parse and summarize, send nothing
 *                      (Abschnitt 24)
 */
public record JUnitPublishOptions(String baseUrl, String executionId, List<String> resultsPaths, String apiToken,
                                   boolean dryRun) {
}
