package com.testryn.security.config;

import com.testryn.security.domain.ServiceToken;
import com.testryn.security.domain.ServiceTokenScope;
import com.testryn.security.service.ServiceTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves an {@code Authorization: Bearer <token>} header into a
 * {@link ServiceTokenAuthentication}, if valid (Abschnitt 19). Leaves the security
 * context untouched (not an error by itself) when the header is absent or the token
 * doesn't verify -- {@code SecurityConfig}'s {@code authorizeHttpRequests} rules are
 * what turn "no authentication" into a 401 for a protected path, keeping this filter
 * a pure resolver with no authorization opinions of its own.
 *
 * <p>Never logs the header value or any part of the token, at any log level, on any
 * path through this filter (Abschnitt 20/35).
 */
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final ServiceTokenService serviceTokenService;

    public ServiceTokenAuthenticationFilter(ServiceTokenService serviceTokenService) {
        this.serviceTokenService = serviceTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String rawToken = header.substring(BEARER_PREFIX.length()).trim();
            serviceTokenService.authenticate(rawToken).ifPresentOrElse(
                    token -> {
                        var authorities = grantedAuthorities(token.getScopes());
                        var authentication = new ServiceTokenAuthentication(token.getId(), token.getName(), authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    },
                    () -> log.debug("Bearer token on {} {} did not authenticate (invalid, unknown, revoked, or expired)",
                            request.getMethod(), request.getRequestURI())
            );
        }
        chain.doFilter(request, response);
    }

    /** Flattens scope implications (WRITE implies READ, ADMIN implies WRITE+READ --
     * see {@link ServiceTokenScope}) into concrete granted authorities once, at
     * authentication time, so {@code SecurityConfig}'s {@code authorizeHttpRequests}
     * rules stay simple {@code hasAuthority(...)} checks. */
    private Set<GrantedAuthority> grantedAuthorities(Set<ServiceTokenScope> scopes) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (scopes.contains(ServiceTokenScope.ADMIN)) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_ADMIN"));
            authorities.add(new SimpleGrantedAuthority("SCOPE_WRITE"));
            authorities.add(new SimpleGrantedAuthority("SCOPE_READ"));
        }
        if (scopes.contains(ServiceTokenScope.WRITE)) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_WRITE"));
            authorities.add(new SimpleGrantedAuthority("SCOPE_READ"));
        }
        if (scopes.contains(ServiceTokenScope.READ)) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_READ"));
        }
        return authorities;
    }
}
