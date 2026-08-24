import { getCoverage } from "../src/resolvers/testrynClient";

jest.mock("../src/resolvers/testrynClient", () => ({
  getCoverage: jest.fn(),
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
});
