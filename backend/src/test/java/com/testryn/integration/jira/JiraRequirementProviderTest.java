package com.testryn.integration.jira;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JiraRequirementProviderTest {

    @Test
    void fetchReturnsEmptyWhenJiraIsNotConfigured() {
        JiraProperties properties = new JiraProperties();
        JiraRequirementProvider provider = new JiraRequirementProvider(properties);

        assertThat(provider.fetch("BIT-27")).isEmpty();
    }
}
