package com.testryn.integration.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted, non-secret Jira Cloud connection metadata. The API token deliberately
 * remains external configuration and is never written to PostgreSQL.
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

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JiraConnectionConfiguration() {
        // for JPA
    }

    public static JiraConnectionConfiguration create(String name, String baseUrl, String email, boolean active) {
        JiraConnectionConfiguration configuration = new JiraConnectionConfiguration();
        configuration.id = DEFAULT_ID;
        configuration.update(name, baseUrl, email, active);
        return configuration;
    }

    public void update(String name, String baseUrl, String email, boolean active) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.email = email;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public Instant getUpdatedAt() { return updatedAt; }
}
