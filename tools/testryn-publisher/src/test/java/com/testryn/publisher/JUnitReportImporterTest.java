package com.testryn.publisher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JUnitReportImporterTest {

    @TempDir
    Path tempDir;

    private final JUnitReportImporter importer = new JUnitReportImporter();

    @Test
    void aSingleFileIsImported() throws IOException {
        Path file = writeSuite("a.xml", "com.example.A", "one");

        JUnitReportImporter.ImportResult result = importer.importFrom(List.of(file.toString()));

        assertThat(result.files()).containsExactly(file);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).automationReference()).isEqualTo("com.example.A#one");
    }

    @Test
    void multipleFilesAreMergedIntoOneCombinedBatch() throws IOException {
        Path a = writeSuite("a.xml", "com.example.A", "one");
        Path b = writeSuite("b.xml", "com.example.B", "two");

        JUnitReportImporter.ImportResult result = importer.importFrom(List.of(a.toString(), b.toString()));

        assertThat(result.files()).containsExactlyInAnyOrder(a, b);
        assertThat(result.results()).hasSize(2);
        assertThat(result.results()).extracting(PublisherResultInput::automationReference)
                .containsExactlyInAnyOrder("com.example.A#one", "com.example.B#two");
    }

    @Test
    void aDirectoryIsExpandedToItsDirectXmlChildrenOnly() throws IOException {
        writeSuite("a.xml", "com.example.A", "one");
        writeSuite("b.xml", "com.example.B", "two");
        Files.writeString(tempDir.resolve("notes.txt"), "not xml, must be ignored");
        Path nested = Files.createDirectory(tempDir.resolve("nested"));
        writeSuiteIn(nested, "c.xml", "com.example.C", "three"); // must NOT be picked up (non-recursive)

        JUnitReportImporter.ImportResult result = importer.importFrom(List.of(tempDir.toString()));

        assertThat(result.files()).hasSize(2);
        assertThat(result.results()).extracting(PublisherResultInput::automationReference)
                .containsExactlyInAnyOrder("com.example.A#one", "com.example.B#two");
    }

    @Test
    void aFileAndADirectoryCanBeMixedInOneCall() throws IOException {
        Path standalone = writeSuite("standalone.xml", "com.example.S", "solo");
        Path dir = Files.createDirectory(tempDir.resolve("reports"));
        writeSuiteIn(dir, "d.xml", "com.example.D", "four");

        JUnitReportImporter.ImportResult result = importer.importFrom(
                List.of(standalone.toString(), dir.toString()));

        assertThat(result.results()).hasSize(2);
    }

    @Test
    void duplicateAutomationReferenceAcrossFilesIsRejectedWithAClearMessage() throws IOException {
        Path a = writeSuite("a.xml", "com.example.A", "same");
        Path b = writeSuite("b.xml", "com.example.A", "same");

        assertThatThrownBy(() -> importer.importFrom(List.of(a.toString(), b.toString())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("com.example.A#same")
                .hasMessageContaining("Duplicate");
    }

    @Test
    void anEmptyDirectoryProducesAClearError() throws IOException {
        Path emptyDir = Files.createDirectory(tempDir.resolve("empty"));

        assertThatThrownBy(() -> importer.importFrom(List.of(emptyDir.toString())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No .xml files");
    }

    @Test
    void aNonExistentPathProducesAClearError() {
        Path missing = tempDir.resolve("does-not-exist.xml");

        assertThatThrownBy(() -> importer.importFrom(List.of(missing.toString())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No such file or directory");
    }

    @Test
    void anEmptyInputListProducesAClearError() {
        assertThatThrownBy(() -> importer.importFrom(List.of()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void aParseFailureInOneFileNamesThatFileInTheErrorMessage() throws IOException {
        Path bad = tempDir.resolve("broken.xml");
        Files.writeString(bad, "<testsuite><testcase name=\"x\"", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> importer.importFrom(List.of(bad.toString())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("broken.xml");
    }

    private Path writeSuite(String filename, String classname, String testName) throws IOException {
        return writeSuiteIn(tempDir, filename, classname, testName);
    }

    private Path writeSuiteIn(Path dir, String filename, String classname, String testName) throws IOException {
        Path file = dir.resolve(filename);
        Files.writeString(file, """
                <testsuite name="%s">
                  <testcase classname="%s" name="%s" time="0.01"/>
                </testsuite>
                """.formatted(classname, classname, testName), StandardCharsets.UTF_8);
        return file;
    }
}
