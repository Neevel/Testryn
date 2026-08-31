package com.testryn.integration.jira.oauth;

import com.testryn.integration.jira.oauth.JiraOAuthClient.AccessibleResource;
import com.testryn.integration.jira.oauth.JiraOAuthClient.TokenResponse;
import com.testryn.integration.jira.oauth.domain.JiraOAuthToken;
import com.testryn.security.domain.ServiceTokenScope;
import com.testryn.security.service.ServiceTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JiraOAuthEndpointsTest extends AbstractJiraOAuthTest {

    @Autowired ServiceTokenService serviceTokenService;

    // --- authorize-url (admin only) -------------------------------------------

    @Test
    void authorizeUrlRequiresAnAdminToken() throws Exception {
        configureSite();
        var readToken = serviceTokenService.create("read", null, EnumSet.of(ServiceTokenScope.READ), null);
        var writeToken = serviceTokenService.create("write", null, EnumSet.of(ServiceTokenScope.WRITE), null);

        mockMvc.perform(post("/api/v1/integrations/jira/oauth/authorize-url").header("Authorization", ""))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/integrations/jira/oauth/authorize-url")
                        .header("Authorization", "Bearer " + readToken.rawToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/integrations/jira/oauth/authorize-url")
                        .header("Authorization", "Bearer " + writeToken.rawToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/integrations/jira/oauth/authorize-url")) // default suite token is ADMIN
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value(org.hamcrest.Matchers.startsWith(
                        "https://auth.atlassian.com/authorize?")));
    }

    @Test
    void authorizeUrlIsRejectedWhenTheClientIsNotFullyConfigured() throws Exception {
        // no site configured yet -> 400 (BadRequest), never a 500 or a stack trace
        mockMvc.perform(post("/api/v1/integrations/jira/oauth/authorize-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("site")));
    }

    // --- callback (public, state-protected) ----------------------------------

    @Test
    void theCallbackNeedsNoServiceTokenAndRendersAPlainStatusPage() throws Exception {
        // an unknown state still returns a friendly HTML page, not 401/403/500
        mockMvc.perform(get("/integrations/jira/oauth/callback")
                        .param("code", "x").param("state", "not-a-real-state")
                        .header("Authorization", ""))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("no longer valid")));
    }

    @Test
    void aSuccessfulCallbackRendersTheCompletionPageAndStoresTheConnection() throws Exception {
        configureSite();
        String stateValue = extractState(oauthService.buildAuthorizationUrl());
        when(oauthClient.exchangeAuthorizationCode(any(), any()))
                .thenReturn(new TokenResponse("acc", "ref", 3600, null));
        when(oauthClient.accessibleResources(any()))
                .thenReturn(List.of(new AccessibleResource("cloud-1", "Acme", SITE_URL)));

        mockMvc.perform(get("/integrations/jira/oauth/callback")
                        .param("code", "the-code").param("state", stateValue)
                        .header("Authorization", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("authorization complete")));

        assertThat(tokenRepository.findById(JiraOAuthToken.DEFAULT_ID)).isPresent();
    }

    // --- disconnect (admin only) --------------------------------------------

    @Test
    void disconnectRequiresAnAdminTokenAndReportsTheStatus() throws Exception {
        configureSite();
        var readToken = serviceTokenService.create("read2", null, EnumSet.of(ServiceTokenScope.READ), null);
        mockMvc.perform(post("/api/v1/integrations/jira/oauth/disconnect")
                        .header("Authorization", "Bearer " + readToken.rawToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/integrations/jira/oauth/disconnect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oauthConnected").value(false));
    }

    // --- connection GET/PUT: authType switch + no secrets -------------------

    @Test
    void authTypeCanBeSwitchedToOAuth2AndBackToApiTokenAndThePutNeverRestatesIt() throws Exception {
        configureSite(); // sets OAUTH2
        mockMvc.perform(get("/api/v1/integrations/jira/connection"))
                .andExpect(jsonPath("$.authType").value("OAUTH2"))
                .andExpect(jsonPath("$.oauthConfigured").value(true))
                .andExpect(jsonPath("$.oauthConnected").value(false))
                .andExpect(jsonPath("$.reauthorizationRequired").value(true));

        // a plain metadata edit that omits authType must keep OAUTH2
        mockMvc.perform(put("/api/v1/integrations/jira/connection").contentType("application/json")
                        .content("{\"name\":\"Renamed\",\"baseUrl\":\"%s\",\"active\":true}".formatted(SITE_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authType").value("OAUTH2"));

        // explicit switch back to API_TOKEN
        mockMvc.perform(put("/api/v1/integrations/jira/connection").contentType("application/json")
                        .content("{\"name\":\"Renamed\",\"baseUrl\":\"%s\",\"active\":true,\"authType\":\"API_TOKEN\"}"
                                .formatted(SITE_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authType").value("API_TOKEN"))
                .andExpect(jsonPath("$.reauthorizationRequired").value(false));
    }

    @Test
    void noOAuthSecretEverAppearsInTheConnectionResponse() throws Exception {
        configureSite();
        // establish a real (encrypted) token
        String state = extractState(oauthService.buildAuthorizationUrl());
        when(oauthClient.exchangeAuthorizationCode(any(), any()))
                .thenReturn(new TokenResponse("super-secret-access", "super-secret-refresh", 3600, null));
        when(oauthClient.accessibleResources(any()))
                .thenReturn(List.of(new AccessibleResource("cloud-1", "Acme", SITE_URL)));
        oauthService.handleCallback("c", state, null);

        String body = mockMvc.perform(get("/api/v1/integrations/jira/connection"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(CLIENT_SECRET);
        assertThat(body).doesNotContain(ENCRYPTION_KEY_B64);
        assertThat(body).doesNotContain("super-secret-access");
        assertThat(body).doesNotContain("super-secret-refresh");
        assertThat(body).contains("\"oauthConnected\":true");
        assertThat(body).contains(SITE_URL); // the site URL is non-secret and expected
    }

    @Test
    void connectionTestInOAuthModeWithoutAuthorizationExplainsWhatIsNeeded() throws Exception {
        configureSite();

        mockMvc.perform(post("/api/v1/integrations/jira/connection/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("authorization")));
    }

    // --- API_TOKEN regression ---------------------------------------------------

    @Test
    void apiTokenModeConnectionResponseKeepsItsExistingShape() throws Exception {
        mockMvc.perform(put("/api/v1/integrations/jira/connection").contentType("application/json")
                        .content("{\"name\":\"Legacy\",\"baseUrl\":\"%s\",\"email\":\"q@a.test\",\"active\":true}"
                                .formatted(SITE_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authType").value("API_TOKEN"))
                .andExpect(jsonPath("$.tokenConfigured").value(false))
                .andExpect(jsonPath("$.apiToken").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    private static String extractState(String url) {
        return java.util.Arrays.stream(java.net.URI.create(url).getRawQuery().split("&"))
                .map(p -> p.split("=", 2))
                .filter(p -> p[0].equals("state"))
                .map(p -> java.net.URLDecoder.decode(p[1], java.nio.charset.StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
    }
}
