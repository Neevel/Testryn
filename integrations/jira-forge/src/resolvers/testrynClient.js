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

function trimTrailingSlash(value) {
  if (!value) {
    return "";
  }
  return value.endsWith("/") ? value.slice(0, -1) : value;
}
