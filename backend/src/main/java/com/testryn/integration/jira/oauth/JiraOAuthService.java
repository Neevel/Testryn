package com.testryn.integration.jira.oauth;

import com.testryn.common.error.BadRequestException;
import com.testryn.common.error.UpstreamServiceException;
import com.testryn.integration.jira.oauth.JiraOAuthClient.AccessibleResource;
import com.testryn.integration.jira.oauth.JiraOAuthClient.OAuthHttpException;
import com.testryn.integration.jira.oauth.JiraOAuthClient.TokenResponse;
import com.testryn.integration.jira.oauth.domain.JiraOAuthState;
import com.testryn.integration.jira.oauth.domain.JiraOAuthToken;
import com.testryn.integration.jira.oauth.repository.JiraOAuthStateRepository;
import com.testryn.integration.jira.oauth.repository.JiraOAuthTokenRepository;
import com.testryn.integration.jira.service.JiraConnectionSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Orchestrates the Jira Cloud OAuth 2.0 (3LO) flow for Testryn's one Jira
 * connection (ADR 0018): authorization URL generation with a single-use {@code
 * state}, callback handling (code exchange, accessible-resources site match,
 * encrypted token storage), transparent rotating-refresh with a pessimistic row
 * lock, disconnect, and status.
 *
 * <p>Secrets ({@code client_secret}, encryption key, access/refresh tokens) never
 * appear in a return value, a log line, or an exception message.
 */
@Service
public class JiraOAuthService {

    private static final Logger log = LoggerFactory.getLogger(JiraOAuthService.class);

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration REFRESH_SKEW = Duration.ofSeconds(60);

    private final JiraOAuthProperties properties;
    private final JiraOAuthClient oauthClient;
    private final JiraOAuthStateRepository stateRepository;
    private final JiraOAuthTokenRepository tokenRepository;
    private final JiraConnectionSettingsService settingsService;
    private final SecureRandom random = new SecureRandom();

    public JiraOAuthService(JiraOAuthProperties properties,
                            JiraOAuthClient oauthClient,
                            JiraOAuthStateRepository stateRepository,
                            JiraOAuthTokenRepository tokenRepository,
                            JiraConnectionSettingsService settingsService) {
        this.properties = properties;
        this.oauthClient = oauthClient;
        this.stateRepository = stateRepository;
        this.tokenRepository = tokenRepository;
        this.settingsService = settingsService;
    }

    // --- authorize ----------------------------------------------------------------

    @Transactional
    public String buildAuthorizationUrl() {
        if (!properties.isReady()) {
            throw new BadRequestException(
                    "Jira OAuth is not configured (client id/secret/redirect URI and encryption key are required)");
        }
        configuredSiteHostOrThrow(); // fail fast if no site is configured yet

        stateRepository.deleteExpired(Instant.now());
        JiraOAuthState.Generated generated = JiraOAuthState.generate(random, STATE_TTL);
        stateRepository.save(generated.entity());

        return UriComponentsBuilder.fromUriString(JiraOAuthProperties.AUTHORIZE_ENDPOINT)
                .queryParam("audience", "api.atlassian.com")
                .queryParam("client_id", properties.getClientId())
                .queryParam("scope", JiraOAuthProperties.SCOPES)
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("state", generated.rawState())
                .queryParam("response_type", "code")
                .queryParam("prompt", "consent")
                .encode()
                .build()
                .toUriString();
    }

    // --- callback ---------------------------------------------------------------

    public enum CallbackOutcome { CONNECTED, DENIED, INVALID_STATE, SITE_MISMATCH, UPSTREAM_ERROR }

