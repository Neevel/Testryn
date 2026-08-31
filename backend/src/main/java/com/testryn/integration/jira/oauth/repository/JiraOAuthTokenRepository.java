package com.testryn.integration.jira.oauth.repository;

import com.testryn.integration.jira.oauth.domain.JiraOAuthToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface JiraOAuthTokenRepository extends JpaRepository<JiraOAuthToken, String> {

    /**
     * Pessimistic write lock on the single token row for the duration of a refresh
     * (ADR 0018 §7): serializes all refresh attempts within one Testryn instance, so
     * a second concurrent caller waits, re-reads, sees the freshly rotated access
     * token and skips its own refresh. Atlassian's 10-minute reuse leeway covers the
     * cross-instance case.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from JiraOAuthToken t where t.id = 'default'")
    Optional<JiraOAuthToken> findDefaultForUpdate();
}
