package com.testryn.publisher;

/** The one piece of information {@link TestrynApiClient} needs back from a request:
 * status code and raw body. Deliberately minimal -- no headers, no streaming. */
public record HttpTransportResponse(int statusCode, String body) {
}
