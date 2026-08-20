package com.testryn.integration.jira;

/**
 * How Testryn authenticates against a Jira connection. Only {@link #API_TOKEN} is
 * implemented today (HTTP Basic with an email + Atlassian API token); the enum
 * exists so {@link JiraRequirementProvider} and the connection-configuration API can
 * add {@link #OAUTH2} later without a breaking change to the provider architecture
 * (ADR 0005) -- an OAuth2 connection would still be a {@code RequirementProvider},
 * just with a different credential source and token-refresh mechanism.
 */
public enum JiraAuthType {
    API_TOKEN,
    OAUTH2
}
