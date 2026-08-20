package com.testryn.report.storage;

/**
 * Result of storing a binary object. Contains no infrastructure details (paths,
 * bucket names, …) — those stay internal to the {@link ReportStorage} implementation.
 */
public record StoredObject(String storageKey, long size, String checksum) {
}
