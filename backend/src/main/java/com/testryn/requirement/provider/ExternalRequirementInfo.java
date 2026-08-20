package com.testryn.requirement.provider;

/**
 * Read-only, display-oriented information about an external requirement/issue,
 * fetched on a best-effort basis to enrich a {@link com.testryn.requirement.domain.RequirementLink}.
 */
public record ExternalRequirementInfo(
        String externalId,
        String externalKey,
        String url,
        String summary
) {
}
