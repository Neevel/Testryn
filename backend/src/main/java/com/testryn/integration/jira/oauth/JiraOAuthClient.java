package com.testryn.integration.jira.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.testryn.common.error.UpstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Low-level HTTP access to the fixed Atlassian OAuth 2.0 (3LO) endpoints
 * ({@code auth.atlassian.com}, {@code api.atlassian.com}). Built with
 * {@link SimpleClientHttpRequestFactory}, never the JDK {@code HttpClient}
 * (AGENTS.md §6a), same as {@link com.testryn.integration.jira.JiraIssueClient}.
 *
 * <p>Never logs the client secret, an authorization code, or a token. On any
 * failure it distinguishes a permanent {@code 4xx} ("the grant is dead, re-auth")
 * from a transient {@code 5xx}/network error ("try again later") via
 * {@link OAuthHttpException#permanent()}.
 */
@Component
public class JiraOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(JiraOAuthClient.class);

    private final RestClient.Builder restClientBuilder;

    public JiraOAuthClient() {
        this(RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()));
    }

    @Autowired
    public JiraOAuthClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public TokenResponse exchangeAuthorizationCode(JiraOAuthProperties props, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("client_id", props.getClientId());
        body.put("client_secret", props.getClientSecret());
        body.put("code", code);
        body.put("redirect_uri", props.getRedirectUri());
        return postToken(body, "authorization code exchange");
    }

    public TokenResponse refresh(JiraOAuthProperties props, String refreshToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", "refresh_token");
        body.put("client_id", props.getClientId());
        body.put("client_secret", props.getClientSecret());
        body.put("refresh_token", refreshToken);
        return postToken(body, "token refresh");
    }

    /** Best-effort; a revoke failure must never block a local disconnect. */
    public void revoke(JiraOAuthProperties props, String refreshToken) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("token", refreshToken);
            body.put("client_id", props.getClientId());
            body.put("client_secret", props.getClientSecret());
            client().post().uri(JiraOAuthProperties.REVOKE_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Jira OAuth token revoke call failed (continuing local disconnect): {}", ex.getMessage());
        }
    }

    public List<AccessibleResource> accessibleResources(String accessToken) {
        JsonNode array;
        try {
            array = client().get().uri(JiraOAuthProperties.ACCESSIBLE_RESOURCES_ENDPOINT)
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.warn("accessible-resources request failed with status {}", ex.getStatusCode().value());
            throw new OAuthHttpException("Could not read the authorized Atlassian sites (HTTP "
                    + ex.getStatusCode().value() + ")", ex.getStatusCode().is4xxClientError());
        } catch (Exception ex) {
            log.warn("accessible-resources request failed: {}", ex.getMessage());
            throw new OAuthHttpException("Atlassian is not reachable", false);
        }
        List<AccessibleResource> resources = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                resources.add(new AccessibleResource(
                        node.path("id").asText(null),
                        node.path("name").asText(null),
                        node.path("url").asText(null)));
            }
        }
        return resources;
    }

    private TokenResponse postToken(Map<String, Object> body, String what) {
        try {
            JsonNode response = client().post().uri(JiraOAuthProperties.TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("access_token")) {
                throw new OAuthHttpException("Atlassian returned no access token on " + what, true);
            }
            return new TokenResponse(
                    response.get("access_token").asText(),
                    response.path("refresh_token").asText(null),
                    response.path("expires_in").asLong(3600L),
                    response.path("scope").asText(null));
        } catch (RestClientResponseException ex) {
            // 4xx here (e.g. invalid_grant) means the code/refresh token is dead --
            // permanent. Body is deliberately not logged (may echo request detail).
            log.warn("Jira OAuth {} failed with status {}", what, ex.getStatusCode().value());
            throw new OAuthHttpException("Jira OAuth " + what + " was rejected (HTTP "
                    + ex.getStatusCode().value() + ")", ex.getStatusCode().is4xxClientError());
        } catch (OAuthHttpException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Jira OAuth {} failed: {}", what, ex.getMessage());
            throw new OAuthHttpException("Atlassian is not reachable", false);
        }
    }

    private RestClient client() {
        return restClientBuilder.clone().build();
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, String scope) {
    }

    public record AccessibleResource(String id, String name, String url) {
    }

    /** Carries whether the failure is permanent (4xx -- re-authorization needed) or
     * transient (5xx/network -- retry later). Mapped to a user-facing message by
     * {@link JiraOAuthService}. */
    public static class OAuthHttpException extends UpstreamServiceException {
        private final boolean permanent;

        public OAuthHttpException(String message, boolean permanent) {
            super(message);
            this.permanent = permanent;
        }

        public boolean permanent() {
            return permanent;
        }
    }
}
