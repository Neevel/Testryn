import type { ExecutionResultStatus, ExecutionTestCase } from "../api/types";

export interface ExecutionSummary {
  counts: Record<ExecutionResultStatus, number>;
  total: number;
  /** Percentage of test cases that have left NOT_RUN, rounded to a whole number. */
  progress: number;
  /** Step-level progress across every test case that HAS step-level data (ADR
   * 0015). `null` when none of the test cases in this execution have any --
   * an execution created before the Step-Level Execution Results block, or one
   * whose data was never backfilled (Abschnitt 37: never fabricated). */
  steps: { total: number; executed: number } | null;
}

export function summarize(testCases: ExecutionTestCase[]): ExecutionSummary {
  const counts: Record<ExecutionResultStatus, number> = {
    NOT_RUN: 0,
    PASSED: 0,
    FAILED: 0,
    SKIPPED: 0,
    BLOCKED: 0,
  };
  for (const etc of testCases) {
    counts[etc.result.status] += 1;
  }
  const total = testCases.length;
  const executed = total - counts.NOT_RUN;
  const progress = total === 0 ? 0 : Math.round((executed / total) * 100);

  let stepTotal = 0;
  let stepExecuted = 0;
  let anyStepData = false;
  for (const etc of testCases) {
    for (const step of etc.steps) {
      if (step.result === null) continue;
      anyStepData = true;
      stepTotal += 1;
      if (step.result.status !== "NOT_RUN") stepExecuted += 1;
    }
  }
  const steps = anyStepData ? { total: stepTotal, executed: stepExecuted } : null;

  return { counts, total, progress, steps };
}
