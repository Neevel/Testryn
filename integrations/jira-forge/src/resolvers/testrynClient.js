import { fetch } from "@forge/api";

/**
 * The one and only place this app talks to Testryn (ADR 0014). Runs entirely in the
 * Forge backend/resolver -- never in the browser -- so TESTRYN_API_TOKEN never
 * reaches the Jira frontend bundle or any browser-visible network response
 * (Abschnitt 7/43).
 *
 * Every code path here returns a small, tagged plain object -- never throws, never
 * forwards a raw error/exception object, a stack trace, a response body, or the
 * token itself back to the caller (Abschnitt 18/38/43). {@code kind} is one of:
 * "ok", "unavailable", "unauthorized".
 */

const DEFAULT_LIMIT = 20;

export async function getCoverage(issueKey) {
  const baseUrl = trimTrailingSlash(process.env.TESTRYN_API_BASE_URL);
  if (!baseUrl) {
    return { kind: "unavailable" };
  }

  const url = `${baseUrl}/api/v1/requirement-links/coverage`
    + `?provider=jira&externalKey=${encodeURIComponent(issueKey)}&limit=${DEFAULT_LIMIT}`;
  const token = process.env.TESTRYN_API_TOKEN;

  let response;
  try {
    response = await fetch(url, {
      method: "GET",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
  } catch (networkError) {
    // Network/DNS/timeout failure -- Testryn is simply not reachable. Deliberately
    // discards the actual error object (Abschnitt 18: no stacktraces as normal
    // user output).
    return { kind: "unavailable" };
  }

  if (response.status === 401 || response.status === 403) {
    return { kind: "unauthorized" };
  }
  if (!response.ok) {
    return { kind: "unavailable" };
  }

  let body;
  try {
    body = await response.json();
  } catch (parseError) {
    return { kind: "unavailable" };
  }

  if (!body || !Array.isArray(body.testCases) || !body.requirement) {
    // Malformed/unexpected shape -- treat exactly like "unavailable" rather than
    // let a missing field crash the panel (Abschnitt 38).
    return { kind: "unavailable" };
  }

  return {
    kind: "ok",
    requirement: body.requirement,
    testCases: body.testCases,
    totalCount: typeof body.totalCount === "number" ? body.totalCount : body.testCases.length,
    // Environment variables are not readable from the frontend at all (Forge
    // platform constraint) -- the resolver is the only place that can read
    // TESTRYN_APP_BASE_URL, so it is handed back here for the frontend to build
    // "Open in Testryn" links from. This is a plain base URL, never a secret.
    appBaseUrl: trimTrailingSlash(process.env.TESTRYN_APP_BASE_URL),
  };
}

export async function updateTestCaseDefinition(issueKey, testCaseId, definition) {
  const coverage = await getCoverage(issueKey);
  if (coverage.kind !== "ok") return coverage;
  const linked = coverage.testCases.find((testCase) => testCase.id === testCaseId);
  if (!linked) return { kind: "invalid" };
  if (!validDefinition(definition) || definition.expectedVersion !== linked.version) {
    return { kind: "invalid" };
  }

  const baseUrl = trimTrailingSlash(process.env.TESTRYN_API_BASE_URL);
  const token = process.env.TESTRYN_API_TOKEN;
  try {
    const response = await fetch(`${baseUrl}/api/v1/test-cases/${encodeURIComponent(testCaseId)}/definition`, {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(definition),
    });
    if (response.status === 401 || response.status === 403) return { kind: "unauthorized" };
    if (response.status === 409) return { kind: "conflict" };
    if (response.status === 400) return { kind: "invalid" };
    if (!response.ok) return { kind: "unavailable" };
    return { kind: "ok" };
  } catch (networkError) {
    return { kind: "unavailable" };
  }
}

export async function getProjects() {
  return sendJson("/api/v1/projects", { method: "GET" }, (body) =>
    Array.isArray(body) ? { kind: "ok", projects: body } : { kind: "unavailable" });
}

/**
 * Read-only lookup backing the "link an existing test case" picker: the paginated
 * project search endpoint (ADR 0008) is reused as-is, capped at one page so a
 * single Forge request stays a single Testryn request (Abschnitt 22). `totalCount`
 * is the real total, so the panel can honestly say "showing N of M".
 */
export async function searchTestCases(projectKey, query) {
  if (typeof projectKey !== "string" || !projectKey.trim()) return { kind: "invalid" };
  const params = new URLSearchParams({ size: "20" });
  if (typeof query === "string" && query.trim()) params.set("query", query.trim());
  return sendJson(
    `/api/v1/projects/${encodeURIComponent(projectKey.trim())}/test-cases?${params.toString()}`,
    { method: "GET" },
    (body) => Array.isArray(body?.content)
      ? {
          kind: "ok",
          testCases: body.content,
          totalCount: typeof body.totalElements === "number" ? body.totalElements : body.content.length,
        }
      : { kind: "unavailable" });
}

/**
 * Links an already existing Testryn test case to the current Jira issue (ADR 0016
 * extension). The browser only ever supplies the test case id; the issue key comes
 * from the trusted Forge invocation context and the human-facing Jira URL is built
 * from Testryn's own persisted integration settings, never from a value the browser
 * could tamper with (same rule as createLinkedTestCase). Reuses the existing
 * provider-neutral `POST /test-cases/{id}/requirements` endpoint -- no new API. A
 * duplicate link comes back from Testryn as 409 and is surfaced as "conflict", not
 * an error.
 */
export async function linkExistingTestCase(issueKey, testCaseId) {
  if (typeof testCaseId !== "string" || !testCaseId.trim()) return { kind: "invalid" };

  const connection = await sendJson("/api/v1/integrations/jira/connection", { method: "GET" },
    (body) => typeof body?.baseUrl === "string"
      ? { kind: "ok", baseUrl: trimTrailingSlash(body.baseUrl) }
      : { kind: "unavailable" });
  if (connection.kind !== "ok" || !connection.baseUrl) return connection;

  const baseUrl = trimTrailingSlash(process.env.TESTRYN_API_BASE_URL);
  const token = process.env.TESTRYN_API_TOKEN;
  try {
    const response = await fetch(
      `${baseUrl}/api/v1/test-cases/${encodeURIComponent(testCaseId.trim())}/requirements`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          provider: "JIRA",
          externalKey: issueKey,
          url: `${connection.baseUrl}/browse/${encodeURIComponent(issueKey)}`,
        }),
      });
    if (response.status === 401 || response.status === 403) return { kind: "unauthorized" };
    if (response.status === 409) return { kind: "conflict" };
    if (response.status === 400 || response.status === 404) return { kind: "invalid" };
    if (!response.ok) return { kind: "unavailable" };
    return { kind: "ok" };
  } catch (networkError) {
    return { kind: "unavailable" };
  }
}

