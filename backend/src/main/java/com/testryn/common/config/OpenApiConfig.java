package com.testryn.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI testrynOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Testryn API")
                .version("v1")
                .description("""
                        API-first test management platform. Test Cases, Test Plans, Executions and
                        Results are owned by Testryn; Jira and other trackers are referenced only
                        through generic Requirement Links.
                        """)
                .contact(new Contact().name("Testryn")));
    }
}
