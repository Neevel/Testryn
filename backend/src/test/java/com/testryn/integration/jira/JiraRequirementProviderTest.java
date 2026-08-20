package com.testryn.integration.jira;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JiraRequirementProviderTest {

    @Test
    void fetchReturnsEmptyWhenJiraIsNotConfigured() {
        JiraProperties properties = new JiraProperties();
        JiraRequirementProvider provider = new JiraRequirementProvider(new JiraIssueClient(properties));

        assertThat(provider.fetch("BIT-27")).isEmpty();
    }

    @Test
    void isInactiveWhenExplicitlySetToInactiveDespiteFullCredentials() {
        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl("https://example.atlassian.net");
        properties.setEmail("bot@example.com");
        properties.setApiToken("token");
        properties.setActive(false);

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.isUsable()).isFalse();
        assertThat(new JiraIssueClient(properties).isUsable()).isFalse();
    }
}
