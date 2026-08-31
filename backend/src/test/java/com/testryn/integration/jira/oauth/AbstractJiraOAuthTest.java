package com.testryn.integration.jira.oauth;

import com.testryn.integration.jira.oauth.repository.JiraOAuthStateRepository;
import com.testryn.integration.jira.oauth.repository.JiraOAuthTokenRepository;
import com.testryn.integration.jira.repository.JiraConnectionConfigurationRepository;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for the Jira OAuth 2.0 tests: a configured OAuth client + a valid
 * (test-only) 32-byte encryption key via dynamic properties, a mocked
 * {@link JiraOAuthClient} (so no Atlassian HTTP is ever attempted), and a helper to
 * put a Jira Cloud site URL in place (needed for the accessible-resources match).
 *
 * <p>All OAuth test classes share this exact context configuration, so Spring's
 * test-context cache reuses one context across them.
 */
@MockBean(JiraOAuthClient.class)
public abstract class AbstractJiraOAuthTest extends AbstractIntegrationTest {

    protected static final String CLIENT_ID = "test-client-id";
    protected static final String CLIENT_SECRET = "test-client-secret-shhh";
    protected static final String REDIRECT_URI = "https://testryn.example/integrations/jira/oauth/callback";
    protected static final String ENCRYPTION_KEY_B64 = Base64.getEncoder().encodeToString(sequential32Bytes());
    protected static final String SITE_URL = "https://acme.atlassian.net";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JiraOAuthClient oauthClient; // the @MockBean
    @Autowired protected JiraOAuthService oauthService;
    @Autowired protected JiraOAuthStateRepository stateRepository;
    @Autowired protected JiraOAuthTokenRepository tokenRepository;
    @Autowired protected JiraConnectionConfigurationRepository connectionRepository;

    @DynamicPropertySource
    static void oauthProperties(DynamicPropertyRegistry registry) {
        registry.add("testryn.jira.oauth.client-id", () -> CLIENT_ID);
        registry.add("testryn.jira.oauth.client-secret", () -> CLIENT_SECRET);
        registry.add("testryn.jira.oauth.redirect-uri", () -> REDIRECT_URI);
        registry.add("testryn.jira.oauth.encryption-key", () -> ENCRYPTION_KEY_B64);
    }

    @BeforeEach
    @AfterEach
    void clearOAuthTables() {
        tokenRepository.deleteAll();
        stateRepository.deleteAll();
        connectionRepository.deleteAll();
    }

    protected void configureSite() throws Exception {
        mockMvc.perform(put("/api/v1/integrations/jira/connection")
                        .contentType("application/json")
                        .content("""
                                {"name":"Acme","baseUrl":"%s","email":"qa@acme.test","active":true,"authType":"OAUTH2"}
                                """.formatted(SITE_URL)))
                .andExpect(status().isOk());
    }

    private static byte[] sequential32Bytes() {
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (i + 1);
        }
        return b;
    }

    /** A cipher with the same key the app uses -- for asserting on stored ciphertext. */
    protected SecretCipher cipher() {
        return SecretCipher.fromBase64Key(ENCRYPTION_KEY_B64);
    }
}
