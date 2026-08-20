package com.testryn.integration.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.testryn.requirement.domain.RequirementProviderType;
import com.testryn.requirement.provider.ExternalRequirementInfo;
import com.testryn.requirement.provider.RequirementProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * {@link RequirementProvider} implementation for Jira. Read-only lookup used to
 * enrich {@code RequirementLink}s with a summary/URL; never a prerequisite for
 * creating a link (ADR 0005). Inactive (returns empty for every lookup) unless
 * {@code TESTRYN_JIRA_BASE_URL}, {@code TESTRYN_JIRA_EMAIL} and
 * {@code TESTRYN_JIRA_API_TOKEN} are all configured.
 */
@Component
public class JiraRequirementProvider implements RequirementProvider {

    private static final Logger log = LoggerFactory.getLogger(JiraRequirementProvider.class);

    private final JiraProperties properties;
    private final RestClient restClient;

    public JiraRequirementProvider(JiraProperties properties) {
        this.properties = properties;
        this.restClient = properties.isConfigured()
                ? RestClient.builder().baseUrl(properties.getBaseUrl()).build()
                : null;
    }

    @Override
    public RequirementProviderType type() {
        return RequirementProviderType.JIRA;
    }

    @Override
    public Optional<ExternalRequirementInfo> fetch(String externalKey) {
        if (restClient == null) {
            return Optional.empty();
        }
        try {
            JiraIssue issue = restClient.get()
                    .uri("/rest/api/3/issue/{key}?fields=summary", externalKey)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                    .retrieve()
                    .body(JiraIssue.class);
            if (issue == null) {
                return Optional.empty();
            }
            String summary = issue.fields() == null ? null : issue.fields().summary();
            String url = properties.getBaseUrl() + "/browse/" + externalKey;
            return Optional.of(new ExternalRequirementInfo(issue.id(), externalKey, url, summary));
        } catch (Exception ex) {
            log.warn("Jira lookup failed for issue {}: {}", externalKey, ex.getMessage());
            return Optional.empty();
        }
    }

    private String basicAuthHeader() {
        String credentials = properties.getEmail() + ":" + properties.getApiToken();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JiraIssue(String id, String key, Fields fields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Fields(String summary) {
    }
}
