package com.testryn.publisher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The publisher core (Abschnitt 17): everything here is independent of where the
 * {@link PublisherResultInput}s came from (JSON file, stdin, later a JUnit XML
 * reader) and independent of how the HTTP request is actually sent
 * ({@link HttpTransport}). Talks to exactly one Testryn endpoint: the bulk
 * result-update API (ADR 0010).
 */
public class TestrynApiClient {

    private final HttpTransport transport;
    private final String baseUrl;
    private final String apiToken;
    private final ObjectMapper objectMapper;

    public TestrynApiClient(HttpTransport transport, String baseUrl, String apiToken) {
        this.transport = transport;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * @throws IOException on a transport-level failure (connection refused, DNS,
     *                      timeout, ...) -- the request never got a response at all.
     *                      A completed HTTP round-trip, success or failure, is always
     *                      a {@link PublishOutcome}, never an exception.
     */
    public PublishOutcome publish(String executionId, List<PublisherResultInput> results) throws IOException {
        String requestBody = buildRequestBody(results);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        // Abschnitt 20: the backend does not enforce auth yet (see ADR 0011), but the
        // publisher is already ready to send a service token once it does.
        if (apiToken != null && !apiToken.isBlank()) {
            headers.put("Authorization", "Bearer " + apiToken);
        }

        String url = baseUrl + "/api/v1/executions/" + executionId + "/results";
        HttpTransportResponse response = transport.send(url, "PATCH", requestBody, headers);
        return interpret(response);
    }

    private String buildRequestBody(List<PublisherResultInput> results) throws IOException {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PublisherResultInput result : results) {
            Map<String, Object> entry = new LinkedHashMap<>();
            putIfPresent(entry, "resultId", result.resultId());
            putIfPresent(entry, "automationReference", result.automationReference());
            putIfPresent(entry, "status", result.status());
            putIfPresent(entry, "comment", result.comment());
            putIfPresent(entry, "durationMs", result.durationMs());
            // Abschnitt 19: default executor to "ci" when the input didn't specify one.
            entry.put("executor", result.executor() != null ? result.executor() : "ci");
            putIfPresent(entry, "actualResult", result.actualResult());
            putIfPresent(entry, "failureDetails", result.failureDetails());
            entries.add(entry);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("results", entries);
        return objectMapper.writeValueAsString(body);
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private PublishOutcome interpret(HttpTransportResponse response) {
        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
        JsonNode body = parseQuietly(response.body());

        if (success) {
            List<String> summary = new ArrayList<>();
            if (body != null && body.has("results")) {
                for (JsonNode entry : body.get("results")) {
                    summary.add(summaryLine(entry));
                }
            }
            return new PublishOutcome(true, response.statusCode(), summary, "Bulk update succeeded", List.of());
        }

        String message = "Bulk update failed with HTTP " + response.statusCode();
        List<String> violations = new ArrayList<>();
        if (body != null) {
            if (body.has("message") && !body.get("message").isNull()) {
                message = body.get("message").asText();
            }
            if (body.has("fieldErrors") && body.get("fieldErrors").isArray()) {
                for (JsonNode violation : body.get("fieldErrors")) {
                    String field = violation.has("field") ? violation.get("field").asText() : "?";
                    String violationMessage = violation.has("message") ? violation.get("message").asText() : "";
                    violations.add(field + ": " + violationMessage);
                }
            }
        } else if (response.body() != null && !response.body().isBlank()) {
            message = message + " -- " + response.body();
        }
        return new PublishOutcome(false, response.statusCode(), List.of(), message, violations);
    }

    private String summaryLine(JsonNode entry) {
        String humanId = entry.has("testCaseHumanId") ? entry.get("testCaseHumanId").asText() : "?";
        String automationReference = entry.has("automationReference") && !entry.get("automationReference").isNull()
                ? entry.get("automationReference").asText() : null;
        String status = entry.has("status") ? entry.get("status").asText() : "?";
        String durationSuffix = entry.has("durationMs") && !entry.get("durationMs").isNull()
                ? " (" + entry.get("durationMs").asLong() + "ms)" : "";
        String label = automationReference != null ? humanId + " [" + automationReference + "]" : humanId;
        return label + " -> " + status + durationSuffix;
    }

    private JsonNode parseQuietly(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }
}
