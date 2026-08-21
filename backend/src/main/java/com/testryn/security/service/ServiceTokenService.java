package com.testryn.security.service;

import com.testryn.common.error.BadRequestException;
import com.testryn.common.error.NotFoundException;
import com.testryn.security.domain.ServiceToken;
import com.testryn.security.domain.ServiceTokenScope;
import com.testryn.security.repository.ServiceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Generation, hashing and verification of {@link ServiceToken}s -- see ADR 0012 for
 * the full design rationale. Two concerns kept in one place deliberately: the token
 * *format* and its *verification* must stay in lockstep, and splitting them across
 * classes would risk them drifting apart.
 *
 * <h2>Token format</h2>
 * {@code testryn_<lookupId>_<secret>} -- {@code lookupId} (12 random bytes, hex) is
 * the non-secret public half used for an indexed O(1) database lookup (Abschnitt 19);
 * {@code secret} (32 random bytes, hex) is the actual credential, never stored in
 * plaintext, only as a SHA-256 hash.
 *
 * <h2>Hashing strategy</h2>
 * Plain SHA-256, not a slow password KDF (bcrypt/scrypt/Argon2). Those exist to slow
 * down brute-forcing a *low-entropy* human password against a stolen hash; a service
 * token's secret half is 256 bits of {@link SecureRandom} output, already
 * computationally infeasible to brute-force regardless of hash speed. Using a slow
 * KDF here would only add latency to every authenticated request for no real security
 * benefit. (A server-side HMAC pepper could be layered on top later for
 * defense-in-depth against a leaked database without a schema change -- not done here,
 * not necessary given the token's entropy.)
 *
 * <h2>Bootstrap tokens</h2>
 * An operator-chosen {@code TESTRYN_BOOTSTRAP_TOKEN} value does not follow the
 * generated {@code testryn_<lookupId>_<secret>} shape. Rather than special-casing
 * bootstrap tokens in the hot authentication path, {@link #authenticate} falls back
 * to a *deterministic* lookup key (a truncated hash of the whole raw value) for any
 * value that doesn't parse as the structured format -- still an indexed lookup, never
 * a table scan, and the bootstrap-created row is a completely ordinary, revocable
 * {@link ServiceToken}. See {@code ServiceTokenBootstrap}.
 */
@Service
@Transactional
public class ServiceTokenService {

    private static final String TOKEN_PREFIX = "testryn_";
    private static final int LOOKUP_ID_HEX_LENGTH = 24; // 12 bytes
    private static final int SECRET_BYTE_LENGTH = 32; // 256 bits
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServiceTokenRepository repository;

    public ServiceTokenService(ServiceTokenRepository repository) {
        this.repository = repository;
    }

    public record GeneratedToken(ServiceToken entity, String rawToken) {
    }

    /** Generates a new random token, stores only its hash, and returns the raw value
     * for one-time display -- the caller (the web layer) must never persist or log it. */
    public GeneratedToken create(String name, String description, Set<ServiceTokenScope> scopes, Instant expiresAt) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name must not be blank");
        }
        if (scopes == null || scopes.isEmpty()) {
            throw new BadRequestException("at least one scope is required");
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new BadRequestException("expiresAt must be in the future");
        }

        String lookupId = randomHex(12);
        String secret = randomHex(SECRET_BYTE_LENGTH);
        String rawToken = TOKEN_PREFIX + lookupId + "_" + secret;

        ServiceToken token = ServiceToken.create(name.trim(), description, lookupId, hash(secret), scopes, expiresAt);
        token = repository.save(token);
        return new GeneratedToken(token, rawToken);
    }

    /** Creates a token entry for an operator-supplied raw value (bootstrap only) --
     * see class javadoc. The returned entity is otherwise indistinguishable from one
     * created via {@link #create}: fully listable, fully revocable. */
    public ServiceToken createFromRawValue(String name, String description, String rawValue,
                                            Set<ServiceTokenScope> scopes) {
        ServiceToken token = ServiceToken.create(name, description, deterministicLookupId(rawValue), hash(rawValue),
                scopes, null);
        return repository.save(token);
    }

    @Transactional(readOnly = true)
    public List<ServiceToken> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public ServiceToken getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("ServiceToken", id));
    }

    public ServiceToken revoke(UUID id) {
        ServiceToken token = getById(id);
        token.revoke();
        return token;
    }

    @Transactional(readOnly = true)
    public boolean hasAnyToken() {
        return repository.count() > 0;
    }

    /**
     * Resolves and verifies a raw {@code Authorization: Bearer} value. Returns
     * {@link Optional#empty()} for every failure reason alike (malformed, unknown,
     * wrong secret, revoked, expired) -- callers must not distinguish these (no
     * "token exists but wrong secret" vs. "no such token" side channel) and must
     * never log the raw value itself.
     */
    public Optional<ServiceToken> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        ParsedToken parsed = parseStructured(rawToken);
        String lookupId = parsed != null ? parsed.lookupId() : deterministicLookupId(rawToken);
        String secretPortion = parsed != null ? parsed.secret() : rawToken;

        Optional<ServiceToken> found = repository.findByLookupId(lookupId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ServiceToken token = found.get();
        if (!constantTimeEquals(token.getTokenHash(), hash(secretPortion))) {
            return Optional.empty();
        }
        if (!token.isActive()) {
            return Optional.empty();
        }
        token.recordUsage();
        return Optional.of(token);
    }

    private record ParsedToken(String lookupId, String secret) {
    }

    private ParsedToken parseStructured(String rawToken) {
        if (!rawToken.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        String rest = rawToken.substring(TOKEN_PREFIX.length());
        if (rest.length() <= LOOKUP_ID_HEX_LENGTH + 1 || rest.charAt(LOOKUP_ID_HEX_LENGTH) != '_') {
            return null;
        }
        String lookupId = rest.substring(0, LOOKUP_ID_HEX_LENGTH);
        String secret = rest.substring(LOOKUP_ID_HEX_LENGTH + 1);
        return secret.isEmpty() ? null : new ParsedToken(lookupId, secret);
    }

    private String deterministicLookupId(String rawValue) {
        return hash(rawValue).substring(0, LOOKUP_ID_HEX_LENGTH);
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Hashes are fixed-length hex, but compare in constant time anyway -- cheap
     * insurance against a timing side-channel. */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
