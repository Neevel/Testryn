package com.testryn.security.web;

import com.testryn.security.domain.ServiceToken;
import com.testryn.security.service.ServiceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.testryn.security.web.ServiceTokenDtos.*;

/**
 * Token management API (Abschnitt 10). Every endpoint here requires the
 * {@code testryn:admin} scope regardless of HTTP method -- enforced declaratively in
 * {@code SecurityConfig}, not in this controller (Abschnitt 23): a plain
 * {@code testryn:write} token must never be able to mint or revoke tokens for itself
 * or others.
 */
@RestController
@RequestMapping("/api/v1/service-tokens")
public class ServiceTokenController {

    private final ServiceTokenService serviceTokenService;

    public ServiceTokenController(ServiceTokenService serviceTokenService) {
        this.serviceTokenService = serviceTokenService;
    }

    @Operation(
            summary = "Create a service token (requires testryn:admin)",
            description = """
                    Returns the raw token value exactly once, in this response only -- it is never shown or
                    returned again by any other endpoint. Store it now; if it is lost, revoke this token and
                    create a new one. Only the SHA-256 hash of its secret half is persisted (ADR 0012).
                    """
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ServiceTokenCreatedResponse> create(@Valid @RequestBody CreateServiceTokenRequest request) {
        var generated = serviceTokenService.create(request.name(), request.description(), request.scopes(),
                request.expiresAt());
        return ResponseEntity.created(URI.create("/api/v1/service-tokens/" + generated.entity().getId()))
                .body(ServiceTokenCreatedResponse.from(generated));
    }

    @Operation(summary = "List service tokens (requires testryn:admin)",
            description = "Never includes the raw token value or its hash -- see ServiceTokenResponse.")
    @GetMapping
    public List<ServiceTokenResponse> list() {
        return serviceTokenService.findAll().stream().map(ServiceTokenResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ServiceTokenResponse getById(@PathVariable UUID id) {
        return ServiceTokenResponse.from(serviceTokenService.getById(id));
    }

    @Operation(
            summary = "Revoke a service token (requires testryn:admin)",
            description = """
                    Idempotent: revoking an already-revoked token just returns its current (already-revoked)
                    state rather than erroring. The row is never deleted -- audit metadata (name, scopes,
                    createdAt, lastUsedAt, revokedAt) is preserved (Abschnitt 17).
                    """
    )
    @PostMapping("/{id}/revoke")
    public ServiceTokenResponse revoke(@PathVariable UUID id) {
        ServiceToken token = serviceTokenService.revoke(id);
        return ServiceTokenResponse.from(token);
    }
}
