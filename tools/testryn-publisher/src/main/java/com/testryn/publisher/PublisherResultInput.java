package com.testryn.publisher;

/**
 * One result entry as read from the publisher's input JSON (Abschnitt 14/29) --
 * mirrors the Testryn bulk-API entry shape (ADR 0010) almost exactly. Deliberately a
 * plain, framework-agnostic record: this is the seam a future input format (e.g. a
 * JUnit XML reader, Abschnitt 17) would also produce, without any JSON-specific
 * annotations of its own -- those live only in {@link JsonResultBatchReader}.
 *
 * <p>Unlike the Testryn API itself, the publisher does not distinguish "field
 * absent" from "field explicitly null": both become {@code null} here and are
 * omitted from the outgoing bulk request (see {@link TestrynApiClient}). Explicitly
 * clearing an already-set field is a rarer, more deliberate action better done
 * directly against the API or the UI, not through this reporting convenience tool --
 * a documented simplification, not an oversight.
 */
public record PublisherResultInput(
        String resultId,
        String automationReference,
        String status,
        String comment,
        Long durationMs,
        String executor,
        String actualResult,
        String failureDetails
) {
}
