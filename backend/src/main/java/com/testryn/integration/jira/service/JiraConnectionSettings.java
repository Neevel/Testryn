package com.testryn.integration.jira.service;

import com.testryn.integration.jira.JiraAuthType;
import org.springframework.util.StringUtils;

/** Immutable effective connection snapshot used by HTTP calls and the REST API. */
public record JiraConnectionSettings(
        String name,
        String baseUrl,
        String email,
        String apiToken,
        JiraAuthType authType,
        boolean active
) {
    public boolean tokenConfigured() { return StringUtils.hasText(apiToken); }

    /** A Jira site can be selected and reachable even when optional server-side
     * credentials for issue enrichment have not been supplied yet. */
    public boolean siteConfigured() {
        return active && StringUtils.hasText(baseUrl);
    }

    public boolean configured() {
        return authType == JiraAuthType.API_TOKEN
                && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(email)
                && tokenConfigured();
    }

    public boolean usable() { return active && configured(); }
}
