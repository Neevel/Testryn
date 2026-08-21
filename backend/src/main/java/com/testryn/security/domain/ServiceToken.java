package com.testryn.security.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A machine-to-machine credential (ADR 0012) -- explicitly NOT a human user account.
 * The raw token value is never persisted: only {@link #lookupId} (a non-secret public
 * identifier used for O(1) lookup, Abschnitt 19) and {@link #tokenHash} (a SHA-256
 * hash of the token's high-entropy secret half) are stored. See
 * {@code ServiceTokenService} for the generation/hashing/verification logic.
 */
@Entity
@Table(name = "service_tokens")
public class ServiceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Non-secret public half of the token (e.g. {@code testryn_<lookupId>_<secret>})
     * -- indexed, used to find the row before hash comparison. Never treated as a
     * secret itself: revealing it does not help an attacker without the secret half. */
    @Column(name = "lookup_id", nullable = false, unique = true, length = 32)
    private String lookupId;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    // EAGER deliberately, unlike e.g. TestCase.tags: scopes are read on every single
    // authenticated request (ServiceTokenAuthenticationFilter, outside any web-layer
    // transaction) and the collection is tiny (at most 3 values) -- eager avoids
    // both a LazyInitializationException outside the service's transaction and any
    // need for a per-call @EntityGraph, at negligible cost.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_token_scopes", joinColumns = @JoinColumn(name = "service_token_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private Set<ServiceTokenScope> scopes = EnumSet.noneOf(ServiceTokenScope.class);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ServiceToken() {
        // for JPA
    }

    public static ServiceToken create(String name, String description, String lookupId, String tokenHash,
                                       Set<ServiceTokenScope> scopes, Instant expiresAt) {
        ServiceToken token = new ServiceToken();
        token.name = name;
        token.description = description;
        token.lookupId = lookupId;
        token.tokenHash = tokenHash;
        token.scopes = EnumSet.copyOf(scopes);
        token.createdAt = Instant.now();
        token.expiresAt = expiresAt;
        return token;
    }

    public void recordUsage() {
        this.lastUsedAt = Instant.now();
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /** Not revoked and not expired -- the only state in which the token may
     * authenticate a request. */
    public boolean isActive() {
        return !isRevoked() && !isExpired();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLookupId() {
        return lookupId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Set<ServiceTokenScope> getScopes() {
        return scopes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
