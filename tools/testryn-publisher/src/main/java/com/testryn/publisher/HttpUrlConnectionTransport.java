package com.testryn.publisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@link HttpTransport} backed by plain {@code java.net.HttpURLConnection} -- the
 * classic, blocking-socket client, deliberately NOT {@code java.net.http.HttpClient}.
 *
 * <p><b>Why not the modern client:</b> {@code java.net.http.HttpClient} always needs
 * to open an NIO {@link java.nio.channels.Selector} at construction time, even for a
 * single synchronous {@code send()} call. Some sandboxed/restricted execution
 * environments cannot create loopback selectors and fail with
 * "Unable to establish loopback connection" the moment the client is built -- the
 * exact same constraint already documented in AGENTS.md #6a for the backend's own
 * outbound Jira client. {@code HttpURLConnection} uses classic blocking
 * {@code java.net.Socket} I/O and has no such requirement, so it works everywhere the
 * modern client might not, at the cost of one wrinkle below.
 *
 * <p><b>The PATCH problem:</b> {@code HttpURLConnection.setRequestMethod("PATCH")}
 * throws {@code ProtocolException: Invalid HTTP method: PATCH} -- a decades-old JDK
 * limitation (the method is validated against a hardcoded whitelist that predates
 * PATCH, RFC 5789). The standard, widely-used workaround is to set the protected
 * {@code method} field directly via reflection, bypassing the whitelist check. Since
 * JDK 9's module system, that requires the {@code java.net} package to be
 * reflectively "opened" -- this module's shaded jar bakes
 * {@code Add-Opens: java.base/java.net} into its manifest (see pom.xml) specifically
 * so {@code java -jar testryn-publisher.jar ...} works with no extra JVM flags. If
 * that ever fails (e.g. running the raw .class files instead of the shaded jar), the
 * error message below says exactly what flag to add.
 */
public class HttpUrlConnectionTransport implements HttpTransport {

    @Override
    public HttpTransportResponse send(String url, String method, String body, Map<String, String> headers)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        setMethod(connection, method);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        if (body != null) {
            connection.setDoOutput(true);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return new HttpTransportResponse(status, responseBody);
    }

    private void setMethod(HttpURLConnection connection, String method) throws IOException {
        try {
            connection.setRequestMethod(method);
            return;
        } catch (java.net.ProtocolException ignored) {
            // Not in HttpURLConnection's whitelist (PATCH, notably) -- fall through
            // to the reflection workaround documented above.
        }
        try {
            Field methodField = HttpURLConnection.class.getDeclaredField("method");
            methodField.setAccessible(true);
            methodField.set(connection, method);
        } catch (ReflectiveOperationException e) {
            throw new IOException(
                    "Could not set HTTP method '" + method + "' on HttpURLConnection. If you are not running the "
                            + "shaded testryn-publisher.jar (which bakes this in via its manifest), re-run with: "
                            + "java --add-opens java.base/java.net=ALL-UNNAMED -jar ...", e);
        }
    }
}
