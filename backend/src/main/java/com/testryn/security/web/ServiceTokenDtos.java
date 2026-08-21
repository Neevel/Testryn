package com.testryn.security.web;

import com.testryn.security.domain.ServiceToken;
import com.testryn.security.domain.ServiceTokenScope;
import com.testryn.security.service.ServiceTokenService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class ServiceTokenDtos {

    private ServiceTokenDtos() {
    }

    public record CreateServiceTokenRequest(
            @NotBlank String name,
            String description,
            @NotNull @NotEmpty Set<ServiceTokenScope> scopes,
            Instant expiresAt
    ) {
    }

    /** Returned ONLY from the create endpoint -- the one and only time the raw token
     * value is ever transmitted (Abschnitt 11/26). Never persisted, never logged,
     * never returned again by any other endpoint. */
    public record ServiceTokenCreatedResponse(
            UUID id,
            String name,
            String description,
            String token,
            Set<ServiceTokenScope> scopes,
            Instant createdAt,
            Instant expiresAt
    ) {
        public static ServiceTokenCreatedResponse from(ServiceTokenService.GeneratedToken generated) {
            ServiceToken entity = generated.entity();
            return new ServiceTokenCreatedResponse(
                    entity.getId(), entity.getName(), entity.getDescription(), generated.rawToken(),
                    entity.getScopes(), entity.getCreatedAt(), entity.getExpiresAt());
        }
    }

    /** Every other response shape -- deliberately has no field that could ever carry
     * the raw token or its hash. */
    public record ServiceTokenResponse(
            UUID id,
            String name,
            String description,
            Set<ServiceTokenScope> scopes,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            Instant revokedAt,
            boolean active
    ) {
        public static ServiceTokenResponse from(ServiceToken token) {
            return new ServiceTokenResponse(
                    token.getId(), token.getName(), token.getDescription(), token.getScopes(),
                    token.getCreatedAt(), token.getLastUsedAt(), token.getExpiresAt(), token.getRevokedAt(),
                    token.isActive());
        }
    }
}
