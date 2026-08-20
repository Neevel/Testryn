package com.testryn.integration.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.testryn.common.error.NotFoundException;
import com.testryn.common.error.UpstreamServiceException;
import com.testryn.requirement.provider.ExternalRequirementInfo;
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
 * <p>Never logs the API token; {@link #basicAuthHeader()} builds the Authorization
 * header value only, which is never itself logged or echoed back.</p>
 */
@Component
public class JiraIssueClient {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueClient.class);

    private final JiraProperties properties;
    private final RestClient restClient;

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
     * constructor Spring should use, since there are two public ones.
     */
    @Autowired
    public JiraIssueClient(JiraProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = properties.isUsable()
                ? restClientBuilder.clone().baseUrl(properties.getBaseUrl()).build()
                : null;
    }

    public boolean isUsable() {
        return restClient != null;
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
     * (not configured, unreachable, or any other non-2xx response) instead of
     * quietly returning nothing.
     */
    public ExternalRequirementInfo fetchOrThrow(String externalKey) {
        if (restClient == null) {
            throw new UpstreamServiceException("Jira is not configured or not active");
        }
        JsonNode issue;
        try {
            issue = restClient.get()
                    .uri("/rest/api/3/issue/{key}?fields=summary,issuetype,status,description", externalKey)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatusCode.valueOf(404)) {
                throw new NotFoundException("Jira issue not found: " + externalKey);
            }
            log.warn("Jira request for {} failed with status {}", externalKey, ex.getStatusCode().value());
            throw new UpstreamServiceException("Jira request failed (HTTP " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            log.warn("Jira request for {} failed: {}", externalKey, ex.getMessage());
            throw new UpstreamServiceException("Jira is not reachable");
        }
        if (issue == null) {
            throw new NotFoundException("Jira issue not found: " + externalKey);
        }
        return toExternalRequirementInfo(externalKey, issue);
    }

    /** Lightweight reachability/credentials check for the connection-test endpoint. */
    public JiraConnectionTestResult testConnection() {
        if (restClient == null) {
            return JiraConnectionTestResult.failure("Jira connection is not configured or not active");
        }
        try {
            JsonNode me = restClient.get()
                    .uri("/rest/api/3/myself")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                    .retrieve()
                    .body(JsonNode.class);
            String displayName = me != null && me.has("displayName") ? me.get("displayName").asText() : null;
            return JiraConnectionTestResult.success(displayName);
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

    private ExternalRequirementInfo toExternalRequirementInfo(String externalKey, JsonNode issue) {
        String id = issue.path("id").asText(null);
        JsonNode fields = issue.path("fields");
        String summary = fields.path("summary").isMissingNode() ? null : fields.path("summary").asText(null);
        String issueType = fields.path("issuetype").path("name").isMissingNode()
                ? null : fields.path("issuetype").path("name").asText(null);
        String status = fields.path("status").path("name").isMissingNode()
                ? null : fields.path("status").path("name").asText(null);
        String description = extractPlainText(fields.path("description"));
        String url = properties.getBaseUrl() + "/browse/" + externalKey;
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

    private String basicAuthHeader() {
        String credentials = properties.getEmail() + ":" + properties.getApiToken();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraConnectionTestResult(boolean success, String message) {
        static JiraConnectionTestResult success(String jiraDisplayName) {
            String message = jiraDisplayName != null
                    ? "Connected to Jira as " + jiraDisplayName
                    : "Connected to Jira";
            return new JiraConnectionTestResult(true, message);
        }

        static JiraConnectionTestResult failure(String message) {
            return new JiraConnectionTestResult(false, message);
        }
    }
}
