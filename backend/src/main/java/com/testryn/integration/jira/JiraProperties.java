package com.testryn.integration.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Jira connection settings. Populated exclusively from environment variables /
 * external configuration — never hard-coded, never committed (see AGENTS.md ->
 * Sicherheit, ADR 0005).
 */
@ConfigurationProperties(prefix = "testryn.jira")
public class JiraProperties {

    private String baseUrl;
    private String email;
    private String apiToken;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(email) && StringUtils.hasText(apiToken);
    }
}
