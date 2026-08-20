package com.testryn.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the publisher's JSON input format (Abschnitt 14/29):
 * <pre>{@code
 * {
 *   "executionId": "...",
 *   "results": [
 *     {"automationReference": "auth.login.valid", "status": "PASSED", "durationMs": 1420}
 *   ]
 * }
 * }</pre>
 * The JSON file is transport only, never persistence (Abschnitt 14) -- this reader's
 * only job is turning it into a {@link ResultBatch} for {@link TestrynApiClient}.
 */
public class JsonResultBatchReader implements ResultBatchReader {

    private final ObjectMapper objectMapper;

    public JsonResultBatchReader() {
        this(new ObjectMapper());
    }

    public JsonResultBatchReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ResultBatch read(InputStream input) throws IOException {
        JsonNode root = objectMapper.readTree(input);
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new IOException("Input is empty or not valid JSON");
        }
        JsonNode executionIdNode = root.get("executionId");
        String executionId = (executionIdNode != null && !executionIdNode.isNull())
                ? executionIdNode.asText() : null;

        JsonNode resultsNode = root.get("results");
        if (resultsNode == null || !resultsNode.isArray()) {
            throw new IOException("Input must have a 'results' array");
        }

        List<PublisherResultInput> results = new ArrayList<>();
        int index = 0;
        for (JsonNode entry : resultsNode) {
            if (!entry.isObject()) {
                throw new IOException("results[" + index + "] must be a JSON object");
            }
            try {
                results.add(new PublisherResultInput(
                        textOrNull(entry, "resultId"),
                        textOrNull(entry, "automationReference"),
                        textOrNull(entry, "status"),
                        textOrNull(entry, "comment"),
                        longOrNull(entry, "durationMs"),
                        textOrNull(entry, "executor"),
                        textOrNull(entry, "actualResult"),
                        textOrNull(entry, "failureDetails")
                ));
            } catch (NumberFormatException e) {
                throw new IOException("results[" + index + "]: " + e.getMessage(), e);
            }
            index++;
        }
        return new ResultBatch(executionId, results);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new NumberFormatException("'" + field + "' must be a number, got: " + value);
        }
        return value.asLong();
    }
}
