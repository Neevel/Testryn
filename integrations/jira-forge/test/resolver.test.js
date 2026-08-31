import {
  createLinkedTestCase,
  createProject,
  getCoverage,
  getProjects,
  linkExistingTestCase,
  searchTestCases,
  startExecution,
  updateTestCaseDefinition,
} from "../src/resolvers/testrynClient";

jest.mock("../src/resolvers/testrynClient", () => ({
  getCoverage: jest.fn(),
  updateTestCaseDefinition: jest.fn(),
  getProjects: jest.fn(),
  createProject: jest.fn(),
  createLinkedTestCase: jest.fn(),
  searchTestCases: jest.fn(),
  linkExistingTestCase: jest.fn(),
  startExecution: jest.fn(),
}));

// The real @forge/resolver package works fine standalone (no Forge runtime needed
// to construct a Resolver and call its handler directly), so this exercises the
// actual library, not a hand-rolled substitute.
import { handler } from "../src/resolvers/index";

describe("resolvers/index handler", () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  test("extracts the issue key from the invocation context and delegates to getCoverage", async () => {
    getCoverage.mockResolvedValue({ kind: "ok", testCases: [], totalCount: 0, appBaseUrl: "https://testryn.example.com" });

    const response = await handler({
      call: { functionKey: "getCoverage", payload: {} },
      context: { extension: { issue: { key: "EVAL-47" } } },
    });

    expect(getCoverage).toHaveBeenCalledWith("EVAL-47");
    expect(response).toEqual({ kind: "ok", testCases: [], totalCount: 0, appBaseUrl: "https://testryn.example.com" });
  });

  test("a missing issue key in the context does not call Testryn and returns 'unavailable'", async () => {
    const response = await handler({
      call: { functionKey: "getCoverage", payload: {} },
      context: { extension: {} },
    });

    expect(getCoverage).not.toHaveBeenCalled();
    expect(response).toEqual({ kind: "unavailable" });
  });

  test("a completely missing extension context is handled the same way, not a crash", async () => {
    const response = await handler({
      call: { functionKey: "getCoverage", payload: {} },
      context: {},
    });

    expect(getCoverage).not.toHaveBeenCalled();
    expect(response).toEqual({ kind: "unavailable" });
  });

  test("definition updates use the trusted issue context", async () => {
    const definition = { expectedVersion: 2, title: "Edited", preconditions: "Ready", steps: [{ action: "A", expectedResult: "B" }] };
    updateTestCaseDefinition.mockResolvedValue({ kind: "ok" });
    const response = await handler({
      call: { functionKey: "updateTestCaseDefinition", payload: { testCaseId: "tc-1", definition } },
      context: { extension: { issue: { key: "EVAL-47" } } },
    });
    expect(updateTestCaseDefinition).toHaveBeenCalledWith("EVAL-47", "tc-1", definition);
    expect(response).toEqual({ kind: "ok" });
  });

  test("linked creation injects the trusted issue key", async () => {
    const testCase = { projectKey: "EVAL", title: "New test", steps: [{ action: "A", expectedResult: "B" }] };
    createLinkedTestCase.mockResolvedValue({ kind: "ok" });
    const response = await handler({
      call: { functionKey: "createLinkedTestCase", payload: { testCase } },
      context: { extension: { issue: { key: "EVAL-48" } } },
    });
    expect(createLinkedTestCase).toHaveBeenCalledWith("EVAL-48", testCase);
    expect(response).toEqual({ kind: "ok" });
  });

  test("test case search passes the project/query payload straight through", async () => {
    searchTestCases.mockResolvedValue({ kind: "ok", testCases: [], totalCount: 0 });
    const response = await handler({
      call: { functionKey: "searchTestCases", payload: { projectKey: "EVAL", query: "login" } },
      context: { extension: { issue: { key: "EVAL-47" } } },
    });
    expect(searchTestCases).toHaveBeenCalledWith("EVAL", "login");
    expect(response).toEqual({ kind: "ok", testCases: [], totalCount: 0 });
  });

  test("linking an existing test case injects the trusted issue key", async () => {
    linkExistingTestCase.mockResolvedValue({ kind: "ok" });
    const response = await handler({
      call: { functionKey: "linkExistingTestCase", payload: { testCaseId: "tc-1" } },
      context: { extension: { issue: { key: "EVAL-47" } } },
    });
    expect(linkExistingTestCase).toHaveBeenCalledWith("EVAL-47", "tc-1");
    expect(response).toEqual({ kind: "ok" });
  });

  test("linking is rejected as invalid when the issue context is missing, without calling Testryn", async () => {
    const response = await handler({
      call: { functionKey: "linkExistingTestCase", payload: { testCaseId: "tc-1" } },
      context: {},
    });
    expect(linkExistingTestCase).not.toHaveBeenCalled();
    expect(response).toEqual({ kind: "invalid" });
  });

  test("linking is rejected as invalid when the browser sends no test case id", async () => {
    const response = await handler({
      call: { functionKey: "linkExistingTestCase", payload: {} },
      context: { extension: { issue: { key: "EVAL-47" } } },
    });
    expect(linkExistingTestCase).not.toHaveBeenCalled();
    expect(response).toEqual({ kind: "invalid" });
  });

  test("starting an execution injects the trusted issue key and forwards the selection", async () => {
    startExecution.mockResolvedValue({ kind: "ok", executionId: "exec-1", executionName: "Iteration 1" });
    const response = await handler({
      call: { functionKey: "startExecution", payload: { testCaseIds: ["tc-1", "tc-2"], name: "Smoke" } },
      context: { extension: { issue: { key: "EVAL-47" } } },
    });
    expect(startExecution).toHaveBeenCalledWith("EVAL-47", { testCaseIds: ["tc-1", "tc-2"], name: "Smoke" });
    expect(response).toEqual({ kind: "ok", executionId: "exec-1", executionName: "Iteration 1" });
  });

  test("starting an execution is rejected as invalid with no issue context, without calling Testryn", async () => {
    const response = await handler({
      call: { functionKey: "startExecution", payload: { testCaseIds: ["tc-1"] } },
      context: {},
    });
    expect(startExecution).not.toHaveBeenCalled();
    expect(response).toEqual({ kind: "invalid" });
  });

  test("starting an execution is rejected as invalid when the browser sends no test case ids", async () => {
    const response = await handler({
      call: { functionKey: "startExecution", payload: { testCaseIds: [] } },
      context: { extension: { issue: { key: "EVAL-47" } } },
    });
    expect(startExecution).not.toHaveBeenCalled();
    expect(response).toEqual({ kind: "invalid" });
  });

  test("project operations delegate without accepting an issue key from the browser", async () => {
    getProjects.mockResolvedValue({ kind: "ok", projects: [] });
    createProject.mockResolvedValue({ kind: "ok", project: { key: "EVAL" } });
    expect(await handler({ call: { functionKey: "getProjects", payload: {} }, context: {} })).toEqual({ kind: "ok", projects: [] });
    expect(await handler({ call: { functionKey: "createProject", payload: { project: { key: "EVAL", name: "Evaluation" } } }, context: {} })).toEqual({ kind: "ok", project: { key: "EVAL" } });
  });
});
