package com.testryn.integration.jira;

import com.testryn.common.error.UpstreamServiceException;
import com.testryn.integration.jira.oauth.JiraOAuthService;
import com.testryn.integration.jira.oauth.JiraOAuthService.ActiveToken;
import com.testryn.integration.jira.service.JiraConnectionSettings;
import com.testryn.requirement.provider.ExternalRequirementInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The OAUTH2 branch of {@link JiraIssueClient} (ADR 0018): calls go through the
 * {@code api.atlassian.com/ex/jira/{cloudId}} gateway with a Bearer token from
 * {@link JiraOAuthService}, while the human-facing browse URL still uses the tenant
 * URL. The API_TOKEN branch is covered unchanged by the existing
 * {@code JiraIssueClient*Test} classes.
 */
class JiraIssueClientOAuthTest {

    private static final String TENANT = "https://acme.atlassian.net";

    private JiraConnectionSettings oauthSettings() {
        return new JiraConnectionSettings("Acme", TENANT, "qa@acme.test", null, JiraAuthType.OAUTH2, true);
    }

    @Test
    void fetchGoesThroughTheGatewayWithABearerTokenAndKeepsTheTenantBrowseUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.atlassian.com/ex/jira/cloud-99/rest/api/3/issue/ACME-7"
                        + "?fields=summary,issuetype,status,description"))
                .andExpect(header("Authorization", "Bearer access-token-123"))
                .andRespond(withSuccess("""
                        {"id":"1","key":"ACME-7","fields":{"summary":"S","issuetype":{"name":"Story"},
                         "status":{"name":"To Do"},"description":null}}
                        """, MediaType.APPLICATION_JSON));

        JiraOAuthService oauth = mock(JiraOAuthService.class);
        when(oauth.currentAccessToken()).thenReturn(new ActiveToken("access-token-123", "cloud-99"));
        JiraIssueClient client = new JiraIssueClient(this::oauthSettingsSupplier, builder, oauth);

        ExternalRequirementInfo info = client.fetchOrThrow("ACME-7");

        assertThat(info.summary()).isEqualTo("S");
        assertThat(info.url()).isEqualTo(TENANT + "/browse/ACME-7"); // tenant URL, not the gateway
        server.verify();
    }

    @Test
    void aFailedRefreshSurfacesTheClearReauthorizationMessageInsteadOfANetworkError() {
        JiraOAuthService oauth = mock(JiraOAuthService.class);
        when(oauth.currentAccessToken())
                .thenThrow(new UpstreamServiceException("Jira authorization expired -- re-authorize the connection"));
        JiraIssueClient client = new JiraIssueClient(this::oauthSettingsSupplier, RestClient.builder(), oauth);

        assertThatThrownBy(() -> client.fetchOrThrow("ACME-7"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("re-authorize");
    }

    @Test
    void quietFetchStillReturnsEmptyWhenOAuthNeedsReauthorization() {
        JiraOAuthService oauth = mock(JiraOAuthService.class);
        when(oauth.currentAccessToken()).thenThrow(new UpstreamServiceException("Jira authorization is required"));
        JiraIssueClient client = new JiraIssueClient(this::oauthSettingsSupplier, RestClient.builder(), oauth);

        assertThat(client.fetchQuietly("ACME-7")).isEmpty();
    }

    @Test
    void aGatewayFourOhFourIsStillANotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.atlassian.com/ex/jira/cloud-1/rest/api/3/issue/NOPE"
                        + "?fields=summary,issuetype,status,description"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        JiraOAuthService oauth = mock(JiraOAuthService.class);
        when(oauth.currentAccessToken()).thenReturn(new ActiveToken("t", "cloud-1"));
        JiraIssueClient client = new JiraIssueClient(this::oauthSettingsSupplier, builder, oauth);

        assertThatThrownBy(() -> client.fetchOrThrow("NOPE"))
                .isInstanceOf(com.testryn.common.error.NotFoundException.class);
    }

    private JiraConnectionSettings oauthSettingsSupplier() {
        return oauthSettings();
    }
}
