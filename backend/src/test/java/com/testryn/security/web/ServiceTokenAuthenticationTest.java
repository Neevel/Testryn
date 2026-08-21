package com.testryn.security.web;

import com.testryn.security.domain.ServiceTokenScope;
import com.testryn.security.service.ServiceTokenService;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bearer-token authentication and scope enforcement end to end (Abschnitt 6, 33) --
 * against real protected endpoints, through the real {@code SecurityConfig} filter
 * chain. Every request here explicitly overrides the {@code Authorization} header
 * (see {@code AbstractIntegrationTest}'s merge semantics: an explicitly-set header on
 * a request always wins over the class-wide default), so "no header at all" is
 * simulated with an empty string value -- present as a header, but not a
 * {@code Bearer ...} value, which is exactly how a client that forgot the header
 * would behave from the filter's point of view.
 */
class ServiceTokenAuthenticationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceTokenService serviceTokenService;

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header("Authorization", ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer not-even-close-to-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTokenIsUnauthorized() throws Exception {
        String neverIssued = "testryn_" + "a".repeat(24) + "_" + "b".repeat(64);

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + neverIssued))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedTokenIsUnauthorized() throws Exception {
        var generated = serviceTokenService.create("Revoked", null, EnumSet.of(ServiceTokenScope.READ), null);
        serviceTokenService.revoke(generated.entity().getId());

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + generated.rawToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        var generated = serviceTokenService.create("Expires Soon", null, EnumSet.of(ServiceTokenScope.READ),
                Instant.now().plusMillis(50));
        Thread.sleep(100);

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + generated.rawToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readTokenCanReadButNotWrite() throws Exception {
        var generated = serviceTokenService.create("Reader", null, EnumSet.of(ServiceTokenScope.READ), null);
        String auth = "Bearer " + generated.rawToken();

        mockMvc.perform(get("/api/v1/projects").header("Authorization", auth))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects").header("Authorization", auth)
                        .contentType("application/json")
                        .content("""
                                {"key":"SHOULDFAIL","name":"Should not be created"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void writeTokenCanReadAndWrite() throws Exception {
        var generated = serviceTokenService.create("Writer", null, EnumSet.of(ServiceTokenScope.WRITE), null);
        String auth = "Bearer " + generated.rawToken();

        mockMvc.perform(get("/api/v1/projects").header("Authorization", auth))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects").header("Authorization", auth)
                        .contentType("application/json")
                        .content("""
                                {"key":"WRTOK1","name":"Created by a write token"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void writeTokenCannotManageServiceTokens() throws Exception {
        var generated = serviceTokenService.create("Writer Only", null, EnumSet.of(ServiceTokenScope.WRITE), null);
        String auth = "Bearer " + generated.rawToken();

        mockMvc.perform(get("/api/v1/service-tokens").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/service-tokens").header("Authorization", auth)
                        .contentType("application/json")
                        .content("""
                                {"name":"Should not be created","scopes":["testryn:read"]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenCanManageServiceTokens() throws Exception {
        var generated = serviceTokenService.create("Admin", null, EnumSet.of(ServiceTokenScope.ADMIN), null);
        String auth = "Bearer " + generated.rawToken();

        mockMvc.perform(get("/api/v1/service-tokens").header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/service-tokens").header("Authorization", auth)
                        .contentType("application/json")
                        .content("""
                                {"name":"Created by admin","scopes":["testryn:read"]}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void adminTokenCanAlsoReadAndWriteOrdinaryData() throws Exception {
        var generated = serviceTokenService.create("Admin Full Access", null, EnumSet.of(ServiceTokenScope.ADMIN), null);
        String auth = "Bearer " + generated.rawToken();

        mockMvc.perform(get("/api/v1/projects").header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects").header("Authorization", auth)
                        .contentType("application/json")
                        .content("""
                                {"key":"ADMOK1","name":"Created by an admin token"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void publicOpenApiDocsAreReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header("Authorization", ""))
                .andExpect(status().isOk());
    }
}
