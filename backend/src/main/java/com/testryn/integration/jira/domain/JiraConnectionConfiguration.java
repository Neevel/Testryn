package com.testryn.integration.jira.domain;

import com.testryn.integration.jira.JiraAuthType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted, non-secret Jira Cloud connection metadata. The API token deliberately
 * remains external configuration and is never written to PostgreSQL. {@code
 * authType} selects between HTTP-Basic-with-API-token and OAuth 2.0 (ADR 0018); the
 * OAuth access/refresh tokens live encrypted in a separate table, never here.
 */
@Entity
@Table(name = "jira_connection_configuration")
public class JiraConnectionConfiguration {

    public static final String DEFAULT_ID = "default";

    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    private JiraAuthType authType = JiraAuthType.API_TOKEN;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JiraConnectionConfiguration() {
        // for JPA
    }

    public static JiraConnectionConfiguration create(String name, String baseUrl, String email, boolean active,
                                                     JiraAuthType authType) {
        JiraConnectionConfiguration configuration = new JiraConnectionConfiguration();
        configuration.id = DEFAULT_ID;
        configuration.update(name, baseUrl, email, active, authType);
        return configuration;
    }

    public void update(String name, String baseUrl, String email, boolean active, JiraAuthType authType) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.email = email;
        this.active = active;
        this.authType = authType == null ? JiraAuthType.API_TOKEN : authType;
        this.updatedAt = Instant.now();
    }

    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public JiraAuthType getAuthType() { return authType == null ? JiraAuthType.API_TOKEN : authType; }
    public Instant getUpdatedAt() { return updatedAt; }
}
