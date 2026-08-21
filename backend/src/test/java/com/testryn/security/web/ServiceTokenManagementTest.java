package com.testryn.security.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers {@code /api/v1/service-tokens} (Abschnitt 10-11, 23, 26-27). Requests here
 * ride on the ADMIN-scoped default token {@code AbstractIntegrationTest} attaches to
 * every request -- ADMIN-only enforcement itself is covered by
 * {@link ServiceTokenAuthenticationTest#nonAdminTokenCannotManageTokens()}.
 */
class ServiceTokenManagementTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createReturnsTheRawTokenExactlyOnce() throws Exception {
        JsonNode created = postJson("""
                {"name":"CI Pipeline","description":"Publisher token","scopes":["testryn:read","testryn:write"]}
                """, 201);

        assertThat(created.get("token").asText()).startsWith("testryn_");
        assertThat(created.get("name").asText()).isEqualTo("CI Pipeline");
        assertThat(created.get("scopes")).hasSize(2);
        assertThat(created.get("id")).isNotNull();
        assertThat(created.get("createdAt")).isNotNull();
    }

    @Test
    void listNeverIncludesTheRawTokenOrAnyTokenLikeField() throws Exception {
        postJson("""
                {"name":"For Listing","scopes":["testryn:read"]}
                """, 201);

        JsonNode list = getJson("/api/v1/service-tokens");

        assertThat(list.isArray()).isTrue();
        for (JsonNode entry : list) {
            assertThat(entry.has("token")).isFalse();
            assertThat(entry.has("tokenHash")).isFalse();
        }
    }

    @Test
    void getByIdNeverIncludesTheRawTokenEither() throws Exception {
        JsonNode created = postJson("""
                {"name":"For Get","scopes":["testryn:read"]}
                """, 201);
        String id = created.get("id").asText();

        JsonNode fetched = getJson("/api/v1/service-tokens/" + id);

        assertThat(fetched.has("token")).isFalse();
        assertThat(fetched.get("name").asText()).isEqualTo("For Get");
        assertThat(fetched.get("active").asBoolean()).isTrue();
        assertThat(fetched.get("revokedAt").isNull()).isTrue();
    }

    @Test
    void createRejectsBlankName() throws Exception {
        postJson("""
                {"name":"","scopes":["testryn:read"]}
                """, 400);
    }

    @Test
    void createRejectsEmptyScopes() throws Exception {
        postJson("""
                {"name":"No scopes","scopes":[]}
                """, 400);
    }

    @Test
    void revokeSetsRevokedAtAndDeactivatesTheToken() throws Exception {
        JsonNode created = postJson("""
                {"name":"To Revoke","scopes":["testryn:write"]}
                """, 201);
        String id = created.get("id").asText();

        JsonNode revoked = postJsonNoBody("/api/v1/service-tokens/" + id + "/revoke", 200);

        assertThat(revoked.get("revokedAt").isNull()).isFalse();
        assertThat(revoked.get("active").asBoolean()).isFalse();
    }

    @Test
    void revokeIsIdempotent() throws Exception {
        JsonNode created = postJson("""
                {"name":"Double Revoke","scopes":["testryn:write"]}
                """, 201);
        String id = created.get("id").asText();

        JsonNode first = postJsonNoBody("/api/v1/service-tokens/" + id + "/revoke", 200);
        JsonNode second = postJsonNoBody("/api/v1/service-tokens/" + id + "/revoke", 200);

        // Compare at millisecond precision: the first response reflects the
        // in-memory Instant.now() (nanosecond precision) from the same transaction
        // that set it, the second is re-read from Postgres's TIMESTAMP column
        // (microsecond precision) -- both represent the same revoke, just rounded
        // differently, not a second, later revocation.
        Instant firstRevokedAt = Instant.parse(first.get("revokedAt").asText()).truncatedTo(ChronoUnit.MILLIS);
        Instant secondRevokedAt = Instant.parse(second.get("revokedAt").asText()).truncatedTo(ChronoUnit.MILLIS);
        assertThat(secondRevokedAt).isEqualTo(firstRevokedAt);
    }

    @Test
    void aRevokedTokenCanNoLongerAuthenticate() throws Exception {
        JsonNode created = postJson("""
                {"name":"Live Then Revoked","scopes":["testryn:read"]}
                """, 201);
        String rawToken = created.get("token").asText();
        String id = created.get("id").asText();

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk());

        postJsonNoBody("/api/v1/service-tokens/" + id + "/revoke", 200);

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers -----------------------------------------------------------------

    private JsonNode postJson(String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post("/api/v1/service-tokens")
                        .contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? null : objectMapper.readTree(response);
    }

    private JsonNode postJsonNoBody(String path, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? null : objectMapper.readTree(response);
    }

    private JsonNode getJson(String path) throws Exception {
        String response = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }
}
