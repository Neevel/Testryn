package com.testryn.integration.jira.oauth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.testryn.integration.jira.oauth.JiraOAuthClient.AccessibleResource;
import com.testryn.integration.jira.oauth.JiraOAuthClient.OAuthHttpException;
import com.testryn.integration.jira.oauth.JiraOAuthClient.TokenResponse;
import com.testryn.integration.jira.oauth.domain.JiraOAuthToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ADR 0018 security guarantee: no OAuth secret (client secret, encryption key,
 * access token, refresh token) ever reaches a log line -- on the happy path or any
 * failure path.
 */
class JiraOAuthSecretLoggingTest extends AbstractJiraOAuthTest {

    private static final String ACCESS = "MARKER-ACCESS-a1b2c3d4e5";
    private static final String REFRESH = "MARKER-REFRESH-f6g7h8i9j0";

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @BeforeEach
    void attach() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        rootLogger.detachAppender(appender);
    }

    @Test
    void noOAuthSecretIsEverLoggedAcrossTheHappyAndFailurePaths() throws Exception {
        configureSite();

        // happy path: authorize + successful callback
        String state = extractState(oauthService.buildAuthorizationUrl());
        when(oauthClient.exchangeAuthorizationCode(any(), any()))
                .thenReturn(new TokenResponse(ACCESS, REFRESH, 3600, "read:jira-work offline_access"));
        when(oauthClient.accessibleResources(any()))
                .thenReturn(List.of(new AccessibleResource("cloud-1", "Acme", SITE_URL)));
        oauthService.handleCallback("code", state, null);

        // failure path: a rejected refresh
        tokenRepository.findById(JiraOAuthToken.DEFAULT_ID).ifPresent(row -> {
            row.apply(row.getAccessTokenCiphertext(), row.getRefreshTokenCiphertext(),
                    Instant.now().minusSeconds(10), row.getCloudId(), row.getSiteUrl(), row.getScopes());
            tokenRepository.save(row);
        });
        when(oauthClient.refresh(any(), any())).thenThrow(new OAuthHttpException("rejected (HTTP 400)", true));
        catchThrowable(() -> oauthService.currentAccessToken());

        // failure path: an invalid-state callback
        oauthService.handleCallback("code", "bogus-state", null);

        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage())
                    .doesNotContain(ACCESS).doesNotContain(REFRESH)
                    .doesNotContain(CLIENT_SECRET).doesNotContain(ENCRYPTION_KEY_B64);
            if (event.getThrowableProxy() != null && event.getThrowableProxy().getMessage() != null) {
                assertThat(event.getThrowableProxy().getMessage())
                        .doesNotContain(ACCESS).doesNotContain(REFRESH)
                        .doesNotContain(CLIENT_SECRET).doesNotContain(ENCRYPTION_KEY_B64);
            }
        }
    }

    private static String extractState(String url) {
        return java.util.Arrays.stream(java.net.URI.create(url).getRawQuery().split("&"))
                .map(p -> p.split("=", 2))
                .filter(p -> p[0].equals("state"))
                .map(p -> java.net.URLDecoder.decode(p[1], java.nio.charset.StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
    }
}
