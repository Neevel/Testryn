package com.testryn.integration.jira.oauth;

import com.testryn.integration.jira.oauth.JiraOAuthClient.OAuthHttpException;
import com.testryn.integration.jira.oauth.JiraOAuthClient.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link JiraOAuthClient} against {@link MockRestServiceServer} -- real request
 * building / response parsing, no socket. Verifies the exact Atlassian endpoint
 * URLs and bodies, and the permanent (4xx) vs transient (5xx/network) split.
 */
class JiraOAuthClientTest {

    private JiraOAuthProperties props() {
        JiraOAuthProperties p = new JiraOAuthProperties();
        p.setClientId("client-abc");
        p.setClientSecret("secret-xyz");
        p.setRedirectUri("https://testryn.example/integrations/jira/oauth/callback");
        return p;
    }

    @Test
    void exchangesAnAuthorizationCodeAtTheAtlassianTokenEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth.atlassian.com/oauth/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.grant_type").value("authorization_code"))
                .andExpect(jsonPath("$.client_id").value("client-abc"))
                .andExpect(jsonPath("$.client_secret").value("secret-xyz"))
                .andExpect(jsonPath("$.code").value("the-code"))
                .andExpect(jsonPath("$.redirect_uri").value("https://testryn.example/integrations/jira/oauth/callback"))
                .andRespond(withSuccess("""
                        {"access_token":"acc-1","refresh_token":"ref-1","expires_in":3600,"scope":"read:jira-work offline_access"}
                        """, MediaType.APPLICATION_JSON));

        TokenResponse token = new JiraOAuthClient(builder).exchangeAuthorizationCode(props(), "the-code");

        assertThat(token.accessToken()).isEqualTo("acc-1");
        assertThat(token.refreshToken()).isEqualTo("ref-1");
        assertThat(token.expiresInSeconds()).isEqualTo(3600L);
        server.verify();
    }

    @Test
    void refreshSendsGrantTypeRefreshTokenAndReturnsTheRotatedTokens() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth.atlassian.com/oauth/token"))
                .andExpect(jsonPath("$.grant_type").value("refresh_token"))
                .andExpect(jsonPath("$.refresh_token").value("old-refresh"))
                .andRespond(withSuccess("""
                        {"access_token":"acc-2","refresh_token":"ref-2-rotated","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));

        TokenResponse token = new JiraOAuthClient(builder).refresh(props(), "old-refresh");

        assertThat(token.accessToken()).isEqualTo("acc-2");
        assertThat(token.refreshToken()).isEqualTo("ref-2-rotated");
        server.verify();
    }

    @Test
    void aFourxxFromTheTokenEndpointIsPermanent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth.atlassian.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new JiraOAuthClient(builder).refresh(props(), "dead"))
                .isInstanceOfSatisfying(OAuthHttpException.class, ex -> assertThat(ex.permanent()).isTrue())
                .hasMessageNotContaining("invalid_grant"); // body not echoed
    }

    @Test
    void aFivexxFromTheTokenEndpointIsTransient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth.atlassian.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> new JiraOAuthClient(builder).exchangeAuthorizationCode(props(), "c"))
                .isInstanceOfSatisfying(OAuthHttpException.class, ex -> assertThat(ex.permanent()).isFalse());
    }

    @Test
    void parsesAccessibleResources() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.atlassian.com/oauth/token/accessible-resources"))
                .andRespond(withSuccess("""
                        [
                          {"id":"cloud-1","name":"Prod","url":"https://acme.atlassian.net"},
                          {"id":"cloud-2","name":"Sandbox","url":"https://acme-sandbox.atlassian.net"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<JiraOAuthClient.AccessibleResource> resources =
                new JiraOAuthClient(builder).accessibleResources("acc-1");

        assertThat(resources).extracting(JiraOAuthClient.AccessibleResource::id).containsExactly("cloud-1", "cloud-2");
        assertThat(resources.get(0).url()).isEqualTo("https://acme.atlassian.net");
        server.verify();
    }

    @Test
    void revokeSwallowsFailuresSoALocalDisconnectIsNeverBlocked() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth.atlassian.com/oauth/revoke"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        new JiraOAuthClient(builder).revoke(props(), "some-refresh"); // must not throw
    }
}
