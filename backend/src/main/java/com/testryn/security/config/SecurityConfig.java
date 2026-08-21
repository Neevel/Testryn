package com.testryn.security.config;

import com.testryn.security.service.ServiceTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless, service-token-only API security (ADR 0012). Deliberately does NOT
 * include: a login page, HTTP Basic, sessions, or CSRF protection -- none of those
 * apply to a bearer-token machine-to-machine API (Abschnitt 18). Human user
 * authentication is out of scope for this block entirely; see ADR 0012 for why the
 * two are kept architecturally separate.
 */
@Configuration
public class SecurityConfig {

    /** Public paths that never require a token (Abschnitt 7). OpenAPI/Swagger are
     * left open unconditionally rather than gated behind a dev/prod profile: this
     * codebase has no existing profile matrix to hook into, and building one just for
     * this would be exactly the "complicated environment matrix" Abschnitt 7 warns
     * against. Anyone who can reach the API network-wise can already read the same
     * information by calling the (protected) endpoints' 401 responses' `path`
     * anyway; the OpenAPI document itself contains no data, only shapes. */
    private static final String[] PUBLIC_PATHS = {
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ServiceTokenService serviceTokenService,
                                            ApiAuthenticationEntryPoint entryPoint,
                                            ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // Stateless bearer-token API: no cookies/sessions to forge, so CSRF
                // protection (which defends session-cookie-based auth) does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults()) // delegates to the existing WebMvcConfigurer CORS setup
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Token management needs its own high-trust scope (Abschnitt 23):
                        // matched before the general /api/** rules below so a plain
                        // write-scoped token can never mint or revoke tokens.
                        .requestMatchers("/api/v1/service-tokens/**").hasAuthority("SCOPE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAuthority("SCOPE_READ")
                        .requestMatchers(HttpMethod.HEAD, "/api/**").hasAuthority("SCOPE_READ")
                        .requestMatchers("/api/**").hasAuthority("SCOPE_WRITE")
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(new ServiceTokenAuthenticationFilter(serviceTokenService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
