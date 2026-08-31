package com.testryn.integration.jira.oauth.repository;

import com.testryn.integration.jira.oauth.domain.JiraOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface JiraOAuthStateRepository extends JpaRepository<JiraOAuthState, String> {

    /**
     * Atomic single-use consume: deletes the row for {@code stateHash} only if it is
     * present AND not yet expired, returning the number of rows affected (1 = the
     * state was valid and is now spent, 0 = unknown / already used / expired). No
     * read-then-delete window, so a replay cannot slip between the check and the
     * delete.
     */
    @Modifying
    @Query("delete from JiraOAuthState s where s.stateHash = :stateHash and s.expiresAt > :now")
    int consume(@Param("stateHash") String stateHash, @Param("now") Instant now);

    @Modifying
    @Query("delete from JiraOAuthState s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
