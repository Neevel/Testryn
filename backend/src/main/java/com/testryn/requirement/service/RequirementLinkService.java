package com.testryn.requirement.service;

import com.testryn.common.error.ConflictException;
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
    private final Map<RequirementProviderType, RequirementProvider> providersByType;

    public RequirementLinkService(RequirementLinkRepository requirementLinkRepository,
                                   TestCaseService testCaseService,
                                   List<RequirementProvider> providers) {
        this.requirementLinkRepository = requirementLinkRepository;
        this.testCaseService = testCaseService;
        this.providersByType = providers.stream()
                .collect(java.util.stream.Collectors.toMap(RequirementProvider::type, Function.identity()));
    }

    public RequirementLink create(UUID testCaseId, RequirementProviderType provider, String externalKey,
                                   String url, String summary) {
        TestCase testCase = testCaseService.getById(testCaseId);

        if (requirementLinkRepository.existsByTestCaseIdAndProviderAndExternalKey(testCaseId, provider, externalKey)) {
            throw new ConflictException(
                    "Requirement link already exists for %s -> %s:%s".formatted(testCase.getHumanId(), provider, externalKey));
        }

        String resolvedUrl = url;
        String resolvedSummary = summary;
        String externalId = null;

        if (!StringUtils.hasText(resolvedSummary) || !StringUtils.hasText(resolvedUrl)) {
            Optional<ExternalRequirementInfo> enrichment = tryFetch(provider, externalKey);
            if (enrichment.isPresent()) {
                ExternalRequirementInfo info = enrichment.get();
                externalId = info.externalId();
                resolvedUrl = StringUtils.hasText(resolvedUrl) ? resolvedUrl : info.url();
                resolvedSummary = StringUtils.hasText(resolvedSummary) ? resolvedSummary : info.summary();
            }
        }

        if (!StringUtils.hasText(resolvedUrl)) {
            throw new IllegalArgumentException("A requirement link requires a url (none provided and none could be resolved)");
        }

        RequirementLink link = RequirementLink.create(testCase, provider, externalId, externalKey, resolvedUrl, resolvedSummary);
        return requirementLinkRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<RequirementLink> findByTestCase(UUID testCaseId) {
        testCaseService.getById(testCaseId);
        return requirementLinkRepository.findByTestCaseIdOrderByCreatedAtAsc(testCaseId);
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
}
