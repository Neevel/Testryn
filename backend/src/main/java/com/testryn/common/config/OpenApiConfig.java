package com.testryn.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI testrynOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Testryn API")
                .version("v1")
                .description("""
                        API-first test management platform. Test Cases, Test Plans, Executions and
                        Results are owned by Testryn; Jira and other trackers are referenced only
                        through generic Requirement Links.

                        Machine-to-machine authentication: `Authorization: Bearer <service-token>`
                        (ADR 0012). GET/HEAD requires the `testryn:read` scope (or a higher one that
                        implies it); POST/PUT/PATCH/DELETE requires `testryn:write`;
                        `/api/v1/service-tokens/**` requires `testryn:admin` regardless of method.
                        Use the "Authorize" button below with a real token to try protected endpoints.
                        """)
                .contact(new Contact().name("Testryn")))
                // Documents the scheme (Abschnitt 28) and lets Swagger UI's "Authorize"
                // button attach the header for every subsequent try-it-out call -- it does
                // NOT mark every operation as requiring it (Spring Security enforces that
                // at runtime regardless of what OpenAPI declares).
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("testryn_<lookupId>_<secret>")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
