package com.testryn.publisher;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link TestrynApiClient} entirely against {@link FakeHttpTransport} --
 * no real socket anywhere in this test (Abschnitt 31).
 */
class TestrynApiClientTest {

    @Test
    void sendsAPatchRequestToTheBulkEndpointWithTheExecutionIdInThePath() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(200, """
                {"results":[]}
                """);
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, 1200L, "ci", null, null)));

        assertThat(transport.lastUrl()).isEqualTo("http://localhost:8080/api/v1/executions/exec-1/results");
        assertThat(transport.lastMethod()).isEqualTo("PATCH");
    }

    @Test
    void trailingSlashOnBaseUrlIsHandled() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(200, "{\"results\":[]}");
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080/", null);

        client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, "ci", null, null)));

        assertThat(transport.lastUrl()).isEqualTo("http://localhost:8080/api/v1/executions/exec-1/results");
    }

    @Test
    void omitsAbsentFieldsFromTheOutgoingRequestBody() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(200, "{\"results\":[]}");
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        client.publish("exec-1", List.of(new PublisherResultInput(
                null, "auth.login.valid", "PASSED", null, null, null, null, null)));

        String body = transport.lastBody();
        assertThat(body).contains("\"automationReference\":\"auth.login.valid\"");
        assertThat(body).contains("\"status\":\"PASSED\"");
        // Absent fields must not appear at all -- never as an explicit null, which
        // would clear them server-side (ADR 0006/0010 merge-patch semantics).
        assertThat(body).doesNotContain("\"comment\"");
        assertThat(body).doesNotContain("\"actualResult\"");
        assertThat(body).doesNotContain("\"failureDetails\"");
        assertThat(body).doesNotContain("\"resultId\"");
    }

    @Test
    void defaultsExecutorToCiWhenNotSpecified() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(200, "{\"results\":[]}");
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, null, null, null)));

        assertThat(transport.lastBody()).contains("\"executor\":\"ci\"");
    }

    @Test
    void respectsAnExplicitExecutorInsteadOfTheDefault() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(200, "{\"results\":[]}");
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, "playwright-runner", null, null)));

        assertThat(transport.lastBody()).contains("\"executor\":\"playwright-runner\"");
        assertThat(transport.lastBody()).doesNotContain("\"executor\":\"ci\"");
    }

    @Test
    void sendsABearerTokenWhenConfigured() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(200, "{\"results\":[]}");
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", "secret-token");

        client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, "ci", null, null)));

        assertThat(transport.lastHeaders()).containsEntry("Authorization", "Bearer secret-token");
    }

    @Test
    void omitsTheAuthorizationHeaderWhenNoTokenIsConfigured() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(200, "{\"results\":[]}");
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, "ci", null, null)));

        assertThat(transport.lastHeaders()).doesNotContainKey("Authorization");
    }

    @Test
    void aSuccessfulResponseIsSummarizedPerResult() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(200, """
                {"results":[
                  {"resultId":"r1","testCaseHumanId":"BIT-TC-1","automationReference":"auth.login.valid",
                   "status":"PASSED","durationMs":1420}
                ]}
                """);
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        PublishOutcome outcome = client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, 1420L, "ci", null, null)));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.summary()).hasSize(1);
        assertThat(outcome.summary().get(0)).contains("BIT-TC-1").contains("auth.login.valid").contains("PASSED");
    }

    @Test
    void aValidationFailureExposesMessageAndFieldErrors() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(400, """
                {"message":"Bulk result update request is invalid: 1 of 1 entries have a problem",
                 "fieldErrors":[{"field":"auth.login.valid","message":"no test case with this automationReference"}]}
                """);
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        PublishOutcome outcome = client.publish("exec-1", List.of(new PublisherResultInput(
                null, "auth.login.valid", "PASSED", null, null, "ci", null, null)));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isEqualTo(400);
        assertThat(outcome.message()).contains("invalid");
        assertThat(outcome.violations()).hasSize(1);
        assertThat(outcome.violations().get(0)).contains("auth.login.valid");
    }

    @Test
    void aFailureWithAnUnparseableBodyStillProducesAUsableOutcome() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(502, "<html>Bad Gateway</html>");
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        PublishOutcome outcome = client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, "ci", null, null)));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isEqualTo(502);
        assertThat(outcome.message()).contains("502");
    }

    @Test
    void aMissingOrInvalidTokenProducesA401Outcome() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(401, """
                {"code":"UNAUTHORIZED","message":"Authentication is required."}
                """);
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        PublishOutcome outcome = client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, "ci", null, null)));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isEqualTo(401);
        assertThat(outcome.message()).contains("Authentication is required");
    }

    @Test
    void aReadOnlyTokenAttemptingABulkUpdateProducesA403Outcome() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(403, """
                {"code":"FORBIDDEN","message":"The service token does not have the required scope."}
                """);
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", "read-only-token");

        PublishOutcome outcome = client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, "ci", null, null)));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isEqualTo(403);
        assertThat(outcome.message()).contains("does not have the required scope");
    }

    @Test
    void theConfiguredTokenValueNeverAppearsInAFailureOutcome() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.returning(401, """
                {"code":"UNAUTHORIZED","message":"Authentication is required."}
                """);
        String secretToken = "testryn_" + "a".repeat(24) + "_MUST-NEVER-APPEAR-IN-OUTPUT";
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", secretToken);

        PublishOutcome outcome = client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, "ci", null, null)));

        assertThat(outcome.message()).doesNotContain(secretToken).doesNotContain("MUST-NEVER-APPEAR-IN-OUTPUT");
        assertThat(outcome.violations().toString()).doesNotContain(secretToken);
    }

    @Test
    void aTransportFailurePropagatesAsIOException() {
        HttpTransport transport = (url, method, body, headers) -> {
            throw new IOException("Connection refused");
        };
        TestrynApiClient client = new TestrynApiClient(transport, "http://localhost:8080", null);

        assertThatThrownBy(() -> client.publish("exec-1", List.of(new PublisherResultInput(
                "r1", null, "PASSED", null, null, "ci", null, null))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Connection refused");
    }

    /** Records the last request it was asked to send and returns a canned response --
     * the whole point being that no test in this file ever opens a real socket. */
    private static final class FakeHttpTransport implements HttpTransport {
        private final int statusCode;
        private final String responseBody;
        private String lastUrl;
        private String lastMethod;
        private String lastBody;
        private Map<String, String> lastHeaders;

        private FakeHttpTransport(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        static FakeHttpTransport returning(int statusCode, String responseBody) {
            return new FakeHttpTransport(statusCode, responseBody);
        }

        @Override
        public HttpTransportResponse send(String url, String method, String body, Map<String, String> headers) {
            this.lastUrl = url;
            this.lastMethod = method;
            this.lastBody = body;
            this.lastHeaders = headers;
            return new HttpTransportResponse(statusCode, responseBody);
        }

        String lastUrl() {
            return lastUrl;
        }

        String lastMethod() {
            return lastMethod;
        }

        String lastBody() {
            return lastBody;
        }

        Map<String, String> lastHeaders() {
            return lastHeaders;
        }
    }
}
