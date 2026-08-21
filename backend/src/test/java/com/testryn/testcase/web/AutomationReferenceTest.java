package com.testryn.testcase.web;

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
 * Covers {@code automationReference} (ADR 0009): setting it on create/update, exact
 * search, and project-scoped duplicate rejection. Bulk-API resolution via
 * automationReference is covered separately in {@link BulkResultUpdateTest} --
 * lookup here is scoped to "does the field behave correctly on TestCase itself".
 */
class AutomationReferenceTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String projectKey;

    @BeforeEach
    void setUpProject() throws Exception {
        projectKey = "AR" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Automation Reference Project"}
                """.formatted(projectKey), 201);
    }

    @Test
    void setsAutomationReferenceOnCreate() throws Exception {
        JsonNode created = createTestCase("Login valid", "auth.login.valid");

        assertThat(created.get("automationReference").asText()).isEqualTo("auth.login.valid");
    }

    @Test
    void automationReferenceIsOptional() throws Exception {
        JsonNode created = createTestCase("Login valid", null);

        assertThat(created.get("automationReference").isNull()).isTrue();
    }

    @Test
    void rejectsANonMachineFriendlyReference() throws Exception {
        postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"Bad ref","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"Do it","expectedResult":"Works"}],
                 "automationReference":"has spaces / slash"}
                """, 400);
    }

    @Test
    void acceptsTheJUnitClassnameHashNameConvention() throws Exception {
        // ADR 0013: testryn-publisher's publish-junit maps JUnit XML to
        // classname#name (e.g. com.example.LoginTest#successfulLogin) -- '#' was
        // deliberately added to ADR 0009's original character set for this.
        JsonNode created = createTestCase("Login valid (JUnit)", "com.example.LoginTest#successfulLogin");

        assertThat(created.get("automationReference").asText()).isEqualTo("com.example.LoginTest#successfulLogin");
    }

    @Test
    void findsATestCaseByExactAutomationReference() throws Exception {
        createTestCase("Login valid", "auth.login.valid");
        createTestCase("Login invalid", "auth.login.invalid");

        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases?automationReference=auth.login.valid");

        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("automationReference").asText()).isEqualTo("auth.login.valid");
    }

    @Test
    void automationReferenceSearchIsCaseSensitiveExactMatch() throws Exception {
        createTestCase("Login valid", "auth.login.valid");

        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases?automationReference=AUTH.LOGIN.VALID");

        assertThat(page.get("content")).isEmpty();
    }

    @Test
    void unknownAutomationReferenceReturnsAnEmptyPage() throws Exception {
        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases?automationReference=does.not.exist");

        assertThat(page.get("content")).isEmpty();
    }

    @Test
    void rejectsADuplicateAutomationReferenceInTheSameProjectOnCreate() throws Exception {
        createTestCase("Login valid", "auth.login.valid");

        postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"Another test case","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"Do it","expectedResult":"Works"}],
                 "automationReference":"auth.login.valid"}
                """, 409);
    }

    @Test
    void rejectsADuplicateAutomationReferenceOnUpdate() throws Exception {
        createTestCase("Login valid", "auth.login.valid");
        JsonNode second = createTestCase("Login invalid", "auth.login.invalid");
        String secondId = second.get("id").asText();

        putJson("/api/v1/test-cases/" + secondId, """
                {"title":"Login invalid","priority":"MEDIUM","status":"DRAFT","tags":[],
                 "steps":[{"action":"Do it","expectedResult":"Works"}],
                 "automationReference":"auth.login.valid"}
                """, 409);
    }

    @Test
    void aTestCaseCanChangeItsOwnAutomationReferenceWithoutConflictingWithItself() throws Exception {
        JsonNode created = createTestCase("Login valid", "auth.login.valid");
        String id = created.get("id").asText();

        JsonNode updated = putJson("/api/v1/test-cases/" + id, """
                {"title":"Login valid","description":"desc","preconditions":"none",
                 "priority":"MEDIUM","status":"DRAFT","tags":[],
                 "steps":[{"action":"Do it","expectedResult":"Works"}],
                 "automationReference":"auth.login.valid.v2"}
                """, 200);

        assertThat(updated.get("automationReference").asText()).isEqualTo("auth.login.valid.v2");
    }

    @Test
    void changingAutomationReferenceOnUpdateDoesNotCreateANewVersion() throws Exception {
        JsonNode created = createTestCase("Login valid", "auth.login.valid");
        String id = created.get("id").asText();

        JsonNode updated = putJson("/api/v1/test-cases/" + id, """
                {"title":"Login valid","description":"desc","preconditions":"none",
                 "priority":"MEDIUM","status":"DRAFT","tags":[],
                 "steps":[{"action":"Do it","expectedResult":"Works"}],
                 "automationReference":"auth.login.valid.renamed"}
                """, 200);

        assertThat(updated.get("currentVersion").get("versionNumber").asInt()).isEqualTo(1);
    }

    // --- helpers -----------------------------------------------------------------

    private JsonNode createTestCase(String title, String automationReference) throws Exception {
        String automationReferenceJson = automationReference == null ? "null" : "\"" + automationReference + "\"";
        return postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"%s","description":"desc","preconditions":"none","priority":"MEDIUM","tags":[],
                 "steps":[{"action":"Do it","expectedResult":"Works"}],
                 "automationReference":%s}
                """.formatted(title, automationReferenceJson), 201);
    }

    private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? null : objectMapper.readTree(response);
    }

    private JsonNode putJson(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(put(path)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? null : objectMapper.readTree(response);
    }

    private JsonNode getJson(String path) throws Exception {
        String response = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }
}
