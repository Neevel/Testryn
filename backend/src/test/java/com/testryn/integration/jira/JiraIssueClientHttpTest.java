package com.testryn.integration.jira;

import com.testryn.common.error.NotFoundException;
import com.testryn.requirement.provider.ExternalRequirementInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises {@link JiraIssueClient} against {@link MockRestServiceServer} (mocks at
 * the {@code ClientHttpRequestFactory} level, no real socket involved) so "issue not
 * found" (Jira 404) and successful field/ADF-description parsing can be verified
 * deterministically without depending on a real Jira instance or network access.
 */
class JiraIssueClientHttpTest {

    private static final String BASE_URL = "https://jira.example.test";

    @Test
    void fetchOrThrowParsesSummaryTypeStatusAndAdfDescription() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        mockServer.expect(requestTo(BASE_URL + "/rest/api/3/issue/BIT-27?fields=summary,issuetype,status,description"))
                .andRespond(withSuccess("""
                        {
                          "id": "10042",
                          "key": "BIT-27",
                          "fields": {
                            "summary": "User can delete their account",
                            "issuetype": {"name": "Story"},
                            "status": {"name": "In Progress"},
                            "description": {
                              "type": "doc",
                              "content": [
                                {"type": "paragraph", "content": [{"type": "text", "text": "Given a logged-in user"}]},
                                {"type": "paragraph", "content": [{"type": "text", "text": "the account can be deleted."}]}
                              ]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        JiraIssueClient client = client(builder);
        ExternalRequirementInfo info = client.fetchOrThrow("BIT-27");

        assertThat(info.externalId()).isEqualTo("10042");
        assertThat(info.externalKey()).isEqualTo("BIT-27");
        assertThat(info.summary()).isEqualTo("User can delete their account");
        assertThat(info.issueType()).isEqualTo("Story");
        assertThat(info.status()).isEqualTo("In Progress");
        assertThat(info.description()).contains("Given a logged-in user").contains("the account can be deleted.");
        assertThat(info.url()).isEqualTo(BASE_URL + "/browse/BIT-27");
        mockServer.verify();
    }

    @Test
    void fetchOrThrowThrowsNotFoundExceptionForA404FromJira() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        mockServer.expect(requestTo(BASE_URL + "/rest/api/3/issue/BIT-999?fields=summary,issuetype,status,description"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("{\"errorMessages\":[\"Issue does not exist\"]}")
                        .contentType(MediaType.APPLICATION_JSON));

        JiraIssueClient client = client(builder);

        assertThatThrownBy(() -> client.fetchOrThrow("BIT-999")).isInstanceOf(NotFoundException.class);
        mockServer.verify();
    }

    @Test
    void fetchQuietlyReturnsEmptyForA404FromJiraInsteadOfPropagating() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        mockServer.expect(requestTo(BASE_URL + "/rest/api/3/issue/BIT-999?fields=summary,issuetype,status,description"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        JiraIssueClient client = client(builder);

        assertThat(client.fetchQuietly("BIT-999")).isEmpty();
    }

    @Test
    void connectionTestRecognizesReachableSiteWithoutDirectApiCredentials() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        mockServer.expect(requestTo(BASE_URL + "/rest/api/3/serverInfo"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl(BASE_URL);

        var result = new JiraIssueClient(properties, builder).testConnection();

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("site is reachable").contains("API token");
        mockServer.verify();
    }

    private JiraIssueClient client(RestClient.Builder builder) {
        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setEmail("bot@example.com");
        properties.setApiToken("token");
        return new JiraIssueClient(properties, builder);
    }
}
