package com.testryn.integration.jira.web;

import com.testryn.integration.jira.JiraAuthType;
import com.testryn.integration.jira.JiraIssueClient.JiraConnectionTestResult;
import com.testryn.integration.jira.JiraProperties;
import com.testryn.requirement.provider.ExternalRequirementInfo;

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
            boolean tokenConfigured,
            boolean usable
    ) {
        public static JiraConnectionResponse from(JiraProperties properties) {
            return new JiraConnectionResponse(
                    properties.getName(),
                    properties.getBaseUrl(),
                    properties.getAuthType(),
                    properties.getEmail(),
                    properties.isActive(),
                    properties.isTokenConfigured(),
                    properties.isUsable()
            );
        }
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
