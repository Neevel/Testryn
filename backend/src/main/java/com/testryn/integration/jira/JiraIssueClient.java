package com.testryn.integration.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.testryn.common.error.NotFoundException;
import com.testryn.common.error.UpstreamServiceException;
import com.testryn.integration.jira.oauth.JiraOAuthProperties;
import com.testryn.integration.jira.oauth.JiraOAuthService;
import com.testryn.requirement.provider.ExternalRequirementInfo;
import com.testryn.integration.jira.service.JiraConnectionSettings;
import com.testryn.integration.jira.service.JiraConnectionSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Low-level HTTP access to the Jira Cloud REST API (v3). Shared by
 * {@link JiraRequirementProvider} (silent, best-effort enrichment -- ADR 0005) and
 * the explicit issue-preview/connection-test endpoints in
 * {@code integration.jira.web}, which need to distinguish "not configured", "not
 * reachable" and "issue not found" with proper HTTP semantics instead of collapsing
 * everything to an empty Optional.
 *
 * <p>Two auth modes (ADR 0018), selected by {@link JiraConnectionSettings#authType()}:
 * <ul>
 *   <li>{@code API_TOKEN} (default, unchanged): tenant URL + HTTP Basic
 *       {@code email:apiToken}.</li>
 *   <li>{@code OAUTH2}: the {@code api.atlassian.com/ex/jira/{cloudId}} gateway +
 *       {@code Bearer <access token>}, where the token comes from
 *       {@link JiraOAuthService#currentAccessToken()} (decrypted, auto-refreshed).</li>
 * </ul>
 *
 * <p>Never logs the API token, the OAuth token, or any Authorization header;
 * {@code basicAuthHeader} builds a header value only.</p>
 */
@Component
public class JiraIssueClient {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueClient.class);
    private static final String ISSUE_PATH = "/rest/api/3/issue/{key}?fields=summary,issuetype,status,description";

    private final Supplier<JiraConnectionSettings> settingsSupplier;
    private final RestClient.Builder restClientBuilder;
    private final JiraOAuthService oauthService;

    public JiraIssueClient(JiraProperties properties) {
        // SimpleClientHttpRequestFactory (java.net.HttpURLConnection-based) instead
        // of the default JDK HttpClient-backed factory: for the low, occasional
        // request volume of Jira lookups there is no benefit to the async/HTTP2
        // client, and HttpURLConnection connects lazily per-request instead of
        // eagerly opening an NIO Selector when the RestClient is built.
        this(properties, RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()));
    }

    /**
     * Accepts a {@link RestClient.Builder} (Spring Boot auto-configures one) rather
     * than building a bare {@code RestClient} internally, so tests can bind a
     * {@code MockRestServiceServer} to it -- exercising real request/response
     * parsing without opening a real socket. {@code @Autowired} disambiguates which
     * constructor Spring should use, since there are several public ones.
     */
    @Autowired
    public JiraIssueClient(JiraConnectionSettingsService settingsService, RestClient.Builder restClientBuilder,
                           JiraOAuthService oauthService) {
        this(settingsService::current, restClientBuilder, oauthService);
    }

    public JiraIssueClient(JiraProperties properties, RestClient.Builder restClientBuilder) {
        this(() -> JiraConnectionSettingsService.fromDefaults(properties), restClientBuilder, null);
    }

    public JiraIssueClient(Supplier<JiraConnectionSettings> settingsSupplier, RestClient.Builder restClientBuilder) {
        this(settingsSupplier, restClientBuilder, null);
    }

    JiraIssueClient(Supplier<JiraConnectionSettings> settingsSupplier,
                    RestClient.Builder restClientBuilder, JiraOAuthService oauthService) {
        this.settingsSupplier = settingsSupplier;
        this.restClientBuilder = restClientBuilder;
        this.oauthService = oauthService;
    }

    public boolean isUsable() {
        JiraConnectionSettings settings = settingsSupplier.get();
        if (settings.authType() == JiraAuthType.OAUTH2) {
            return settings.siteConfigured() && oauthService != null && oauthService.status().connected();
        }
        return settings.usable();
    }

    /** Best-effort lookup used for silent enrichment -- never throws. */
    public Optional<ExternalRequirementInfo> fetchQuietly(String externalKey) {
        try {
            return Optional.of(fetchOrThrow(externalKey));
        } catch (Exception ex) {
            log.warn("Jira lookup failed for issue {}: {}", externalKey, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Explicit lookup for the issue-preview endpoint. Throws
     * {@link NotFoundException} (Jira returned 404) or {@link UpstreamServiceException}
     * (not configured, unreachable, auth expired, or any other non-2xx response)
     * instead of quietly returning nothing.
     */
    public ExternalRequirementInfo fetchOrThrow(String externalKey) {
        JiraConnectionSettings settings = settingsSupplier.get();
        AuthContext auth = authContextOrThrow(settings);
        JsonNode issue;
        try {
            issue = auth.restClient().get()
                    .uri(ISSUE_PATH, externalKey)
                    .header(HttpHeaders.AUTHORIZATION, auth.authorizationHeader())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatusCode.valueOf(404)) {
                throw new NotFoundException("Jira issue not found: " + externalKey);
            }
            log.warn("Jira request for {} failed with status {}", externalKey, ex.getStatusCode().value());
            throw new UpstreamServiceException("Jira request failed (HTTP " + ex.getStatusCode().value() + ")");
        } catch (UpstreamServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Jira request for {} failed: {}", externalKey, ex.getMessage());
            throw new UpstreamServiceException("Jira is not reachable");
        }
        if (issue == null) {
            throw new NotFoundException("Jira issue not found: " + externalKey);
        }
        return toExternalRequirementInfo(externalKey, issue, settings);
    }

    /** Lightweight reachability/credentials check for the connection-test endpoint. */
    public JiraConnectionTestResult testConnection() {
        JiraConnectionSettings settings = settingsSupplier.get();
        if (!settings.siteConfigured()) {
            return JiraConnectionTestResult.failure("Jira Cloud site is not configured or active");
        }
        if (settings.authType() == JiraAuthType.OAUTH2) {
            if (oauthService == null) {
                return JiraConnectionTestResult.failure("Jira OAuth is not available");
            }
            try {
                JiraOAuthService.ActiveToken token = oauthService.currentAccessToken();
                return JiraConnectionTestResult.success(oauthService.assertConfiguredSiteVisible(token.accessToken()));
            } catch (UpstreamServiceException ex) {
                return JiraConnectionTestResult.failure(ex.getMessage());
            }
        }
        RestClient restClient = apiTokenClient(settings);
        if (restClient == null) {
            return testSiteReachability(settings);
        }
        try {
            JsonNode me = restClient.get()
                    .uri("/rest/api/3/myself")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(settings))
                    .retrieve()
                    .body(JsonNode.class);
            String displayName = me != null && me.has("displayName") ? me.get("displayName").asText() : null;
            return JiraConnectionTestResult.success(displayName != null ? "Connected to Jira as " + displayName
                    : "Connected to Jira");
        } catch (RestClientResponseException ex) {
            log.warn("Jira connection test failed with status {}", ex.getStatusCode().value());
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                return JiraConnectionTestResult.failure("Authentication rejected by Jira (check email/API token)");
            }
            return JiraConnectionTestResult.failure("Jira responded with HTTP " + ex.getStatusCode().value());
        } catch (Exception ex) {
            log.warn("Jira connection test failed: {}", ex.getMessage());
            return JiraConnectionTestResult.failure("Jira is not reachable");
        }
    }

    private JiraConnectionTestResult testSiteReachability(JiraConnectionSettings settings) {
        try {
            restClientBuilder.clone().baseUrl(settings.baseUrl()).build().get()
                    .uri("/rest/api/3/serverInfo")
                    .retrieve()
                    .toBodilessEntity();
            return JiraConnectionTestResult.siteReachable();
        } catch (RestClientResponseException ex) {
            // A 401/403 response still proves that the configured Atlassian site is
            // reachable; only direct authenticated enrichment remains unavailable.
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                return JiraConnectionTestResult.siteReachable();
            }
            log.warn("Jira site test failed with status {}", ex.getStatusCode().value());
            return JiraConnectionTestResult.failure("Jira responded with HTTP " + ex.getStatusCode().value());
        } catch (Exception ex) {
            log.warn("Jira site test failed: {}", ex.getMessage());
            return JiraConnectionTestResult.failure("Jira site is not reachable");
        }
    }

    /** The base URL + Authorization header to use for an authenticated Jira call,
     * resolved from the current auth mode. Throws {@link UpstreamServiceException}
     * when nothing usable is configured (or OAuth needs re-authorization -- the
     * message from {@link JiraOAuthService} is passed through). */
    private AuthContext authContextOrThrow(JiraConnectionSettings settings) {
        if (settings.authType() == JiraAuthType.OAUTH2) {
            if (oauthService == null) {
                throw new UpstreamServiceException("Jira OAuth is not available");
            }
            JiraOAuthService.ActiveToken token = oauthService.currentAccessToken();
            RestClient client = restClientBuilder.clone()
                    .baseUrl(JiraOAuthProperties.API_GATEWAY_BASE + "/ex/jira/" + token.cloudId())
                    .build();
            return new AuthContext(client, "Bearer " + token.accessToken());
        }
        RestClient client = apiTokenClient(settings);
        if (client == null) {
            throw new UpstreamServiceException("Jira is not configured or not active");
        }
        return new AuthContext(client, basicAuthHeader(settings));
    }

    private ExternalRequirementInfo toExternalRequirementInfo(String externalKey, JsonNode issue,
                                                               JiraConnectionSettings settings) {
        String id = issue.path("id").asText(null);
        JsonNode fields = issue.path("fields");
        String summary = fields.path("summary").isMissingNode() ? null : fields.path("summary").asText(null);
        String issueType = fields.path("issuetype").path("name").isMissingNode()
                ? null : fields.path("issuetype").path("name").asText(null);
        String status = fields.path("status").path("name").isMissingNode()
                ? null : fields.path("status").path("name").asText(null);
        String description = extractPlainText(fields.path("description"));
        // Human-facing browse link always uses the tenant URL, even in OAuth mode
        // (the api.atlassian.com gateway is for API calls only).
        String url = settings.baseUrl() + "/browse/" + externalKey;
        return new ExternalRequirementInfo(id, externalKey, url, summary, issueType, status, description);
    }

    /**
     * Jira Cloud returns {@code description} as Atlassian Document Format (ADF), a
     * rich-text JSON tree. A full ADF renderer is out of scope for this MVP (and
     * would still not depend on customer-specific custom-field IDs -- description is
     * always this same standard field); this walks {@code content} nodes collecting
     * {@code text} leaves into plain text, which is enough for a readable preview.
     */
    private String extractPlainText(JsonNode adfNode) {
        if (adfNode == null || adfNode.isMissingNode() || adfNode.isNull()) {
            return null;
        }
        String text = collectText(adfNode).trim();
        return text.isEmpty() ? null : text;
    }

    private String collectText(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.has("text") && node.get("text").isTextual()) {
            sb.append(node.get("text").asText());
        }
        if (node.has("content") && node.get("content").isArray()) {
            String joined = StreamSupport.stream(node.get("content").spliterator(), false)
                    .map(this::collectText)
                    .collect(Collectors.joining(" "));
            sb.append(joined);
        }
        if (node.has("type") && "paragraph".equals(node.get("type").asText())) {
            sb.append("\n");
        }
        return sb.toString();
    }

    private RestClient apiTokenClient(JiraConnectionSettings settings) {
        return settings.usable() ? restClientBuilder.clone().baseUrl(settings.baseUrl()).build() : null;
    }

    private String basicAuthHeader(JiraConnectionSettings settings) {
        String credentials = settings.email() + ":" + settings.apiToken();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private record AuthContext(RestClient restClient, String authorizationHeader) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraConnectionTestResult(boolean success, String message) {
        static JiraConnectionTestResult success(String jiraDisplayNameOrMessage) {
            return new JiraConnectionTestResult(true, jiraDisplayNameOrMessage != null
                    ? jiraDisplayNameOrMessage : "Connected to Jira");
        }

        static JiraConnectionTestResult siteReachable() {
            return new JiraConnectionTestResult(true,
                    "Jira Cloud site is reachable. Direct issue enrichment requires a server-side Jira API token.");
        }

        static JiraConnectionTestResult failure(String message) {
            return new JiraConnectionTestResult(false, message);
        }
    }
}
