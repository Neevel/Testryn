package com.testryn.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by the API. Deliberately does not leak stack traces or
 * internal exception details to clients.
 *
 * <p>{@code code} is an optional, stable machine-readable identifier (e.g.
 * {@code "UNAUTHORIZED"}, {@code "FORBIDDEN"}) for callers that want to branch on
 * error type without string-matching {@code message}. Additive and nullable
 * deliberately: most existing handlers still leave it {@code null} (they already
 * communicate the category via HTTP status + {@code error}) -- this is an extension
 * of the one existing error model, not a second one (Abschnitt 21).
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors,
        String code
) {

    public record FieldViolation(String field, String message) {
    }
}
