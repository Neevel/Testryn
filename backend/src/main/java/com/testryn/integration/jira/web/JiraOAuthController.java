package com.testryn.integration.jira.web;

import com.testryn.integration.jira.oauth.JiraOAuthService;
import com.testryn.integration.jira.oauth.JiraOAuthService.CallbackOutcome;
import com.testryn.integration.jira.service.JiraConnectionSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import static com.testryn.integration.jira.web.JiraIntegrationDtos.*;

/**
 * Jira OAuth 2.0 (3LO) endpoints (ADR 0018).
 *
 * <ul>
 *   <li>{@code POST /api/v1/integrations/jira/oauth/authorize-url} and
 *       {@code .../disconnect} live under {@code /api/**} and require a
 *       {@code testryn:admin} service token (SecurityConfig) -- changing an
 *       instance-wide credential is admin-grade.</li>
 *   <li>{@code GET /integrations/jira/oauth/callback} is deliberately outside
 *       {@code /api/**} and permitted without a token: an Atlassian browser
 *       redirect cannot carry one. It is protected solely by the single-use,
 *       TTL-bounded {@code state} (see {@link JiraOAuthService}). It never accepts a
 *       client-supplied redirect and only renders a small static page.</li>
 * </ul>
 *
 * Controllers here only map/validate/delegate -- all logic is in
 * {@link JiraOAuthService}.
 */
@Tag(name = "Jira Integration", description = "OAuth 2.0 authorization for the configured Jira connection")
@RestController
public class JiraOAuthController {

    private final JiraOAuthService oauthService;
    private final JiraConnectionSettingsService settingsService;

    public JiraOAuthController(JiraOAuthService oauthService, JiraConnectionSettingsService settingsService) {
        this.oauthService = oauthService;
        this.settingsService = settingsService;
    }

    @Operation(summary = "Build the Atlassian authorization URL (admin only). The state is generated and stored server-side.")
    @PostMapping("/api/v1/integrations/jira/oauth/authorize-url")
    public JiraAuthorizationUrlResponse authorizeUrl() {
        return new JiraAuthorizationUrlResponse(oauthService.buildAuthorizationUrl());
    }

    @Operation(summary = "OAuth redirect target (public, protected by the single-use state). Renders a small status page.")
    @GetMapping(value = "/integrations/jira/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error) {
        CallbackOutcome outcome = oauthService.handleCallback(code, state, error);
        return switch (outcome) {
            case CONNECTED -> page("Jira authorization complete",
                    "Testryn is now connected to Jira via OAuth. You can close this tab and return to Settings.");
            case DENIED -> page("Authorization was declined",
                    "No access was granted. Nothing in Testryn changed. You can close this tab.");
            case INVALID_STATE -> page("This authorization link is no longer valid",
                    "Start the connection again from Testryn Settings. You can close this tab.");
            case SITE_MISMATCH -> page("Wrong Atlassian site",
                    "The authorized Atlassian account cannot access the Jira Cloud site configured in Testryn. "
                            + "Check the site URL in Settings, then try again.");
            case UPSTREAM_ERROR -> page("Could not complete authorization",
                    "Atlassian could not be reached or rejected the request. Nothing in Testryn changed. Try again later.");
        };
    }

    @Operation(summary = "Remove the stored OAuth credentials (admin only). Never deletes test data, requirement links or Jira issues.")
    @PostMapping("/api/v1/integrations/jira/oauth/disconnect")
    public JiraConnectionResponse disconnect() {
        oauthService.disconnect();
        return JiraConnectionResponse.from(settingsService.current(), oauthService.status());
    }

    private static String page(String heading, String body) {
        String safeHeading = escape(heading);
        String safeBody = escape(body);
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>body{font:16px/1.5 system-ui,sans-serif;margin:0;display:flex;min-height:100vh;
                align-items:center;justify-content:center;background:#f4f5f7;color:#172b4d}
                .card{background:#fff;max-width:32rem;padding:2rem;border-radius:8px;
                box-shadow:0 1px 3px rgba(9,30,66,.2)}h1{font-size:1.25rem;margin:0 0 .5rem}</style>
                </head><body><div class="card"><h1>%s</h1><p>%s</p></div></body></html>
                """.formatted(safeHeading, safeHeading, safeBody);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
