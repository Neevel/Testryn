package com.testryn.integration.jira.oauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The single persisted OAuth token set for Testryn's one Jira connection (ADR 0018,
 * one row, {@code id = 'default'} -- same single-row pattern as
 * {@code JiraConnectionConfiguration}). Access and refresh tokens are stored only
 * as AES-256-GCM ciphertext ({@link com.testryn.integration.jira.oauth.SecretCipher});
 * this entity never holds or exposes plaintext.
 */
@Entity
@Table(name = "jira_oauth_token")
public class JiraOAuthToken {

    public static final String DEFAULT_ID = "default";

    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;

    @Column(name = "access_token_ciphertext", nullable = false, columnDefinition = "text")
    private String accessTokenCiphertext;

    @Column(name = "refresh_token_ciphertext", nullable = false, columnDefinition = "text")
    private String refreshTokenCiphertext;

    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;

    @Column(name = "cloud_id", nullable = false, length = 64)
    private String cloudId;

    @Column(name = "site_url", nullable = false, length = 500)
    private String siteUrl;

    @Column(name = "scopes", length = 500)
    private String scopes;

    @Column(name = "obtained_at", nullable = false)
    private Instant obtainedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JiraOAuthToken() {
        // for JPA
    }

    public static JiraOAuthToken create(String accessTokenCiphertext, String refreshTokenCiphertext,
                                        Instant accessTokenExpiresAt, String cloudId, String siteUrl, String scopes) {
        JiraOAuthToken token = new JiraOAuthToken();
        token.id = DEFAULT_ID;
        token.obtainedAt = Instant.now();
        token.apply(accessTokenCiphertext, refreshTokenCiphertext, accessTokenExpiresAt, cloudId, siteUrl, scopes);
        return token;
    }

    /** Replaces both ciphertexts -- used on initial exchange and on every refresh
     * (rotating refresh tokens: the old refresh token is overwritten, ADR 0018). */
    public void apply(String accessTokenCiphertext, String refreshTokenCiphertext,
                      Instant accessTokenExpiresAt, String cloudId, String siteUrl, String scopes) {
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.refreshTokenCiphertext = refreshTokenCiphertext;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.cloudId = cloudId;
        this.siteUrl = siteUrl;
        this.scopes = scopes;
        this.updatedAt = Instant.now();
    }

    /** True when the access token is still valid past {@code now + skew}. */
    public boolean accessTokenFresh(Instant now, java.time.Duration skew) {
        return now.plus(skew).isBefore(accessTokenExpiresAt);
    }

    public String getAccessTokenCiphertext() {
        return accessTokenCiphertext;
    }

    public String getRefreshTokenCiphertext() {
        return refreshTokenCiphertext;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public String getCloudId() {
        return cloudId;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public String getScopes() {
        return scopes;
    }

    public Instant getObtainedAt() {
        return obtainedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
