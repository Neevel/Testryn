package com.testryn.report.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Storage abstraction for binary report/attachment content. Only metadata (this
 * interface's return values) is referenced from the relational database — the bytes
 * themselves never are (ADR 0001, ADR 0004). The MVP implementation
 * ({@link FilesystemReportStorage}) writes to a local, Docker-volume-backed
 * directory; a future S3-compatible implementation can replace it without touching
 * the domain model, as long as it implements this interface.
 */
public interface ReportStorage {

    /**
     * Stores {@code content} and returns a server-generated reference to it.
     * Implementations must never derive the storage location from client-controlled
     * input (e.g. {@code originalFilename}) to avoid path traversal.
     */
    StoredObject store(String projectKey, String originalFilename, String contentType, InputStream content,
                        long size) throws IOException;

    Resource load(String storageKey);

    void delete(String storageKey);
}
