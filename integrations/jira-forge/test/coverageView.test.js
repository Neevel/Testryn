import {
  executorLabel,
  getFailedOrBlockedSteps,
  isAttentionStatus,
  markLinkable,
  orderByAttention,
  rankOf,
  summarizeCoverage,
  summarizeSteps,
  stepsForDisplay,
  toggleExpanded,
  truncate,
} from "../src/frontend/coverageView";

function stepResult(status, overrides = {}) {
  return { status, actualResult: null, failureDetails: null, comment: null, ...overrides };
}

function step(position, status, overrides = {}) {
  return { position, action: `Step ${position}`, expectedResult: "ok", result: stepResult(status, overrides) };
}

function testCase(id, latestExecution) {
  return { id, humanId: `TC-${id}`, title: "Test", status: "ACTIVE", priority: "MEDIUM", version: 1,
    preconditions: null, steps: [], latestExecution };
}

describe("isAttentionStatus", () => {
  test("FAILED and BLOCKED need attention", () => {
    expect(isAttentionStatus("FAILED")).toBe(true);
    expect(isAttentionStatus("BLOCKED")).toBe(true);
  });

  test("PASSED, SKIPPED, NOT_RUN do not", () => {
    expect(isAttentionStatus("PASSED")).toBe(false);
    expect(isAttentionStatus("SKIPPED")).toBe(false);
    expect(isAttentionStatus("NOT_RUN")).toBe(false);
  });
});

describe("accordion state", () => {
  test("starts collapsed and expands then collapses one test case", () => {
    const initial = new Set();
    const expanded = toggleExpanded(initial, "tc-1");
    expect(initial.size).toBe(0);
    expect(expanded.has("tc-1")).toBe(true);
    expect(toggleExpanded(expanded, "tc-1").has("tc-1")).toBe(false);
  });

  test("allows multiple test cases to remain expanded", () => {
    const one = toggleExpanded(new Set(), "tc-1");
    const two = toggleExpanded(one, "tc-2");
    expect([...two]).toEqual(["tc-1", "tc-2"]);
  });
});

describe("stepsForDisplay", () => {
  const currentV2 = [
    { order: 1, action: "Current v2 action", expectedResult: "Current v2 expected" },
    { order: 2, action: "New v2 step", expectedResult: "New" },
  ];

  test("uses the pinned execution snapshot instead of mixing in the current version", () => {
    const pinnedV1 = [{ position: 1, action: "Pinned v1 action", expectedResult: "Pinned v1 expected", result: stepResult("FAILED") }];
    expect(stepsForDisplay(currentV2, pinnedV1)).toEqual(pinnedV1);
  });

  test("normalizes current definition order when there are no reported step results", () => {
    expect(stepsForDisplay(currentV2, null)).toEqual([
      { ...currentV2[0], position: 1, result: null },
      { ...currentV2[1], position: 2, result: null },
    ]);
  });

  test("uses the current definition for JUnit or legacy executions with an empty step result list", () => {
    expect(stepsForDisplay(currentV2, []).map((step) => step.position)).toEqual([1, 2]);
  });
});

describe("summarizeSteps", () => {
  test("null for no step data at all (JUnit/legacy execution)", () => {
    expect(summarizeSteps(null)).toBeNull();
    expect(summarizeSteps([])).toBeNull();
  });

  test("counts every status bucket, including NOT_RUN and BLOCKED and SKIPPED distinctly", () => {
    const summary = summarizeSteps([
      step(1, "PASSED"),
      step(2, "PASSED"),
      step(3, "FAILED"),
      step(4, "NOT_RUN"),
      step(5, "BLOCKED"),
      step(6, "SKIPPED"),
    ]);
    expect(summary).toEqual({ passed: 2, failed: 1, blocked: 1, skipped: 1, notRun: 1 });
  });
});

describe("getFailedOrBlockedSteps", () => {
  test("empty for no step data", () => {
    expect(getFailedOrBlockedSteps(null)).toEqual([]);
  });

  test("returns only FAILED and BLOCKED, preserving order, never PASSED/SKIPPED/NOT_RUN", () => {
    const steps = [step(1, "PASSED"), step(2, "FAILED"), step(3, "NOT_RUN"), step(4, "BLOCKED"), step(5, "SKIPPED")];
    const result = getFailedOrBlockedSteps(steps);
    expect(result.map((s) => s.position)).toEqual([2, 4]);
  });
});

