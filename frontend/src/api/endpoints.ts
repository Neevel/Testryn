import { api } from "./client";
import type {
  Execution,
  ExecutionResultStatus,
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

export const TestCasesApi = {
  listForProject: (projectKey: string) => api.get<TestCase[]>(`/api/v1/projects/${projectKey}/test-cases`),
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
    },
  ) => api.put<TestCase>(`/api/v1/test-cases/${id}`, input),
  exportUrl: (projectKey: string, format: "json" | "csv" | "markdown") =>
    `/api/v1/projects/${projectKey}/test-cases/export?format=${format}`,
};

export const RequirementsApi = {
  listForTestCase: (testCaseId: string) => api.get<RequirementLink[]>(`/api/v1/test-cases/${testCaseId}/requirements`),
  create: (
    testCaseId: string,
    input: { provider: RequirementProviderType; externalKey: string; url?: string; summary?: string },
  ) => api.post<RequirementLink>(`/api/v1/test-cases/${testCaseId}/requirements`, input),
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
  updateResult: (
    executionId: string,
    resultId: string,
    input: { status: ExecutionResultStatus; comment?: string; durationMs?: number; executor?: string; failureDetails?: string },
  ) => api.patch(`/api/v1/executions/${executionId}/results/${resultId}`, input),
};

export const ReportsApi = {
  listForExecution: (executionId: string) => api.get<Report[]>(`/api/v1/executions/${executionId}/reports`),
  upload: (executionId: string, file: File) => api.upload<Report>(`/api/v1/executions/${executionId}/reports`, file),
  downloadPath: (reportId: string) => `/api/v1/reports/${reportId}/download`,
};
