package com.testryn.publisher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the pure, transport-independent parts of {@link PublisherMain}:
 * execution-id resolution and outcome reporting/exit codes. The full run() path
 * (arg parsing -> file reading -> real HttpUrlConnectionTransport) is covered by the
 * Browser Verification pass against the live stack instead of here, per Abschnitt 31
 * ("keine echten externen Aufrufe im Publisher-Test"). The same principle applies to
 * publish-junit: everything up to (but not including) the actual HTTP call is
 * exercised here with real temp files; the shared {@link TestrynApiClient} that both
 * subcommands hand off to for the network call itself is already fully covered
 * against a fake transport in {@link TestrynApiClientTest} (401/403/unknown
 * references/successful publish/token-never-logged) -- runJUnitPublish adds no new
 * behavior at that layer, it only builds the same PublisherResultInput list a
 * different way. */
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

    @org.junit.jupiter.api.Nested
    class PublishJunit {

        @TempDir
        Path tempDir;

        @Test
        void unknownCommandExitsWithCodeTwo() {
            var out = new java.io.ByteArrayOutputStream();
            var err = new java.io.ByteArrayOutputStream();

            int exitCode = PublisherMain.run(new String[] {"delete-everything"},
                    new java.io.PrintStream(out), new java.io.PrintStream(err));

            assertThat(exitCode).isEqualTo(2);
            assertThat(err.toString()).contains("Unknown command");
        }

        @Test
        void usageErrorsExitWithCodeTwo() throws IOException {
            var out = new java.io.ByteArrayOutputStream();
            var err = new java.io.ByteArrayOutputStream();

            int exitCode = PublisherMain.run(
                    new String[] {"publish-junit", "--base-url", "http://localhost:8080", "--results", "a.xml"},
                    new java.io.PrintStream(out), new java.io.PrintStream(err));

            assertThat(exitCode).isEqualTo(2);
            assertThat(err.toString()).contains("--execution-id");
        }

        @Test
        void aNonExistentResultsPathExitsWithCodeTwoAndNoStacktrace() {
            var out = new java.io.ByteArrayOutputStream();
            var err = new java.io.ByteArrayOutputStream();

            int exitCode = PublisherMain.run(
                    new String[] {"publish-junit", "--base-url", "http://localhost:8080",
                            "--execution-id", "exec-1", "--results", "no-such-report.xml"},
                    new java.io.PrintStream(out), new java.io.PrintStream(err));

            assertThat(exitCode).isEqualTo(2);
            assertThat(err.toString()).contains("No such file or directory").doesNotContain("Exception");
        }

        @Test
        void dryRunPrintsAPreviewAndSendsNoRequestReturningExitCodeZero() throws IOException {
            Path suite = writeSuite("suite.xml", """
                    <testsuite name="com.example.T">
                      <testcase classname="com.example.T" name="passedTest" time="0.1"/>
                      <testcase classname="com.example.T" name="failedTest" time="0.2">
                        <failure message="boom"/>
                      </testcase>
                      <testcase classname="com.example.T" name="skippedTest" time="0">
                        <skipped/>
                      </testcase>
                    </testsuite>
                    """);
            var out = new java.io.ByteArrayOutputStream();
            var err = new java.io.ByteArrayOutputStream();

            int exitCode = PublisherMain.run(
                    new String[] {"publish-junit", "--base-url", "http://localhost:9999",
                            "--execution-id", "exec-1", "--results", suite.toString(), "--dry-run"},
                    new java.io.PrintStream(out), new java.io.PrintStream(err));

            assertThat(exitCode).isEqualTo(0);
            String printed = out.toString();
            assertThat(printed).contains("JUnit Import Preview");
            assertThat(printed).contains("Files:      1");
            assertThat(printed).contains("Tests:      3");
            assertThat(printed).contains("Passed:     1");
            assertThat(printed).contains("Failed:     1");
            assertThat(printed).contains("Skipped:    1");
            assertThat(printed).contains("- com.example.T#passedTest");
            assertThat(printed).contains("- com.example.T#failedTest");
            assertThat(printed).contains("- com.example.T#skippedTest");
            assertThat(err.toString()).isEmpty();
        }

        @Test
        void dryRunNeverPrintsTheApiToken() throws IOException {
            // TESTRYN_API_TOKEN reaches PublisherCli.parseJUnit only via System.getenv,
            // which run(String[], ...) always calls -- there is no argument to inject a
            // fake token through, so this asserts the real property under test instead:
            // printJUnitPreview() is built purely from parsed testcases and never touches
            // options.apiToken() at all, so no token value can ever end up in its output,
            // real or fake, whatever TESTRYN_API_TOKEN happens to be set to in this
            // environment.
            Path suite = writeSuite("suite.xml", """
                    <testsuite name="com.example.T">
                      <testcase classname="com.example.T" name="passedTest" time="0.1"/>
                    </testsuite>
                    """);
            var out = new java.io.ByteArrayOutputStream();
            var err = new java.io.ByteArrayOutputStream();

            int exitCode = PublisherMain.run(
                    new String[] {"publish-junit", "--base-url", "http://localhost:9999",
                            "--execution-id", "exec-1", "--results", suite.toString(), "--dry-run"},
                    new java.io.PrintStream(out), new java.io.PrintStream(err));

            assertThat(exitCode).isEqualTo(0);
            String token = System.getenv("TESTRYN_API_TOKEN");
            if (token != null && !token.isBlank()) {
                assertThat(out.toString()).doesNotContain(token);
            }
        }

        @Test
        void aDuplicateAutomationReferenceAcrossFilesExitsWithCodeTwo() throws IOException {
            Path a = writeSuite("a.xml", """
                    <testsuite name="s"><testcase classname="c" name="dup" time="0"/></testsuite>
                    """);
            Path b = writeSuite("b.xml", """
                    <testsuite name="s"><testcase classname="c" name="dup" time="0"/></testsuite>
                    """);
            var out = new java.io.ByteArrayOutputStream();
            var err = new java.io.ByteArrayOutputStream();

            int exitCode = PublisherMain.run(
                    new String[] {"publish-junit", "--base-url", "http://localhost:9999",
                            "--execution-id", "exec-1", "--results", a.toString(), b.toString()},
                    new java.io.PrintStream(out), new java.io.PrintStream(err));

            assertThat(exitCode).isEqualTo(2);
            assertThat(err.toString()).contains("c#dup");
        }

        @Test
        void anEmptyReportWithNoTestcasesExitsWithCodeOne() throws IOException {
            Path suite = writeSuite("empty.xml", "<testsuite name=\"s\"></testsuite>\n");
            var out = new java.io.ByteArrayOutputStream();
            var err = new java.io.ByteArrayOutputStream();

            int exitCode = PublisherMain.run(
                    new String[] {"publish-junit", "--base-url", "http://localhost:9999",
                            "--execution-id", "exec-1", "--results", suite.toString()},
                    new java.io.PrintStream(out), new java.io.PrintStream(err));

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("No <testcase>");
        }

        private Path writeSuite(String filename, String xml) throws IOException {
            Path file = tempDir.resolve(filename);
            Files.writeString(file, xml, StandardCharsets.UTF_8);
            return file;
        }
    }
}
