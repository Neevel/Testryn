package com.testryn.integration.jira.web;

import com.testryn.integration.jira.JiraIssueClient;
import com.testryn.integration.jira.service.JiraConnectionSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static com.testryn.integration.jira.web.JiraIntegrationDtos.*;

/**
 * Status/configuration/test/lookup endpoints for the Jira connection. Non-secret
 * metadata can be changed at runtime; the token remains external configuration and
 * this controller only exposes whether one exists, never its value.
 */
@Tag(name = "Jira Integration", description = "Status, connection test and issue lookup for the configured Jira connection")
@RestController
@RequestMapping("/api/v1/integrations/jira")
public class JiraIntegrationController {

    private final JiraConnectionSettingsService settingsService;
    private final JiraIssueClient client;

    public JiraIntegrationController(JiraConnectionSettingsService settingsService, JiraIssueClient client) {
        this.settingsService = settingsService;
        this.client = client;
    }

    @Operation(summary = "Current Jira connection configuration (never includes the API token)")
    @GetMapping("/connection")
    public JiraConnectionResponse connection() {
        return JiraConnectionResponse.from(settingsService.current());
    }

    @Operation(summary = "Update non-secret Jira Cloud connection settings")
    @PutMapping("/connection")
    public JiraConnectionResponse updateConnection(@Valid @RequestBody UpdateJiraConnectionRequest request) {
        return JiraConnectionResponse.from(settingsService.update(
                request.name(), request.baseUrl(), request.email(), request.active()));
    }

    @Operation(summary = "Test the configured Jira connection by calling Jira as the configured identity")
    @PostMapping("/connection/test")
    public JiraConnectionTestResponse testConnection() {
        return JiraConnectionTestResponse.from(client.testConnection());
    }

    @Operation(
            summary = "Look up a Jira issue by key",
            description = """
                    Used to preview an issue before creating a RequirementLink (see
                    POST /test-cases/{id}/requirements). Returns 404 if Jira reports the issue does not
                    exist, 502 if Jira is not configured/reachable/otherwise fails.
                    """
    )
    @GetMapping("/issues/{key}")
    @ResponseStatus(HttpStatus.OK)
    public JiraIssuePreviewResponse getIssue(@PathVariable String key) {
        return JiraIssuePreviewResponse.from(client.fetchOrThrow(key));
    }
}
