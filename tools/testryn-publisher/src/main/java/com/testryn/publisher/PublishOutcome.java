package com.testryn.publisher;

import java.util.List;

/**
 * The result of one completed HTTP round-trip to the bulk-update endpoint (a real
 * network/transport failure is a thrown {@code IOException} from
 * {@link TestrynApiClient#publish}, not this type -- see its javadoc).
 *
 * @param success   true only on a 2xx response
 * @param httpStatus the raw HTTP status code
 * @param summary   one line per updated result on success (Abschnitt 23: "Duration /
 *                  Executor kontrollieren"), empty on failure
 * @param message   a short, human-readable top-level message (ApiError.message on
 *                  failure, a short confirmation on success)
 * @param violations one entry per field-level problem on a 400 (ApiError.fieldErrors,
 *                   Abschnitt 7), empty otherwise
 */
public record PublishOutcome(
        boolean success,
        int httpStatus,
        List<String> summary,
        String message,
        List<String> violations
) {
}
