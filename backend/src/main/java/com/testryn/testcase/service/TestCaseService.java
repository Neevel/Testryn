package com.testryn.testcase.service;

import com.testryn.common.error.BadRequestException;
import com.testryn.common.error.ConflictException;
import com.testryn.common.error.NotFoundException;
import com.testryn.project.domain.Project;
import com.testryn.project.service.ProjectService;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCasePriority;
import com.testryn.testcase.domain.TestCaseStatus;
import com.testryn.testcase.domain.TestCaseVersion;
import com.testryn.testcase.domain.TestStep;
import com.testryn.testcase.repository.TestCaseRepository;
import com.testryn.testcase.repository.TestCaseSpecifications;
import com.testryn.testcase.repository.TestCaseVersionRepository;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.testryn.testcase.service.TestCaseCommands.CreateTestCaseCommand;
import static com.testryn.testcase.service.TestCaseCommands.StepCommand;
import static com.testryn.testcase.service.TestCaseCommands.UpdateTestCaseCommand;
import static com.testryn.testcase.service.TestCaseCommands.UpdateTestCaseDefinitionCommand;

@Service
@Transactional
public class TestCaseService {

    /** Machine-friendly identifier only: letters/digits/dot/underscore/hyphen, e.g.
     * {@code auth.login.valid} -- Abschnitt 8 ("maschinenfreundlich"). ADR 0009's
     * original character set additionally allows {@code #} as of ADR 0013: the JUnit
     * XML publish-junit importer's {@code classname#name} convention (e.g.
     * {@code com.example.LoginTest#successfulLogin}) is exactly the same separator
     * JUnit/IDE tooling itself already uses for a class-plus-method reference, and is
     * not ambiguous with any other allowed character. */
    private static final Pattern AUTOMATION_REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9_.#\\-]{1,200}$");

    private final TestCaseRepository testCaseRepository;
    private final TestCaseVersionRepository testCaseVersionRepository;
    private final ProjectService projectService;

    public TestCaseService(TestCaseRepository testCaseRepository,
                            TestCaseVersionRepository testCaseVersionRepository,
                            ProjectService projectService) {
        this.testCaseRepository = testCaseRepository;
        this.testCaseVersionRepository = testCaseVersionRepository;
        this.projectService = projectService;
    }

    public TestCase create(String projectKey, CreateTestCaseCommand command) {
        if (command.steps() == null || command.steps().isEmpty()) {
            throw new BadRequestException("A test case requires at least one step");
        }
        Project project = projectService.getByKey(projectKey);
        int nextSequence = testCaseRepository.findMaxSequenceNumber(project.getId()) + 1;
        String humanId = project.getKey() + "-TC-" + nextSequence;
        String automationReference = normalizeAutomationReference(command.automationReference());
        requireUniqueAutomationReference(project.getId(), automationReference, null);

        TestCase testCase = TestCase.create(project, humanId, nextSequence, command.priority(), command.tags(),
                automationReference);
        testCase = testCaseRepository.save(testCase);

        TestCaseVersion version = TestCaseVersion.create(
                testCase, 1, command.title(), command.description(), command.preconditions(),
                toSteps(command.steps()));
        version = testCaseVersionRepository.save(version);

        testCase.assignVersion(version);
        return testCase;
    }

    @Transactional(readOnly = true)
    public TestCase getById(UUID id) {
        return testCaseRepository.findById(id).orElseThrow(() -> NotFoundException.of("TestCase", id));
    }

    @Transactional(readOnly = true)
    public TestCase getByHumanId(String humanId) {
        return testCaseRepository.findByHumanId(humanId)
                .orElseThrow(() -> NotFoundException.of("TestCase", humanId));
    }

    @Transactional(readOnly = true)
    public List<TestCase> findByProjectKey(String projectKey) {
        Project project = projectService.getByKey(projectKey);
        return testCaseRepository.findByProjectIdOrderByHumanIdAsc(project.getId());
    }

    /**
     * Search/filter/paginate (ADR 0008) -- backs both the UI's Test Case table and
     * an AI agent's near-duplicate check before creating a new test case (Abschnitt
     * 10/11). Every filter is optional; {@code null}/blank means "don't filter on
     * this field".
     */
    @Transactional(readOnly = true)
    public Page<TestCase> search(String projectKey, String query, String tag, String requirementKey,
                                  TestCaseStatus status, TestCasePriority priority, String automationReference,
                                  Pageable pageable) {
        Project project = projectService.getByKey(projectKey);
        var specification = TestCaseSpecifications.combine(
                project.getId(), query, tag, requirementKey, status, priority, automationReference);
        Page<TestCase> page = testCaseRepository.findAll(specification, pageable);
        // currentVersion.steps is deliberately not fetch-joined in the search query
        // (would force in-memory pagination, ADR 0008) -- initialize it per page
        // instead, bounded by page size rather than the whole result set.
        page.forEach(tc -> {
            if (tc.getCurrentVersion() != null) {
                Hibernate.initialize(tc.getCurrentVersion().getSteps());
            }
            Hibernate.initialize(tc.getTags());
        });
        return page;
    }