describe("truncate", () => {
  test("short text is not truncated", () => {
    const result = truncate("short", 160);
    expect(result).toEqual({ shown: "short", isTruncated: false });
  });

  test("long text is truncated but the caller can still access the full original text", () => {
    const long = "x".repeat(500);
    const result = truncate(long, 160);
    expect(result.isTruncated).toBe(true);
    expect(result.shown.length).toBe(161); // 160 chars + ellipsis
    expect(result.shown.startsWith("x".repeat(160))).toBe(true);
    // truncate() itself never discards the original -- callers keep `text` around
    // and only substitute `result.shown` for the collapsed display (Abschnitt 14).
    expect(long.length).toBe(500);
  });

  test("null/empty text is handled without throwing", () => {
    expect(truncate(null, 160)).toEqual({ shown: null, isTruncated: false });
    expect(truncate("", 160)).toEqual({ shown: "", isTruncated: false });
  });
});

describe("executorLabel", () => {
  test("'ci' in any casing reads as CI", () => {
    expect(executorLabel("ci")).toBe("CI");
    expect(executorLabel("CI")).toBe("CI");
    expect(executorLabel(" Ci ")).toBe("CI");
  });

  test("any other non-empty executor reads as Manual", () => {
    expect(executorLabel("marcel.jeske")).toBe("Manual");
    expect(executorLabel("jenkins-agent-3")).toBe("Manual");
  });

  test("no executor at all is never guessed", () => {
    expect(executorLabel(null)).toBeNull();
    expect(executorLabel("")).toBeNull();
  });
});

describe("orderByAttention / rankOf", () => {
  test("FAILED and BLOCKED sort before NOT_RUN, no-execution, PASSED, and SKIPPED", () => {
    const failed = testCase("failed", { status: "FAILED", steps: [] });
    const blocked = testCase("blocked", { status: "BLOCKED", steps: [] });
    const notRun = testCase("notRun", { status: "NOT_RUN", steps: [] });
    const noExecution = testCase("noExecution", null);
    const passed = testCase("passed", { status: "PASSED", steps: [] });
    const skipped = testCase("skipped", { status: "SKIPPED", steps: [] });

    const ordered = orderByAttention([skipped, passed, noExecution, notRun, blocked, failed]);

    expect(ordered.map((tc) => tc.id)).toEqual(["failed", "blocked", "notRun", "noExecution", "passed", "skipped"]);
  });

  test("does not mutate the input array (a pure sort)", () => {
    const original = [testCase("a", { status: "PASSED" }), testCase("b", { status: "FAILED" })];
    const originalOrder = original.map((tc) => tc.id);

    orderByAttention(original);

    expect(original.map((tc) => tc.id)).toEqual(originalOrder);
  });

  test("a test case with no execution ranks between NOT_RUN and PASSED", () => {
    const noExecutionRank = rankOf(testCase("x", null));
    const notRunRank = rankOf(testCase("x", { status: "NOT_RUN" }));
    const passedRank = rankOf(testCase("x", { status: "PASSED" }));
    expect(notRunRank).toBeLessThan(noExecutionRank);
    expect(noExecutionRank).toBeLessThan(passedRank);
  });
});

describe("markLinkable", () => {
  const hits = [{ id: "tc-1", humanId: "EVAL-TC-1" }, { id: "tc-2", humanId: "EVAL-TC-2" }, { id: "tc-3", humanId: "EVAL-TC-3" }];

  test("flags search hits that already cover this issue", () => {
    const marked = markLinkable(hits, ["tc-2"]);
    expect(marked.map((tc) => tc.alreadyLinked)).toEqual([false, true, false]);
  });

  test("nothing linked yet leaves every hit linkable", () => {
    expect(markLinkable(hits, []).every((tc) => tc.alreadyLinked === false)).toBe(true);
    expect(markLinkable(hits, undefined).every((tc) => tc.alreadyLinked === false)).toBe(true);
  });

  test("null/empty search results do not throw", () => {
    expect(markLinkable(null, ["tc-1"])).toEqual([]);
    expect(markLinkable([], ["tc-1"])).toEqual([]);
  });

  test("does not mutate the input rows", () => {
    const original = [{ id: "tc-1" }];
    markLinkable(original, ["tc-1"]);
    expect(original[0]).toEqual({ id: "tc-1" });
  });
});

describe("summarizeCoverage", () => {
  test("folds 'no execution yet' into the same bucket as NOT_RUN", () => {
    const counts = summarizeCoverage([
      testCase("a", { status: "PASSED" }),
      testCase("b", null),
      testCase("c", { status: "NOT_RUN" }),
    ]);
    expect(counts).toEqual({ PASSED: 1, NOT_RUN: 2 });
  });

  test("counts every real status present, nothing fabricated", () => {
    const counts = summarizeCoverage([
      testCase("a", { status: "FAILED" }),
      testCase("b", { status: "FAILED" }),
      testCase("c", { status: "BLOCKED" }),
      testCase("d", { status: "SKIPPED" }),
    ]);
    expect(counts).toEqual({ FAILED: 2, BLOCKED: 1, SKIPPED: 1 });
  });

  test("empty list yields empty counts", () => {
    expect(summarizeCoverage([])).toEqual({});
  });
});
