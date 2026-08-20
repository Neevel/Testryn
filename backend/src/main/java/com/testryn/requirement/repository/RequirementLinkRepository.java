package com.testryn.requirement.repository;

import com.testryn.requirement.domain.RequirementLink;
import com.testryn.requirement.domain.RequirementProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RequirementLinkRepository extends JpaRepository<RequirementLink, UUID> {

    List<RequirementLink> findByTestCaseIdOrderByCreatedAtAsc(UUID testCaseId);

    boolean existsByTestCaseIdAndProviderAndExternalKey(UUID testCaseId, RequirementProviderType provider,
                                                          String externalKey);
}