    @Transactional(readOnly = true)
    public List<TestCaseVersion> findVersions(UUID testCaseId) {
        // Ensure the test case exists so callers get a 404 instead of an empty list.
        getById(testCaseId);
        return testCaseVersionRepository.findByTestCaseIdOrderByVersionNumberDesc(testCaseId);
    }

    public TestCase update(UUID id, UpdateTestCaseCommand command) {
        if (command.steps() == null || command.steps().isEmpty()) {
            throw new BadRequestException("A test case requires at least one step");
        }
        TestCase testCase = getById(id);

        if (contentChanged(testCase.getCurrentVersion(), command)) {
            int nextVersionNumber = testCase.getCurrentVersion() == null
                    ? 1
                    : testCase.getCurrentVersion().getVersionNumber() + 1;
            TestCaseVersion newVersion = TestCaseVersion.create(
                    testCase, nextVersionNumber, command.title(), command.description(),
                    command.preconditions(), toSteps(command.steps()));
            newVersion = testCaseVersionRepository.save(newVersion);
            testCase.assignVersion(newVersion);
        }

        String automationReference = normalizeAutomationReference(command.automationReference());
        requireUniqueAutomationReference(testCase.getProject().getId(), automationReference, testCase.getId());
        testCase.updateMetadata(command.status(), command.priority(), command.tags(), automationReference);
        return testCase;
    }

    public TestCase updateDefinition(UUID id, UpdateTestCaseDefinitionCommand command) {
        if (command.steps() == null || command.steps().isEmpty()) {
            throw new BadRequestException("A test case requires at least one step");
        }
        TestCase testCase = getById(id);
        TestCaseVersion current = testCase.getCurrentVersion();
        int currentVersion = current == null ? 0 : current.getVersionNumber();
        if (command.expectedVersion() != currentVersion) {
            throw new ConflictException("Test case was changed since it was opened; reload it before saving");
        }

        var fullCommand = new UpdateTestCaseCommand(
                command.title(), command.description(), command.preconditions(),
                command.steps(), testCase.getStatus(), testCase.getPriority(), testCase.getTags(),
                testCase.getAutomationReference());
        return update(id, fullCommand);
    }

    /** {@code null}/blank means "not automated" -- trims otherwise, never returns
     * blank. Rejects anything that isn't a machine-friendly token (Abschnitt 8). */
    private String normalizeAutomationReference(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (!AUTOMATION_REFERENCE_PATTERN.matcher(trimmed).matches()) {
            throw new BadRequestException(
                    "automationReference must be a machine-friendly token (letters, digits, '.', '_', '-', '#' only), "
                            + "e.g. 'auth.login.valid' or 'com.example.LoginTest#successfulLogin': " + raw);
        }
        return trimmed;
    }

    /** Project-scoped uniqueness (Abschnitt 10): DB has the authoritative unique
     * index too (defense in depth against races), this gives a clear 409 instead of
     * a generic constraint-violation 409. */
    private void requireUniqueAutomationReference(UUID projectId, String automationReference, UUID excludeTestCaseId) {
        if (automationReference == null) {
            return;
        }
        boolean exists = excludeTestCaseId == null
                ? testCaseRepository.existsByProject_IdAndAutomationReference(projectId, automationReference)
                : testCaseRepository.existsByProject_IdAndAutomationReferenceAndIdNot(projectId, automationReference, excludeTestCaseId);
        if (exists) {
            throw new ConflictException(
                    "automationReference '" + automationReference + "' is already used by another test case in this project");
        }
    }

    private boolean contentChanged(TestCaseVersion current, UpdateTestCaseCommand command) {
        if (current == null) {
            return true;
        }
        if (!Objects.equals(current.getTitle(), command.title())
                || !Objects.equals(current.getDescription(), command.description())
                || !Objects.equals(current.getPreconditions(), command.preconditions())) {
            return true;
        }
        List<TestStep> currentSteps = current.getSteps();
        List<StepCommand> newSteps = command.steps();
        if (currentSteps.size() != newSteps.size()) {
            return true;
        }
        for (int i = 0; i < currentSteps.size(); i++) {
            TestStep existing = currentSteps.get(i);
            StepCommand incoming = newSteps.get(i);
            if (!Objects.equals(existing.getAction(), incoming.action())
                    || !Objects.equals(existing.getInputData(), incoming.inputData())
                    || !Objects.equals(existing.getExpectedResult(), incoming.expectedResult())) {
                return true;
            }
        }
        return false;
    }

    private List<TestStep> toSteps(List<StepCommand> stepCommands) {
        List<TestStep> steps = new ArrayList<>();
        int order = 1;
        for (StepCommand stepCommand : stepCommands) {
            steps.add(new TestStep(order++, stepCommand.action(), stepCommand.inputData(), stepCommand.expectedResult()));
        }
        return steps;
    }
}
