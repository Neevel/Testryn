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
 * Covers the COMPLETED/ABORTED result-write guard (Abschnitt 50, ADR 0015): once an
 * execution is finished, nothing may write a result into it any more -- testcase-
 * level, step-level, single or bulk. The CI publisher needs no separate coverage
 * here: it calls the exact same bulk testcase-level endpoint exercised below.
 */
class ExecutionWriteGuardTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String executionId;
    private String resultId;
    private String stepResultId;

    @BeforeEach
    void setUpExecution() throws Exception {
        String projectKey = uniqueKey("WG");
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Write Guard Project"}
                """.formatted(projectKey), 201);
        JsonNode testCase = postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"Guarded","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"a","expectedResult":"b"}]}
                """, 201);
        JsonNode execution = postJson("/api/v1/projects/" + projectKey + "/executions", """
                {"name":"Guarded Execution","testCaseIds":["%s"]}
                """.formatted(testCase.get("id").asText()), 201);
        executionId = execution.get("id").asText();
        JsonNode etc = execution.get("testCases").get(0);
        resultId = etc.get("result").get("id").asText();
        stepResultId = etc.get("steps").get(0).get("result").get("id").asText();
    }

    @Test
    void writesAreAcceptedWhileRunning() throws Exception {
        // CREATED -> RUNNING happens automatically on the first write.
        patchAndExpect("/api/v1/executions/{eid}/results/{rid}", resultId,"""
                {"status":"PASSED"}
                """, 200);
    }

    @Test
    void testcaseLevelWritesAreRejectedOnceCompleted() throws Exception {
        completeExecution();

        patchAndExpect("/api/v1/executions/{eid}/results/{rid}", resultId,"""
                {"status":"PASSED"}
                """, 409);
    }

    @Test
    void testcaseLevelWritesAreRejectedOnceAborted() throws Exception {
        abortExecution();

        patchAndExpect("/api/v1/executions/{eid}/results/{rid}", resultId,"""
                {"status":"PASSED"}
                """, 409);
    }

    @Test
    void bulkTestcaseLevelWritesAreRejectedOnceCompleted() throws Exception {
        completeExecution();

        mockMvc.perform(patch("/api/v1/executions/{eid}/results", executionId)
                        .contentType("application/json")
                        .content("""
                                {"results":[{"resultId":"%s","status":"PASSED"}]}
                                """.formatted(resultId)))
                .andExpect(status().isConflict());
    }

    @Test
    void stepLevelWritesAreRejectedOnceCompleted() throws Exception {
        completeExecution();

        patchAndExpect("/api/v1/executions/{eid}/step-results/{sid}", stepResultId,"""
                {"status":"PASSED"}
                """, 409);
    }

    @Test
    void stepLevelWritesAreRejectedOnceAborted() throws Exception {
        abortExecution();

        patchAndExpect("/api/v1/executions/{eid}/step-results/{sid}", stepResultId,"""
                {"status":"PASSED"}
                """, 409);
    }

    @Test
    void bulkStepLevelWritesAreRejectedOnceCompleted() throws Exception {
        completeExecution();

        mockMvc.perform(patch("/api/v1/executions/{eid}/step-results", executionId)
                        .contentType("application/json")
                        .content("""
                                {"results":[{"stepResultId":"%s","status":"PASSED"}]}
                                """.formatted(stepResultId)))
                .andExpect(status().isConflict());
    }

    // --- helpers -----------------------------------------------------------------

    private void completeExecution() throws Exception {
        // An execution must actually be RUNNING before it can be marked COMPLETED --
        // one real write first, matching how a tester would naturally reach this
        // state, then the completion transition itself.
        patchAndExpect("/api/v1/executions/{eid}/results/{rid}", resultId,"""
                {"status":"PASSED"}
                """, 200);
        mockMvc.perform(patch("/api/v1/executions/{eid}", executionId)
                        .contentType("application/json")
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk());
    }

    private void abortExecution() throws Exception {
        mockMvc.perform(patch("/api/v1/executions/{eid}", executionId)
                        .contentType("application/json")
                        .content("""
                                {"status":"ABORTED"}
                                """))
                .andExpect(status().isOk());
    }

    private void patchAndExpect(String pathTemplate, String pathVar, String body, int expectedStatus) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch(pathTemplate, executionId, pathVar)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus));
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
