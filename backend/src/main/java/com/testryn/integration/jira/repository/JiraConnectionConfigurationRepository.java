package com.testryn.integration.jira.repository;

import com.testryn.integration.jira.domain.JiraConnectionConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JiraConnectionConfigurationRepository
        extends JpaRepository<JiraConnectionConfiguration, String> {
}
