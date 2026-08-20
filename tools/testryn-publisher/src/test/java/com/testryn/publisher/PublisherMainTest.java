package com.testryn.publisher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the pure, transport-independent parts of {@link PublisherMain}:
 * execution-id resolution and outcome reporting/exit codes. The full run() path
 * (arg parsing -> file reading -> real HttpUrlConnectionTransport) is covered by the
 * Browser Verification pass against the live stack instead of here, per Abschnitt 31
 * ("keine echten externen Aufrufe im Publisher-Test"). */
class PublisherMainTest {

    @Test
    void resolvesExecutionIdFromCliWhenFileHasNone() {
        assertThat(PublisherMain.resolveExecutionId("exec-1", null)).isEqualTo("exec-1");
    }

    @Test
    void resolvesExecutionIdFromFileWhenCliHasNone() {
        assertThat(PublisherMain.resolveExecutionId(null, "exec-1")).isEqualTo("exec-1");
    }

    @Test
    void acceptsAgreeingCliAndFileExecutionIds() {
        assertThat(PublisherMain.resolveExecutionId("exec-1", "exec-1")).isEqualTo("exec-1");
    }

    @Test
    void rejectsDisagreeingCliAndFileExecutionIds() {
        assertThatThrownByResolving("exec-1", "exec-2");
    }

    @Test
    void rejectsNeitherCliNorFileExecutionId() {
        assertThatThrownByResolving(null, null);
    }

    @Test
    void reportsSuccessWithExitCodeZero() {
        var out = new java.io.ByteArrayOutputStream();
        var err = new java.io.ByteArrayOutputStream();
        PublishOutcome outcome = new PublishOutcome(true, 200,
                java.util.List.of("BIT-TC-1 -> PASSED"), "Bulk update succeeded", java.util.List.of());

        int exitCode = PublisherMain.report(outcome, 1, new java.io.PrintStream(out), new java.io.PrintStream(err));

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Bulk update succeeded").contains("BIT-TC-1 -> PASSED");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void reportsFailureWithExitCodeOneAndViolationsOnStderr() {
        var out = new java.io.ByteArrayOutputStream();
        var err = new java.io.ByteArrayOutputStream();
        PublishOutcome outcome = new PublishOutcome(false, 400, java.util.List.of(),
                "Bulk result update request is invalid", java.util.List.of("auth.login.valid: unknown reference"));

        int exitCode = PublisherMain.report(outcome, 1, new java.io.PrintStream(out), new java.io.PrintStream(err));

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("invalid").contains("auth.login.valid");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void usageErrorsExitWithCodeTwo() {
        var out = new java.io.ByteArrayOutputStream();
        var err = new java.io.ByteArrayOutputStream();

        int exitCode = PublisherMain.run(new String[] {"publish"},
                new java.io.PrintStream(out), new java.io.PrintStream(err));

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("--base-url");
    }

    @Test
    void unreadableResultsFileExitsWithCodeOne() {
        var out = new java.io.ByteArrayOutputStream();
        var err = new java.io.ByteArrayOutputStream();

        int exitCode = PublisherMain.run(
                new String[] {"publish", "--base-url", "http://localhost:8080",
                        "--results", "this-file-does-not-exist-anywhere.json"},
                new java.io.PrintStream(out), new java.io.PrintStream(err));

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Could not read results input");
    }

    private void assertThatThrownByResolving(String cli, String file) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PublisherMain.resolveExecutionId(cli, file))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
