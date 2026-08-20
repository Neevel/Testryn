package com.testryn.integration.jira.web;

import com.testryn.integration.jira.JiraIssueClient;
import com.testryn.integration.jira.JiraProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static com.testryn.integration.jira.web.JiraIntegrationDtos.*;

/**
 * Read-only status/test/lookup endpoints for the Jira connection. There is no
 * endpoint to write connection settings via the API in this MVP -- configuration is
 * environment-variable-based (see {@link JiraProperties}, ADR 0007); this controller
 * only ever exposes whether a token is configured, never its value.
 */
@Tag(name = "Jira Integration", description = "Status, connection test and issue lookup for the configured Jira connection")
@RestController
@RequestMapping("/api/v1/integrations/jira")
public class JiraIntegrationController {

    private final JiraProperties properties;
    private final JiraIssueClient client;

    public JiraIntegrationController(JiraProperties properties, JiraIssueClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Operation(summary = "Current Jira connection configuration (never includes the API token)")
    @GetMapping("/connection")
    public JiraConnectionResponse connection() {
        return JiraConnectionResponse.from(properties);
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
