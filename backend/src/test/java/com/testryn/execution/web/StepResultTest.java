package com.testryn.execution.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Step-Level Execution Results (ADR 0015): initial NOT_RUN state, the four
 * real outcomes, partial-patch semantics, bulk atomicity, testcase-level status
 * derivation, and backward compatibility with executions that predate this block.
 */
class StepResultTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String projectKey;
    private String executionId;
    private String testCaseId;
    private String step1Id;
    private String step2Id;
    private String step3Id;
    private String step4Id;

    // A plain `System.nanoTime() % 100000` project-key suffix (the convention used
    // elsewhere in this suite) collided within this class's own 20+ tests running
    // back to back -- an AtomicInteger counter added on top guarantees uniqueness
    // within a single JVM run regardless of how fast @BeforeEach fires, without
    // changing the shared convention used by every other test class.
    private static final AtomicInteger PROJECT_KEY_SEQUENCE = new AtomicInteger();

    @BeforeEach
    void setUpExecutionWithFourSteps() throws Exception {
        projectKey = "SR" + (System.nanoTime() % 100000) + "" + PROJECT_KEY_SEQUENCE.incrementAndGet();
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Step Result Project"}
                """.formatted(projectKey), 201);

        JsonNode testCase = postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"Login with valid credentials","priority":"MEDIUM","tags":[],
                 "steps":[
                   {"action":"Open login page","expectedResult":"Login page is visible"},
                   {"action":"Enter valid credentials","expectedResult":"Credentials are accepted"},
                   {"action":"Submit login","expectedResult":"Dashboard is displayed"},
                   {"action":"Check welcome banner","expectedResult":"Welcome banner is shown"}
                 ]}
                """, 201);
        testCaseId = testCase.get("id").asText();

        JsonNode execution = postJson("/api/v1/projects/" + projectKey + "/executions", """
                {"name":"Login regression","testCaseIds":["%s"]}
                """.formatted(testCaseId), 201);
        executionId = execution.get("id").asText();

        JsonNode steps = execution.get("testCases").get(0).get("steps");
        step1Id = steps.get(0).get("result").get("id").asText();
        step2Id = steps.get(1).get("result").get("id").asText();
        step3Id = steps.get(2).get("result").get("id").asText();
        step4Id = steps.get(3).get("result").get("id").asText();
    }

    @Test
    void allStepResultsStartAsNotRun() throws Exception {
        JsonNode steps = stepsOf(fetchExecution());
        for (JsonNode step : steps) {
            assertThat(step.get("result").get("status").asText()).isEqualTo("NOT_RUN");
            assertThat(step.get("result").get("executedAt").isNull()).isTrue();
        }
        assertThat(testCaseStatus(fetchExecution())).isEqualTo("NOT_RUN");
    }

    @Test
    void aStepCanBeMarkedPassed() throws Exception {
        patchStep(step1Id, """
                {"status":"PASSED","actualResult":"Login page displayed"}
                """, 200);

        JsonNode result = stepResult(fetchExecution(), step1Id);
        assertThat(result.get("status").asText()).isEqualTo("PASSED");
        assertThat(result.get("actualResult").asText()).isEqualTo("Login page displayed");
        assertThat(result.get("executedAt").isNull()).isFalse();
    }

    @Test
    void aStepCanBeMarkedFailedWithFailureDetails() throws Exception {
        patchStep(step3Id, """
                {"status":"FAILED","actualResult":"HTTP 500","failureDetails":"IllegalStateException: ..."}
                """, 200);

        JsonNode result = stepResult(fetchExecution(), step3Id);
        assertThat(result.get("status").asText()).isEqualTo("FAILED");
        assertThat(result.get("actualResult").asText()).isEqualTo("HTTP 500");
        assertThat(result.get("failureDetails").asText()).contains("IllegalStateException");
    }

    @Test
    void aStepCanBeMarkedBlocked() throws Exception {
        patchStep(step2Id, """
                {"status":"BLOCKED","comment":"environment down"}
                """, 200);

        JsonNode result = stepResult(fetchExecution(), step2Id);
        assertThat(result.get("status").asText()).isEqualTo("BLOCKED");
        assertThat(result.get("comment").asText()).isEqualTo("environment down");
    }

    @Test
    void aStepCanBeMarkedSkipped() throws Exception {
        patchStep(step4Id, """
                {"status":"SKIPPED"}
                """, 200);

        assertThat(stepResult(fetchExecution(), step4Id).get("status").asText()).isEqualTo("SKIPPED");
    }

    @Test
    void aPartialPatchPreservesFieldsNotMentioned() throws Exception {
        patchStep(step1Id, """
                {"status":"FAILED","actualResult":"HTTP 500","comment":"first note"}
                """, 200);

        // Only the comment changes; status/actualResult must survive untouched.
        patchStep(step1Id, """
                {"comment":"updated note"}
                """, 200);

        JsonNode result = stepResult(fetchExecution(), step1Id);
        assertThat(result.get("status").asText()).isEqualTo("FAILED");
        assertThat(result.get("actualResult").asText()).isEqualTo("HTTP 500");
        assertThat(result.get("comment").asText()).isEqualTo("updated note");
    }

    @Test
    void anExplicitNullClearsAField() throws Exception {
        patchStep(step1Id, """
                {"status":"FAILED","comment":"will be cleared"}
                """, 200);

        patchStep(step1Id, """
                {"comment":null}
                """, 200);

        JsonNode result = stepResult(fetchExecution(), step1Id);
        assertThat(result.get("comment").isNull()).isTrue();
        assertThat(result.get("status").asText()).isEqualTo("FAILED");
    }

    @Test
    void aNullStatusIsRejected() throws Exception {
        patchStep(step1Id, """
                {"status":null}
                """, 400);
    }

    @Test
    void anUnknownStepResultIdIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/executions/{eid}/step-results/{sid}", executionId, UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"status":"PASSED"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStepResultIdFromAnotherExecutionIsRejected() throws Exception {
        JsonNode otherTestCase = postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"Other","priority":"LOW","tags":[],
                 "steps":[{"action":"a","expectedResult":"b"}]}
                """, 201);
        JsonNode otherExecution = postJson("/api/v1/projects/" + projectKey + "/executions", """
                {"name":"Other Execution","testCaseIds":["%s"]}
                """.formatted(otherTestCase.get("id").asText()), 201);
        String foreignStepId = otherExecution.get("testCases").get(0).get("steps").get(0)
                .get("result").get("id").asText();

        // Addressed via THIS execution's id, but the step result belongs to the other one.
        mockMvc.perform(patch("/api/v1/executions/{eid}/step-results/{sid}", executionId, foreignStepId)
                        .contentType("application/json")
                        .content("""
                                {"status":"PASSED"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- Testcase-level status derivation (Abschnitt 9) ---------------------------

    @Test
    void allStepsPassedDerivesTestCasePassed() throws Exception {
        for (String stepId : new String[] {step1Id, step2Id, step3Id, step4Id}) {
            patchStep(stepId, """
                    {"status":"PASSED"}
                    """, 200);
        }
        assertThat(testCaseStatus(fetchExecution())).isEqualTo("PASSED");
    }

    @Test
    void oneFailedStepDerivesTestCaseFailedEvenWithStepsRemaining() throws Exception {
        patchStep(step1Id, """
                {"status":"PASSED"}
                """, 200);
        patchStep(step2Id, """
                {"status":"PASSED"}
                """, 200);
        patchStep(step3Id, """
                {"status":"FAILED","actualResult":"HTTP 500"}
                """, 200);
        // step4 stays NOT_RUN -- the end-to-end scenario from Abschnitt 56.

        assertThat(testCaseStatus(fetchExecution())).isEqualTo("FAILED");
        assertThat(stepResult(fetchExecution(), step4Id).get("status").asText()).isEqualTo("NOT_RUN");
    }

    @Test
    void aBlockedStepDerivesTestCaseBlockedWhenNoStepFailed() throws Exception {
        patchStep(step1Id, """
                {"status":"PASSED"}
                """, 200);
        patchStep(step2Id, """
                {"status":"BLOCKED"}
                """, 200);

        assertThat(testCaseStatus(fetchExecution())).isEqualTo("BLOCKED");
    }

    @Test
    void incompleteStepsKeepTestCaseNotRun() throws Exception {
        patchStep(step1Id, """
                {"status":"PASSED"}
                """, 200);
        // steps 2-4 still NOT_RUN.

        assertThat(testCaseStatus(fetchExecution())).isEqualTo("NOT_RUN");
    }

    @Test
    void allExecutedStepsSkippedDerivesTestCaseSkipped() throws Exception {
        patchStep(step1Id, """
                {"status":"SKIPPED"}
                """, 200);
        patchStep(step2Id, """
                {"status":"SKIPPED"}
                """, 200);
        // steps 3-4 still NOT_RUN -- "all EXECUTED steps skipped", not all steps.

        assertThat(testCaseStatus(fetchExecution())).isEqualTo("SKIPPED");
    }

    @Test
    void derivingStatusFromStepsNeverErasesAnExistingTestcaseLevelComment() throws Exception {
        // A manual note set directly on the testcase-level result beforehand.
        String resultId = fetchExecution().get("testCases").get(0).get("result").get("id").asText();
        mockMvc.perform(patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"pre-existing manual note"}
                                """))
                .andExpect(status().isOk());

        patchStep(step1Id, """
                {"status":"PASSED"}
                """, 200);

        JsonNode result = fetchExecution().get("testCases").get(0).get("result");
        assertThat(result.get("comment").asText()).isEqualTo("pre-existing manual note");
    }

    @Test
    void aTestcaseLevelPatchLikeJUnitPublishNeverTouchesOrDerivesFromStepResults() throws Exception {
        // Simulates the CI/JUnit path (Abschnitt 20/36/57): a testcase-level result
        // set directly, with no step ever touched. Step results must stay exactly
        // as they were (NOT_RUN) -- the derive-from-steps logic must never run as a
        // side effect of the testcase-level patch path, only of the step-level one.
        String resultId = fetchExecution().get("testCases").get(0).get("result").get("id").asText();

        mockMvc.perform(patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultId)
                        .contentType("application/json")
                        .content("""
                                {"status":"FAILED","executor":"ci"}
                                """))
                .andExpect(status().isOk());

        JsonNode execution = fetchExecution();
        assertThat(testCaseStatus(execution)).isEqualTo("FAILED");
        for (JsonNode step : stepsOf(execution)) {
            assertThat(step.get("result").get("status").asText()).isEqualTo("NOT_RUN");
        }
    }

    // --- Bulk step updates ---------------------------------------------------------

    @Test
    void bulkUpdatesSeveralStepsInOneAtomicRequest() throws Exception {
        bulkPatchSteps("""
                {"results":[
                  {"stepResultId":"%s","status":"PASSED"},
                  {"stepResultId":"%s","status":"PASSED"},
                  {"stepResultId":"%s","status":"FAILED","actualResult":"HTTP 500"}
                ]}
                """.formatted(step1Id, step2Id, step3Id), 200);

        JsonNode execution = fetchExecution();
        assertThat(stepResult(execution, step1Id).get("status").asText()).isEqualTo("PASSED");
        assertThat(stepResult(execution, step2Id).get("status").asText()).isEqualTo("PASSED");
        assertThat(stepResult(execution, step3Id).get("status").asText()).isEqualTo("FAILED");
        assertThat(testCaseStatus(execution)).isEqualTo("FAILED");
    }

    @Test
    void bulkStepUpdateIsAllOrNothing() throws Exception {
        bulkPatchSteps("""
                {"results":[
                  {"stepResultId":"%s","status":"PASSED"},
                  {"stepResultId":"%s","status":"NOT_A_REAL_STATUS"}
                ]}
                """.formatted(step1Id, step2Id), 400);

        assertThat(stepResult(fetchExecution(), step1Id).get("status").asText()).isEqualTo("NOT_RUN");
    }

    @Test
    void bulkStepUpdateRejectsDuplicateStepResultIdsInTheSameRequest() throws Exception {
        bulkPatchSteps("""
                {"results":[
                  {"stepResultId":"%s","status":"PASSED"},
                  {"stepResultId":"%s","status":"FAILED"}
                ]}
                """.formatted(step1Id, step1Id), 400);

        assertThat(stepResult(fetchExecution(), step1Id).get("status").asText()).isEqualTo("NOT_RUN");
    }

    @Test
    void bulkStepUpdateRejectsAnUnknownStepResultId() throws Exception {
        JsonNode error = bulkPatchSteps("""
                {"results":[{"stepResultId":"%s","status":"PASSED"}]}
                """.formatted(UUID.randomUUID()), 400);

        assertThat(error.get("fieldErrors")).isNotEmpty();
    }

    @Test
    void emptyBulkStepResultsArrayIsRejected() throws Exception {
        bulkPatchSteps("""
                {"results":[]}
                """, 400);
    }

    // --- Backward compatibility with pre-existing executions -----------------------

    @Test
    void anExecutionWithNoStepResultRowsReportsNullResultsInsteadOfCrashing() throws Exception {
        // Simulates an execution created before this block: no execution_step_results
        // rows at all, even though the execution/test-case/steps themselves exist.
        jdbcTemplate.update("DELETE FROM execution_step_results WHERE execution_test_case_id IN "
                + "(SELECT id FROM execution_test_cases WHERE execution_id = ?::uuid)", executionId);

        JsonNode steps = stepsOf(fetchExecution());
        for (JsonNode step : steps) {
            assertThat(step.get("result").isNull()).isTrue();
            // The step's own content is still present -- only the result is missing.
            assertThat(step.get("action").asText()).isNotBlank();
        }
    }

    // --- helpers -----------------------------------------------------------------

    private JsonNode stepsOf(JsonNode execution) {
        return execution.get("testCases").get(0).get("steps");
    }

    private JsonNode stepResult(JsonNode execution, String stepResultId) {
        for (JsonNode step : stepsOf(execution)) {
            JsonNode result = step.get("result");
            if (result != null && !result.isNull() && result.get("id").asText().equals(stepResultId)) {
                return result;
            }
        }
        throw new AssertionError("step result not found: " + stepResultId);
    }

    private String testCaseStatus(JsonNode execution) {
        return execution.get("testCases").get(0).get("result").get("status").asText();
    }

    private JsonNode fetchExecution() throws Exception {
        String response = mockMvc.perform(get("/api/v1/executions/{eid}", executionId))
                        .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private JsonNode patchStep(String stepResultId, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(patch("/api/v1/executions/{eid}/step-results/{sid}", executionId, stepResultId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? null : objectMapper.readTree(response);
    }

    private JsonNode bulkPatchSteps(String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(patch("/api/v1/executions/{eid}/step-results", executionId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? null : objectMapper.readTree(response);
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
