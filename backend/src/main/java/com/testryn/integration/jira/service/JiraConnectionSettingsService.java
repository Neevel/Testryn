package com.testryn.integration.jira.service;

import com.testryn.integration.jira.JiraProperties;
import com.testryn.integration.jira.domain.JiraConnectionConfiguration;
import com.testryn.integration.jira.repository.JiraConnectionConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;

@Service
public class JiraConnectionSettingsService {

    private final JiraProperties defaults;
    private final JiraConnectionConfigurationRepository repository;

    public JiraConnectionSettingsService(JiraProperties defaults,
                                         JiraConnectionConfigurationRepository repository) {
        this.defaults = defaults;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public JiraConnectionSettings current() {
        return repository.findById(JiraConnectionConfiguration.DEFAULT_ID)
                .map(configuration -> new JiraConnectionSettings(
                        configuration.getName(), configuration.getBaseUrl(), configuration.getEmail(),
                        defaults.getApiToken(), defaults.getAuthType(), configuration.isActive()))
                .orElseGet(() -> fromDefaults(defaults));
    }

    @Transactional
    public JiraConnectionSettings update(String name, String baseUrl, String email, boolean active) {
        String normalizedName = requireText(name, "Connection name");
        String normalizedBaseUrl = normalizeCloudUrl(baseUrl);
        String normalizedEmail = StringUtils.hasText(email) ? email.trim() : null;

        JiraConnectionConfiguration configuration = repository
                .findById(JiraConnectionConfiguration.DEFAULT_ID)
                .orElseGet(() -> JiraConnectionConfiguration.create(
                        normalizedName, normalizedBaseUrl, normalizedEmail, active));
        configuration.update(normalizedName, normalizedBaseUrl, normalizedEmail, active);
        repository.save(configuration);
        return new JiraConnectionSettings(normalizedName, normalizedBaseUrl, normalizedEmail,
                defaults.getApiToken(), defaults.getAuthType(), active);
    }

    public static JiraConnectionSettings fromDefaults(JiraProperties properties) {
        return new JiraConnectionSettings(properties.getName(), trimTrailingSlash(properties.getBaseUrl()),
                properties.getEmail(), properties.getApiToken(), properties.getAuthType(), properties.isActive());
    }

    private String normalizeCloudUrl(String value) {
        String candidate = requireText(value, "Jira Cloud URL");
        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost();
            boolean cloudHost = host != null && (host.equals("atlassian.net") || host.endsWith(".atlassian.net"));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !cloudHost || uri.getUserInfo() != null
                    || uri.getPort() != -1 || (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath()))
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Enter a Jira Cloud base URL such as https://company.atlassian.net");
            }
            return "https://" + host.toLowerCase();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Enter a Jira Cloud base URL such as https://company.atlassian.net");
        }
    }

    private static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) return value;
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
