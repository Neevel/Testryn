package com.testryn.testcase.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TestCaseDefinitionUpdateTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void definitionPatchVersionsContentPreservesMetadataAndRejectsStaleEditors() throws Exception {
        String key = "EDIT" + System.nanoTime() % 100000;
        post("/api/v1/projects", """
                {"key":"%s","name":"Editor API Project"}
                """.formatted(key), 201);
        JsonNode created = post("/api/v1/projects/" + key + "/test-cases", """
                {"title":"Login","preconditions":"User exists","priority":"HIGH","tags":["smoke"],
                 "automationReference":"auth.login","steps":[{"action":"Open","expectedResult":"Visible"}]}
                """, 201);

        String id = created.get("id").asText();
        String response = mockMvc.perform(patch("/api/v1/test-cases/{id}/definition", id)
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"expectedVersion":1,"title":"Login edited","description":"Detailed test description","preconditions":"Account is active",
                                 "steps":[{"action":"Open login","inputData":"https://app.example/login","expectedResult":"Form is visible"},
                                          {"action":"Submit","expectedResult":"Dashboard is visible"}]}
                                """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode updated = objectMapper.readTree(response);
        assertThat(updated.at("/currentVersion/versionNumber").asInt()).isEqualTo(2);
        assertThat(updated.at("/currentVersion/steps")).hasSize(2);
        assertThat(updated.at("/currentVersion/steps/0/inputData").asText()).isEqualTo("https://app.example/login");
        assertThat(updated.at("/currentVersion/description").asText()).isEqualTo("Detailed test description");
        assertThat(updated.get("priority").asText()).isEqualTo("HIGH");
        assertThat(updated.get("tags").get(0).asText()).isEqualTo("smoke");
        assertThat(updated.get("automationReference").asText()).isEqualTo("auth.login");

        mockMvc.perform(patch("/api/v1/test-cases/{id}/definition", id)
                        .contentType("application/json")
                        .content("""
                                {"expectedVersion":1,"title":"Stale","steps":[{"action":"A","expectedResult":"B"}]}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void linkedCreationCreatesTestAndRequirementTogether() throws Exception {
        String key = "LINK" + System.nanoTime();
        String issueKey = "EVAL-" + Math.abs(System.nanoTime());
        post("/api/v1/projects", """
                {"key":"%s","name":"Linked Creation Project"}
                """.formatted(key), 201);

        JsonNode created = post("/api/v1/requirement-links/test-cases", """
                {"projectKey":"%s","title":"Checkout from Jira","description":"Covers checkout",
                 "preconditions":"Cart has an item","priority":"HIGH","tags":["jira"],
                 "steps":[{"action":"Submit order","expectedResult":"Order is confirmed"}],
                 "provider":"JIRA","externalKey":"%s",
                 "url":"https://example.atlassian.net/browse/%s"}
                """.formatted(key, issueKey, issueKey), 201);
        assertThat(created.at("/currentVersion/description").asText()).isEqualTo("Covers checkout");

        String coverage = mockMvc.perform(get("/api/v1/requirement-links/coverage")
                        .param("provider", "jira").param("externalKey", issueKey))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(coverage);
        assertThat(body.get("totalCount").asInt()).isEqualTo(1);
        assertThat(body.at("/testCases/0/description").asText()).isEqualTo("Covers checkout");
    }

    private JsonNode post(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .contentType("application/json").content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return response.isBlank() ? null : objectMapper.readTree(response);
    }
}
