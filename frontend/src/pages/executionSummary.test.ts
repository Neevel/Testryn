import { describe, expect, it } from "vitest";
import type { ExecutionResultStatus, ExecutionStep, ExecutionTestCase } from "../api/types";
import { summarize } from "./executionSummary";

function step(status: ExecutionResultStatus | null, order = 1): ExecutionStep {
  return {
    order,
    action: "Do it",
    expectedResult: "Works",
    result:
      status === null
        ? null
        : {
            id: crypto.randomUUID(),
            status,
            actualResult: null,
            comment: null,
            failureDetails: null,
            executedAt: null,
            executor: null,
          },
  };
}

function testCase(status: ExecutionResultStatus, steps: ExecutionStep[] = []): ExecutionTestCase {
  return {
    testCaseId: crypto.randomUUID(),
    testCaseHumanId: "TC-1",
    testCaseVersionNumber: 1,
    title: "Some test",
    description: null,
    preconditions: null,
    steps,
    position: 1,
    result: {
      id: crypto.randomUUID(),
      status,
      comment: null,
      durationMs: null,
      executedAt: null,
      executor: null,
      actualResult: null,
      failureDetails: null,
    },
  };
}

describe("summarize", () => {
  it("counts every status and totals correctly", () => {
    const summary = summarize([
      testCase("PASSED"),
      testCase("PASSED"),
      testCase("FAILED"),
      testCase("BLOCKED"),
      testCase("SKIPPED"),
      testCase("NOT_RUN"),
    ]);

    expect(summary.total).toBe(6);
    expect(summary.counts).toEqual({ NOT_RUN: 1, PASSED: 2, FAILED: 1, BLOCKED: 1, SKIPPED: 1 });
  });

  it("progress is 0% when every test case is still NOT_RUN", () => {
    const summary = summarize([testCase("NOT_RUN"), testCase("NOT_RUN")]);
    expect(summary.progress).toBe(0);
  });

  it("progress is 100% once nothing is NOT_RUN anymore, regardless of pass/fail", () => {
    const summary = summarize([testCase("PASSED"), testCase("FAILED"), testCase("BLOCKED")]);
    expect(summary.progress).toBe(100);
  });

  it("rounds partial progress to the nearest whole percent", () => {
    // 1 of 3 executed -> 33.33% -> rounds to 33
    const summary = summarize([testCase("PASSED"), testCase("NOT_RUN"), testCase("NOT_RUN")]);
    expect(summary.progress).toBe(33);
  });

  it("does not divide by zero for an empty execution", () => {
    const summary = summarize([]);
    expect(summary.total).toBe(0);
    expect(summary.progress).toBe(0);
  });

  it("steps is null when no test case has any step-level data (pre-ADR-0015 execution)", () => {
    const summary = summarize([testCase("PASSED", [step(null), step(null, 2)])]);
    expect(summary.steps).toBeNull();
  });

  it("counts real step-level progress across test cases that have it", () => {
    const summary = summarize([
      testCase("NOT_RUN", [step("PASSED", 1), step("PASSED", 2), step("NOT_RUN", 3)]),
      testCase("NOT_RUN", [step("FAILED", 1)]),
    ]);
    expect(summary.steps).toEqual({ total: 4, executed: 3 });
  });
});
