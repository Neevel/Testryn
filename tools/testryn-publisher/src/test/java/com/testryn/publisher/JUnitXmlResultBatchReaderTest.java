package com.testryn.publisher;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JUnitXmlResultBatchReaderTest {

    private final JUnitXmlResultBatchReader reader = new JUnitXmlResultBatchReader();

    @Test
    void parsesABareTestsuiteRoot() throws IOException {
        ResultBatch batch = read("""
                <testsuite name="com.example.LoginTest" tests="1">
                  <testcase classname="com.example.LoginTest" name="successfulLogin" time="0.5"/>
                </testsuite>
                """);

        assertThat(batch.executionId()).isNull();
        assertThat(batch.results()).hasSize(1);
        PublisherResultInput result = batch.results().get(0);
        assertThat(result.automationReference()).isEqualTo("com.example.LoginTest#successfulLogin");
        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.durationMs()).isEqualTo(500L);
    }

    @Test
    void parsesATestsuitesWrapperRootWithMultipleSuites() throws IOException {
        ResultBatch batch = read("""
                <testsuites>
                  <testsuite name="com.example.LoginTest">
                    <testcase classname="com.example.LoginTest" name="successfulLogin" time="0.1"/>
                  </testsuite>
                  <testsuite name="com.example.LogoutTest">
                    <testcase classname="com.example.LogoutTest" name="successfulLogout" time="0.2"/>
                  </testsuite>
                </testsuites>
                """);

        assertThat(batch.results()).hasSize(2);
        assertThat(batch.results()).extracting(PublisherResultInput::automationReference)
                .containsExactlyInAnyOrder(
                        "com.example.LoginTest#successfulLogin",
                        "com.example.LogoutTest#successfulLogout");
    }

    @Test
    void aTestcaseWithNoFailureErrorOrSkippedChildIsPassed() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase classname="com.example.T" name="ok" time="0.001"/>
                """));

        assertThat(batch.results().get(0).status()).isEqualTo("PASSED");
    }

    @Test
    void aFailureChildMapsToFailedWithMessageAndTypePlusBody() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase classname="com.example.T" name="fails" time="0.01">
                  <failure message="expected true but was false" type="java.lang.AssertionError">
                    at com.example.T.fails(T.java:42)
                  </failure>
                </testcase>
                """));

        PublisherResultInput result = batch.results().get(0);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.actualResult()).isEqualTo("expected true but was false");
        assertThat(result.failureDetails())
                .contains("java.lang.AssertionError")
                .contains("at com.example.T.fails(T.java:42)");
    }

    @Test
    void anErrorChildMapsToFailedTheSameWayAsFailure() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase classname="com.example.T" name="errors" time="0.01">
                  <error message="null pointer" type="java.lang.NullPointerException">boom</error>
                </testcase>
                """));

        PublisherResultInput result = batch.results().get(0);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.actualResult()).isEqualTo("null pointer");
        assertThat(result.failureDetails()).contains("java.lang.NullPointerException").contains("boom");
    }

    @Test
    void aSkippedChildMapsToSkippedWithItsMessage() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase classname="com.example.T" name="skipped" time="0">
                  <skipped message="disabled: not yet implemented"/>
                </testcase>
                """));

        PublisherResultInput result = batch.results().get(0);
        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(result.actualResult()).isEqualTo("disabled: not yet implemented");
    }

    @Test
    void aSkippedChildWithoutAMessageAttributeFallsBackToItsBodyText() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase classname="com.example.T" name="skipped" time="0">
                  <skipped>ignored via @Disabled</skipped>
                </testcase>
                """));

        assertThat(batch.results().get(0).actualResult()).isEqualTo("ignored via @Disabled");
    }

    @Test
    void neverProducesTheBlockedStatus() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase classname="com.example.T" name="ok" time="0"/>
                <testcase classname="com.example.T" name="fails" time="0"><failure message="x"/></testcase>
                <testcase classname="com.example.T" name="skip" time="0"><skipped/></testcase>
                """));

        assertThat(batch.results()).extracting(PublisherResultInput::status)
                .containsExactlyInAnyOrder("PASSED", "FAILED", "SKIPPED");
    }

    @Test
    void convertsFractionalSecondsToMillisecondsWithoutPrecisionDrift() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase classname="com.example.T" name="t1" time="1.273"/>
                """));

        assertThat(batch.results().get(0).durationMs()).isEqualTo(1273L);
    }

    @Test
    void aMissingTimeAttributeYieldsNoDurationRatherThanFailing() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase classname="com.example.T" name="t1"/>
                """));

        assertThat(batch.results().get(0).durationMs()).isNull();
    }

    @Test
    void classnameAndNameAreJoinedWithAHashToFormTheAutomationReference() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase classname="com.example.pkg.MyTest" name="myMethod" time="0"/>
                """));

        assertThat(batch.results().get(0).automationReference()).isEqualTo("com.example.pkg.MyTest#myMethod");
    }

    @Test
    void aMissingClassnameFallsBackToNameAlone() throws IOException {
        ResultBatch batch = read(testsuiteWith("""
                <testcase name="myMethod" time="0"/>
                """));

        assertThat(batch.results().get(0).automationReference()).isEqualTo("myMethod");
    }

    @Test
    void aTestcaseWithNeitherNameNorClassnameIsRejected() {
        assertThatThrownBy(() -> read(testsuiteWith("""
                <testcase time="0"/>
                """))).isInstanceOf(IOException.class);
    }

    @Test
    void malformedXmlProducesAClearIOException() {
        assertThatThrownBy(() -> read("<testsuite><testcase name=\"x\""))
                .isInstanceOf(IOException.class);
    }

    @Test
    void emptyInputProducesAClearIOException() {
        assertThatThrownBy(() -> read("")).isInstanceOf(IOException.class);
    }

    @Test
    void aWrongRootElementProducesAClearIOException() {
        assertThatThrownBy(() -> read("<surefire-report/>"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("testsuite");
    }

    @Test
    void binaryContentProducesAClearIOExceptionNotARawStacktrace() {
        byte[] binary = {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE, 0x03};
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(binary)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void rejectsADoctypeDeclarationOutright() {
        // disallow-doctype-decl means ANY DOCTYPE is refused, which is the strongest
        // and simplest defense against XXE/billion-laughs -- legitimate JUnit/Surefire
        // XML never has one.
        assertThatThrownBy(() -> read("""
                <?xml version="1.0"?>
                <!DOCTYPE testsuite [<!ENTITY xxe "harmless">]>
                <testsuite name="s"><testcase classname="c" name="n" time="0"/></testsuite>
                """)).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsAClassicXxeFileDisclosureAttempt() {
        String xxePayload = """
                <?xml version="1.0"?>
                <!DOCTYPE testsuite [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <testsuite name="s">
                  <testcase classname="c" name="n" time="0">
                    <failure message="&xxe;"/>
                  </testcase>
                </testsuite>
                """;

        assertThatThrownBy(() -> read(xxePayload)).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsAnExternalEntityReferencingARemoteUrl() {
        String xxePayload = """
                <?xml version="1.0"?>
                <!DOCTYPE testsuite [
                  <!ENTITY xxe SYSTEM "http://169.254.169.254/latest/meta-data/">
                ]>
                <testsuite name="s">
                  <testcase classname="c" name="n" time="0">
                    <failure message="&xxe;"/>
                  </testcase>
                </testsuite>
                """;

        assertThatThrownBy(() -> read(xxePayload)).isInstanceOf(IOException.class);
    }

    private String testsuiteWith(String testcasesXml) {
        return "<testsuite name=\"com.example.T\">\n" + testcasesXml + "\n</testsuite>";
    }

    private ResultBatch read(String xml) throws IOException {
        try (InputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return reader.read(input);
        }
    }
}
