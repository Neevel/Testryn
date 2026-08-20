package com.testryn.report.web;

import com.testryn.report.domain.Report;

import java.time.Instant;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record ReportResponse(
            UUID id,
            UUID executionId,
            String filename,
            String contentType,
            long sizeBytes,
            String checksum,
            Instant uploadedAt
    ) {
        public static ReportResponse from(Report report) {
            return new ReportResponse(
                    report.getId(),
                    report.getExecution().getId(),
                    report.getFilename(),
                    report.getContentType(),
                    report.getSizeBytes(),
                    report.getChecksum(),
                    report.getUploadedAt()
            );
        }
    }
}
