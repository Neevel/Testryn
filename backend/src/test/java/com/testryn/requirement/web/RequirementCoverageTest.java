package com.testryn.requirement.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.security.domain.ServiceTokenScope;
import com.testryn.security.service.ServiceTokenService;
import com.testryn.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the requirement-coverage read view (ADR 0014) -- the endpoint the Jira
 * Forge issue panel is built on, but exercised here purely as a generic, provider-
 * neutral Testryn REST API (Abschnitt 37). {@code provider=jira} appears only as an
 * ordinary parameter value, same as any other test in this suite that happens to
 * link a {@code JIRA} requirement.
 */
class RequirementCoverageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ServiceTokenService serviceTokenService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // Hibernate statistics are off by default (application.yml has no
    // generate_statistics setting); turned on only for this test class so the
    // N+1 regression test below can actually count real SQL executions instead of
    // just trusting the code's shape.
    // Note: turning this on makes Hibernate log a multi-line "Session Metrics" block
    // per session at INFO -- verbose but harmless test output; a
    // logging.level.* override added via @DynamicPropertySource does not take
    // effect in time to suppress it (Boot's logging system initializes before
    // dynamic test properties are applied), so it is left as-is rather than adding
    // a logback config file just for this.
    @DynamicPropertySource
    static void enableHibernateStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    private String projectKey;

    @BeforeEach
    void setUpProject() throws Exception {
        projectKey = "RC" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Requirement Coverage Project"}
                """.formatted(projectKey), 201);
    }

    @Test
    void resolvesTestCasesLinkedToAProviderAndExternalKey() throws Exception {
        String testCaseId = createTestCase("Successful login");
        linkRequirement(testCaseId, "EVAL-47");

        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .param("provider", "jira")
                        .param("externalKey", "EVAL-47"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirement.provider").value("JIRA"))
                .andExpect(jsonPath("$.requirement.externalKey").value("EVAL-47"))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.testCases", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.testCases[0].id").value(testCaseId))
                .andExpect(jsonPath("$.testCases[0].title").value("Successful login"))
                .andExpect(jsonPath("$.testCases[0].steps[0].action").value("Enter valid username"))
                .andExpect(jsonPath("$.testCases[0].steps[0].expectedResult").value("Username is accepted"));
    }

    @Test
    void providerMatchingIsCaseInsensitiveAndExternalKeyIsNormalized() throws Exception {
        String testCaseId = createTestCase("Successful login");
        linkRequirement(testCaseId, "eval-48");

        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .param("provider", "JiRa")
                        .param("externalKey", "eval-48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void noLinksForTheGivenKeyReturnsAnEmptyListNotAnError() throws Exception {
        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .param("provider", "jira")
                        .param("externalKey", "EVAL-999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases").isEmpty())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void resolvesMultipleTestCasesLinkedToTheSameRequirement() throws Exception {
        linkRequirement(createTestCase("Successful login"), "EVAL-50");
        linkRequirement(createTestCase("Invalid password"), "EVAL-50");
        linkRequirement(createTestCase("Locked user"), "EVAL-50");

        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .param("provider", "jira")
                        .param("externalKey", "EVAL-50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.testCases", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test
    void aTestCaseWithNoExecutionHasNoLatestExecutionButIsStillListed() throws Exception {
        String testCaseId = createTestCase("Never run");
        linkRequirement(testCaseId, "EVAL-51");

        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .param("provider", "jira")
                        .param("externalKey", "EVAL-51"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases[0].id").value(testCaseId))
                .andExpect(jsonPath("$.testCases[0].latestExecution").doesNotExist());
    }

    @Test
    void reportsTheMostRecentlyAddedExecutionAndItsCurrentResult() throws Exception {
        String testCaseId = createTestCase("Successful login");
        linkRequirement(testCaseId, "EVAL-52");
        JsonNode execution = postJson("/api/v1/projects/" + projectKey + "/executions", """
                {"name":"Regression Login - Iteration 2","testCaseIds":["%s"]}
                """.formatted(testCaseId), 201);
        String executionId = execution.get("id").asText();
        String resultId = execution.get("testCases").get(0).get("result").get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultId)
                        .contentType("application/json")
                        .content("""
                                {"status":"PASSED","durationMs":1200}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .param("provider", "jira")
                        .param("externalKey", "EVAL-52"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases[0].latestExecution.executionId").value(executionId))
                .andExpect(jsonPath("$.testCases[0].latestExecution.executionName").value("Regression Login - Iteration 2"))
                .andExpect(jsonPath("$.testCases[0].latestExecution.status").value("PASSED"))
                .andExpect(jsonPath("$.testCases[0].latestExecution.durationMs").value(1200))
                .andExpect(jsonPath("$.testCases[0].latestExecution.executedAt").exists());
    }

    @Test
    void requestWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .header("Authorization", "")
                        .param("provider", "jira")
                        .param("externalKey", "EVAL-47"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aPlainReadScopedTokenIsSufficientNoHigherScopeRequired() throws Exception {
        // Documents, rather than merely assumes, that this GET endpoint needs no
        // more than the baseline read scope: WRITE/ADMIN both imply READ (ADR 0012),
        // so there is no "wrong scope" 403 case for a read-only endpoint like this
        // one -- READ is already the least-privileged scope that exists.
        var generated = serviceTokenService.create("Coverage Reader", null, EnumSet.of(ServiceTokenScope.READ), null);

        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .header("Authorization", "Bearer " + generated.rawToken())
                        .param("provider", "jira")
                        .param("externalKey", "EVAL-47"))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownProviderIsRejectedAsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .param("provider", "azure-devops")
                        .param("externalKey", "EVAL-47"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resultCountIsCappedAtTheGivenLimitButTotalCountIsStillReal() throws Exception {
        for (int i = 0; i < 5; i++) {
            linkRequirement(createTestCase("Test " + i), "EVAL-53");
        }

        mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .param("provider", "jira")
                        .param("externalKey", "EVAL-53")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.testCases", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void theQueryCountDoesNotGrowLinearlyWithTheNumberOfLinkedTestCases() throws Exception {
        linkRequirement(createTestCase("Solo test"), "EVAL-60");
        long queriesForOne = queryCountOf(() -> performCoverage("EVAL-60"));

        for (int i = 0; i < 4; i++) {
            linkRequirement(createTestCase("Batch test " + i), "EVAL-61");
        }
        long queriesForFive = queryCountOf(() -> performCoverage("EVAL-61"));

        // A true N+1 (one extra query per test case) would make this grow by 4; a
        // batch-fetching implementation issues the same small, fixed number of
        // queries regardless of how many test cases are linked.
        assertThat(queriesForFive).isEqualTo(queriesForOne);
    }

    // --- helpers -----------------------------------------------------------------

    private void performCoverage(String externalKey) {
        try {
            mockMvc.perform(get("/api/v1/requirement-links/coverage")
                            .param("provider", "jira")
                            .param("externalKey", externalKey))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long queryCountOf(Runnable action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        action.run();
        return statistics.getQueryExecutionCount();
    }

    private String createTestCase(String title) throws Exception {
        JsonNode testCase = postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"%s","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"Enter valid username","expectedResult":"Username is accepted"}]}
                """.formatted(title), 201);
        return testCase.get("id").asText();
    }

    private void linkRequirement(String testCaseId, String externalKey) throws Exception {
        postJson("/api/v1/test-cases/" + testCaseId + "/requirements", """
                {"provider":"JIRA","externalKey":"%s","url":"https://example.atlassian.net/browse/%s","summary":"x"}
                """.formatted(externalKey, externalKey), 201);
    }

    private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? null : objectMapper.readTree(response);
    }
}
