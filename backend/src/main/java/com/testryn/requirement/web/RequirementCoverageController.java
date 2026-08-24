package com.testryn.requirement.web;

import com.testryn.requirement.service.RequirementCoverageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.testryn.requirement.web.RequirementCoverageDtos.RequirementCoverageResponse;

/**
 * Read-only "which test cases cover this requirement" view (ADR 0014) -- the single
 * endpoint the Jira Forge issue panel calls, but provider-neutral: {@code provider}
 * is a plain query parameter, not baked into the path, so this stays a generic
 * requirement-coverage lookup that Jira happens to be the first caller of
 * (Abschnitt 6). Requires {@code testryn:read} like every other GET under
 * {@code /api/**} (ADR 0012) -- no new security rule needed.
 */
@RestController
public class RequirementCoverageController {

    private final RequirementCoverageService requirementCoverageService;

    public RequirementCoverageController(RequirementCoverageService requirementCoverageService) {
        this.requirementCoverageService = requirementCoverageService;
    }

    @Operation(
            summary = "Test cases covering a requirement, with steps and latest execution result",
            description = """
                    Resolves every test case linked to the given provider + externalKey (e.g. a Jira
                    issue key) in one call: current title/status/priority/preconditions/steps, and each
                    test case's most recently added execution result, if any. No link for the given
                    key returns an empty testCases list, not an error. Capped at `limit` entries
                    (default 20, max 100); `totalCount` reports the real total so a caller can offer
                    "view all" instead of silently truncating.
                    """
    )
    @GetMapping("/api/v1/requirement-links/coverage")
    public RequirementCoverageResponse coverage(
            @Parameter(description = "e.g. 'jira' -- case-insensitive") @RequestParam String provider,
            @Parameter(description = "e.g. 'EVAL-47' -- case-insensitive") @RequestParam String externalKey,
            @Parameter(description = "max test cases to return, default 20, max 100")
            @RequestParam(required = false) Integer limit) {
        return requirementCoverageService.getCoverage(
                requirementCoverageService.parseProvider(provider), externalKey, limit);
    }
}
