package com.testryn.testcase.service;

import com.testryn.project.domain.Project;
import com.testryn.project.service.ProjectService;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCasePriority;
import com.testryn.testcase.domain.TestCaseStatus;
import com.testryn.testcase.repository.TestCaseRepository;
import com.testryn.testcase.repository.TestCaseVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.testryn.testcase.service.TestCaseCommands.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Domain-level tests for the versioning rule from ADR 0002: a content change creates
 * a new, immutable {@code TestCaseVersion}; a metadata-only change does not.
 */
@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private TestCaseVersionRepository testCaseVersionRepository;
    @Mock
    private ProjectService projectService;

    private TestCaseService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new TestCaseService(testCaseRepository, testCaseVersionRepository, projectService);
        project = Project.create("BITLESS", "Bitless", null);

        when(testCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testCaseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createAssignsHumanIdAndFirstVersion() {
        when(projectService.getByKey("BITLESS")).thenReturn(project);
        when(testCaseRepository.findMaxSequenceNumber(any())).thenReturn(0);

        var command = new CreateTestCaseCommand(
                "Erfolgreiche Anmeldung", "desc", "preconditions", TestCasePriority.HIGH, Set.of("smoke"),
                List.of(new StepCommand("Login-Seite öffnen", "Login-Formular wird angezeigt")), null);

        TestCase created = service.create("BITLESS", command);

        assertThat(created.getHumanId()).isEqualTo("BITLESS-TC-1");
        assertThat(created.getCurrentVersion().getVersionNumber()).isEqualTo(1);
        assertThat(created.getCurrentVersion().getSteps()).hasSize(1);
        assertThat(created.getStatus()).isEqualTo(TestCaseStatus.DRAFT);
    }

    @Test
    void updateWithUnchangedContentDoesNotCreateNewVersion() {
        TestCase testCase = createInitialTestCase();
        var initialVersion = testCase.getCurrentVersion();
        when(testCaseRepository.findById(testCase.getId())).thenReturn(java.util.Optional.of(testCase));

        var command = new UpdateTestCaseCommand(
                "Erfolgreiche Anmeldung", "desc", "preconditions",
                List.of(new StepCommand("Login-Seite öffnen", "Login-Formular wird angezeigt")),
                TestCaseStatus.ACTIVE, TestCasePriority.CRITICAL, Set.of("smoke", "regression"), null);

        TestCase updated = service.update(testCase.getId(), command);

        assertThat(updated.getCurrentVersion()).isSameAs(initialVersion);
        assertThat(updated.getCurrentVersion().getVersionNumber()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(TestCaseStatus.ACTIVE);
        assertThat(updated.getPriority()).isEqualTo(TestCasePriority.CRITICAL);
        assertThat(updated.getTags()).containsExactlyInAnyOrder("smoke", "regression");
    }

    @Test
    void updateWithChangedContentCreatesNewVersionAndKeepsOldOneIntact() {
        TestCase testCase = createInitialTestCase();
        var version1 = testCase.getCurrentVersion();
        when(testCaseRepository.findById(testCase.getId())).thenReturn(java.util.Optional.of(testCase));

        var command = new UpdateTestCaseCommand(
                "Erfolgreiche Anmeldung (überarbeitet)", "neue desc", "preconditions",
                List.of(new StepCommand("Login-Seite öffnen", "Login-Formular wird angezeigt"),
                        new StepCommand("Zugangsdaten eingeben", "Felder akzeptieren Eingabe")),
                TestCaseStatus.ACTIVE, TestCasePriority.HIGH, Set.of("smoke"), null);

        TestCase updated = service.update(testCase.getId(), command);

        assertThat(updated.getCurrentVersion()).isNotSameAs(version1);
        assertThat(updated.getCurrentVersion().getVersionNumber()).isEqualTo(2);
        assertThat(updated.getCurrentVersion().getSteps()).hasSize(2);

        // The historical version must remain exactly as it was.
        assertThat(version1.getVersionNumber()).isEqualTo(1);
        assertThat(version1.getTitle()).isEqualTo("Erfolgreiche Anmeldung");
        assertThat(version1.getSteps()).hasSize(1);
    }

    @Test
    void definitionUpdateCreatesVersionWithoutChangingMetadata() {
        TestCase testCase = createInitialTestCase();
        when(testCaseRepository.findById(testCase.getId())).thenReturn(java.util.Optional.of(testCase));

        TestCase updated = service.updateDefinition(testCase.getId(), new UpdateTestCaseDefinitionCommand(
                1, "Login edited", "new description", "new preconditions",
                List.of(new StepCommand("Open page", "Page is visible"),
                        new StepCommand("Sign in", "Dashboard is visible"))));

        assertThat(updated.getCurrentVersion().getVersionNumber()).isEqualTo(2);
        assertThat(updated.getCurrentVersion().getTitle()).isEqualTo("Login edited");
        assertThat(updated.getPriority()).isEqualTo(TestCasePriority.HIGH);
        assertThat(updated.getStatus()).isEqualTo(TestCaseStatus.DRAFT);
        assertThat(updated.getTags()).containsExactly("smoke");
    }

    @Test
    void definitionUpdateRejectsStaleVersion() {
        TestCase testCase = createInitialTestCase();
        when(testCaseRepository.findById(testCase.getId())).thenReturn(java.util.Optional.of(testCase));

        assertThatThrownBy(() -> service.updateDefinition(testCase.getId(),
                new UpdateTestCaseDefinitionCommand(2, "Stale", null, null,
                        List.of(new StepCommand("A", "B")))))
                .isInstanceOf(com.testryn.common.error.ConflictException.class)
                .hasMessageContaining("reload");
        assertThat(testCase.getCurrentVersion().getVersionNumber()).isEqualTo(1);
    }

    private TestCase createInitialTestCase() {
        when(projectService.getByKey("BITLESS")).thenReturn(project);
        when(testCaseRepository.findMaxSequenceNumber(any())).thenReturn(0);
        var command = new CreateTestCaseCommand(
                "Erfolgreiche Anmeldung", "desc", "preconditions", TestCasePriority.HIGH, Set.of("smoke"),
                List.of(new StepCommand("Login-Seite öffnen", "Login-Formular wird angezeigt")), null);
        return service.create("BITLESS", command);
    }
}
