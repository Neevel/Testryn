package com.testryn.requirement.provider;

/**
 * Read-only, display-oriented information about an external requirement/issue,
 * fetched on a best-effort basis to enrich a {@link com.testryn.requirement.domain.RequirementLink}
 * or to preview a link before it is created. Deliberately provider-agnostic (ADR
 * 0005) -- {@code issueType}/{@code status} are generic labels every provider this
 * product is likely to support (Jira, GitHub, Azure DevOps) can populate in some
 * form, not Jira-specific concepts.
 */
public record ExternalRequirementInfo(
        String externalId,
        String externalKey,
        String url,
        String summary,
        String issueType,
        String status,
        String description
) {
}
