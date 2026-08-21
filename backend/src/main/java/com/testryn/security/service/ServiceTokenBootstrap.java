package com.testryn.security.service;

import com.testryn.security.domain.ServiceTokenScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Solves the bootstrap problem (Abschnitt 12): once auth is enforced, something has
 * to create the very first token. Chosen approach ("Option A", the smallest of the
 * three sketched in the task): an operator-supplied {@code TESTRYN_BOOTSTRAP_TOKEN}
 * environment variable becomes an ordinary, fully-revocable ADMIN
 * {@link com.testryn.security.domain.ServiceToken} -- but only once, and only if no
 * service token exists yet.
 *
 * <p>Why this is not a standing backdoor: after the first token is created (by
 * anyone, via this mechanism or {@code POST /api/v1/service-tokens} using it), {@link
 * ServiceTokenService#hasAnyToken()} becomes {@code true} and this runner permanently
 * no-ops on every subsequent startup, even if the environment variable is still set.
 * There is no code path that re-checks or re-honors the variable afterwards, and the
 * created row is revocable exactly like any other token -- see ADR 0012. Operators
 * are expected to create real, named tokens and revoke the bootstrap one once done
 * (documented in docs/security.md), but nothing in the code enforces that; it is
 * intentionally not a "temporary" token in the technical sense, only in the
 * recommended-workflow sense.
 */
@Component
public class ServiceTokenBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenBootstrap.class);

    private final ServiceTokenService serviceTokenService;
    private final String bootstrapToken;

    public ServiceTokenBootstrap(ServiceTokenService serviceTokenService,
                                  @Value("${testryn.security.bootstrap-token:}") String bootstrapToken) {
        this.serviceTokenService = serviceTokenService;
        this.bootstrapToken = bootstrapToken;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapToken == null || bootstrapToken.isBlank()) {
            return;
        }
        if (serviceTokenService.hasAnyToken()) {
            log.info("TESTRYN_BOOTSTRAP_TOKEN is set but service tokens already exist -- ignoring it. "
                    + "Bootstrap only ever creates a token once, the first time the instance starts with none.");
            return;
        }
        serviceTokenService.createFromRawValue(
                "Bootstrap Admin Token",
                "Created automatically from TESTRYN_BOOTSTRAP_TOKEN at startup because no service tokens existed "
                        + "yet. Use it to create real, named tokens via POST /api/v1/service-tokens, then revoke "
                        + "this one via POST /api/v1/service-tokens/{id}/revoke.",
                bootstrapToken,
                EnumSet.of(ServiceTokenScope.ADMIN));
        // Never log the token value itself -- the operator already has it, they set it.
        log.warn("Created a bootstrap ADMIN service token from TESTRYN_BOOTSTRAP_TOKEN because no service tokens "
                + "existed yet. Use it once to create real tokens, then revoke it.");
    }
}
