package com.testryn.integration.jira.oauth;

import com.testryn.common.error.UpstreamServiceException;
import com.testryn.integration.jira.oauth.JiraOAuthClient.AccessibleResource;
import com.testryn.integration.jira.oauth.JiraOAuthClient.OAuthHttpException;
import com.testryn.integration.jira.oauth.JiraOAuthClient.TokenResponse;
import com.testryn.integration.jira.oauth.JiraOAuthService.CallbackOutcome;
import com.testryn.integration.jira.oauth.domain.JiraOAuthToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JiraOAuthServiceIntegrationTest extends AbstractJiraOAuthTest {

    @Autowired JdbcTemplate jdbc;

    private String freshState() {
        String url = oauthService.buildAuthorizationUrl();
        return queryParam(url, "state");
    }

    // --- authorization URL ---------------------------------------------------

    @Test
    void authorizationUrlContainsEveryRequiredAtlassianParameterAndTheConfiguredRedirect() throws Exception {
        configureSite();

        String url = oauthService.buildAuthorizationUrl();

        assertThat(url).startsWith("https://auth.atlassian.com/authorize?");
        assertThat(queryParam(url, "audience")).isEqualTo("api.atlassian.com");
        assertThat(queryParam(url, "client_id")).isEqualTo(CLIENT_ID);
        assertThat(queryParam(url, "response_type")).isEqualTo("code");
        assertThat(queryParam(url, "prompt")).isEqualTo("consent");
        assertThat(queryParam(url, "scope")).isEqualTo("read:jira-work offline_access");
        assertThat(queryParam(url, "redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(queryParam(url, "state")).isNotBlank();
        // client secret / encryption key never appear in the URL
        assertThat(url).doesNotContain(CLIENT_SECRET).doesNotContain(ENCRYPTION_KEY_B64);
    }

    @Test
    void eachAuthorizationUrlGetsAFreshStateAndOnlyItsHashIsPersisted() throws Exception {
        configureSite();

        String s1 = freshState();
        String s2 = freshState();

        assertThat(s1).isNotEqualTo(s2);
        List<String> stored = jdbc.queryForList("select state_hash from jira_oauth_state", String.class);
        assertThat(stored).doesNotContain(s1, s2); // raw state never stored
        assertThat(stored).hasSize(2);
    }

    @Test
    void authorizationUrlIsRefusedWhenNoSiteIsConfiguredYet() {
        assertThatThrownBy(() -> oauthService.buildAuthorizationUrl())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("site");
    }

    // --- callback ----------------------------------------------------------------

    @Test
    void aSuccessfulCallbackStoresEncryptedTokensAndTheMatchedCloudId() throws Exception {
        configureSite();
        String state = freshState();
        when(oauthClient.exchangeAuthorizationCode(any(), eq("auth-code")))
                .thenReturn(new TokenResponse("access-A", "refresh-A", 3600, "read:jira-work offline_access"));
        when(oauthClient.accessibleResources("access-A")).thenReturn(List.of(
                new AccessibleResource("cloud-sandbox", "Sandbox", "https://acme-sandbox.atlassian.net"),
                new AccessibleResource("cloud-prod", "Prod", "https://acme.atlassian.net")));

        CallbackOutcome outcome = oauthService.handleCallback("auth-code", state, null);

        assertThat(outcome).isEqualTo(CallbackOutcome.CONNECTED);
        Map<String, Object> row = jdbc.queryForMap("select * from jira_oauth_token");
        assertThat(row.get("cloud_id")).isEqualTo("cloud-prod");
        assertThat(row.get("site_url")).isEqualTo("https://acme.atlassian.net");
        // stored values are ciphertext, not the raw tokens
        assertThat(row.get("access_token_ciphertext")).asString().doesNotContain("access-A");
        assertThat(row.get("refresh_token_ciphertext")).asString().doesNotContain("refresh-A");
        assertThat(cipher().decrypt((String) row.get("access_token_ciphertext"))).isEqualTo("access-A");
        assertThat(cipher().decrypt((String) row.get("refresh_token_ciphertext"))).isEqualTo("refresh-A");
    }

    @Test
    void anUnknownStateIsRejectedAndNothingIsStored() throws Exception {
        configureSite();

        assertThat(oauthService.handleCallback("code", "never-issued", null))
                .isEqualTo(CallbackOutcome.INVALID_STATE);
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void aStateCannotBeUsedTwice() throws Exception {
        configureSite();
        String state = freshState();
        when(oauthClient.exchangeAuthorizationCode(any(), any()))
                .thenReturn(new TokenResponse("a", "r", 3600, null));
        when(oauthClient.accessibleResources(any())).thenReturn(List.of(
                new AccessibleResource("c", "Acme", SITE_URL)));

        assertThat(oauthService.handleCallback("code", state, null)).isEqualTo(CallbackOutcome.CONNECTED);
        assertThat(oauthService.handleCallback("code", state, null)).isEqualTo(CallbackOutcome.INVALID_STATE);
    }

    @Test
    void anExpiredStateIsRejected() throws Exception {
        configureSite();
        String state = freshState();
        // force the row past its TTL
        jdbc.update("update jira_oauth_state set expires_at = ?",
                java.time.OffsetDateTime.now().minusMinutes(1));

        assertThat(oauthService.handleCallback("code", state, null)).isEqualTo(CallbackOutcome.INVALID_STATE);
    }

    @Test
    void anErrorParameterFromAtlassianIsSurfacedAsDeniedWithoutConsumingState() throws Exception {
        configureSite();
        String state = freshState();

        assertThat(oauthService.handleCallback(null, state, "access_denied")).isEqualTo(CallbackOutcome.DENIED);
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void aSiteTheAuthorizedAccountCannotAccessIsRejected() throws Exception {
        configureSite();
        String state = freshState();
        when(oauthClient.exchangeAuthorizationCode(any(), any()))
                .thenReturn(new TokenResponse("acc", "ref", 3600, null));
        when(oauthClient.accessibleResources("acc")).thenReturn(List.of(
                new AccessibleResource("other", "Other", "https://someone-else.atlassian.net")));

        assertThat(oauthService.handleCallback("code", state, null)).isEqualTo(CallbackOutcome.SITE_MISMATCH);
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void aGrantWithoutARefreshTokenIsRejectedSinceTheConnectionCouldNotBeKeptAlive() throws Exception {
        configureSite();
        String state = freshState();
        when(oauthClient.exchangeAuthorizationCode(any(), any()))
                .thenReturn(new TokenResponse("acc", null, 3600, "read:jira-work")); // offline_access not granted

        assertThat(oauthService.handleCallback("code", state, null)).isEqualTo(CallbackOutcome.UPSTREAM_ERROR);
        assertThat(tokenRepository.count()).isZero();
    }

    // --- access token / refresh -------------------------------------------------

    @Test
    void currentAccessTokenReturnsTheStoredTokenWhileStillFresh() {
        seedToken("access-fresh", "refresh-fresh", Instant.now().plusSeconds(1800));

        JiraOAuthService.ActiveToken token = oauthService.currentAccessToken();

        assertThat(token.accessToken()).isEqualTo("access-fresh");
        assertThat(token.cloudId()).isEqualTo("cloud-1");
        verify(oauthClient, times(0)).refresh(any(), any());
    }

    @Test
    void anExpiredAccessTokenIsRefreshedAndTheRefreshTokenIsRotatedInStorage() {
        seedToken("access-old", "refresh-old", Instant.now().minusSeconds(30));
        when(oauthClient.refresh(any(), eq("refresh-old")))
                .thenReturn(new TokenResponse("access-new", "refresh-new-rotated", 3600, null));

        JiraOAuthService.ActiveToken token = oauthService.currentAccessToken();

        assertThat(token.accessToken()).isEqualTo("access-new");
        JiraOAuthToken row = tokenRepository.findById(JiraOAuthToken.DEFAULT_ID).orElseThrow();
        assertThat(cipher().decrypt(row.getAccessTokenCiphertext())).isEqualTo("access-new");
        assertThat(cipher().decrypt(row.getRefreshTokenCiphertext())).isEqualTo("refresh-new-rotated");
        assertThat(row.getAccessTokenExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void aPermanentRefreshFailureDropsTheDeadTokenAndAsksForReauthorization() {
        seedToken("acc", "refresh-dead", Instant.now().minusSeconds(30));
        when(oauthClient.refresh(any(), any())).thenThrow(new OAuthHttpException("rejected (HTTP 400)", true));

        assertThatThrownBy(() -> oauthService.currentAccessToken())
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("re-authorize");
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void aTransientRefreshFailureKeepsTheTokenAndReportsUnreachable() {
        seedToken("acc", "refresh", Instant.now().minusSeconds(30));
        when(oauthClient.refresh(any(), any())).thenThrow(new OAuthHttpException("bad gateway", false));

        assertThatThrownBy(() -> oauthService.currentAccessToken())
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("not reachable");
        assertThat(tokenRepository.count()).isOne();
    }

    @Test
    void concurrentCallersRefreshExactlyOnceThenReuseTheRotatedToken() throws Exception {
        seedToken("acc-old", "refresh-old", Instant.now().minusSeconds(30));
        doAnswer(inv -> {
            Thread.sleep(400); // hold the row lock long enough that the 2nd caller is definitely waiting
            return new TokenResponse("acc-new", "refresh-new", 3600, null);
        }).when(oauthClient).refresh(any(), any());

        var pool = Executors.newFixedThreadPool(2);
        try {
            var f1 = CompletableFuture.supplyAsync(() -> oauthService.currentAccessToken(), pool);
            var f2 = CompletableFuture.supplyAsync(() -> oauthService.currentAccessToken(), pool);
            CompletableFuture.allOf(f1, f2).join();
            assertThat(f1.join().accessToken()).isEqualTo("acc-new");
            assertThat(f2.join().accessToken()).isEqualTo("acc-new");
        } finally {
            pool.shutdownNow();
        }
        verify(oauthClient, times(1)).refresh(any(), any());
    }

    // --- disconnect -----------------------------------------------------------

    @Test
    void disconnectRemovesOnlyTheOAuthCredentialsAndNeverAnyTestData() throws Exception {
        configureSite();
        // Create the requirement link BEFORE any token exists, so the (best-effort)
        // Jira enrichment finds no OAuth token and skips silently -- no real HTTP.
        String testCaseId = createLinkedTestCaseViaApi();
        seedToken("acc", "refresh", Instant.now().plusSeconds(1800));

        oauthService.disconnect();

        assertThat(tokenRepository.count()).isZero();
        verify(oauthClient, times(1)).revoke(any(), eq("refresh"));
        assertThat(connectionRepository.count()).isOne(); // site config untouched
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/test-cases/" + testCaseId + "/requirements"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].externalKey").value("ACME-1"));
    }

    private String createLinkedTestCaseViaApi() throws Exception {
        var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/projects")
                .contentType("application/json").content("{\"key\":\"OAUTHKEEP\",\"name\":\"Keep\"}");
        mockMvc.perform(post);
        var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/requirement-links/test-cases")
                        .contentType("application/json")
                        .content("""
                                {"projectKey":"OAUTHKEEP","title":"survives disconnect","priority":"MEDIUM",
                                 "steps":[{"action":"a","expectedResult":"b"}],
                                 "provider":"JIRA","externalKey":"ACME-1","url":"%s/browse/ACME-1"}
                                """.formatted(SITE_URL)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void disconnectOnAConnectionThatWasNeverAuthorizedIsANoOp() {
        oauthService.disconnect();
        assertThat(tokenRepository.count()).isZero();
    }

    // --- helpers --------------------------------------------------------------

    private void seedToken(String access, String refresh, Instant expiresAt) {
        SecretCipher c = cipher();
        tokenRepository.save(JiraOAuthToken.create(
                c.encrypt(access), c.encrypt(refresh), expiresAt, "cloud-1", SITE_URL, "read:jira-work offline_access"));
    }

    private static String queryParam(String url, String name) {
        return URI.create(url).getQuery() == null ? null :
                java.util.Arrays.stream(URI.create(url).getRawQuery().split("&"))
                        .map(p -> p.split("=", 2))
                        .filter(p -> p[0].equals(name))
                        .map(p -> java.net.URLDecoder.decode(p[1], java.nio.charset.StandardCharsets.UTF_8))
                        .findFirst().orElse(null);
    }
}
