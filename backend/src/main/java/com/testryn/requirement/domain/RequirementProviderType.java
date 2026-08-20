package com.testryn.requirement.domain;

/**
 * Identifies the external system a {@link RequirementLink} points to. Adding a new
 * provider (e.g. GitHub, Azure DevOps) means adding a value here and a matching
 * {@code com.testryn.requirement.provider.RequirementProvider} implementation in a new
 * {@code integration.*} module — the domain model itself never changes (ADR 0005).
 */
public enum RequirementProviderType {
    JIRA
}
