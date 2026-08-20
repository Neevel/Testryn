package com.testryn.testcase.repository;

import com.testryn.requirement.domain.RequirementLink;
import com.testryn.testcase.domain.TestCase;
import com.testryn.testcase.domain.TestCasePriority;
import com.testryn.testcase.domain.TestCaseStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic, optional filters for {@code GET /projects/{projectKey}/test-cases}
 * (Abschnitt 10/11 -- search by id/title/tag/requirement key/status/priority, usable
 * by both the UI and an AI agent checking for near-duplicates before creating a new
 * test case). Deliberately does not fetch {@code currentVersion.steps} here (a
 * collection fetch-join would force Hibernate to paginate the whole result set in
 * memory) -- callers initialize that separately per page, bounded by page size, not
 * dataset size.
 */
public final class TestCaseSpecifications {

    private TestCaseSpecifications() {
    }

    public static Specification<TestCase> hasProject(UUID projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    /** Matches the human-readable ID or the current version's title, case-insensitively. */
    public static Specification<TestCase> queryMatches(String queryText) {
        return (root, query, cb) -> {
            String pattern = "%" + queryText.toLowerCase() + "%";
            var version = root.join("currentVersion", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("humanId")), pattern),
                    cb.like(cb.lower(version.get("title")), pattern)
            );
        };
    }

    public static Specification<TestCase> hasTag(String tag) {
        return (root, query, cb) -> {
            query.distinct(true);
            var tags = root.join("tags", JoinType.INNER);
            return cb.equal(tags, tag);
        };
    }

    public static Specification<TestCase> hasStatus(TestCaseStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<TestCase> hasPriority(TestCasePriority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    /** Exact match, case-sensitive by design -- automationReference is a machine
     * identifier (e.g. {@code auth.login.valid}), not free text (Abschnitt 10). */
    public static Specification<TestCase> hasAutomationReference(String automationReference) {
        return (root, query, cb) -> cb.equal(root.get("automationReference"), automationReference);
    }

    /** Test case has at least one RequirementLink whose externalKey matches (case-insensitive). */
    public static Specification<TestCase> hasRequirementKey(String requirementKey) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var link = subquery.from(RequirementLink.class);
            subquery.select(link.get("testCase").get("id"))
                    .where(cb.equal(cb.upper(link.get("externalKey")), requirementKey.toUpperCase()));
            return root.get("id").in(subquery);
        };
    }

    /** project + currentVersion + tags eager-fetched; NOT currentVersion.steps (see class javadoc). */
    public static Specification<TestCase> fetchForResponse() {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("project", JoinType.LEFT);
                root.fetch("currentVersion", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }

    public static Specification<TestCase> combine(UUID projectId, String queryText, String tag,
                                                    String requirementKey, TestCaseStatus status,
                                                    TestCasePriority priority, String automationReference) {
        List<Specification<TestCase>> specs = new ArrayList<>();
        specs.add(hasProject(projectId));
        specs.add(fetchForResponse());
        if (queryText != null && !queryText.isBlank()) {
            specs.add(queryMatches(queryText));
        }
        if (tag != null && !tag.isBlank()) {
            specs.add(hasTag(tag));
        }
        if (requirementKey != null && !requirementKey.isBlank()) {
            specs.add(hasRequirementKey(requirementKey));
        }
        if (status != null) {
            specs.add(hasStatus(status));
        }
        if (priority != null) {
            specs.add(hasPriority(priority));
        }
        if (automationReference != null && !automationReference.isBlank()) {
            specs.add(hasAutomationReference(automationReference));
        }
        Specification<TestCase> combined = Specification.where(null);
        for (Specification<TestCase> spec : specs) {
            combined = combined.and(spec);
        }
        return combined;
    }
}
