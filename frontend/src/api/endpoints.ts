import { api } from "./client";
import type {
  Execution,
  ExecutionResult,
  ExecutionResultStatus,
  JiraConnection,
  JiraConnectionTestResult,
  JiraIssuePreview,
  PageResponse,
  Project,
  Report,
  RequirementLink,
  RequirementProviderType,
  Step,
  TestCase,
  TestCasePriority,
  TestCaseStatus,
  TestCaseVersion,
  TestPlan,
} from "./types";

export const ProjectsApi = {
  list: () => api.get<Project[]>("/api/v1/projects"),
  get: (key: string) => api.get<Project>(`/api/v1/projects/${key}`),
  create: (input: { key: string; name: string; description?: string }) =>
    api.post<Project>("/api/v1/projects", input),
  update: (key: string, input: { name: string; description?: string }) =>
    api.put<Project>(`/api/v1/projects/${key}`, input),
};

export interface TestCaseSearchParams {
  query?: string;
  tag?: string;
  requirementKey?: string;
  status?: TestCaseStatus;
  priority?: TestCasePriority;
  automationReference?: string;
  page?: number;
  size?: number;
}

export const TestCasesApi = {
  search: (projectKey: string, params: TestCaseSearchParams = {}) => {
    const qs = new URLSearchParams();
    if (params.query) qs.set("query", params.query);
    if (params.tag) qs.set("tag", params.tag);
    if (params.requirementKey) qs.set("requirementKey", params.requirementKey);
    if (params.status) qs.set("status", params.status);
    if (params.priority) qs.set("priority", params.priority);
    if (params.automationReference) qs.set("automationReference", params.automationReference);
    qs.set("page", String(params.page ?? 0));
    qs.set("size", String(params.size ?? 20));
    return api.get<PageResponse<TestCase>>(`/api/v1/projects/${projectKey}/test-cases?${qs.toString()}`);
  },
  /** Convenience for call sites that just want "all test cases" (e.g. a picker dropdown). */
  listAll: (projectKey: string) => TestCasesApi.search(projectKey, { size: 100 }).then((p) => p.content),
  get: (id: string) => api.get<TestCase>(`/api/v1/test-cases/${id}`),
  versions: (id: string) => api.get<TestCaseVersion[]>(`/api/v1/test-cases/${id}/versions`),
  create: (
    projectKey: string,
    input: {
      title: string;
      description?: string;
      preconditions?: string;
      priority: TestCasePriority;
      tags: string[];
      steps: Step[];
      automationReference?: string | null;
    },
  ) => api.post<TestCase>(`/api/v1/projects/${projectKey}/test-cases`, input),
  update: (
    id: string,
    input: {
      title: string;
      description?: string;
      preconditions?: string;
      steps: Step[];
      status: TestCaseStatus;
      priority: TestCasePriority;
      tags: string[];
      automationReference?: string | null;
    },
  ) => api.put<TestCase>(`/api/v1/test-cases/${id}`, input),
  exportUrl: (projectKey: string, format: "json" | "csv" | "markdown") =>
    `/api/v1/projects/${projectKey}/test-cases/export?format=${format}`,
};

export const RequirementsApi = {
  listForTestCase: (testCaseId: string) => api.get<RequirementLink[]>(`/api/v1/test-cases/${testCaseId}/requirements`),
  listForProject: (projectKey: string) => api.get<RequirementLink[]>(`/api/v1/projects/${projectKey}/requirements`),
  create: (
    testCaseId: string,
    input: { provider: RequirementProviderType; externalKey: string; url?: string; summary?: string },
  ) => api.post<RequirementLink>(`/api/v1/test-cases/${testCaseId}/requirements`, input),
  remove: (testCaseId: string, linkId: string) =>
    api.del<void>(`/api/v1/test-cases/${testCaseId}/requirements/${linkId}`),
};

export const JiraApi = {
  connection: () => api.get<JiraConnection>("/api/v1/integrations/jira/connection"),
  testConnection: () => api.post<JiraConnectionTestResult>("/api/v1/integrations/jira/connection/test"),
  previewIssue: (key: string) => api.get<JiraIssuePreview>(`/api/v1/integrations/jira/issues/${encodeURIComponent(key)}`),
};

export const TestPlansApi = {
  listForProject: (projectKey: string) => api.get<TestPlan[]>(`/api/v1/projects/${projectKey}/test-plans`),
  get: (id: string) => api.get<TestPlan>(`/api/v1/test-plans/${id}`),
  create: (projectKey: string, input: { name: string; description?: string }) =>
    api.post<TestPlan>(`/api/v1/projects/${projectKey}/test-plans`, input),
  addTestCase: (planId: string, testCaseId: string) =>
    api.post<TestPlan>(`/api/v1/test-plans/${planId}/test-cases`, { testCaseId }),
  removeTestCase: (planId: string, testCaseId: string) =>
    api.del<void>(`/api/v1/test-plans/${planId}/test-cases/${testCaseId}`),
};

export const ExecutionsApi = {
  listForProject: (projectKey: string) => api.get<Execution[]>(`/api/v1/projects/${projectKey}/executions`),
  listForPlan: (planId: string) => api.get<Execution[]>(`/api/v1/test-plans/${planId}/executions`),
  get: (id: string) => api.get<Execution>(`/api/v1/executions/${id}`),
  createFromPlan: (planId: string, name?: string) =>
    api.post<Execution>(`/api/v1/test-plans/${planId}/executions`, name ? { name } : {}),
  setStatus: (id: string, status: "COMPLETED" | "ABORTED") => api.patch<Execution>(`/api/v1/executions/${id}`, { status }),
  /**
   * JSON Merge Patch (ADR 0006): only the fields present in `input` are changed;
   * anything omitted keeps its current value on the server. Never send a field with
   * value `undefined` if you mean to leave it untouched -- JSON.stringify drops it,
   * which is exactly "don't change this".
   */
  updateResult: (
    executionId: string,
    resultId: string,
    input: {
      status?: ExecutionResultStatus;
      comment?: string | null;
      durationMs?: number | null;
      executor?: string | null;
      actualResult?: string | null;
      failureDetails?: string | null;
    },
  ) => api.patch<ExecutionResult>(`/api/v1/executions/${executionId}/results/${resultId}`, input),
};

export const ReportsApi = {
  listForExecution: (executionId: string) => api.get<Report[]>(`/api/v1/executions/${executionId}/reports`),
  upload: (executionId: string, file: File) => api.upload<Report>(`/api/v1/executions/${executionId}/reports`, file),
  downloadPath: (reportId: string) => `/api/v1/reports/${reportId}/download`,
};
