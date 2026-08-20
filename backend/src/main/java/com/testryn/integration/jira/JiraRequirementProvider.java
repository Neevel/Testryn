package com.testryn.integration.jira;

import com.testryn.requirement.domain.RequirementProviderType;
import com.testryn.requirement.provider.ExternalRequirementInfo;
import com.testryn.requirement.provider.RequirementProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link RequirementProvider} implementation for Jira. Read-only lookup used to
 * enrich {@code RequirementLink}s with a summary/URL/status/etc.; never a
 * prerequisite for creating a link (ADR 0005). Inactive (returns empty for every
 * lookup) unless the Jira connection is fully configured and active -- see
 * {@link JiraProperties}. The actual HTTP work lives in {@link JiraIssueClient},
 * shared with the explicit issue-preview and connection-test endpoints.
 */
@Component
public class JiraRequirementProvider implements RequirementProvider {

    private final JiraIssueClient client;

    public JiraRequirementProvider(JiraIssueClient client) {
        this.client = client;
    }

    @Override
    public RequirementProviderType type() {
        return RequirementProviderType.JIRA;
    }

    @Override
    public Optional<ExternalRequirementInfo> fetch(String externalKey) {
        return client.fetchQuietly(externalKey);
    }
}
