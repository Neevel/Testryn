package com.testryn.requirement.repository;

import com.testryn.requirement.domain.RequirementLink;
import com.testryn.requirement.domain.RequirementProviderType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RequirementLinkRepository extends JpaRepository<RequirementLink, UUID> {

    @EntityGraph(attributePaths = {"testCase", "testCase.currentVersion"})
    List<RequirementLink> findByTestCaseIdOrderByCreatedAtAsc(UUID testCaseId);

    boolean existsByTestCaseIdAndProviderAndExternalKey(UUID testCaseId, RequirementProviderType provider,
                                                          String externalKey);

    /** Backs the project-level "Requirements" navigation tab (Abschnitt 27). */
    @EntityGraph(attributePaths = {"testCase", "testCase.currentVersion"})
    List<RequirementLink> findByTestCase_Project_IdOrderByCreatedAtDesc(UUID projectId);

    /**
     * The reverse lookup the Jira Forge issue panel (and any future provider's
     * equivalent) needs: given a provider + externalKey (e.g. Jira issue key
     * {@code EVAL-47}), which test cases cover it? Deliberately provider-neutral --
     * see ADR 0014 -- this repository, like the rest of the requirement module,
     * knows nothing Jira-specific; a Forge resolver is simply one caller that happens
     * to always pass {@code JIRA}.
     */
    @EntityGraph(attributePaths = {"testCase"})
    List<RequirementLink> findByProviderAndExternalKeyOrderByCreatedAtAsc(RequirementProviderType provider,
                                                                           String externalKey);
}
