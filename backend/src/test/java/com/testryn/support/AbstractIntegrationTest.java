package com.testryn.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    }
}
