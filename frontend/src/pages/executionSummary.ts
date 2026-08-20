import type { ExecutionResultStatus, ExecutionTestCase } from "../api/types";

export interface ExecutionSummary {
  counts: Record<ExecutionResultStatus, number>;
  total: number;
  /** Percentage of test cases that have left NOT_RUN, rounded to a whole number. */
  progress: number;
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
  return { counts, total, progress };
}
