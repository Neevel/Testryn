package com.testryn.testcase.repository;

import com.testryn.testcase.domain.TestCaseVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestCaseVersionRepository extends JpaRepository<TestCaseVersion, UUID> {

    List<TestCaseVersion> findByTestCaseIdOrderByVersionNumberDesc(UUID testCaseId);
}
