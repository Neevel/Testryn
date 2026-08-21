package com.testryn.security.service;

import com.testryn.common.error.BadRequestException;
import com.testryn.common.error.NotFoundException;
import com.testryn.security.domain.ServiceToken;
import com.testryn.security.domain.ServiceTokenScope;
import com.testryn.security.repository.ServiceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Domain-level tests for token generation, hashing, and verification (ADR 0012).
 * Uses a hand-rolled in-memory fake behind the mocked repository (keyed by lookupId,
 * mirroring the real unique index) rather than precise Mockito stubbing -- the whole
 * point under test is a stateful generate-then-look-up-then-verify round trip.
 */
@ExtendWith(MockitoExtension.class)
class ServiceTokenServiceTest {

    @Mock
    private ServiceTokenRepository repository;

    private final Map<String, ServiceToken> byLookupId = new HashMap<>();
    private ServiceTokenService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.save(any())).thenAnswer(inv -> {
            ServiceToken token = inv.getArgument(0);
            byLookupId.put(token.getLookupId(), token);
            return token;
        });
        lenient().when(repository.findByLookupId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(byLookupId.get(inv.getArgument(0, String.class))));
        service = new ServiceTokenService(repository);
    }

    @Test
    void generatedTokenHasTheDocumentedShape() {
        var generated = service.create("CI Pipeline", "desc", EnumSet.of(ServiceTokenScope.WRITE), null);

        assertThat(generated.rawToken()).startsWith("testryn_");
        String rest = generated.rawToken().substring("testryn_".length());
        String[] parts = rest.split("_", 2);
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).hasSize(24).matches("[0-9a-f]+"); // 12 bytes hex
        assertThat(parts[1]).hasSize(64).matches("[0-9a-f]+"); // 32 bytes hex
    }

    @Test
    void theStoredEntityNeverContainsTheRawTokenOrAPlaintextSecret() {
        var generated = service.create("CI Pipeline", "desc", EnumSet.of(ServiceTokenScope.WRITE), null);

        assertThat(generated.entity().getTokenHash()).isNotEqualTo(generated.rawToken());
        assertThat(generated.entity().getTokenHash()).doesNotContain(generated.rawToken());
        // The hash is deterministic (SHA-256 hex) and must not equal the raw secret half.
        String secretHalf = generated.rawToken().substring(generated.rawToken().lastIndexOf('_') + 1);
        assertThat(generated.entity().getTokenHash()).isNotEqualTo(secretHalf);
    }

    @Test
    void aFreshlyCreatedTokenAuthenticatesSuccessfully() {
        var generated = service.create("CI Pipeline", "desc", EnumSet.of(ServiceTokenScope.WRITE), null);

        Optional<ServiceToken> result = service.authenticate(generated.rawToken());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(generated.entity().getId());
    }

    @Test
    void authenticationUpdatesLastUsedAt() {
        var generated = service.create("CI Pipeline", "desc", EnumSet.of(ServiceTokenScope.WRITE), null);
        assertThat(generated.entity().getLastUsedAt()).isNull();

        service.authenticate(generated.rawToken());

        assertThat(generated.entity().getLastUsedAt()).isNotNull();
    }

    @Test
    void rejectsAMalformedToken() {
        assertThat(service.authenticate("not-a-real-token")).isEmpty();
        assertThat(service.authenticate("testryn_tooshort")).isEmpty();
        assertThat(service.authenticate("")).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(service.authenticate(null)).isEmpty();
    }

    @Test
    void rejectsAnUnknownLookupId() {
        service.create("CI Pipeline", "desc", EnumSet.of(ServiceTokenScope.WRITE), null);

        String fakeToken = "testryn_" + "0".repeat(24) + "_" + "1".repeat(64);

        assertThat(service.authenticate(fakeToken)).isEmpty();
    }

    @Test
    void rejectsACorrectLookupIdWithTheWrongSecret() {
        var generated = service.create("CI Pipeline", "desc", EnumSet.of(ServiceTokenScope.WRITE), null);
        String lookupId = generated.rawToken().split("_", 3)[1];
        String tamperedToken = "testryn_" + lookupId + "_" + "f".repeat(64);

        assertThat(service.authenticate(tamperedToken)).isEmpty();
    }

    @Test
    void rejectsARevokedToken() {
        var generated = service.create("CI Pipeline", "desc", EnumSet.of(ServiceTokenScope.WRITE), null);
        generated.entity().revoke();

        assertThat(service.authenticate(generated.rawToken())).isEmpty();
    }

    @Test
    void revokingIsIdempotent() {
        var generated = service.create("CI Pipeline", "desc", EnumSet.of(ServiceTokenScope.WRITE), null);
        assertThat(generated.entity().getRevokedAt()).isNull();

        generated.entity().revoke();
        Instant afterFirstRevoke = generated.entity().getRevokedAt();
        assertThat(afterFirstRevoke).isNotNull();

        generated.entity().revoke();

        assertThat(generated.entity().getRevokedAt()).isEqualTo(afterFirstRevoke);
    }

    @Test
    void rejectsAnExpiredToken() {
        var generated = service.create("CI Pipeline", "desc", EnumSet.of(ServiceTokenScope.WRITE),
                Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(service.authenticate(generated.rawToken())).isPresent();

        // Simulate expiry having already passed by creating a second token with a
        // past expiresAt directly against the entity factory (create() itself
        // rejects a past expiresAt as invalid input -- this exercises the *runtime*
        // expiry check, a different code path).
        ServiceToken expired = ServiceToken.create("Expired", null, "aaaaaaaaaaaaaaaaaaaaaaaa",
                hashOf("secretsecretsecretsecretsecretsecretsecretsecretsecretsecretse"),
                EnumSet.of(ServiceTokenScope.READ), Instant.now().minus(1, ChronoUnit.HOURS));
        byLookupId.put(expired.getLookupId(), expired);
        String expiredRawToken = "testryn_aaaaaaaaaaaaaaaaaaaaaaaa_secretsecretsecretsecretsecretsecretsecretsecretsecretsecretse";

        assertThat(service.authenticate(expiredRawToken)).isEmpty();
    }

    @Test
    void createRejectsABlankName() {
        assertThatThrownBy(() -> service.create("  ", null, EnumSet.of(ServiceTokenScope.READ), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createRejectsEmptyScopes() {
        assertThatThrownBy(() -> service.create("Name", null, Set.of(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createRejectsAPastExpiry() {
        assertThatThrownBy(() -> service.create("Name", null, EnumSet.of(ServiceTokenScope.READ),
                Instant.now().minus(1, ChronoUnit.HOURS)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownId() {
        when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void bootstrapFromRawValueAuthenticatesWithTheExactOperatorSuppliedString() {
        ServiceToken bootstrap = service.createFromRawValue("Bootstrap Admin Token", "desc",
                "operator-chosen-bootstrap-secret-value", EnumSet.of(ServiceTokenScope.ADMIN));

        Optional<ServiceToken> result = service.authenticate("operator-chosen-bootstrap-secret-value");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(bootstrap.getId());
        assertThat(result.get().getScopes()).containsExactly(ServiceTokenScope.ADMIN);
    }

    @Test
    void hasAnyTokenReflectsRepositoryCount() {
        when(repository.count()).thenReturn(0L, 1L);

        assertThat(service.hasAnyToken()).isFalse();
        assertThat(service.hasAnyToken()).isTrue();
    }

    // --- helper -----------------------------------------------------------------

    private String hashOf(String secret) {
        // Mirrors ServiceTokenService's private hashing so the hand-built ServiceToken
        // in rejectsAnExpiredToken() has a hash consistent with what authenticate()
        // would compute for the same secret.
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
