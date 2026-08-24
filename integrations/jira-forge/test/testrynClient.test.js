import { fetch } from "@forge/api";
import { getCoverage } from "../src/resolvers/testrynClient";

jest.mock("@forge/api", () => ({
  fetch: jest.fn(),
}));

describe("testrynClient.getCoverage", () => {
  const REAL_ENV = process.env;

  beforeEach(() => {
    jest.resetAllMocks();
    process.env = {
      ...REAL_ENV,
      TESTRYN_API_BASE_URL: "https://testryn.example.com",
      TESTRYN_APP_BASE_URL: "https://testryn.example.com",
      TESTRYN_API_TOKEN: "testryn_secret_token_value_that_must_never_leak",
    };
  });

  afterAll(() => {
    process.env = REAL_ENV;
  });

  function jsonResponse(status, body) {
    return {
      ok: status >= 200 && status < 300,
      status,
      json: async () => body,
    };
  }

  test("builds the correct request URL and Authorization header", async () => {
    fetch.mockResolvedValue(jsonResponse(200, { requirement: { provider: "JIRA", externalKey: "EVAL-47" }, testCases: [], totalCount: 0 }));

    await getCoverage("EVAL-47");

    expect(fetch).toHaveBeenCalledTimes(1);
    const [url, options] = fetch.mock.calls[0];
    expect(url).toBe(
        "https://testryn.example.com/api/v1/requirement-links/coverage?provider=jira&externalKey=EVAL-47&limit=20");
    expect(options.headers.Authorization).toBe("Bearer testryn_secret_token_value_that_must_never_leak");
  });

  test("a successful response with test cases is returned with kind 'ok'", async () => {
    const testCases = [
      { id: "tc-1", humanId: "BIT-TC-14", title: "Successful login", status: "ACTIVE", version: 2, steps: [], latestExecution: null },
    ];
    fetch.mockResolvedValue(jsonResponse(200,
        { requirement: { provider: "JIRA", externalKey: "EVAL-47" }, testCases, totalCount: 1 }));

    const result = await getCoverage("EVAL-47");

    expect(result.kind).toBe("ok");
    expect(result.testCases).toEqual(testCases);
    expect(result.totalCount).toBe(1);
    expect(result.appBaseUrl).toBe("https://testryn.example.com");
  });

  test("an empty testCases array is still a successful 'ok' response", async () => {
    fetch.mockResolvedValue(jsonResponse(200,
        { requirement: { provider: "JIRA", externalKey: "EVAL-999" }, testCases: [], totalCount: 0 }));

    const result = await getCoverage("EVAL-999");

    expect(result.kind).toBe("ok");
    expect(result.testCases).toEqual([]);
    expect(result.totalCount).toBe(0);
  });

  test("a 401 from Testryn is surfaced as 'unauthorized', not the raw status", async () => {
    fetch.mockResolvedValue(jsonResponse(401, { code: "UNAUTHORIZED", message: "Authentication is required." }));

    const result = await getCoverage("EVAL-47");

    expect(result).toEqual({ kind: "unauthorized" });
  });

  test("a 403 from Testryn is also surfaced as 'unauthorized'", async () => {
    fetch.mockResolvedValue(jsonResponse(403, { code: "FORBIDDEN", message: "The service token does not have the required scope." }));

    const result = await getCoverage("EVAL-47");

    expect(result).toEqual({ kind: "unauthorized" });
  });

  test("a network failure (Testryn unreachable) is surfaced as 'unavailable'", async () => {
    fetch.mockRejectedValue(new Error("ECONNREFUSED 127.0.0.1:8080"));

    const result = await getCoverage("EVAL-47");

    expect(result).toEqual({ kind: "unavailable" });
  });

  test("an unexpected 5xx from Testryn is surfaced as 'unavailable'", async () => {
    fetch.mockResolvedValue({ ok: false, status: 502, json: async () => { throw new Error("no body"); } });

    const result = await getCoverage("EVAL-47");

    expect(result).toEqual({ kind: "unavailable" });
  });

  test("a malformed (non-JSON) response body is surfaced as 'unavailable', not a crash", async () => {
    fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => { throw new SyntaxError("Unexpected token < in JSON"); },
    });

    const result = await getCoverage("EVAL-47");

    expect(result).toEqual({ kind: "unavailable" });
  });

  test("a response missing the expected shape (no testCases array) is surfaced as 'unavailable'", async () => {
    fetch.mockResolvedValue(jsonResponse(200, { somethingElse: true }));

    const result = await getCoverage("EVAL-47");

    expect(result).toEqual({ kind: "unavailable" });
  });

  test("no base URL configured is surfaced as 'unavailable' without ever calling fetch", async () => {
    process.env.TESTRYN_API_BASE_URL = "";

    const result = await getCoverage("EVAL-47");

    expect(result).toEqual({ kind: "unavailable" });
    expect(fetch).not.toHaveBeenCalled();
  });

  test("the configured token never appears anywhere in the returned result, success or failure", async () => {
    const token = "testryn_secret_token_value_that_must_never_leak";
    fetch.mockResolvedValue(jsonResponse(200,
        { requirement: { provider: "JIRA", externalKey: "EVAL-47" }, testCases: [], totalCount: 0 }));

    const okResult = await getCoverage("EVAL-47");
    expect(JSON.stringify(okResult)).not.toContain(token);

    fetch.mockResolvedValue(jsonResponse(401, {}));
    const authResult = await getCoverage("EVAL-47");
    expect(JSON.stringify(authResult)).not.toContain(token);

    fetch.mockRejectedValue(new Error(`failed while using token ${token}`));
    const unavailableResult = await getCoverage("EVAL-47");
    expect(JSON.stringify(unavailableResult)).not.toContain(token);
  });

  test("works without a configured token (missing Authorization header, not a crash)", async () => {
    delete process.env.TESTRYN_API_TOKEN;
    fetch.mockResolvedValue(jsonResponse(200,
        { requirement: { provider: "JIRA", externalKey: "EVAL-47" }, testCases: [], totalCount: 0 }));

    await getCoverage("EVAL-47");

    const [, options] = fetch.mock.calls[0];
    expect(options.headers.Authorization).toBeUndefined();
  });
});
