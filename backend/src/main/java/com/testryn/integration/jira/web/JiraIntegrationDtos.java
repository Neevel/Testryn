package com.testryn.integration.jira.web;

import com.testryn.integration.jira.JiraAuthType;
import com.testryn.integration.jira.JiraIssueClient.JiraConnectionTestResult;
import com.testryn.integration.jira.oauth.JiraOAuthService.OAuthStatus;
import com.testryn.integration.jira.service.JiraConnectionSettings;
import com.testryn.requirement.provider.ExternalRequirementInfo;
import jakarta.validation.constraints.NotBlank;

public final class JiraIntegrationDtos {

    private JiraIntegrationDtos() {
    }

    /**
     * Never includes the API token, the OAuth client secret, the encryption key, or
     * an access/refresh token -- only booleans and the (non-secret) site URL. See
     * AGENTS.md -> Sicherheit, ADR 0018.
     */
    public record JiraConnectionResponse(
            String name,
            String baseUrl,
            JiraAuthType authType,
            String email,
            boolean active,
            boolean siteConfigured,
            boolean tokenConfigured,
            boolean usable,
            // OAuth 2.0 (ADR 0018) -- all non-secret status only:
            boolean oauthConfigured,
            boolean oauthConnected,
            String oauthSiteUrl,
            boolean reauthorizationRequired
    ) {
        public static JiraConnectionResponse from(JiraConnectionSettings settings, OAuthStatus oauth) {
            return new JiraConnectionResponse(
                    settings.name(),
                    settings.baseUrl(),
                    settings.authType(),
                    settings.email(),
                    settings.active(),
                    settings.siteConfigured(),
                    settings.tokenConfigured(),
                    settings.authType() == JiraAuthType.OAUTH2 ? oauth.connected() && settings.siteConfigured()
                            : settings.usable(),
                    oauth.configured(),
                    oauth.connected(),
                    oauth.siteUrl(),
                    oauth.reauthorizationRequired()
            );
        }
    }

    /** Non-secret settings only. API tokens and OAuth credentials remain external
     * server configuration. {@code authType} is optional -- omit it to keep the
     * current value. */
    public record UpdateJiraConnectionRequest(
            @NotBlank String name,
            @NotBlank String baseUrl,
            String email,
            boolean active,
            JiraAuthType authType
    ) {
    }

    public record JiraAuthorizationUrlResponse(String authorizationUrl) {
    }

    public record JiraConnectionTestResponse(boolean success, String message) {
        public static JiraConnectionTestResponse from(JiraConnectionTestResult result) {
            return new JiraConnectionTestResponse(result.success(), result.message());
        }
    }

    public record JiraIssuePreviewResponse(
            String externalId,
            String externalKey,
            String url,
            String summary,
            String issueType,
            String status,
            String description
    ) {
        public static JiraIssuePreviewResponse from(ExternalRequirementInfo info) {
            return new JiraIssuePreviewResponse(
                    info.externalId(), info.externalKey(), info.url(), info.summary(),
                    info.issueType(), info.status(), info.description()
            );
        }
    }
}
