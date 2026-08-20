package com.testryn.report.service;

import com.testryn.common.error.BadRequestException;
import com.testryn.common.error.NotFoundException;
import com.testryn.execution.domain.Execution;
import com.testryn.execution.service.ExecutionService;
import com.testryn.report.domain.Report;
import com.testryn.report.repository.ReportRepository;
import com.testryn.report.storage.ReportStorage;
import com.testryn.report.storage.StoredObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportStorage reportStorage;
    private final ExecutionService executionService;
    private final long maxUploadSizeBytes;

    public ReportService(ReportRepository reportRepository, ReportStorage reportStorage,
                          ExecutionService executionService,
                          @Value("${testryn.storage.max-upload-size-bytes}") long maxUploadSizeBytes) {
        this.reportRepository = reportRepository;
        this.reportStorage = reportStorage;
        this.executionService = executionService;
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    public Report upload(UUID executionId, String originalFilename, String contentType, long size,
                          InputStream content) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BadRequestException("A report file name is required");
        }
        if (size <= 0) {
            throw new BadRequestException("Uploaded report must not be empty");
        }
        if (size > maxUploadSizeBytes) {
            throw new BadRequestException("Uploaded report exceeds the maximum allowed size of "
                    + maxUploadSizeBytes + " bytes");
        }
        Execution execution = executionService.getById(executionId);
        String safeContentType = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";

        StoredObject stored;
        try {
            stored = reportStorage.store(execution.getProject().getKey(), originalFilename, safeContentType,
                    content, size);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded report", e);
        }

        Report report = Report.create(execution, originalFilename, safeContentType, stored.size(),
                stored.storageKey(), stored.checksum());
        return reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<Report> findByExecution(UUID executionId) {
        executionService.getById(executionId);
        return reportRepository.findByExecutionIdOrderByUploadedAtDesc(executionId);
    }

    @Transactional(readOnly = true)
    public Report getById(UUID reportId) {
        return reportRepository.findById(reportId).orElseThrow(() -> NotFoundException.of("Report", reportId));
    }

    @Transactional(readOnly = true)
    public Resource download(UUID reportId) {
        Report report = getById(reportId);
        return reportStorage.load(report.getStorageKey());
    }
}
