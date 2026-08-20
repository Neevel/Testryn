package com.testryn.execution.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dedicated tests for the JSON Merge Patch semantics of
 * {@code PATCH /executions/{executionId}/results/{resultId}} -- see ADR 0006. The
 * core regression this guards against: a UI update that only means to change one
 * field (e.g. a tester adding a comment) must never silently erase fields a CI
 * pipeline had already reported (duration, executor, ...).
 */
class ExecutionResultPatchTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String executionId;
    private String resultId;

    @BeforeEach
    void setUpExecutionWithFullyPopulatedResult() throws Exception {
        String projectKey = "RP" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Result Patch Project"}
                """.formatted(projectKey), 201);

        JsonNode testCase = postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"Login works","description":"desc","preconditions":"none","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"Do it","expectedResult":"Works"}]}
                """, 201);
        String testCaseId = testCase.get("id").asText();

        JsonNode plan = postJson("/api/v1/projects/" + projectKey + "/test-plans", """
                {"name":"Plan"}
                """, 201);
        String planId = plan.get("id").asText();

        postJson("/api/v1/test-plans/" + planId + "/test-cases", """
                {"testCaseId":"%s"}
                """.formatted(testCaseId), 201);

        JsonNode execution = postJson("/api/v1/test-plans/" + planId + "/executions", "{}", 201);
        executionId = execution.get("id").asText();
        resultId = execution.get("testCases").get(0).get("result").get("id").asText();

        // Simulate a CI pipeline having already reported a full result.
        patchJson("""
                {"status":"PASSED","comment":"initial run","durationMs":4200,"executor":"ci-pipeline",
                 "actualResult":"Login succeeded","failureDetails":null}
                """, 200);
    }

    @Test
    void resultStartsWithAllFieldsPopulated() throws Exception {
        JsonNode result = fetchResult();
        assertThat(result.get("status").asText()).isEqualTo("PASSED");
        assertThat(result.get("comment").asText()).isEqualTo("initial run");
        assertThat(result.get("durationMs").asLong()).isEqualTo(4200);
        assertThat(result.get("executor").asText()).isEqualTo("ci-pipeline");
        assertThat(result.get("actualResult").asText()).isEqualTo("Login succeeded");
        assertThat(result.get("failureDetails").isNull()).isTrue();
    }

    @Test
    void patchingOnlyCommentLeavesEverythingElseUntouched() throws Exception {
        JsonNode result = patchJson("""
                {"comment":"Re-checked manually"}
                """, 200);

        assertThat(result.get("comment").asText()).isEqualTo("Re-checked manually");
        // Everything a CI pipeline had already written must survive untouched.
        assertThat(result.get("status").asText()).isEqualTo("PASSED");
        assertThat(result.get("durationMs").asLong()).isEqualTo(4200);
        assertThat(result.get("executor").asText()).isEqualTo("ci-pipeline");
        assertThat(result.get("actualResult").asText()).isEqualTo("Login succeeded");
    }

    @Test
    void patchingOnlyStatusChangesOnlyStatus() throws Exception {
        JsonNode result = patchJson("""
                {"status":"BLOCKED"}
                """, 200);

        assertThat(result.get("status").asText()).isEqualTo("BLOCKED");
        assertThat(result.get("comment").asText()).isEqualTo("initial run");
        assertThat(result.get("durationMs").asLong()).isEqualTo(4200);
        assertThat(result.get("executor").asText()).isEqualTo("ci-pipeline");
        assertThat(result.get("actualResult").asText()).isEqualTo("Login succeeded");
    }

    @Test
    void aUiOnlyResultChangeCannotWipeOutExistingPipelineData() throws Exception {
        // Mirrors the exact regression from the bug report: a UI form that only
        // ever sends status + comment must not erase durationMs/executor/actualResult.
        JsonNode result = patchJson("""
                {"status":"FAILED","comment":"regression confirmed"}
                """, 200);

        assertThat(result.get("status").asText()).isEqualTo("FAILED");
        assertThat(result.get("comment").asText()).isEqualTo("regression confirmed");
        assertThat(result.get("durationMs").asLong()).isEqualTo(4200);
        assertThat(result.get("executor").asText()).isEqualTo("ci-pipeline");
        assertThat(result.get("actualResult").asText()).isEqualTo("Login succeeded");
    }

    @Test
    void explicitNullClearsAField() throws Exception {
        JsonNode result = patchJson("""
                {"comment":null}
                """, 200);

        assertThat(result.get("comment").isNull()).isTrue();
        // Unrelated fields still untouched.
        assertThat(result.get("durationMs").asLong()).isEqualTo(4200);
        assertThat(result.get("executor").asText()).isEqualTo("ci-pipeline");
    }

    @Test
    void statusCanNotBeExplicitlyNulled() throws Exception {
        mockMvc.perform(patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"status":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownStatusValueIsRejectedAsBadRequestNotServerError() throws Exception {
        mockMvc.perform(patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"status":"NOT_A_REAL_STATUS"}
                                """))
                .andExpect(status().isBadRequest());
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

    private JsonNode patchJson(String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private JsonNode fetchResult() throws Exception {
        String response = mockMvc.perform(get("/api/v1/executions/{eid}", executionId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode execution = objectMapper.readTree(response);
        for (JsonNode etc : execution.get("testCases")) {
            if (etc.get("result").get("id").asText().equals(resultId)) {
                return etc.get("result");
            }
        }
        throw new AssertionError("result not found: " + resultId);
    }
}
