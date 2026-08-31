package com.testryn.integration.jira.web;

import com.testryn.integration.jira.JiraAuthType;
import com.testryn.integration.jira.JiraIssueClient.JiraConnectionTestResult;
import com.testryn.integration.jira.service.JiraConnectionSettings;
import com.testryn.requirement.provider.ExternalRequirementInfo;
import jakarta.validation.constraints.NotBlank;

public final class JiraIntegrationDtos {

    private JiraIntegrationDtos() {
    }

    /**
     * Never includes the API token or any other secret -- only whether one is
     * configured ({@code tokenConfigured}). See AGENTS.md -> Sicherheit.
     */
    public record JiraConnectionResponse(
            String name,
            String baseUrl,
            JiraAuthType authType,
            String email,
            boolean active,
            boolean siteConfigured,
            boolean tokenConfigured,
            boolean usable
    ) {
        public static JiraConnectionResponse from(JiraConnectionSettings settings) {
            return new JiraConnectionResponse(
                    settings.name(),
                    settings.baseUrl(),
                    settings.authType(),
                    settings.email(),
                    settings.active(),
                    settings.siteConfigured(),
                    settings.tokenConfigured(),
                    settings.usable()
            );
        }
    }

    /** Non-secret settings only. API tokens remain external server configuration. */
    public record UpdateJiraConnectionRequest(
            @NotBlank String name,
            @NotBlank String baseUrl,
            String email,
            boolean active
    ) {
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
