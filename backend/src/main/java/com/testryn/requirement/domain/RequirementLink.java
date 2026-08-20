package com.testryn.requirement.domain;

import com.testryn.testcase.domain.TestCase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A generic link from a {@link TestCase} to a requirement/issue in an external
 * system. Deliberately provider-agnostic — see ADR 0005. Jira is the first provider,
 * but this entity itself has no Jira-specific fields.
 */
@Entity
@Table(name = "requirement_links")
public class RequirementLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private RequirementProviderType provider;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "external_key", nullable = false, length = 100)
    private String externalKey;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RequirementLink() {
        // for JPA
    }

    public static RequirementLink create(TestCase testCase, RequirementProviderType provider,
                                          String externalId, String externalKey, String url, String summary) {
        RequirementLink link = new RequirementLink();
        link.testCase = testCase;
        link.provider = provider;
        link.externalId = externalId;
        link.externalKey = externalKey;
        link.url = url;
        link.summary = summary;
        link.createdAt = Instant.now();
        return link;
    }

    public UUID getId() {
        return id;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public RequirementProviderType getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public String getUrl() {
        return url;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
