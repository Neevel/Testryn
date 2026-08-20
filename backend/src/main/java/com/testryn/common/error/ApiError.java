package com.testryn.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by the API. Deliberately does not leak stack traces or
 * internal exception details to clients.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {

    public record FieldViolation(String field, String message) {
    }
}
