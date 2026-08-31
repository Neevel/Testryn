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
