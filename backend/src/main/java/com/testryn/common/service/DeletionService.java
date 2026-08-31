package com.testryn.common.service;

import com.testryn.execution.service.ExecutionService;
import com.testryn.project.domain.Project;
import com.testryn.project.service.ProjectService;
import com.testryn.report.service.ReportService;
import com.testryn.testcase.service.TestCaseService;
import com.testryn.testplan.service.TestPlanService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class DeletionService {
    private final JdbcTemplate jdbc;
    private final ProjectService projects;
    private final TestCaseService testCases;
    private final TestPlanService plans;
    private final ExecutionService executions;
    private final ReportService reports;
    public DeletionService(JdbcTemplate jdbc, ProjectService projects, TestCaseService testCases, TestPlanService plans, ExecutionService executions, ReportService reports) { this.jdbc=jdbc;this.projects=projects;this.testCases=testCases;this.plans=plans;this.executions=executions;this.reports=reports; }
    public void deleteTestCase(UUID id) { testCases.getById(id); jdbc.update("delete from test_cases where id=?", id); }
    public void deletePlan(UUID id) { plans.getById(id); jdbc.update("delete from test_plans where id=?", id); }
    public void deleteExecution(UUID id) { executions.getById(id); reports.deleteForExecution(id); deleteExecutionRows("id=?", id); }
    public void deleteProject(String key) {
        Project p=projects.getByKey(key); UUID id=p.getId(); reports.deleteForProject(id);
        for (UUID executionId : jdbc.query("select id from executions where project_id=?", (rs,n)->rs.getObject(1,UUID.class), id)) deleteExecutionRows("id=?", executionId);
        jdbc.update("delete from test_plan_entries where test_plan_id in (select id from test_plans where project_id=?)",id);
        jdbc.update("delete from test_plans where project_id=?",id);
        jdbc.update("update test_cases set current_version_id=null where project_id=?",id);
        jdbc.update("delete from test_steps where test_case_version_id in (select v.id from test_case_versions v join test_cases t on v.test_case_id=t.id where t.project_id=?)",id);
        jdbc.update("delete from test_case_versions where test_case_id in (select id from test_cases where project_id=?)",id);
        jdbc.update("delete from requirement_links where test_case_id in (select id from test_cases where project_id=?)",id);
        jdbc.update("delete from test_case_tags where test_case_id in (select id from test_cases where project_id=?)",id);
        jdbc.update("delete from test_cases where project_id=?",id); jdbc.update("delete from projects where id=?",id);
    }
    private void deleteExecutionRows(String clause, UUID id) { jdbc.update("delete from reports where execution_id=?",id); jdbc.update("delete from execution_step_results where execution_test_case_id in (select id from execution_test_cases where execution_id=?)",id); jdbc.update("delete from execution_results where execution_test_case_id in (select id from execution_test_cases where execution_id=?)",id); jdbc.update("delete from execution_test_cases where execution_id=?",id); jdbc.update("delete from executions where "+clause,id); }
}
