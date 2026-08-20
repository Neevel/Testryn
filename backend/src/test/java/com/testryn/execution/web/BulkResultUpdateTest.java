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
 * Covers {@code PATCH /executions/{executionId}/results} (ADR 0010): the CI
 * publisher's primary entry point. Three test cases, three automationReferences,
 * mirroring the exact example from Abschnitt 22 (auth.login.valid/invalid/locked).
 */
class BulkResultUpdateTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String executionId;
    private String resultValidId;
    private String resultInvalidId;
    private String resultLockedId;

    @BeforeEach
    void setUpExecutionWithThreeAutomatedTestCases() throws Exception {
        String projectKey = "BR" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Bulk Result Project"}
                """.formatted(projectKey), 201);

        String validId = createTestCase(projectKey, "Login valid", "auth.login.valid");
        String invalidId = createTestCase(projectKey, "Login invalid", "auth.login.invalid");
        String lockedId = createTestCase(projectKey, "Login locked", "auth.login.locked");

        JsonNode plan = postJson("/api/v1/projects/" + projectKey + "/test-plans", """
                {"name":"Auth Plan"}
                """, 201);
        String planId = plan.get("id").asText();
        for (String tcId : new String[] {validId, invalidId, lockedId}) {
            postJson("/api/v1/test-plans/" + planId + "/test-cases", """
                    {"testCaseId":"%s"}
                    """.formatted(tcId), 201);
        }

        JsonNode execution = postJson("/api/v1/test-plans/" + planId + "/executions", "{}", 201);
        executionId = execution.get("id").asText();
        resultValidId = resultIdFor(execution, validId);
        resultInvalidId = resultIdFor(execution, invalidId);
        resultLockedId = resultIdFor(execution, lockedId);
    }

    @Test
    void updatesSeveralResultsByResultIdInOneRequest() throws Exception {
        JsonNode response = bulkPatch("""
                {"results":[
                  {"resultId":"%s","status":"PASSED","durationMs":1420,"executor":"ci"},
                  {"resultId":"%s","status":"FAILED","durationMs":800,"executor":"ci",
                   "actualResult":"HTTP 500","failureDetails":"Expected HTTP 200"}
                ]}
                """.formatted(resultValidId, resultInvalidId), 200);

        assertThat(response.get("results")).hasSize(2);
        JsonNode fetched = fetchExecution();
        assertThat(statusOf(fetched, resultValidId)).isEqualTo("PASSED");
        assertThat(statusOf(fetched, resultInvalidId)).isEqualTo("FAILED");
        // The third result, not mentioned in this request, is untouched.
        assertThat(statusOf(fetched, resultLockedId)).isEqualTo("NOT_RUN");
    }

    @Test
    void updatesResultsByAutomationReference() throws Exception {
        bulkPatch("""
                {"results":[
                  {"automationReference":"auth.login.valid","status":"PASSED","durationMs":1420},
                  {"automationReference":"auth.login.invalid","status":"FAILED"},
                  {"automationReference":"auth.login.locked","status":"SKIPPED"}
                ]}
                """, 200);

        JsonNode fetched = fetchExecution();
        assertThat(statusOf(fetched, resultValidId)).isEqualTo("PASSED");
        assertThat(statusOf(fetched, resultInvalidId)).isEqualTo("FAILED");
        assertThat(statusOf(fetched, resultLockedId)).isEqualTo("SKIPPED");
    }

    @Test
    void moveExecutionFromCreatedToRunningOnFirstBulkUpdate() throws Exception {
        assertThat(fetchExecution().get("status").asText()).isEqualTo("CREATED");

        bulkPatch("""
                {"results":[{"automationReference":"auth.login.valid","status":"PASSED"}]}
                """, 200);

        assertThat(fetchExecution().get("status").asText()).isEqualTo("RUNNING");
    }

    @Test
    void partialSemanticsPreserveFieldsNotMentionedInALaterBulkUpdate() throws Exception {
        // CI reports a full result first (mirrors Abschnitt 24).
        bulkPatch("""
                {"results":[{"resultId":"%s","status":"PASSED","durationMs":1200,"executor":"ci",
                             "actualResult":"ok"}]}
                """.formatted(resultValidId), 200);
        // A tester adds a manual note afterwards, directly via the single-result PATCH.
        mockMvc.perform(patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultValidId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"comment":"manual note"}
                                """))
                .andExpect(status().isOk());

        // CI re-reports the same test case with only status+duration+executor (Abschnitt 24 example).
        bulkPatch("""
                {"results":[{"automationReference":"auth.login.valid","status":"PASSED",
                             "durationMs":1200,"executor":"ci"}]}
                """, 200);

        JsonNode result = fetchResult(resultValidId);
        assertThat(result.get("comment").asText()).isEqualTo("manual note");
        assertThat(result.get("actualResult").asText()).isEqualTo("ok");
        assertThat(result.get("status").asText()).isEqualTo("PASSED");
    }

    @Test
    void atomicRollbackWhenOneEntryIsInvalid() throws Exception {
        bulkPatch("""
                {"results":[
                  {"resultId":"%s","status":"PASSED"},
                  {"automationReference":"auth.login.does-not-exist","status":"FAILED"}
                ]}
                """.formatted(resultValidId), 400);

        // The valid entry must NOT have been applied either -- all or nothing.
        assertThat(statusOf(fetchExecution(), resultValidId)).isEqualTo("NOT_RUN");
    }

    @Test
    void rejectsAResultIdThatBelongsToADifferentExecution() throws Exception {
        // A second, unrelated ad-hoc execution in the same project -- its result IDs
        // are real, just not part of THIS execution.
        String projectKey = "BR" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Other Execution Project"}
                """.formatted(projectKey), 201);
        String otherTestCaseId = createTestCase(projectKey, "Other", null);
        JsonNode otherExecution = postJson("/api/v1/projects/" + projectKey + "/executions", """
                {"name":"Other Execution","testCaseIds":["%s"]}
                """.formatted(otherTestCaseId), 201);
        String foreignResultId = resultIdFor(otherExecution, otherTestCaseId);

        JsonNode error = bulkPatch("""
                {"results":[{"resultId":"%s","status":"PASSED"}]}
                """.formatted(foreignResultId), 400);

        assertThat(error.get("fieldErrors")).isNotEmpty();
        // And, crucially, the foreign result itself was not touched either.
        String response = mockMvc.perform(get("/api/v1/executions/{eid}", otherExecution.get("id").asText()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode foreignExecution = objectMapper.readTree(response);
        assertThat(statusOf(foreignExecution, foreignResultId)).isEqualTo("NOT_RUN");
    }

    @Test
    void rejectsAnUnknownResultId() throws Exception {
        JsonNode error = bulkPatch("""
                {"results":[{"resultId":"%s","status":"PASSED"}]}
                """.formatted(java.util.UUID.randomUUID()), 400);

        assertThat(error.get("fieldErrors")).isNotEmpty();
        assertThat(statusOf(fetchExecution(), resultValidId)).isEqualTo("NOT_RUN");
    }

    @Test
    void rejectsDuplicateResultIdsWithinTheSameRequest() throws Exception {
        bulkPatch("""
                {"results":[
                  {"resultId":"%s","status":"PASSED"},
                  {"resultId":"%s","status":"FAILED"}
                ]}
                """.formatted(resultValidId, resultValidId), 400);

        assertThat(statusOf(fetchExecution(), resultValidId)).isEqualTo("NOT_RUN");
    }

    @Test
    void rejectsAnInvalidStatusValue() throws Exception {
        bulkPatch("""
                {"results":[{"resultId":"%s","status":"NOT_A_REAL_STATUS"}]}
                """.formatted(resultValidId), 400);

        assertThat(statusOf(fetchExecution(), resultValidId)).isEqualTo("NOT_RUN");
    }

    @Test
    void rejectsANegativeDuration() throws Exception {
        bulkPatch("""
                {"results":[{"resultId":"%s","status":"PASSED","durationMs":-5}]}
                """.formatted(resultValidId), 400);

        assertThat(statusOf(fetchExecution(), resultValidId)).isEqualTo("NOT_RUN");
    }

    @Test
    void rejectsWhenResultIdAndAutomationReferenceDisagree() throws Exception {
        bulkPatch("""
                {"results":[{"resultId":"%s","automationReference":"auth.login.invalid","status":"PASSED"}]}
                """.formatted(resultValidId), 400);

        assertThat(statusOf(fetchExecution(), resultValidId)).isEqualTo("NOT_RUN");
    }

    @Test
    void acceptsWhenResultIdAndAutomationReferenceAgree() throws Exception {
        bulkPatch("""
                {"results":[{"resultId":"%s","automationReference":"auth.login.valid","status":"PASSED"}]}
                """.formatted(resultValidId), 200);

        assertThat(statusOf(fetchExecution(), resultValidId)).isEqualTo("PASSED");
    }

    @Test
    void rejectsAnEntryWithNeitherResultIdNorAutomationReference() throws Exception {
        bulkPatch("""
                {"results":[{"status":"PASSED"}]}
                """, 400);
    }

    @Test
    void rejectsAnAutomationReferenceThatIsNotPartOfThisExecution() throws Exception {
        // A real, existing automationReference elsewhere in the same project -- just
        // not added to THIS execution's plan. Must not silently create a test case or
        // resolve globally (Abschnitt 11).
        String projectKey = "BR" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Other Project"}
                """.formatted(projectKey), 201);
        createTestCase(projectKey, "Unrelated", "some.other.reference");

        JsonNode error = bulkPatch("""
                {"results":[{"automationReference":"some.other.reference","status":"PASSED"}]}
                """, 400);

        assertThat(error.get("fieldErrors")).isNotEmpty();
    }

    @Test
    void rejectsAnUnknownAutomationReference() throws Exception {
        JsonNode error = bulkPatch("""
                {"results":[{"automationReference":"does.not.exist.anywhere","status":"PASSED"}]}
                """, 400);

        assertThat(error.get("fieldErrors")).isNotEmpty();
    }

    @Test
    void emptyResultsArrayIsRejected() throws Exception {
        bulkPatch("""
                {"results":[]}
                """, 400);
    }

    // --- helpers -----------------------------------------------------------------

    private String createTestCase(String projectKey, String title, String automationReference) throws Exception {
        String automationReferenceJson = automationReference == null ? "null" : "\"" + automationReference + "\"";
        JsonNode tc = postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"%s","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"Do it","expectedResult":"Works"}],
                 "automationReference":%s}
                """.formatted(title, automationReferenceJson), 201);
        return tc.get("id").asText();
    }

    private String resultIdFor(JsonNode execution, String testCaseId) {
        for (JsonNode etc : execution.get("testCases")) {
            if (etc.get("testCaseId").asText().equals(testCaseId)) {
                return etc.get("result").get("id").asText();
            }
        }
        throw new AssertionError("test case not found in execution snapshot: " + testCaseId);
    }

    private String statusOf(JsonNode execution, String resultId) {
        for (JsonNode etc : execution.get("testCases")) {
            if (etc.get("result").get("id").asText().equals(resultId)) {
                return etc.get("result").get("status").asText();
            }
        }
        throw new AssertionError("result not found: " + resultId);
    }

    private JsonNode fetchExecution() throws Exception {
        String response = mockMvc.perform(get("/api/v1/executions/{eid}", executionId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private JsonNode fetchResult(String resultId) throws Exception {
        JsonNode execution = fetchExecution();
        for (JsonNode etc : execution.get("testCases")) {
            if (etc.get("result").get("id").asText().equals(resultId)) {
                return etc.get("result");
            }
        }
        throw new AssertionError("result not found: " + resultId);
    }

    private JsonNode bulkPatch(String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(patch("/api/v1/executions/{eid}/results", executionId)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
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
