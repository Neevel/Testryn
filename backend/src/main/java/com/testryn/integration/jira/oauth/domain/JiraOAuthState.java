package com.testryn.integration.jira.oauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * A pending OAuth authorization attempt (ADR 0018). The raw {@code state} value
 * (256 bits of {@link SecureRandom}) is returned to the caller inside the
 * authorization URL and NEVER stored -- only its SHA-256 hash is persisted, so a
 * leaked database row cannot be replayed against the callback. Single-use: the row
 * is deleted the moment it is consumed. TTL-bounded via {@link #expiresAt}.
 */
@Entity
@Table(name = "jira_oauth_state")
public class JiraOAuthState {

    @Id
    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected JiraOAuthState() {
        // for JPA
    }

    /** @return the [entity, rawState] pair -- the raw value is only available here,
     * at creation time, and must be embedded into the authorization URL by the caller. */
    public static Generated generate(SecureRandom random, java.time.Duration ttl) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String rawState = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        JiraOAuthState entity = new JiraOAuthState();
        entity.stateHash = sha256Hex(rawState);
        entity.createdAt = Instant.now();
        entity.expiresAt = entity.createdAt.plus(ttl);
        return new Generated(entity, rawState);
    }

    public static String sha256Hex(String rawState) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawState.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public String getStateHash() {
        return stateHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public record Generated(JiraOAuthState entity, String rawState) {
    }
}
