package com.testryn.requirement.service;

import com.testryn.common.error.ConflictException;
import com.testryn.project.domain.Project;
import com.testryn.requirement.domain.RequirementLink;
import com.testryn.requirement.domain.RequirementProviderType;
import com.testryn.requirement.provider.ExternalRequirementInfo;
import com.testryn.requirement.provider.RequirementProvider;
import com.testryn.requirement.repository.RequirementLinkRepository;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCasePriority;
import com.testryn.testcase.service.TestCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * See ADR 0005: creating a RequirementLink must never depend on the external
 * provider being reachable — enrichment is strictly best-effort.
 */
@ExtendWith(MockitoExtension.class)
class RequirementLinkServiceTest {

    @Mock
    private RequirementLinkRepository requirementLinkRepository;
    @Mock
    private TestCaseService testCaseService;

    private TestCase testCase;
    private final UUID testCaseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Project project = Project.create("BITLESS", "Bitless", null);
        testCase = TestCase.create(project, "BITLESS-TC-1", 1, TestCasePriority.MEDIUM, Set.of());
        lenient().when(requirementLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsLinkWithoutAnyConfiguredProvider() {
        RequirementLinkService service = new RequirementLinkService(requirementLinkRepository, testCaseService, List.of());
        when(testCaseService.getById(testCaseId)).thenReturn(testCase);

        RequirementLink link = service.create(testCaseId, RequirementProviderType.JIRA, "BIT-27",
                "https://example.atlassian.net/browse/BIT-27", "Login story");

        assertThat(link.getExternalKey()).isEqualTo("BIT-27");
        assertThat(link.getUrl()).isEqualTo("https://example.atlassian.net/browse/BIT-27");
        assertThat(link.getSummary()).isEqualTo("Login story");
    }

    @Test
    void enrichesMissingSummaryFromProviderWhenAvailable() {
        RequirementProvider fakeJira = new RequirementProvider() {
            @Override
            public RequirementProviderType type() {
                return RequirementProviderType.JIRA;
            }

            @Override
            public Optional<ExternalRequirementInfo> fetch(String externalKey) {
                return Optional.of(new ExternalRequirementInfo("10042", externalKey,
                        "https://example.atlassian.net/browse/" + externalKey, "Fetched summary"));
            }
        };
        RequirementLinkService service = new RequirementLinkService(requirementLinkRepository, testCaseService, List.of(fakeJira));
        when(testCaseService.getById(testCaseId)).thenReturn(testCase);

        RequirementLink link = service.create(testCaseId, RequirementProviderType.JIRA, "BIT-27", null, null);

        assertThat(link.getSummary()).isEqualTo("Fetched summary");
        assertThat(link.getUrl()).isEqualTo("https://example.atlassian.net/browse/BIT-27");
        assertThat(link.getExternalId()).isEqualTo("10042");
    }

    @Test
    void rejectsDuplicateLinkForSameProviderAndKey() {
        RequirementLinkService service = new RequirementLinkService(requirementLinkRepository, testCaseService, List.of());
        when(testCaseService.getById(testCaseId)).thenReturn(testCase);
        when(requirementLinkRepository.existsByTestCaseIdAndProviderAndExternalKey(
                testCaseId, RequirementProviderType.JIRA, "BIT-27")).thenReturn(true);

        assertThatThrownBy(() -> service.create(testCaseId, RequirementProviderType.JIRA, "BIT-27",
                "https://example.atlassian.net/browse/BIT-27", null))
                .isInstanceOf(ConflictException.class);
    }
}