export async function createProject(project) {
  if (!project || typeof project.key !== "string" || typeof project.name !== "string") return { kind: "invalid" };
  return sendJson("/api/v1/projects", { method: "POST", body: project }, (body) => ({ kind: "ok", project: body }));
}

export async function createLinkedTestCase(issueKey, input) {
  if (!input || typeof input.projectKey !== "string") return { kind: "invalid" };
  // The Jira URL belongs to Testryn's persisted integration settings. Reading it
  // here avoids baking one customer's site into the Forge deployment.
  const connection = await sendJson("/api/v1/integrations/jira/connection", { method: "GET" },
    (body) => typeof body?.baseUrl === "string"
      ? { kind: "ok", baseUrl: trimTrailingSlash(body.baseUrl) }
      : { kind: "unavailable" });
  if (connection.kind !== "ok" || !connection.baseUrl) return connection;
  const body = {
    ...input,
    provider: "JIRA",
    externalKey: issueKey,
    url: `${connection.baseUrl}/browse/${encodeURIComponent(issueKey)}`,
  };
  return sendJson("/api/v1/requirement-links/test-cases", { method: "POST", body },
    (created) => ({ kind: "ok", testCase: created }));
}

/**
 * Starts a new Testryn execution for a subset of the test cases linked to the
 * current Jira issue (ADR 0016 extension / Task 2). Jira stays read-only.
 *
 * Reuses the existing provider-neutral ad-hoc execution endpoint
 * `POST /api/v1/projects/{projectKey}/executions` unchanged -- that endpoint
 * already builds an immutable snapshot pinned to each test case's current version
 * (ADR 0003), so no Forge-specific path bypasses the snapshot/versioning rules and
 * no new backend API is needed.
 *
 * Trust model matches updateTestCaseDefinition: the browser only supplies test
 * case ids; every id is re-checked against this issue's own coverage set (read
 * server-side from the trusted invocation context) before anything is created, and
 * the project is derived from that verified coverage, never from a browser value.
 * A selection spanning more than one project is rejected -- the ad-hoc endpoint is
 * single-project by design.
 */