    /**
     * Handles the browser redirect from Atlassian. Consumes {@code state} atomically
     * (single-use, TTL-bounded), exchanges {@code code}, resolves the cloud id for
     * the already-configured site, and stores the encrypted tokens. Never throws --
     * returns an {@link CallbackOutcome} the callback endpoint renders as a static
     * page.
     */
    @Transactional
    public CallbackOutcome handleCallback(String code, String state, String error) {
        if (StringUtils.hasText(error)) {
            return CallbackOutcome.DENIED;
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            return CallbackOutcome.INVALID_STATE;
        }
        int consumed = stateRepository.consume(JiraOAuthState.sha256Hex(state), Instant.now());
        if (consumed != 1) {
            return CallbackOutcome.INVALID_STATE;
        }
        if (!properties.isReady()) {
            return CallbackOutcome.UPSTREAM_ERROR;
        }

        String configuredHost;
        try {
            configuredHost = configuredSiteHostOrThrow();
        } catch (RuntimeException ex) {
            return CallbackOutcome.SITE_MISMATCH;
        }

        try {
            TokenResponse tokens = oauthClient.exchangeAuthorizationCode(properties, code);
            if (!StringUtils.hasText(tokens.refreshToken())) {
                // offline_access missing from the granted scopes -- we cannot keep
                // the connection alive without a refresh token.
                return CallbackOutcome.UPSTREAM_ERROR;
            }
            List<AccessibleResource> resources = oauthClient.accessibleResources(tokens.accessToken());
            Optional<AccessibleResource> match = resources.stream()
                    .filter(r -> configuredHost.equalsIgnoreCase(hostOf(r.url())))
                    .findFirst();
            if (match.isEmpty()) {
                return CallbackOutcome.SITE_MISMATCH;
            }

            SecretCipher cipher = SecretCipher.fromBase64Key(properties.getEncryptionKey());
            Instant expiresAt = Instant.now().plusSeconds(tokens.expiresInSeconds());
            String siteUrl = normalizeHttps(match.get().url());
            JiraOAuthToken row = tokenRepository.findById(JiraOAuthToken.DEFAULT_ID)
                    .orElseGet(() -> JiraOAuthToken.create(
                            cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()),
                            expiresAt, match.get().id(), siteUrl, tokens.scope()));
            row.apply(cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()),
                    expiresAt, match.get().id(), siteUrl, tokens.scope());
            tokenRepository.save(row);
            log.info("Jira OAuth connection established for site {}", siteUrl);
            return CallbackOutcome.CONNECTED;
        } catch (OAuthHttpException ex) {
            log.warn("Jira OAuth callback failed: {}", ex.getMessage());
            return CallbackOutcome.UPSTREAM_ERROR;
        } catch (RuntimeException ex) {
            log.warn("Jira OAuth callback failed unexpectedly: {}", ex.getClass().getSimpleName());
            return CallbackOutcome.UPSTREAM_ERROR;
        }
    }

    // --- access token for outbound calls --------------------------------------

    public record ActiveToken(String accessToken, String cloudId) {
    }

    /**
     * Returns a currently-valid access token (refreshing transparently, ADR 0018 §7)
     * plus the cloud id for building {@code api.atlassian.com/ex/jira/{cloudId}}
     * URLs. The token row is locked {@code FOR UPDATE} for the whole method, so
     * concurrent callers serialize and the second one reuses the rotated token.
     *
     * <p>{@code REQUIRES_NEW}: this often runs inside a caller's transaction (e.g.
     * best-effort enrichment during requirement-link creation). Its own failure or a
     * token rotation must not roll back, or poison, that outer transaction.
     * {@code noRollbackFor}: on a permanently-dead refresh token the row is deleted
     * AND an exception is thrown -- the delete must still commit.
     *
     * @throws UpstreamServiceException if not connected, or if the refresh failed
     *         (message distinguishes "re-authorize" from "not reachable")
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = UpstreamServiceException.class)
    public ActiveToken currentAccessToken() {
        if (!properties.isReady()) {
            throw new UpstreamServiceException("Jira OAuth is not configured");
        }
        JiraOAuthToken row = tokenRepository.findDefaultForUpdate()
                .orElseThrow(() -> new UpstreamServiceException("Jira OAuth authorization is required"));
        SecretCipher cipher = SecretCipher.fromBase64Key(properties.getEncryptionKey());

        if (row.accessTokenFresh(Instant.now(), REFRESH_SKEW)) {
            return new ActiveToken(cipher.decrypt(row.getAccessTokenCiphertext()), row.getCloudId());
        }

        String refreshToken = cipher.decrypt(row.getRefreshTokenCiphertext());
        try {
            TokenResponse refreshed = oauthClient.refresh(properties, refreshToken);
            String newRefresh = StringUtils.hasText(refreshed.refreshToken())
                    ? refreshed.refreshToken() : refreshToken; // rotation is expected, but never lose the token
            Instant expiresAt = Instant.now().plusSeconds(refreshed.expiresInSeconds());
            row.apply(cipher.encrypt(refreshed.accessToken()), cipher.encrypt(newRefresh),
                    expiresAt, row.getCloudId(), row.getSiteUrl(),
                    refreshed.scope() != null ? refreshed.scope() : row.getScopes());
            tokenRepository.save(row);
            return new ActiveToken(refreshed.accessToken(), row.getCloudId());
        } catch (OAuthHttpException ex) {
            if (ex.permanent()) {
                // invalid_grant etc. -- the refresh token is dead, drop it so the
                // status flips to "authorization required" rather than looping.
                tokenRepository.delete(row);
                throw new UpstreamServiceException("Jira authorization expired -- re-authorize the connection");
            }
            throw new UpstreamServiceException("Jira is not reachable");
        }
    }

    // --- disconnect -----------------------------------------------------------

    @Transactional
    public void disconnect() {
        Optional<JiraOAuthToken> row = tokenRepository.findById(JiraOAuthToken.DEFAULT_ID);
        if (row.isEmpty()) {
            return;
        }
        if (properties.isReady()) {
            try {
                SecretCipher cipher = SecretCipher.fromBase64Key(properties.getEncryptionKey());
                oauthClient.revoke(properties, cipher.decrypt(row.get().getRefreshTokenCiphertext()));
            } catch (RuntimeException ex) {
                log.warn("Jira OAuth revoke skipped ({}), removing local credentials anyway", ex.getMessage());
            }
        }
        tokenRepository.delete(row.get());
        log.info("Jira OAuth connection disconnected (local credentials removed; no test data touched)");
    }

    // --- status / test ------------------------------------------------------------

    public record OAuthStatus(boolean configured, boolean connected, String siteUrl, boolean reauthorizationRequired) {
    }

    @Transactional(readOnly = true)
    public OAuthStatus status() {
        boolean configured = properties.isReady();
        Optional<JiraOAuthToken> row = tokenRepository.findById(JiraOAuthToken.DEFAULT_ID);
        boolean selected = settingsService.current().authType() == com.testryn.integration.jira.JiraAuthType.OAUTH2;
        return new OAuthStatus(
                configured,
                row.isPresent(),
                row.map(JiraOAuthToken::getSiteUrl).orElse(null),
                selected && row.isEmpty());
    }

    /** OAUTH2-mode connection test helper: given a valid access token (obtained by
     * the caller via the proxied {@link #currentAccessToken()} -- no self-invocation
     * here), confirm the configured site is still visible to the authorized account.
     * Needs no Jira scope. Not transactional -- it only makes an outbound call. */
    public String assertConfiguredSiteVisible(String accessToken) {
        String configuredHost = configuredSiteHostOrThrow();
        List<AccessibleResource> resources = oauthClient.accessibleResources(accessToken);
        boolean stillVisible = resources.stream().anyMatch(r -> configuredHost.equalsIgnoreCase(hostOf(r.url())));
        if (!stillVisible) {
            throw new UpstreamServiceException(
                    "The authorized Atlassian account can no longer access " + settingsService.current().baseUrl());
        }
        return "Connected to Jira via OAuth for " + settingsService.current().baseUrl();
    }

    // --- helpers ----------------------------------------------------------------

    private String configuredSiteHostOrThrow() {
        String baseUrl = settingsService.current().baseUrl();
        String host = hostOf(baseUrl);
        if (host == null) {
            throw new BadRequestException("Configure the Jira Cloud site URL before connecting via OAuth");
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private static String hostOf(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url.trim()).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String normalizeHttps(String url) {
        String host = hostOf(url);
        return host == null ? url : "https://" + host.toLowerCase(Locale.ROOT);
    }
}
