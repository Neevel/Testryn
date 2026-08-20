package com.testryn.requirement.web;

import com.testryn.requirement.domain.RequirementLink;
import com.testryn.requirement.domain.RequirementProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class RequirementLinkDtos {

    private RequirementLinkDtos() {
    }

    public record CreateRequirementLinkRequest(
            @NotNull RequirementProviderType provider,
            @NotBlank String externalKey,
            String url,
            String summary
    ) {
    }

    public record RequirementLinkResponse(
            UUID id,
            UUID testCaseId,
            String testCaseHumanId,
            String testCaseTitle,
            RequirementProviderType provider,
            String externalId,
            String externalKey,
            String url,
            String summary,
            String issueType,
            String status,
            String description,
            Instant createdAt
    ) {
        public static RequirementLinkResponse from(RequirementLink link) {
            var testCase = link.getTestCase();
            return new RequirementLinkResponse(
                    link.getId(),
                    testCase.getId(),
                    testCase.getHumanId(),
                    testCase.getCurrentVersion() == null ? null : testCase.getCurrentVersion().getTitle(),
                    link.getProvider(),
                    link.getExternalId(),
                    link.getExternalKey(),
                    link.getUrl(),
                    link.getSummary(),
                    link.getIssueType(),
                    link.getStatus(),
                    link.getDescription(),
                    link.getCreatedAt()
            );
        }
    }
}
