package com.testryn.security.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Machine-to-machine access scopes for {@link ServiceToken}s (ADR 0012). Deliberately
 * flat and small -- not a general-purpose permission system.
 *
 * <p>Implication rules (documented once, enforced consistently in
 * {@code com.testryn.security.config.SecurityConfig}):
 * <ul>
 *   <li>{@code WRITE} implies {@code READ} -- a token that may change data may also
 *       read it; a caller only ever needs to request {@code write} to get full CRUD.</li>
 *   <li>{@code ADMIN} implies {@code READ} and {@code WRITE}, and is additionally the
 *       only scope that may call the service-token management endpoints
 *       ({@code /api/v1/service-tokens/**}). A plain {@code write} token can update
 *       any test data but can never mint or revoke tokens -- see Abschnitt 23.</li>
 * </ul>
 *
 * <p>Wire format is {@code "testryn:read"}/{@code "testryn:write"}/
 * {@code "testryn:admin"} (Abschnitt 5/11/24), kept separate from the Java constant
 * names via {@link JsonValue}/{@link JsonCreator} -- the JPA column (bound via
 * {@code @Enumerated(EnumType.STRING)}, unaffected by these Jackson annotations)
 * stores the plain {@code name()} form (READ/WRITE/ADMIN).
 */
public enum ServiceTokenScope {
    READ,
    WRITE,
    ADMIN;

    private static final String PREFIX = "testryn:";

    @JsonValue
    public String toWireValue() {
        return PREFIX + name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ServiceTokenScope fromWireValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(PREFIX)) {
            normalized = normalized.substring(PREFIX.length());
        }
        for (ServiceTokenScope scope : values()) {
            if (scope.name().equalsIgnoreCase(normalized)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown scope: " + value);
    }
}
