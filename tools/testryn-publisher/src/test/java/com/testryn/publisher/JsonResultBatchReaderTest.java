package com.testryn.publisher;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonResultBatchReaderTest {

    private final JsonResultBatchReader reader = new JsonResultBatchReader();

    @Test
    void parsesTheMinimalExampleFromAbschnitt29() throws IOException {
        ResultBatch batch = read("""
                {
                  "executionId": "exec-1",
                  "results": [
                    {"automationReference": "auth.login.valid", "status": "PASSED", "durationMs": 1100}
                  ]
                }
                """);

        assertThat(batch.executionId()).isEqualTo("exec-1");
        assertThat(batch.results()).hasSize(1);
        PublisherResultInput result = batch.results().get(0);
        assertThat(result.automationReference()).isEqualTo("auth.login.valid");
        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.durationMs()).isEqualTo(1100L);
        assertThat(result.resultId()).isNull();
        assertThat(result.executor()).isNull();
    }

    @Test
    void executionIdIsOptional() throws IOException {
        ResultBatch batch = read("""
                {"results": [{"resultId": "r1", "status": "PASSED"}]}
                """);

        assertThat(batch.executionId()).isNull();
        assertThat(batch.results()).hasSize(1);
    }

    @Test
    void parsesAllFieldsOfOneEntry() throws IOException {
        ResultBatch batch = read("""
                {"results": [{
                  "resultId": "r1",
                  "automationReference": "auth.login.valid",
                  "status": "FAILED",
                  "comment": "flaky",
                  "durationMs": 2311,
                  "executor": "playwright",
                  "actualResult": "HTTP 500",
                  "failureDetails": "Expected HTTP 200"
                }]}
                """);

        PublisherResultInput result = batch.results().get(0);
        assertThat(result.resultId()).isEqualTo("r1");
        assertThat(result.comment()).isEqualTo("flaky");
        assertThat(result.executor()).isEqualTo("playwright");
        assertThat(result.actualResult()).isEqualTo("HTTP 500");
        assertThat(result.failureDetails()).isEqualTo("Expected HTTP 200");
    }

    @Test
    void rejectsMissingResultsArray() {
        assertThatThrownBy(() -> read("""
                {"executionId": "exec-1"}
                """)).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsANonNumericDuration() {
        assertThatThrownBy(() -> read("""
                {"results": [{"resultId": "r1", "status": "PASSED", "durationMs": "not-a-number"}]}
                """)).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsEmptyInput() {
        assertThatThrownBy(() -> read("")).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsANonObjectEntry() {
        assertThatThrownBy(() -> read("""
                {"results": ["not an object"]}
                """)).isInstanceOf(IOException.class);
    }

    private ResultBatch read(String json) throws IOException {
        try (InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return reader.read(input);
        }
    }
}
