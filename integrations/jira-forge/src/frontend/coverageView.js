/**
 * Pure, framework-free helpers behind the panel's "summary first, failure first,
 * details on demand" rendering decisions (Forge Panel UX Refinement block).
 * Deliberately separated from TestCaseCard.jsx/App.jsx: UI Kit components compile
 * to Forge's own bridge protocol, not real DOM, so they cannot be meaningfully
 * rendered/asserted against in Jest -- but the DATA decisions that drive what gets
 * rendered (which steps count as "needs attention", how test cases are ordered,
 * how long text gets truncated) are plain JS and fully testable here.
 */

export function isAttentionStatus(status) {
  return status === "FAILED" || status === "BLOCKED";
}

/** `null` when the execution has no step-level data at all (Abschnitt 30: a
 * pre-ADR-0015 execution, or a JUnit/testcase-level-only automation result) --
 * distinct from an execution whose steps are all legitimately NOT_RUN. */
export function summarizeSteps(steps) {
  if (!steps || steps.length === 0) return null;
  const summary = { passed: 0, failed: 0, blocked: 0, skipped: 0, notRun: 0 };
  for (const step of steps) {
    switch (step.result.status) {
      case "PASSED": summary.passed += 1; break;
      case "FAILED": summary.failed += 1; break;
      case "BLOCKED": summary.blocked += 1; break;
      case "SKIPPED": summary.skipped += 1; break;
      default: summary.notRun += 1;
    }
  }
  return summary;
}

/** Abschnitt 5: FAILED steps, and BLOCKED ones too -- never PASSED/SKIPPED/NOT_RUN
 * unless explicitly requested via "Show all steps" instead. */
export function getFailedOrBlockedSteps(steps) {
  if (!steps) return [];
  return steps.filter((s) => isAttentionStatus(s.result.status));
}

/** Abschnitt 13/14: never drops data, only how much is shown by default. */
export function truncate(text, maxLength) {
  if (!text || text.length <= maxLength) {
    return { shown: text, isTruncated: false };
  }
  return { shown: text.slice(0, maxLength) + "…", isTruncated: true };
}

/** Abschnitt 34: "ci" (any casing, matching the publisher's own default, ADR 0011)
 * reads as "CI"; any other non-empty executor reads as "Manual" -- Testryn does not
 * track individual tester identity beyond the free-text executor field, so this
 * stays a plain yes/no, never a guess when no executor was reported at all. */
export function executorLabel(executor) {
  if (!executor) return null;
  return executor.trim().toLowerCase() === "ci" ? "CI" : "Manual";
}

/**
 * Abschnitt 24: FAILED and BLOCKED first (a reviewer needs to see these), then
 * NOT_RUN and "no execution yet" together (neither is "known good"), then PASSED
 * and SKIPPED last (no immediate attention needed). Purely a Forge-side render
 * order for the current page of results -- never touches the coverage API's own
 * order, `totalCount`, or pagination math, so no fachliche Reihenfolge elsewhere
 * (e.g. the API's own link-creation-order) is altered.
 */
const ATTENTION_RANK = { FAILED: 0, BLOCKED: 1, NOT_RUN: 2, PASSED: 4, SKIPPED: 5 };
const NO_EXECUTION_RANK = 3;

export function rankOf(testCase) {
  if (!testCase.latestExecution) return NO_EXECUTION_RANK;
  return ATTENTION_RANK[testCase.latestExecution.status] ?? NO_EXECUTION_RANK;
}

export function orderByAttention(testCases) {
  return [...testCases].sort((a, b) => rankOf(a) - rankOf(b));
}

/** Abschnitt 9: a test case with no execution at all is folded into the same
 * "not run" bucket as a NOT_RUN execution result -- both mean "nothing known yet"
 * from a reviewer's point of view. */
export function summarizeCoverage(testCases) {
  const counts = {};
  for (const tc of testCases) {
    const status = tc.latestExecution ? tc.latestExecution.status : "NOT_RUN";
    counts[status] = (counts[status] ?? 0) + 1;
  }
  return counts;
}

export function formatDate(isoString) {
  if (!isoString) {
    return "not yet run";
  }
  try {
    return new Date(isoString).toLocaleDateString(undefined, { day: "2-digit", month: "short", year: "numeric" });
  } catch (e) {
    return isoString;
  }
}
