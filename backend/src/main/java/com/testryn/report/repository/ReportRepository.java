package com.testryn.report.repository;

import com.testryn.report.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findByExecutionIdOrderByUploadedAtDesc(UUID executionId);
    List<Report> findByExecutionId(UUID executionId);
    List<Report> findByExecutionProjectId(UUID projectId);
}
