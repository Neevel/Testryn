package com.testryn.integration.jira.oauth;

import com.testryn.integration.jira.oauth.domain.JiraOAuthState;
import com.testryn.integration.jira.oauth.domain.JiraOAuthToken;
import com.testryn.integration.jira.oauth.repository.JiraOAuthStateRepository;
import com.testryn.integration.jira.oauth.repository.JiraOAuthTokenRepository;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.SecureRandom;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OAuth client id/secret/redirect are configured but the token encryption key is
 * NOT. The application must still start and serve every non-OAuth path; only the
 * OAuth operations fail, with a clear message and no stack trace (ADR 0018 fallback
 * strategy).
 */
class JiraOAuthMissingKeyTest extends AbstractIntegrationTest {

    @MockBean JiraOAuthClient oauthClient;
    @Autowired MockMvc mockMvc;
    @Autowired JiraOAuthStateRepository stateRepository;
    @Autowired JiraOAuthTokenRepository tokenRepository;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("testryn.jira.oauth.client-id", () -> "id");
        registry.add("testryn.jira.oauth.client-secret", () -> "secret");
        registry.add("testryn.jira.oauth.redirect-uri", () -> "https://t.example/integrations/jira/oauth/callback");
        registry.add("testryn.jira.oauth.encryption-key", () -> ""); // deliberately absent
    }

    @Test
    void authorizeUrlIsRejectedWithABadRequestNamingTheEncryptionKey() throws Exception {
        mockMvc.perform(put("/api/v1/integrations/jira/connection").contentType("application/json")
                .content("{\"name\":\"A\",\"baseUrl\":\"https://acme.atlassian.net\",\"active\":true,\"authType\":\"OAUTH2\"}"));

        mockMvc.perform(post("/api/v1/integrations/jira/oauth/authorize-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("encryption key")));
    }

    @Test
    void aCallbackWithAValidStateStillFailsGracefullyWithoutTheKey() throws Exception {
        JiraOAuthState.Generated generated = JiraOAuthState.generate(new SecureRandom(), Duration.ofMinutes(10));
        stateRepository.save(generated.entity());

        mockMvc.perform(get("/integrations/jira/oauth/callback")
                        .param("code", "c").param("state", generated.rawState())
                        .header("Authorization", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Could not complete authorization")));
    }

    @Test
    void theRestOfTheApiIsUnaffected() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/jira/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oauthConfigured").value(false)); // key missing -> not "ready"
        tokenRepository.deleteAll();
        stateRepository.deleteAll();
    }
}
