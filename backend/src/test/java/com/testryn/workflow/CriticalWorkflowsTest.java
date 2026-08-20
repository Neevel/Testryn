package com.testryn.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST-level tests for the five critical workflows from the product mandate
 * (Abschnitt 12) and, together, the Meilenstein-1 end-to-end path (Abschnitt 15).
 */
class CriticalWorkflowsTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Workflow 1: Project -> Test Case erstellen -> Steps -> Requirement verknüpfen. */
    @Test
    void workflow1_projectTestCaseStepsAndRequirementLink() throws Exception {
        String projectKey = createProject("WF1", "Workflow 1 Project");

        JsonNode testCase = createTestCase(projectKey, "Erfolgreiche Anmeldung");
        String testCaseId = testCase.get("id").asText();

        assertThat(testCase.get("humanId").asText()).isEqualTo(projectKey + "-TC-1");
        assertThat(testCase.get("currentVersion").get("steps")).hasSize(1);

        String requirementBody = """
                {"provider":"JIRA","externalKey":"BIT-27",
                 "url":"https://example.atlassian.net/browse/BIT-27","summary":"Login story"}
                """;
        mockMvc.perform(post("/api/v1/test-cases/{id}/requirements", testCaseId)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content(requirementBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/test-cases/{id}/requirements", testCaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalKey").value("BIT-27"));
    }

    /** Workflow 2: Test Plan erstellen -> Test Cases hinzufügen -> Execution erzeugen. */
    @Test
    void workflow2_testPlanWithTestCasesAndExecution() throws Exception {
        String projectKey = createProject("WF2", "Workflow 2 Project");
        JsonNode tc1 = createTestCase(projectKey, "Login funktioniert");
        JsonNode tc2 = createTestCase(projectKey, "Logout funktioniert");

        JsonNode plan = createTestPlan(projectKey, "Regression Login & Account");
        String planId = plan.get("id").asText();

        addTestCaseToPlan(planId, tc1.get("id").asText());
        addTestCaseToPlan(planId, tc2.get("id").asText());

        String execResponse = mockMvc.perform(post("/api/v1/test-plans/{id}/executions", planId)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode execution = objectMapper.readTree(execResponse);

        assertThat(execution.get("iterationNumber").asInt()).isEqualTo(1);
        assertThat(execution.get("testCases")).hasSize(2);
        assertThat(execution.get("status").asText()).isEqualTo("CREATED");

        // The runner needs the pinned version's full content, not just id/title.
        JsonNode firstTestCase = execution.get("testCases").get(0);
        assertThat(firstTestCase.get("description").asText()).isEqualTo("desc");
        assertThat(firstTestCase.get("preconditions").asText()).isEqualTo("none");
        assertThat(firstTestCase.get("steps")).hasSize(1);
        assertThat(firstTestCase.get("steps").get(0).get("action").asText()).isEqualTo("Schritt ausführen");
        assertThat(firstTestCase.get("steps").get(0).get("expectedResult").asText()).isEqualTo("Erwartetes Ergebnis");
    }

    /** Workflow 3: Execution -> Result PASS setzen -> Result FAILED setzen -> Historie prüfen. */
    @Test
    void workflow3_executionResultsAndHistory() throws Exception {
        String projectKey = createProject("WF3", "Workflow 3 Project");
        JsonNode tc = createTestCase(projectKey, "Suchfunktion");
        JsonNode plan = createTestPlan(projectKey, "Smoke Suite");
        addTestCaseToPlan(plan.get("id").asText(), tc.get("id").asText());
        JsonNode execution = createExecutionFromPlan(plan.get("id").asText());
        String executionId = execution.get("id").asText();
        String resultId = execution.get("testCases").get(0).get("result").get("id").asText();

        mockMvc.perform(patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultId)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                        .content("""
                                {"status":"PASSED","comment":"looks good","durationMs":1200}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"));

        mockMvc.perform(patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultId)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                        .content("""
                                {"status":"FAILED","comment":"regression found","actualResult":"Error page shown",
                                 "failureDetails":"AssertionError"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actualResult").value("Error page shown"))
                .andExpect(jsonPath("$.failureDetails").value("AssertionError"));

        // Execution transitions out of CREATED once a result was recorded.
        mockMvc.perform(get("/api/v1/executions/{id}", executionId))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // An existing (non-NOT_RUN) result stays editable -- e.g. correcting FAILED to BLOCKED.
        mockMvc.perform(patch("/api/v1/executions/{eid}/results/{rid}", executionId, resultId)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                        .content("""
                                {"status":"BLOCKED","comment":"blocked by env outage"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.comment").value("blocked by env outage"));
    }

    /**
     * Workflow 4 (besonders wichtig, ADR 0002/0003): Version 1 -> Execution erstellen
     * -> Test Case ändern -> Version 2 -> alte Execution nutzt weiterhin Version 1.
     */
    @Test
    void workflow4_historicalExecutionKeepsItsOriginalTestCaseVersion() throws Exception {
        String projectKey = createProject("WF4", "Workflow 4 Project");
        JsonNode tc = createTestCase(projectKey, "Checkout Prozess");
        String testCaseId = tc.get("id").asText();

        JsonNode plan = createTestPlan(projectKey, "Checkout Regression");
        addTestCaseToPlan(plan.get("id").asText(), testCaseId);

        JsonNode execution1 = createExecutionFromPlan(plan.get("id").asText());
        assertThat(execution1.get("testCases").get(0).get("testCaseVersionNumber").asInt()).isEqualTo(1);

        // Change the test case content -> creates version 2.
        String updateBody = """
                {"title":"Checkout Prozess (neu)","description":"updated","preconditions":"none",
                 "steps":[{"action":"Warenkorb öffnen","expectedResult":"Warenkorb sichtbar"},
                           {"action":"Bezahlen klicken","expectedResult":"Zahlungserfolg"}],
                 "status":"ACTIVE","priority":"HIGH","tags":[]}
                """;
        mockMvc.perform(put("/api/v1/test-cases/{id}", testCaseId)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion.versionNumber").value(2));

        // A NEW execution now snapshots version 2 ...
        JsonNode execution2 = createExecutionFromPlan(plan.get("id").asText());
        assertThat(execution2.get("iterationNumber").asInt()).isEqualTo(2);
        assertThat(execution2.get("testCases").get(0).get("testCaseVersionNumber").asInt()).isEqualTo(2);

        // ... while the ORIGINAL execution's snapshot is untouched: still version 1.
        String reloaded = mockMvc.perform(get("/api/v1/executions/{id}", execution1.get("id").asText()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode reloadedExecution1 = objectMapper.readTree(reloaded);
        JsonNode etc1 = reloadedExecution1.get("testCases").get(0);
        assertThat(etc1.get("testCaseVersionNumber").asInt()).isEqualTo(1);
        assertThat(etc1.get("title").asText()).isEqualTo("Checkout Prozess");
        // The full pinned content (not just title/version) must also stay on v1 --
        // the runner reads description/preconditions/steps straight from here.
        assertThat(etc1.get("description").asText()).isEqualTo("desc");
        assertThat(etc1.get("steps")).hasSize(1);
        assertThat(etc1.get("steps").get(0).get("action").asText()).isEqualTo("Schritt ausführen");

        JsonNode etc2 = execution2.get("testCases").get(0);
        assertThat(etc2.get("title").asText()).isEqualTo("Checkout Prozess (neu)");
        assertThat(etc2.get("description").asText()).isEqualTo("updated");
        assertThat(etc2.get("steps")).hasSize(2);
        assertThat(etc2.get("steps").get(1).get("action").asText()).isEqualTo("Bezahlen klicken");
    }

    /** Workflow 5: Report hochladen -> Execution zugeordnet -> Report erneut herunterladen. */
    @Test
    void workflow5_reportUploadAndDownload() throws Exception {
        String projectKey = createProject("WF5", "Workflow 5 Project");
        JsonNode tc = createTestCase(projectKey, "API Smoke Test");
        JsonNode plan = createTestPlan(projectKey, "API Suite");
        addTestCaseToPlan(plan.get("id").asText(), tc.get("id").asText());
        JsonNode execution = createExecutionFromPlan(plan.get("id").asText());
        String executionId = execution.get("id").asText();

        byte[] reportContent = "<testsuite name=\"demo\" tests=\"1\" failures=\"0\"/>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "junit-report.xml", "application/xml", reportContent);

        String uploadResponse = mockMvc.perform(multipart("/api/v1/executions/{id}/reports", executionId)
                        .file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode report = objectMapper.readTree(uploadResponse);
        String reportId = report.get("id").asText();
        assertThat(report.get("filename").asText()).isEqualTo("junit-report.xml");
        assertThat(report.get("sizeBytes").asLong()).isEqualTo(reportContent.length);

        mockMvc.perform(get("/api/v1/executions/{id}/reports", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reportId));

        byte[] downloaded = mockMvc.perform(get("/api/v1/reports/{id}/download", reportId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(reportContent);
    }

    // --- helpers -----------------------------------------------------------------

    private String createProject(String key, String name) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("key", key, "name", name));
        mockMvc.perform(post("/api/v1/projects").contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content(body))
                .andExpect(status().isCreated());
        return key;
    }

    private JsonNode createTestCase(String projectKey, String title) throws Exception {
        String body = """
                {"title":"%s","description":"desc","preconditions":"none","priority":"MEDIUM","tags":["smoke"],
                 "steps":[{"action":"Schritt ausführen","expectedResult":"Erwartetes Ergebnis"}]}
                """.formatted(title);
        String response = mockMvc.perform(post("/api/v1/projects/{projectKey}/test-cases", projectKey)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private JsonNode createTestPlan(String projectKey, String name) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", name));
        String response = mockMvc.perform(post("/api/v1/projects/{projectKey}/test-plans", projectKey)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private void addTestCaseToPlan(String planId, String testCaseId) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("testCaseId", testCaseId));
        mockMvc.perform(post("/api/v1/test-plans/{id}/test-cases", planId)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content(body))
                .andExpect(status().isCreated());
    }

    private JsonNode createExecutionFromPlan(String planId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/test-plans/{id}/executions", planId)
                        .contentType("application/json").characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }
}
