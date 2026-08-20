package com.testryn.requirement.web;

import com.testryn.requirement.service.RequirementLinkService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.testryn.requirement.web.RequirementLinkDtos.*;

@RestController
@RequestMapping("/api/v1/test-cases/{testCaseId}/requirements")
public class RequirementLinkController {

    private final RequirementLinkService requirementLinkService;

    public RequirementLinkController(RequirementLinkService requirementLinkService) {
        this.requirementLinkService = requirementLinkService;
    }

    @Operation(
            summary = "Link a requirement (e.g. a Jira issue) to a test case",
            description = """
                    `url` is required either explicitly or resolvable by enrichment: if the configured
                    provider (currently Jira) can reach the issue, issueType/status/description/summary/url are
                    filled in automatically (any explicitly supplied url/summary win). If the provider is
                    unreachable, the caller must supply `url` itself. Duplicate links (same provider +
                    externalKey on the same test case) are rejected with 409 Conflict.
                    """
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementLinkResponse create(@PathVariable UUID testCaseId,
                                           @Valid @RequestBody CreateRequirementLinkRequest request) {
        var link = requirementLinkService.create(
                testCaseId, request.provider(), request.externalKey(), request.url(), request.summary());
        return RequirementLinkResponse.from(link);
    }

    @GetMapping
    public List<RequirementLinkResponse> list(@PathVariable UUID testCaseId) {
        return requirementLinkService.findByTestCase(testCaseId).stream().map(RequirementLinkResponse::from).toList();
    }

    @DeleteMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID testCaseId, @PathVariable UUID linkId) {
        // Only removes Testryn's RequirementLink, never the external issue itself.
        requirementLinkService.remove(testCaseId, linkId);
    }
}
