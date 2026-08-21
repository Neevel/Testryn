package com.testryn.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.testryn.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Abschnitt 20/35: the raw {@code Authorization} header value must never appear in a
 * log line, on ANY path -- success, malformed input, an unknown token, or a database
 * error. Captures every log event (root logger, so nothing can hide in an
 * unconsidered category) around several distinctive fake token values and asserts
 * none of them, or their exception messages, ever appear.
 */
class SecurityLoggingTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @BeforeEach
    void attachLogCapture() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachLogCapture() {
        rootLogger.detachAppender(appender);
    }

    @Test
    void authorizationHeaderValueNeverAppearsInAnyLogLine() throws Exception {
        String malformedMarker = "MARKER-MALFORMED-9f8e7d6c5b4a";
        String unknownStructuredMarker = "b".repeat(64) + "-MARKER-UNKNOWN-1a2b3c4d";
        String garbageMarker = "MARKER-GARBAGE-!!!not-hex-at-all!!!";

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + malformedMarker));
        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer testryn_" + "a".repeat(24) + "_" + unknownStructuredMarker));
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + garbageMarker));
        // A request with no bearer token at all -- still must not log an empty/garbled header attempt containing marker text.
        mockMvc.perform(get("/api/v1/projects").header("Authorization", ""));

        for (ILoggingEvent event : appender.list) {
            String formatted = event.getFormattedMessage();
            assertThat(formatted).doesNotContain(malformedMarker);
            assertThat(formatted).doesNotContain(unknownStructuredMarker);
            assertThat(formatted).doesNotContain(garbageMarker);
            if (event.getThrowableProxy() != null) {
                String throwableMessage = event.getThrowableProxy().getMessage();
                if (throwableMessage != null) {
                    assertThat(throwableMessage).doesNotContain(malformedMarker);
                    assertThat(throwableMessage).doesNotContain(unknownStructuredMarker);
                    assertThat(throwableMessage).doesNotContain(garbageMarker);
                }
            }
        }
    }
}
