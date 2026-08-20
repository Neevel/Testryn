package com.testryn.requirement.web;

import com.testryn.requirement.service.RequirementLinkService;
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
}
