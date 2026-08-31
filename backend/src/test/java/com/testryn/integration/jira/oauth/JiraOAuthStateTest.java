package com.testryn.integration.jira.oauth;

import com.testryn.integration.jira.oauth.domain.JiraOAuthState;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JiraOAuthStateTest {

    private final SecureRandom random = new SecureRandom();

    @Test
    void generatesAHighEntropyRawStateAndStoresOnlyItsHash() {
        JiraOAuthState.Generated a = JiraOAuthState.generate(random, Duration.ofMinutes(10));
        JiraOAuthState.Generated b = JiraOAuthState.generate(random, Duration.ofMinutes(10));

        assertThat(a.rawState()).isNotBlank().hasSizeGreaterThanOrEqualTo(40);
        assertThat(a.rawState()).isNotEqualTo(b.rawState());
        // The persisted value is the SHA-256 hash, never the raw state.
        assertThat(a.entity().getStateHash()).isEqualTo(JiraOAuthState.sha256Hex(a.rawState()));
        assertThat(a.entity().getStateHash()).isNotEqualTo(a.rawState());
        assertThat(a.entity().getStateHash()).hasSize(64);
    }

    @Test
    void isExpiredOnceTheTtlHasPassed() {
        JiraOAuthState state = JiraOAuthState.generate(random, Duration.ofMinutes(10)).entity();

        assertThat(state.isExpired(Instant.now())).isFalse();
        assertThat(state.isExpired(Instant.now().plus(Duration.ofMinutes(11)))).isTrue();
    }

    @Test
    void hashingIsDeterministicForAGivenRawState() {
        assertThat(JiraOAuthState.sha256Hex("abc")).isEqualTo(JiraOAuthState.sha256Hex("abc"));
        assertThat(JiraOAuthState.sha256Hex("abc")).isNotEqualTo(JiraOAuthState.sha256Hex("abd"));
    }
}
