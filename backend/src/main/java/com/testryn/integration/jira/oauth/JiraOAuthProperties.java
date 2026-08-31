package com.testryn.integration.jira.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Jira Cloud OAuth 2.0 (3LO) client configuration. Populated exclusively from
 * environment variables / external server configuration (ADR 0018) -- never
 * hard-coded, never committed, never returned by an API response or written to a
 * log. The status endpoint only ever exposes {@link #isConfigured()}.
 *
 * <p>{@code encryptionKey} is the AES-256 key (Base64 of exactly 32 bytes) used to
 * encrypt the persisted access/refresh tokens. It is deliberately grouped here with
 * the OAuth client credentials because it is only needed when OAuth is in use; the
 * API-token auth path never touches it, so the application starts and runs fine in
 * {@code API_TOKEN} mode without it.
 */
@ConfigurationProperties(prefix = "testryn.jira.oauth")
public class JiraOAuthProperties {

    /** Fixed Atlassian endpoints -- not configurable, not customer-specific. */
    public static final String AUTHORIZE_ENDPOINT = "https://auth.atlassian.com/authorize";
    public static final String TOKEN_ENDPOINT = "https://auth.atlassian.com/oauth/token";
    public static final String REVOKE_ENDPOINT = "https://auth.atlassian.com/oauth/revoke";
    public static final String ACCESSIBLE_RESOURCES_ENDPOINT =
            "https://api.atlassian.com/oauth/token/accessible-resources";
    public static final String API_GATEWAY_BASE = "https://api.atlassian.com";

    /** Minimal scope set (ADR 0018): issue read + refresh token. */
    public static final String SCOPES = "read:jira-work offline_access";

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String encryptionKey;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    /** Client id/secret/redirect present. Says nothing about the encryption key --
     * see {@link #isReady()}. */
    public boolean isConfigured() {
        return StringUtils.hasText(clientId)
                && StringUtils.hasText(clientSecret)
                && StringUtils.hasText(redirectUri);
    }

    /** Everything needed to actually run the flow, including token encryption. */
    public boolean isReady() {
        return isConfigured() && StringUtils.hasText(encryptionKey);
    }
}
