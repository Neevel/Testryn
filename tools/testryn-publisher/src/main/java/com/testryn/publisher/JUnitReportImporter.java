package com.testryn.publisher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Resolves {@code --results} inputs (files and/or directories, Abschnitt 12) to a
 * sorted list of XML report files, parses each with a {@link ResultBatchReader}
 * (normally {@link JUnitXmlResultBatchReader}), and merges them into one combined
 * batch -- exactly the "parse everything, then one atomic request" flow the existing
 * Bulk API already requires (ADR 0010, Abschnitt 17). This class owns the one thing
 * that only makes sense across multiple files: cross-file duplicate
 * {@code automationReference} detection (Abschnitt 18).
 *
 * <p>Deliberately does NOT duplicate the server's "unknown automationReference"
 * validation (Abschnitt 16/34, "nicht Client-seitig Domainregeln duplizieren"): the
 * Bulk API already rejects the whole request atomically and lists every
 * unresolvable reference (ADR 0010) -- the same {@link PublishOutcome#violations()}
 * path {@code publish} already used for the JSON workflow surfaces this for
 * {@code publish-junit} too, unchanged.
 */
public final class JUnitReportImporter {

    private final ResultBatchReader xmlReader;

    public JUnitReportImporter() {
        this(new JUnitXmlResultBatchReader());
    }

    public JUnitReportImporter(ResultBatchReader xmlReader) {
        this.xmlReader = xmlReader;
    }

    public record ImportResult(List<Path> files, List<PublisherResultInput> results) {
    }

    public ImportResult importFrom(List<String> inputs) throws IOException {
        List<Path> files = resolveFiles(inputs);

        List<PublisherResultInput> combined = new ArrayList<>();
        Map<String, Path> firstSeenIn = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();

        for (Path file : files) {
            ResultBatch batch;
            try (InputStream in = Files.newInputStream(file)) {
                batch = xmlReader.read(in);
            } catch (IOException e) {
                throw new IOException("Failed to parse " + file + ": " + e.getMessage(), e);
            }
            for (PublisherResultInput result : batch.results()) {
                String reference = result.automationReference();
                if (reference != null) {
                    Path existing = firstSeenIn.putIfAbsent(reference, file);
                    if (existing != null) {
                        duplicates.add(reference + " (in " + existing + " and " + file + ")");
                    }
                }
                combined.add(result);
            }
        }

        if (!duplicates.isEmpty()) {
            throw new IOException("Duplicate automationReference found across the given report(s), refusing to "
                    + "guess which result is authoritative:\n  " + String.join("\n  ", duplicates));
        }
        return new ImportResult(files, combined);
    }

    /** Files as given, directories expanded to their direct (non-recursive,
     * Abschnitt 12) {@code *.xml} children, sorted for deterministic output. */
    private List<Path> resolveFiles(List<String> inputs) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String input : inputs) {
            Path path = Path.of(input);
            if (Files.isDirectory(path)) {
                files.addAll(listXmlFiles(path));
            } else if (Files.isRegularFile(path)) {
                files.add(path);
            } else if (Files.notExists(path)) {
                throw new IOException("No such file or directory: " + path);
            } else {
                throw new IOException("Not a regular file or directory: " + path);
            }
        }
        if (files.isEmpty()) {
            throw new IOException("No report files given");
        }
        return files;
    }

    private List<Path> listXmlFiles(Path directory) throws IOException {
        List<Path> xmlFiles;
        try (Stream<Path> stream = Files.list(directory)) {
            xmlFiles = stream
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .sorted()
                    .toList();
        }
        if (xmlFiles.isEmpty()) {
            throw new IOException("No .xml files found in directory: " + directory
                    + " (only its direct contents are read, not subdirectories)");
        }
        return xmlFiles;
    }
}
