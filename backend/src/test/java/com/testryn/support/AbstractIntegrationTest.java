package com.testryn.support;

import com.testryn.security.domain.ServiceTokenScope;
import com.testryn.security.service.ServiceTokenService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base class for tests that need a real, disposable PostgreSQL database (matching
 * ADR 0001 — Testryn's persistence is not meaningfully testable against an in-memory
 * substitute).
 *
 * <p>Deliberately uses the Testcontainers <em>singleton container</em> pattern
 * (started once in a static initializer, never stopped explicitly -- the Ryuk
 * resource reaper cleans it up at JVM exit) instead of {@code @Testcontainers}
 * + {@code @Container}: the container field is declared once here but the JVM
 * inherits it into every subclass, and JUnit's per-class {@code @Container}
 * lifecycle stops the container after the FIRST test class that uses it finishes --
 * breaking every subsequent integration test class in the same run with
 * "connection refused". The singleton pattern shares one container across all
 * integration test classes safely and, as a side effect, lets Spring's test context
 * cache reuse a single ApplicationContext across them too (identical, stable
 * datasource URL for the whole JVM run).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
// Spring Boot only auto-detects a nested @TestConfiguration on the CONCRETE test
// class actually being run, not one inherited from an abstract superclass -- an
// explicit @Import here is required for DefaultAuthConfiguration below to apply to
// every subclass (found the hard way: without this, every request in every
// subclass got a real, unauthenticated 401).
@Import(AbstractIntegrationTest.DefaultAuthConfiguration.class)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testryn")
            .withUsername("testryn")
            .withPassword("testryn");

    static final Path STORAGE_DIR;

    static {
        POSTGRES.start();
        try {
            STORAGE_DIR = Files.createTempDirectory("testryn-report-storage");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("testryn.storage.base-path", () -> STORAGE_DIR.toString());
        // Abschnitt 51: `mvn test` must be deterministic regardless of real
        // TESTRYN_JIRA_* variables a developer's shell happens to have set (used
        // elsewhere for live Jira verification) -- three RequirementWorkflowTest
        // assertions were observed to flip from "not configured" to "configured"
        // purely because of that ambient shell state. Dynamic test properties take
        // precedence over environment-variable-derived ones, so this forces every
        // Spring-context integration test onto a known, "not configured" Jira
        // connection regardless of the real environment. JiraRequirementProviderTest
        // and other tests that construct their own JiraProperties directly (no
        // Spring context) are unaffected either way.
        registry.add("testryn.jira.active", () -> "false");
        registry.add("testryn.jira.base-url", () -> "");
        registry.add("testryn.jira.email", () -> "");
        registry.add("testryn.jira.api-token", () -> "");
    }

    /**
     * Every existing test in this suite predates service-token authentication (ADR
     * 0012) and asserts specific business-logic status codes (400/404/409/...), not
     * auth status codes -- rewriting every {@code mockMvc.perform(...)} call across
     * the whole suite to attach a header would be a huge, purely mechanical, and
     * error-prone change. Instead, this {@link MockMvcBuilderCustomizer} attaches a
     * real, freshly-created ADMIN-scoped bearer token as a *default* request header:
     * every request in every test is authenticated (through the real security filter
     * chain, not a bypass) unless a test explicitly overrides the header itself --
     * which the dedicated authentication/authorization tests do, to exercise
     * missing/invalid/revoked/expired/wrong-scope tokens.
     */
    @TestConfiguration
    static class DefaultAuthConfiguration {

        @Bean
        MockMvcBuilderCustomizer defaultAuthorizationHeaderCustomizer(ServiceTokenService serviceTokenService) {
            return builder -> {
                var generated = serviceTokenService.create(
                        "Integration Test Suite Token", "Auto-created by AbstractIntegrationTest",
                        EnumSet.of(ServiceTokenScope.ADMIN), null);
                builder.defaultRequest(get("/").header("Authorization", "Bearer " + generated.rawToken()));
            };
        }
    }
}
