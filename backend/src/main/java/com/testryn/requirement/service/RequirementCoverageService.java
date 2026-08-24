package com.testryn.requirement.service;

import com.testryn.common.error.BadRequestException;
import com.testryn.execution.domain.ExecutionTestCase;
import com.testryn.execution.repository.ExecutionTestCaseRepository;
import com.testryn.requirement.domain.RequirementLink;
import com.testryn.requirement.domain.RequirementProviderType;
import com.testryn.requirement.repository.RequirementLinkRepository;
import com.testryn.requirement.web.RequirementCoverageDtos.CoverageTestCaseResponse;
import com.testryn.requirement.web.RequirementCoverageDtos.RequirementCoverageResponse;
import com.testryn.requirement.web.RequirementCoverageDtos.RequirementRef;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Which test cases cover this requirement, with their steps and latest result?" --
 * the read view the Jira Forge issue panel is built on (ADR 0014), but reachable by
 * any caller: nothing here is Jira-specific, {@link RequirementProviderType} is
 * already a generic enum (Abschnitt 6).
 *
 * <p>Kept as its own service rather than folded into {@link RequirementLinkService}:
 * this one genuinely spans three aggregates (requirement links, test cases with
 * their current version/steps, and each test case's latest execution result) and
 * deserves to say so in its own name, whereas {@code RequirementLinkService} stays
 * focused on requirement-link CRUD.
 */
@Service
@Transactional(readOnly = true)
public class RequirementCoverageService {

    /** MVP pagination cap (Abschnitt 23): render the first page in full, point
     * elsewhere for the rest rather than silently truncating without saying so. */
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final RequirementLinkRepository requirementLinkRepository;
    private final TestCaseRepository testCaseRepository;
    private final ExecutionTestCaseRepository executionTestCaseRepository;

    public RequirementCoverageService(RequirementLinkRepository requirementLinkRepository,
                                       TestCaseRepository testCaseRepository,
                                       ExecutionTestCaseRepository executionTestCaseRepository) {
        this.requirementLinkRepository = requirementLinkRepository;
        this.testCaseRepository = testCaseRepository;
        this.executionTestCaseRepository = executionTestCaseRepository;
    }

    public RequirementProviderType parseProvider(String rawProvider) {
        if (!StringUtils.hasText(rawProvider)) {
            throw new BadRequestException("provider is required");
        }
        try {
            return RequirementProviderType.valueOf(rawProvider.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown provider '" + rawProvider + "'");
        }
    }

    /**
     * One Testryn request resolves the whole panel (Abschnitt 22): a single
     * requirement-link lookup, a single batch test-case fetch (with steps), and a
     * single batch "latest execution" fetch -- never one query per test case.
     */
    public RequirementCoverageResponse getCoverage(RequirementProviderType provider, String externalKey, Integer limit) {
        if (!StringUtils.hasText(externalKey)) {
            throw new BadRequestException("externalKey is required");
        }
        String normalizedKey = externalKey.trim().toUpperCase();
        int effectiveLimit = clampLimit(limit);

        List<RequirementLink> links =
                requirementLinkRepository.findByProviderAndExternalKeyOrderByCreatedAtAsc(provider, normalizedKey);

        // A requirement can legitimately be covered by more than one test case; the
        // same test case linking to the same requirement twice cannot happen
        // (RequirementLinkService enforces that uniqueness), but distinct() here
        // costs nothing and removes any doubt.
        List<UUID> allTestCaseIds = links.stream().map(l -> l.getTestCase().getId()).distinct().toList();
        int totalCount = allTestCaseIds.size();
        List<UUID> pageIds = allTestCaseIds.stream().limit(effectiveLimit).toList();

        if (pageIds.isEmpty()) {
            return new RequirementCoverageResponse(new RequirementRef(provider, normalizedKey), List.of(), 0);
        }

        Map<UUID, TestCase> testCasesById = testCaseRepository.findByIdIn(pageIds).stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity()));
        Map<UUID, ExecutionTestCase> latestByTestCaseId = latestExecutionsByTestCaseId(pageIds);

        // Preserve link order (oldest link first, matching the existing
        // project-requirements list's own ordering convention) rather than whatever
        // order findByIdIn happens to return.
        List<CoverageTestCaseResponse> testCases = pageIds.stream()
                .map(testCasesById::get)
                .filter(java.util.Objects::nonNull)
                .map(tc -> CoverageTestCaseResponse.from(tc, latestByTestCaseId))
                .toList();

        return new RequirementCoverageResponse(new RequirementRef(provider, normalizedKey), testCases, totalCount);
    }

    private Map<UUID, ExecutionTestCase> latestExecutionsByTestCaseId(List<UUID> testCaseIds) {
        Map<UUID, ExecutionTestCase> byTestCaseId = new LinkedHashMap<>();
        for (ExecutionTestCase etc : executionTestCaseRepository.findLatestByTestCaseIds(testCaseIds)) {
            // putIfAbsent: guard against the documented pathological tie (two
            // executions with the identical createdAt instant) returning two rows
            // for the same test case -- keep only the first one seen.
            byTestCaseId.putIfAbsent(etc.getTestCase().getId(), etc);
        }
        return byTestCaseId;
    }

    private int clampLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1) {
            throw new BadRequestException("limit must be at least 1");
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
