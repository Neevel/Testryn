package com.testryn.integration.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Jira connection settings. Populated exclusively from environment variables /
 * external configuration — never hard-coded, never committed (see AGENTS.md ->
 * Sicherheit, ADR 0005, ADR 0007). {@code apiToken} must never be exposed through
 * any API response or log line -- see {@code JiraIntegrationController}, which only
 * ever exposes {@link #isTokenConfigured()}, never the value itself.
 */
@ConfigurationProperties(prefix = "testryn.jira")
public class JiraProperties {

    private String name = "Default Jira Connection";
    private String baseUrl;
    private String email;
    private String apiToken;
    private JiraAuthType authType = JiraAuthType.API_TOKEN;
    private boolean active = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public JiraAuthType getAuthType() {
        return authType;
    }

    public void setAuthType(JiraAuthType authType) {
        this.authType = authType;
    }

    /** Lets a connection be fully configured but temporarily switched off. */
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isTokenConfigured() {
        return StringUtils.hasText(apiToken);
    }

    public boolean isConfigured() {
        return authType == JiraAuthType.API_TOKEN
                && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(email)
                && StringUtils.hasText(apiToken);
    }

    /** Configured, active, and (for API_TOKEN) has everything it needs to call Jira. */
    public boolean isUsable() {
        return active && isConfigured();
    }
}
