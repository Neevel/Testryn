package com.testryn.report.domain;

import com.testryn.execution.domain.Execution;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a file uploaded and attached to an {@link Execution}. The binary
 * content lives behind {@code ReportStorage}, referenced here only by
 * {@code storageKey} (ADR 0004). A Report is metadata about evidence, not the
 * fachlich primary result — {@code ExecutionResult} remains that (see AGENTS.md).
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(name = "filename", nullable = false, length = 500)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected Report() {
        // for JPA
    }

    public static Report create(Execution execution, String filename, String contentType, long sizeBytes,
                                 String storageKey, String checksum) {
        Report report = new Report();
        report.execution = execution;
        report.filename = filename;
        report.contentType = contentType;
        report.sizeBytes = sizeBytes;
        report.storageKey = storageKey;
        report.checksum = checksum;
        report.uploadedAt = Instant.now();
        return report;
    }

    public UUID getId() {
        return id;
    }

    public Execution getExecution() {
        return execution;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getChecksum() {
        return checksum;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
