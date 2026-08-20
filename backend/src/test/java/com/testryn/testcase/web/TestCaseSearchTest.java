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
 * Covers the search/filter/pagination behavior of
 * {@code GET /projects/{projectKey}/test-cases} (ADR 0008) -- the primary mechanism
 * an AI agent uses to check for near-duplicate test cases (Abschnitt 11) before
 * creating a new one, and the UI's Test Case table search/filter.
 */
class TestCaseSearchTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String projectKey;

    @BeforeEach
    void setUpProjectWithSeveralTestCases() throws Exception {
        projectKey = "TS" + System.nanoTime() % 100000;
        postJson("/api/v1/projects", """
                {"key":"%s","name":"Search Project"}
                """.formatted(projectKey), 201);

        createTestCase("Login with valid credentials", "HIGH", "smoke");
        createTestCase("Login with invalid password", "MEDIUM", "regression");
        createTestCase("Logout clears session", "LOW", "smoke");
        createTestCase("Checkout with valid payment", "CRITICAL", "regression");
    }

    @Test
    void defaultListReturnsAllTestCasesInAPageEnvelope() throws Exception {
        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases");

        assertThat(page.get("content")).hasSize(4);
        assertThat(page.get("totalElements").asLong()).isEqualTo(4);
        assertThat(page.get("page").asInt()).isEqualTo(0);
    }

    @Test
    void queryMatchesTitleCaseInsensitively() throws Exception {
        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases?query=login");

        assertThat(page.get("content")).hasSize(2);
        for (JsonNode tc : page.get("content")) {
            assertThat(tc.get("currentVersion").get("title").asText().toLowerCase()).contains("login");
        }
    }

    @Test
    void queryMatchesHumanReadableId() throws Exception {
        JsonNode first = getJson("/api/v1/projects/" + projectKey + "/test-cases").get("content").get(0);
        String humanId = first.get("humanId").asText();

        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases?query=" + humanId);

        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("humanId").asText()).isEqualTo(humanId);
    }

    @Test
    void filtersByTag() throws Exception {
        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases?tag=smoke");

        assertThat(page.get("content")).hasSize(2);
        for (JsonNode tc : page.get("content")) {
            boolean hasSmoke = false;
            for (JsonNode t : tc.get("tags")) {
                hasSmoke |= t.asText().equals("smoke");
            }
            assertThat(hasSmoke).isTrue();
        }
    }

    @Test
    void filtersByPriority() throws Exception {
        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases?priority=CRITICAL");

        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("priority").asText()).isEqualTo("CRITICAL");
    }

    @Test
    void filtersByRequirementKeyCaseInsensitively() throws Exception {
        String testCaseId = getJson("/api/v1/projects/" + projectKey + "/test-cases?query=Checkout")
                .get("content").get(0).get("id").asText();
        postJson("/api/v1/test-cases/" + testCaseId + "/requirements", """
                {"provider":"JIRA","externalKey":"BIT-42","url":"https://example.atlassian.net/browse/BIT-42","summary":"x"}
                """, 201);

        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases?requirementKey=bit-42");

        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(testCaseId);
    }

    @Test
    void paginatesWithASmallPageSize() throws Exception {
        JsonNode firstPage = getJson("/api/v1/projects/" + projectKey + "/test-cases?size=2&page=0");
        JsonNode secondPage = getJson("/api/v1/projects/" + projectKey + "/test-cases?size=2&page=1");

        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(secondPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(4);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("content").get(0).get("id").asText())
                .isNotEqualTo(secondPage.get("content").get(0).get("id").asText());
    }

    @Test
    void combiningFiltersThatMatchNothingReturnsAnEmptyPageNot404() throws Exception {
        JsonNode page = getJson("/api/v1/projects/" + projectKey + "/test-cases?query=login&priority=CRITICAL");

        assertThat(page.get("content")).isEmpty();
        assertThat(page.get("totalElements").asLong()).isEqualTo(0);
    }

    // --- helpers -----------------------------------------------------------------

    private void createTestCase(String title, String priority, String tag) throws Exception {
        postJson("/api/v1/projects/" + projectKey + "/test-cases", """
                {"title":"%s","description":"desc","preconditions":"none","priority":"%s","tags":["%s"],
                 "steps":[{"action":"Do it","expectedResult":"Works"}]}
                """.formatted(title, priority, tag), 201);
    }

    private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private JsonNode getJson(String path) throws Exception {
        String response = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }
}
