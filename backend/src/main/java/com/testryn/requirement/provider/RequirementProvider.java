package com.testryn.requirement.provider;

import com.testryn.requirement.domain.RequirementProviderType;

import java.util.Optional;

/**
 * Abstraction over an external requirement/issue source (Jira today, potentially
 * GitHub or Azure DevOps later). See ADR 0005 — Testryn's core domain must never
 * depend on a concrete provider being configured or reachable.
 */
public interface RequirementProvider {

    RequirementProviderType type();

    /**
     * Best-effort lookup of display information for an external key. Returns
     * {@link Optional#empty()} when the provider is not configured, not reachable, or
     * the key is unknown — callers must treat that as "no enrichment available", never
     * as an error that blocks creating a {@code RequirementLink}.
     */
    Optional<ExternalRequirementInfo> fetch(String externalKey);
}
