// Mirrors the backend's web DTOs (see backend/src/main/java/com/testryn/**/web).
// Kept intentionally close to the REST responses -- the UI has no business logic of
// its own (AGENTS.md: API-first).

export interface Project {
  id: string;
  key: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export type TestCaseStatus = "DRAFT" | "ACTIVE" | "DEPRECATED";
export type TestCasePriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface Step {
  order: number;
  action: string;
  expectedResult: string;
}

export interface TestCaseVersion {
  id: string;
  versionNumber: number;
  title: string;
  description: string | null;
  preconditions: string | null;
  steps: Step[];
  createdAt: string;
}

export interface TestCase {
  id: string;
  humanId: string;
  projectKey: string;
  status: TestCaseStatus;
  priority: TestCasePriority;
  tags: string[];
  currentVersion: TestCaseVersion | null;
  createdAt: string;
  updatedAt: string;
}

export type RequirementProviderType = "JIRA";

export interface RequirementLink {
  id: string;
  testCaseId: string;
  testCaseHumanId: string;
  testCaseTitle: string | null;
  provider: RequirementProviderType;
  externalId: string | null;
  externalKey: string;
  url: string;
  summary: string | null;
  issueType: string | null;
  status: string | null;
  description: string | null;
  createdAt: string;
}

export interface JiraIssuePreview {
  externalId: string | null;
  externalKey: string;
  url: string;
  summary: string | null;
  issueType: string | null;
  status: string | null;
  description: string | null;
}

export type JiraAuthType = "API_TOKEN" | "OAUTH2";

export interface JiraConnection {
  name: string;
  baseUrl: string | null;
  authType: JiraAuthType;
  email: string | null;
  active: boolean;
  tokenConfigured: boolean;
  usable: boolean;
}

export interface JiraConnectionTestResult {
  success: boolean;
  message: string;
}

export interface TestPlanEntry {
  testCaseId: string;
  testCaseHumanId: string;
  testCaseTitle: string | null;
  position: number;
}

export interface TestPlan {
  id: string;
  projectKey: string;
  name: string;
  description: string | null;
  testCases: TestPlanEntry[];
  createdAt: string;
  updatedAt: string;
}

export type ExecutionStatus = "CREATED" | "RUNNING" | "COMPLETED" | "ABORTED";
export type ExecutionResultStatus = "NOT_RUN" | "PASSED" | "FAILED" | "SKIPPED" | "BLOCKED";

export interface ExecutionResult {
  id: string;
  status: ExecutionResultStatus;
  comment: string | null;
  durationMs: number | null;
  executedAt: string | null;
  executor: string | null;
  actualResult: string | null;
  failureDetails: string | null;
}

export interface ExecutionTestCase {
  testCaseId: string;
  testCaseHumanId: string;
  testCaseVersionNumber: number;
  title: string;
  description: string | null;
  preconditions: string | null;
  steps: Step[];
  position: number;
  result: ExecutionResult;
}

export interface Execution {
  id: string;
  projectKey: string;
  testPlanId: string | null;
  iterationNumber: number;
  name: string;
  status: ExecutionStatus;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  testCases: ExecutionTestCase[];
}

export interface Report {
  id: string;
  executionId: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  checksum: string | null;
  uploadedAt: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors: { field: string; message: string }[] | null;
}

/** Mirrors com.testryn.common.web.PageResponse -- see ADR 0008. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
