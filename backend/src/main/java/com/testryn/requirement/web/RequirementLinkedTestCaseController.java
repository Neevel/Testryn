package com.testryn.requirement.web;

import com.testryn.requirement.domain.RequirementProviderType;
import com.testryn.requirement.service.RequirementLinkService;
import com.testryn.testcase.domain.TestCasePriority;
import com.testryn.testcase.service.TestCaseCommands;
import com.testryn.testcase.web.TestCaseDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
public class RequirementLinkedTestCaseController {

    private final RequirementLinkService requirementLinkService;

    public RequirementLinkedTestCaseController(RequirementLinkService requirementLinkService) {
        this.requirementLinkService = requirementLinkService;
    }

    @PostMapping("/api/v1/requirement-links/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public TestCaseDtos.TestCaseResponse create(@Valid @RequestBody CreateLinkedTestCaseRequest request) {
        var command = new TestCaseCommands.CreateTestCaseCommand(
                request.title(), request.description(), request.preconditions(), request.priority(), request.tags(),
                request.steps().stream().map(step -> new TestCaseCommands.StepCommand(
                        step.action(), step.inputData(), step.expectedResult())).toList(), request.automationReference());
        return TestCaseDtos.TestCaseResponse.from(requirementLinkService.createLinkedTestCase(
                request.projectKey(), command, request.provider(), request.externalKey(), request.url(), request.summary()));
    }

    public record CreateLinkedTestCaseRequest(
            @NotBlank String projectKey,
            @NotBlank String title,
            String description,
            String preconditions,
            @NotNull TestCasePriority priority,
            Set<String> tags,
            @NotEmpty @Valid List<TestCaseDtos.StepRequest> steps,
            String automationReference,
            @NotNull RequirementProviderType provider,
            @NotBlank String externalKey,
            String url,
            String summary
    ) {}
}