export async function startExecution(issueKey, input) {
  const requestedIds = Array.isArray(input?.testCaseIds) ? input.testCaseIds : [];
  if (requestedIds.length === 0 || !requestedIds.every((id) => typeof id === "string")) {
    return { kind: "invalid" };
  }

  const coverage = await getCoverage(issueKey);
  if (coverage.kind !== "ok") return coverage;

  const linkedById = new Map(coverage.testCases.map((testCase) => [testCase.id, testCase]));
  const selected = [];
  for (const id of requestedIds) {
    const match = linkedById.get(id);
    if (!match) return { kind: "invalid" };
    selected.push(match);
  }

  const projectKeys = [...new Set(selected.map((testCase) => testCase.projectKey))];
  if (projectKeys.length !== 1 || !projectKeys[0]) {
    return { kind: "invalid", reason: "multiProject" };
  }
  const projectKey = projectKeys[0];

  const baseUrl = trimTrailingSlash(process.env.TESTRYN_API_BASE_URL);
  if (!baseUrl) return { kind: "unavailable" };
  const token = process.env.TESTRYN_API_TOKEN;
  const name = typeof input?.name === "string" && input.name.trim() ? input.name.trim() : null;

  try {
    const response = await fetch(`${baseUrl}/api/v1/projects/${encodeURIComponent(projectKey)}/executions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ name, testCaseIds: requestedIds }),
    });
    if (response.status === 401 || response.status === 403) return { kind: "unauthorized" };
    if (response.status === 400 || response.status === 404) return { kind: "invalid" };
    if (!response.ok) return { kind: "unavailable" };
    let body;
    try {
      body = await response.json();
    } catch (parseError) {
      return { kind: "unavailable" };
    }
    if (!body || typeof body.id !== "string") return { kind: "unavailable" };
    return { kind: "ok", executionId: body.id, executionName: body.name ?? null };
  } catch (networkError) {
    return { kind: "unavailable" };
  }
}

async function sendJson(path, request, onSuccess) {
  const baseUrl = trimTrailingSlash(process.env.TESTRYN_API_BASE_URL);
  if (!baseUrl) return { kind: "unavailable" };
  const token = process.env.TESTRYN_API_TOKEN;
  try {
    const response = await fetch(`${baseUrl}${path}`, {
      method: request.method,
      headers: {
        ...(request.body ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...(request.body ? { body: JSON.stringify(request.body) } : {}),
    });
    if (response.status === 401 || response.status === 403) return { kind: "unauthorized" };
    if (response.status === 400 || response.status === 409) return { kind: "invalid" };
    if (!response.ok) return { kind: "unavailable" };
    const body = await response.json();
    return onSuccess(body);
  } catch (ignored) {
    return { kind: "unavailable" };
  }
}

function validDefinition(value) {
  return value && Number.isInteger(value.expectedVersion) && value.expectedVersion > 0
    && typeof value.title === "string" && value.title.trim().length > 0
    && (value.description == null || typeof value.description === "string")
    && (value.preconditions == null || typeof value.preconditions === "string")
    && Array.isArray(value.steps) && value.steps.length > 0
    && value.steps.every((step) => typeof step.action === "string" && step.action.trim().length > 0
      && (step.inputData == null || typeof step.inputData === "string")
      && typeof step.expectedResult === "string" && step.expectedResult.trim().length > 0);
}

function trimTrailingSlash(value) {
  if (!value) {
    return "";
  }
  return value.endsWith("/") ? value.slice(0, -1) : value;
}
