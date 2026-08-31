package com.testryn.integration.jira;

import com.testryn.integration.jira.repository.JiraConnectionConfigurationRepository;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JiraConnectionConfigurationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JiraConnectionConfigurationRepository repository;

    @BeforeEach
    @AfterEach
    void clearConfiguration() {
        repository.deleteAll();
    }

    @Test
    void nonSecretCloudSettingsCanBeUpdatedAndArePersisted() throws Exception {
        mockMvc.perform(put("/api/v1/integrations/jira/connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Customer Jira",
                                  "baseUrl": "https://customer.atlassian.net/",
                                  "email": "qa@example.com",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Customer Jira"))
                .andExpect(jsonPath("$.baseUrl").value("https://customer.atlassian.net"))
                .andExpect(jsonPath("$.email").value("qa@example.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.siteConfigured").value(true))
                .andExpect(jsonPath("$.tokenConfigured").value(false))
                .andExpect(jsonPath("$.apiToken").doesNotExist());

        mockMvc.perform(get("/api/v1/integrations/jira/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUrl").value("https://customer.atlassian.net"));
    }

    @Test
    void rejectsNonCloudAndPathUrls() throws Exception {
        mockMvc.perform(put("/api/v1/integrations/jira/connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Unsafe",
                                  "baseUrl": "http://localhost:8080/internal",
                                  "active": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
