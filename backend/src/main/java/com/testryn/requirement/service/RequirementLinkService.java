package com.testryn.requirement.service;

import com.testryn.common.error.ConflictException;
import com.testryn.common.error.NotFoundException;
import com.testryn.project.service.ProjectService;
import com.testryn.requirement.domain.RequirementLink;
import com.testryn.requirement.domain.RequirementProviderType;
import com.testryn.requirement.provider.ExternalRequirementInfo;
import com.testryn.requirement.provider.RequirementProvider;
import com.testryn.requirement.repository.RequirementLinkRepository;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.service.TestCaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
@Transactional
public class RequirementLinkService {

    private static final Logger log = LoggerFactory.getLogger(RequirementLinkService.class);

    private final RequirementLinkRepository requirementLinkRepository;
    private final TestCaseService testCaseService;
    private final ProjectService projectService;
    private final Map<RequirementProviderType, RequirementProvider> providersByType;

    public RequirementLinkService(RequirementLinkRepository requirementLinkRepository,
                                   TestCaseService testCaseService,
                                   ProjectService projectService,
                                   List<RequirementProvider> providers) {
        this.requirementLinkRepository = requirementLinkRepository;
        this.testCaseService = testCaseService;
        this.projectService = projectService;
        this.providersByType = providers.stream()
                .collect(java.util.stream.Collectors.toMap(RequirementProvider::type, Function.identity()));
    }

    public RequirementLink create(UUID testCaseId, RequirementProviderType provider, String externalKey,
                                   String url, String summary) {
        TestCase testCase = testCaseService.getById(testCaseId);
        String normalizedKey = normalizeKey(externalKey);

        if (requirementLinkRepository.existsByTestCaseIdAndProviderAndExternalKey(testCaseId, provider, normalizedKey)) {
            throw new ConflictException(
                    "Requirement link already exists for %s -> %s:%s".formatted(testCase.getHumanId(), provider, normalizedKey));
        }

        String resolvedUrl = url;
        String resolvedSummary = summary;
        String externalId = null;
        String issueType = null;
        String status = null;
        String description = null;

        // Always attempt enrichment (not just when url/summary are missing): even
        // when the caller supplied url/summary explicitly, issueType/status/
        // description can only come from the provider. Best-effort -- never blocks
        // link creation (ADR 0005).
        Optional<ExternalRequirementInfo> enrichment = tryFetch(provider, normalizedKey);
        if (enrichment.isPresent()) {
            ExternalRequirementInfo info = enrichment.get();
            externalId = info.externalId();
            resolvedUrl = StringUtils.hasText(resolvedUrl) ? resolvedUrl : info.url();
            resolvedSummary = StringUtils.hasText(resolvedSummary) ? resolvedSummary : info.summary();
            issueType = info.issueType();
            status = info.status();
            description = info.description();
        }

        if (!StringUtils.hasText(resolvedUrl)) {
            throw new IllegalArgumentException("A requirement link requires a url (none provided and none could be resolved)");
        }

        RequirementLink link = RequirementLink.create(testCase, provider, externalId, normalizedKey, resolvedUrl,
                resolvedSummary, issueType, status, description);
        return requirementLinkRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<RequirementLink> findByTestCase(UUID testCaseId) {
        testCaseService.getById(testCaseId);
        return requirementLinkRepository.findByTestCaseIdOrderByCreatedAtAsc(testCaseId);
    }

    /** Backs the project-level "Requirements" navigation tab (Abschnitt 27). */
    @Transactional(readOnly = true)
    public List<RequirementLink> findByProjectKey(String projectKey) {
        var project = projectService.getByKey(projectKey);
        return requirementLinkRepository.findByTestCase_Project_IdOrderByCreatedAtDesc(project.getId());
    }

    public void remove(UUID testCaseId, UUID linkId) {
        testCaseService.getById(testCaseId);
        RequirementLink link = requirementLinkRepository.findById(linkId)
                .orElseThrow(() -> NotFoundException.of("RequirementLink", linkId));
        if (!link.getTestCase().getId().equals(testCaseId)) {
            throw new NotFoundException("RequirementLink %s does not belong to test case %s".formatted(linkId, testCaseId));
        }
        // Only removes Testryn's link, never touches the external issue itself.
        requirementLinkRepository.delete(link);
    }

    private Optional<ExternalRequirementInfo> tryFetch(RequirementProviderType provider, String externalKey) {
        RequirementProvider requirementProvider = providersByType.get(provider);
        if (requirementProvider == null) {
            return Optional.empty();
        }
        try {
            return requirementProvider.fetch(externalKey);
        } catch (Exception ex) {
            // Enrichment is best-effort only; Testryn must not depend on the external
            // system being reachable (ADR 0005).
            log.warn("Failed to enrich requirement link {}:{} from provider: {}", provider, externalKey, ex.getMessage());
            return Optional.empty();
        }
    }

    private String normalizeKey(String externalKey) {
        if (!StringUtils.hasText(externalKey)) {
            throw new IllegalArgumentException("externalKey must not be blank");
        }
        return externalKey.trim().toUpperCase();
    }
}
