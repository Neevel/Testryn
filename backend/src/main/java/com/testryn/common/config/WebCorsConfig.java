package com.testryn.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Local-development CORS setup so the Vite dev server (a different origin) can call
 * the API directly. In a Docker Compose / production setup the frontend is typically
 * served behind the same reverse proxy, making this permissive by design for MVP
 * purposes only.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Value("${testryn.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
