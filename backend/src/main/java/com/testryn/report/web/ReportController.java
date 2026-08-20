package com.testryn.report.web;

import com.testryn.report.domain.Report;
import com.testryn.report.service.ReportService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.testryn.report.web.ReportDtos.ReportResponse;

@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping(value = "/api/v1/executions/{executionId}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse upload(@PathVariable UUID executionId, @RequestParam("file") MultipartFile file) {
        try {
            Report report = reportService.upload(executionId, file.getOriginalFilename(), file.getContentType(),
                    file.getSize(), file.getInputStream());
            return ReportResponse.from(report);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }

    @GetMapping("/api/v1/executions/{executionId}/reports")
    public List<ReportResponse> list(@PathVariable UUID executionId) {
        return reportService.findByExecution(executionId).stream().map(ReportResponse::from).toList();
    }

    @GetMapping("/api/v1/reports/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        Report report = reportService.getById(id);
        Resource resource = reportService.download(id);
        String encodedFilename = java.net.URLEncoder.encode(report.getFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }
}
