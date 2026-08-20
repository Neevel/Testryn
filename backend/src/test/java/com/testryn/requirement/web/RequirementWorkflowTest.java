package com.testryn.requirement.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the requirement-link workflow (Abschnitt 7/8) end to end at the HTTP level,
 * plus the read-only Jira integration status endpoints (Abschnitt 5/6) in the
 * (realistic for this test environment) "not configured" state.
 */
class RequirementWorkflowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String testCaseId;

    @BeforeEach
    void setUpTestCase() throws Exception {
        String projectKey = "RW" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Requirement Workflow Project"}
                """.formatted(projectKey), 201);
        JsonNode testCase = postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"Delete account","description":"desc","preconditions":"none","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"Do it","expectedResult":"Works"}]}
                """, 201);
        testCaseId = testCase.get("id").asText();
    }

    @Test
    void creatingTheSameRequirementLinkTwiceIsRejectedAsConflict() throws Exception {
        postJson("/api/v1/test-cases/" + testCaseId + "/requirements", """
                {"provider":"JIRA","externalKey":"BIT-27","url":"https://example.atlassian.net/browse/BIT-27","summary":"x"}
                """, 201);

        mockMvc.perform(post("/api/v1/test-cases/{id}/requirements", testCaseId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"provider":"JIRA","externalKey":"BIT-27","url":"https://example.atlassian.net/browse/BIT-27","summary":"x"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void requirementKeysAreCaseNormalizedForDuplicateDetection() throws Exception {
        postJson("/api/v1/test-cases/" + testCaseId + "/requirements", """
                {"provider":"JIRA","externalKey":"bit-27","url":"https://example.atlassian.net/browse/BIT-27","summary":"x"}
                """, 201);

        // Same key, different case -> must still be recognized as a duplicate.
        mockMvc.perform(post("/api/v1/test-cases/{id}/requirements", testCaseId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"provider":"JIRA","externalKey":"BIT-27","url":"https://example.atlassian.net/browse/BIT-27","summary":"x"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void requirementLinkCanBeRemovedWithoutAffectingTheTestCase() throws Exception {
        JsonNode link = postJson("/api/v1/test-cases/" + testCaseId + "/requirements", """
                {"provider":"JIRA","externalKey":"BIT-27","url":"https://example.atlassian.net/browse/BIT-27","summary":"x"}
                """, 201);
        String linkId = link.get("id").asText();

        mockMvc.perform(delete("/api/v1/test-cases/{tid}/requirements/{lid}", testCaseId, linkId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/test-cases/{id}/requirements", testCaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // The test case itself is unaffected by removing the link.
        mockMvc.perform(get("/api/v1/test-cases/{id}", testCaseId))
                .andExpect(status().isOk());
    }

    @Test
    void jiraConnectionStatusReportsNotUsableWhenUnconfigured() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/jira/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenConfigured").value(false))
                .andExpect(jsonPath("$.usable").value(false));
    }

    @Test
    void jiraConnectionTestReportsFailureWhenUnconfigured() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/jira/connection/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void jiraIssueLookupIsABadGatewayWhenJiraIsNotConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/jira/issues/{key}", "BIT-27"))
                .andExpect(status().isBadGateway());
    }

    // --- helpers -----------------------------------------------------------------

    private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }
}
