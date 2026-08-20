package com.testryn.integration.jira;

import com.testryn.common.error.UpstreamServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the explicit (non-silent) Jira lookup paths -- the issue-preview and
 * connection-test endpoints need to distinguish "not configured", "not reachable"
 * and "issue not found" instead of collapsing everything to an empty Optional like
 * the best-effort {@link JiraRequirementProvider#fetch} does (ADR 0005).
 */
class JiraIssueClientTest {

    @Test
    void fetchOrThrowFailsWithUpstreamServiceExceptionWhenNotConfigured() {
        JiraIssueClient client = new JiraIssueClient(new JiraProperties());

        assertThat(client.isUsable()).isFalse();
        assertThatThrownBy(() -> client.fetchOrThrow("BIT-27"))
                .isInstanceOf(UpstreamServiceException.class);
    }

    @Test
    void testConnectionReportsFailureWhenNotConfigured() {
        JiraIssueClient client = new JiraIssueClient(new JiraProperties());

        var result = client.testConnection();

        assertThat(result.success()).isFalse();
        assertThat(result.message()).containsIgnoringCase("not configured");
    }

    @Test
    void fetchOrThrowFailsWithUpstreamServiceExceptionWhenJiraIsUnreachable() {
        JiraProperties properties = new JiraProperties();
        // A base URL that resolves but refuses connections -- simulates "Jira is not
        // reachable" without depending on network access in this test.
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setEmail("bot@example.com");
        properties.setApiToken("token");
        JiraIssueClient client = new JiraIssueClient(properties);

        assertThat(client.isUsable()).isTrue();
        assertThatThrownBy(() -> client.fetchOrThrow("BIT-27"))
                .isInstanceOf(UpstreamServiceException.class);
    }

    @Test
    void quietFetchNeverThrowsEvenWhenJiraIsUnreachable() {
        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setEmail("bot@example.com");
        properties.setApiToken("token");
        JiraIssueClient client = new JiraIssueClient(properties);

        assertThat(client.fetchQuietly("BIT-27")).isEmpty();
    }
}
