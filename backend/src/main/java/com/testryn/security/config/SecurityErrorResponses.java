package com.testryn.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testryn.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes the same {@link ApiError} shape the rest of the API uses (Abschnitt 21 --
 * "kein paralleles Error-System") for the two failures that happen inside Spring
 * Security's filter chain, before a request ever reaches a controller/
 * {@code @RestControllerAdvice}: missing/invalid authentication and insufficient
 * scope.
 */
final class SecurityErrorResponses {

    private SecurityErrorResponses() {
    }

    static void write(HttpServletRequest request, HttpServletResponse response, ObjectMapper objectMapper,
                       HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                null,
                code
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
