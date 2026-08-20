package com.testryn.requirement.web;

import com.testryn.requirement.service.RequirementLinkService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.testryn.requirement.web.RequirementLinkDtos.RequirementLinkResponse;

/**
 * Project-scoped, read-only aggregate over every {@code RequirementLink} in a
 * project, across all its test cases -- backs the project-level "Requirements"
 * navigation tab (Abschnitt 27). Mutations still go through
 * {@link RequirementLinkController} (test-case-scoped), which is the single owner of
 * requirement-link write operations.
 */
@RestController
public class ProjectRequirementsController {

    private final RequirementLinkService requirementLinkService;

    public ProjectRequirementsController(RequirementLinkService requirementLinkService) {
        this.requirementLinkService = requirementLinkService;
    }

    @Operation(summary = "All requirement links across every test case in a project")
    @GetMapping("/api/v1/projects/{projectKey}/requirements")
    public List<RequirementLinkResponse> listForProject(@PathVariable String projectKey) {
        return requirementLinkService.findByProjectKey(projectKey).stream().map(RequirementLinkResponse::from).toList();
    }
}
