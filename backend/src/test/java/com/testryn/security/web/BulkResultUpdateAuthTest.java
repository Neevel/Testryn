package com.testryn.security.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.security.domain.ServiceTokenScope;
import com.testryn.security.service.ServiceTokenService;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Abschnitt 14/31: the CI publisher's one and only endpoint (the bulk result-update
 * API, ADR 0010) is fully subject to the same scope rules as everything else -- a
 * read-only token must not be able to report results, and the whole existing
 * automationReference/bulk workflow must still work end to end once a valid write
 * token is used.
 */
class BulkResultUpdateAuthTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ServiceTokenService serviceTokenService;

    private String executionId;
    private String resultId;

    @BeforeEach
    void setUpExecution() throws Exception {
        String projectKey = "BA" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Bulk Auth Project"}
                """.formatted(projectKey), 201);
        JsonNode testCase = postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"Auth check","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"a","expectedResult":"b"}],"automationReference":"auth.check.valid"}
                """, 201);
        String testCaseId = testCase.get("id").asText();
        JsonNode execution = postJson("/api/v1/projects/" + projectKey + "/executions", """
                {"name":"Auth Exec","testCaseIds":["%s"]}
                """.formatted(testCaseId), 201);
        executionId = execution.get("id").asText();
        resultId = execution.get("testCases").get(0).get("result").get("id").asText();
    }

    @Test
    void bulkUpdateWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/executions/{eid}/results", executionId)
                        .header("Authorization", "")
                        .contentType("application/json")
                        .content("""
                                {"results":[{"resultId":"%s","status":"PASSED"}]}
                                """.formatted(resultId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readOnlyTokenCannotPublishBulkResults() throws Exception {
        var generated = serviceTokenService.create("Read Only", null, EnumSet.of(ServiceTokenScope.READ), null);

        mockMvc.perform(patch("/api/v1/executions/{eid}/results", executionId)
                        .header("Authorization", "Bearer " + generated.rawToken())
                        .contentType("application/json")
                        .content("""
                                {"results":[{"resultId":"%s","status":"PASSED"}]}
                                """.formatted(resultId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeTokenSuccessfullyPublishesBulkResults() throws Exception {
        var generated = serviceTokenService.create("CI Publisher", null, EnumSet.of(ServiceTokenScope.WRITE), null);

        mockMvc.perform(patch("/api/v1/executions/{eid}/results", executionId)
                        .header("Authorization", "Bearer " + generated.rawToken())
                        .contentType("application/json")
                        .content("""
                                {"results":[{"automationReference":"auth.check.valid","status":"PASSED","durationMs":123}]}
                                """))
                .andExpect(status().isOk());
    }

    // --- helpers -----------------------------------------------------------------

    private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? null : objectMapper.readTree(response);
    }
}
