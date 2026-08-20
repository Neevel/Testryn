package com.testryn.publisher;

import java.io.IOException;
import java.util.Map;

/**
 * The seam between {@link TestrynApiClient}'s request/response handling and the
 * actual socket I/O -- lets {@code TestrynApiClientTest} exercise the client's logic
 * (URL building, header handling, status-code interpretation, ApiError parsing)
 * against a fake, with no real network call anywhere in the test suite (Abschnitt
 * 31: "keine echten externen Jira-Aufrufe im Publisher-Test" -- applies equally to
 * calls against Testryn itself).
 */
public interface HttpTransport {

    HttpTransportResponse send(String url, String method, String body, Map<String, String> headers)
            throws IOException;
}
