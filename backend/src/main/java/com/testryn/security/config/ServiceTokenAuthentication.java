package com.testryn.security.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.UUID;

/**
 * The {@code Authentication} placed into the {@code SecurityContext} once a
 * {@link com.testryn.security.domain.ServiceToken} has been verified. Deliberately
 * carries only non-secret identifying data (id, name) -- never the token or its hash
 * -- so it is always safe to log or expose (e.g. for an audit trail later).
 */
public class ServiceTokenAuthentication extends AbstractAuthenticationToken {

    private final UUID tokenId;
    private final String tokenName;

    public ServiceTokenAuthentication(UUID tokenId, String tokenName,
                                       Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.tokenId = tokenId;
        this.tokenName = tokenName;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        // Never expose token material through the Authentication object.
        return null;
    }

    @Override
    public Object getPrincipal() {
        return tokenName;
    }

    public UUID getTokenId() {
        return tokenId;
    }

    public String getTokenName() {
        return tokenName;
    }
}
