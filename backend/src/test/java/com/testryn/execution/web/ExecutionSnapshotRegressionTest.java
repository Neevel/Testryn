package com.testryn.execution.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The mandatory regression scenario from Abschnitt 43: an execution's step
 * snapshot (and its step results) must never be affected by a later test case
 * edit, even though both the old and the new version's steps live in the same
 * {@code test_steps} table (ADR 0015 -- historical accuracy without a redundant
 * snapshot-copy table, relying instead on {@code TestStep} rows already being
 * immutable and version-scoped).
 */
class ExecutionSnapshotRegressionTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anExecutionKeepsItsOriginalStepAfterTheTestCaseIsEditedToADifferentStepSet() throws Exception {
        String projectKey = "SNAP" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Snapshot Regression Project"}
                """.formatted(projectKey), 201);

        // v1: exactly one step, "Step A".
        JsonNode testCase = postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"Snapshot Test","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"Step A action","expectedResult":"Step A expected"}]}
                """, 201);
        String testCaseId = testCase.get("id").asText();

        // Execution #1 snapshots v1 -- exactly Step A.
        JsonNode execution = postJson("/api/v1/projects/" + projectKey + "/executions", """
                {"name":"Execution 1","testCaseIds":["%s"]}
                """.formatted(testCaseId), 201);
        String executionId = execution.get("id").asText();
        JsonNode stepsBeforeEdit = execution.get("testCases").get(0).get("steps");
        assertThat(stepsBeforeEdit).hasSize(1);
        assertThat(stepsBeforeEdit.get(0).get("action").asText()).isEqualTo("Step A action");
        String stepAResultId = stepsBeforeEdit.get(0).get("result").get("id").asText();

        // Mark Step A as PASSED on execution #1, before the test case changes at all.
        mockMvc.perform(patch("/api/v1/executions/{eid}/step-results/{sid}", executionId, stepAResultId)
                        .contentType("application/json")
                        .content("""
                                {"status":"PASSED","actualResult":"Step A observed"}
                                """))
                .andExpect(status().isOk());

        // v2: Step A's action text changes, and a new Step B is added.
        mockMvc.perform(put("/api/v1/test-cases/{id}", testCaseId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"title":"Snapshot Test","priority":"MEDIUM","status":"DRAFT","tags":[],
                                 "steps":[
                                   {"action":"Step A action CHANGED","expectedResult":"Step A expected CHANGED"},
                                   {"action":"Step B action","expectedResult":"Step B expected"}
                                 ]}
                                """))
                .andExpect(status().isOk());

        // Execution #1, re-fetched, must be completely unaffected: still exactly
        // one step, still the ORIGINAL text, still PASSED with its original
        // actualResult -- never the v2 wording, never a phantom Step B.
        String response = mockMvc.perform(get("/api/v1/executions/{eid}", executionId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode reloaded = objectMapper.readTree(response);
        JsonNode stepsAfterEdit = reloaded.get("testCases").get(0).get("steps");

        assertThat(stepsAfterEdit).hasSize(1);
        assertThat(stepsAfterEdit.get(0).get("action").asText()).isEqualTo("Step A action");
        assertThat(stepsAfterEdit.get(0).get("expectedResult").asText()).isEqualTo("Step A expected");
        assertThat(stepsAfterEdit.get(0).get("result").get("status").asText()).isEqualTo("PASSED");
        assertThat(stepsAfterEdit.get(0).get("result").get("actualResult").asText()).isEqualTo("Step A observed");
        assertThat(reloaded.get("testCases").get(0).get("testCaseVersionNumber").asInt()).isEqualTo(1);

        // A brand-new execution created AFTER the edit, however, correctly sees v2:
        // both steps, the new wording, all fresh NOT_RUN results.
        JsonNode executionTwo = postJson("/api/v1/projects/" + projectKey + "/executions", """
                {"name":"Execution 2","testCaseIds":["%s"]}
                """.formatted(testCaseId), 201);
        JsonNode stepsV2 = executionTwo.get("testCases").get(0).get("steps");
        assertThat(stepsV2).hasSize(2);
        assertThat(stepsV2.get(0).get("action").asText()).isEqualTo("Step A action CHANGED");
        assertThat(stepsV2.get(1).get("action").asText()).isEqualTo("Step B action");
        assertThat(stepsV2.get(0).get("result").get("status").asText()).isEqualTo("NOT_RUN");
        assertThat(stepsV2.get(1).get("result").get("status").asText()).isEqualTo("NOT_RUN");
        assertThat(executionTwo.get("testCases").get(0).get("testCaseVersionNumber").asInt()).isEqualTo(2);
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
