import React, { useState } from "react";
import { Box, Button, Icon, Inline, Link, Lozenge, Stack, Text } from "@forge/react";
import { statusMeta } from "./statusMeta";

const INITIAL_STEP_LIMIT = 5;

/**
 * One linked test case, collapsed by default (Abschnitt 12/28: not all steps open
 * for every test case at once). Expanding shows preconditions and, if the latest
 * execution has step-level data, every step's actual outcome (Abschnitt 29) --
 * collapsing hides them again. Purely local UI state either way: the coverage
 * response already includes everything, so expanding never triggers another
 * Testryn request (Abschnitt 22).
 */
export function TestCaseCard({ testCase, appBaseUrl }) {
  const [expanded, setExpanded] = useState(false);
  const latest = testCase.latestExecution;
  const meta = latest ? statusMeta(latest.status) : null;
  const stepSummary = summarizeSteps(latest);

  return (
    <Box padding="space.100">
      <Stack space="space.075">
        <Button appearance="subtle" onClick={() => setExpanded(!expanded)}>
          {(expanded ? "▾ " : "▸ ") + testCase.humanId + "  " + testCase.title}
        </Button>

        <Text size="small">
          {testCase.status} · v{testCase.version}
          {testCase.priority ? ` · ${testCase.priority}` : ""}
        </Text>

        {latest ? (
          <Stack space="space.025">
            <Inline space="space.050" alignBlock="center">
              <Icon glyph={meta.glyph} label={meta.label} color={meta.color} />
              <Lozenge appearance={meta.appearance}>{meta.label}</Lozenge>
              <Text size="small">Last run: {formatDate(latest.executedAt)}</Text>
              <ExecutorBadge executor={latest.executor} />
            </Inline>
            {stepSummary ? (
              <Text size="small" color="color.text.subtlest">
                {stepSummary.passed} / {stepSummary.total} steps passed
                {stepSummary.failed > 0 ? `, ${stepSummary.failed} failed` : ""}
              </Text>
            ) : null}
          </Stack>
        ) : (
          <Text size="small" color="color.text.subtlest">No execution yet</Text>
        )}

        {expanded ? <TestCaseDetails testCase={testCase} latest={latest} /> : null}

        <Inline space="space.200">
          <Link href={`${appBaseUrl}/test-cases/${testCase.id}`} openNewTab>
            Open in Testryn
          </Link>
          {latest ? (
            <Link href={`${appBaseUrl}/executions/${latest.executionId}`} openNewTab>
              Open latest execution
            </Link>
          ) : null}
        </Inline>
      </Stack>
    </Box>
  );
}

/** Abschnitt 34: real data only -- "ci" (any casing, matching the publisher's own
 * default, ADR 0011) reads as "CI"; any other non-empty executor reads as "Manual"
 * (Testryn does not track individual tester identity beyond the free-text executor
 * field, so this stays a plain yes/no, not a new identity concept, Abschnitt 34). */
function ExecutorBadge({ executor }) {
  if (!executor) return null;
  const label = executor.trim().toLowerCase() === "ci" ? "CI" : "Manual";
  return (
    <Text size="small" color="color.text.subtlest">
      · {label}
    </Text>
  );
}

function TestCaseDetails({ testCase, latest }) {
  const [showAllSteps, setShowAllSteps] = useState(false);
  const steps = latest ? latest.steps : null;
  const hasStepResults = steps && steps.length > 0;
  const hasFailure = hasStepResults && steps.some((s) => s.result && s.result.status === "FAILED");
  // Failure-first UX (Abschnitt 32): never truncate a failed run -- the failing
  // step must be visible the moment the card is expanded, no extra click. A fully
  // green (or not-yet-run) case with many steps still collapses to a short preview.
  const visibleSteps = hasStepResults && !showAllSteps && !hasFailure
      ? steps.slice(0, INITIAL_STEP_LIMIT)
      : steps;

  return (
    <Box padding="space.100">
      <Stack space="space.100">
        {testCase.preconditions ? (
          <Stack space="space.025">
            <Text weight="bold" size="small">Preconditions</Text>
            <Text size="small">{testCase.preconditions}</Text>
          </Stack>
        ) : null}

        {renderStepsSection(testCase, latest, hasStepResults, visibleSteps)}

        {hasStepResults && !hasFailure && !showAllSteps && steps.length > INITIAL_STEP_LIMIT ? (
          <Button appearance="link" onClick={() => setShowAllSteps(true)}>
            Show all {steps.length} steps
          </Button>
        ) : null}
      </Stack>
    </Box>
  );
}

function renderStepsSection(testCase, latest, hasStepResults, visibleSteps) {
  if (hasStepResults) {
    return (
      <Stack space="space.200">
        {visibleSteps.map((step) => (
          <StepDetail key={step.position} step={step} />
        ))}
      </Stack>
    );
  }

  // latest execution exists but carries no step data at all: either an execution
  // from before ADR 0015, or a testcase-level-only automation result (JUnit,
  // Abschnitt 20/36) -- state this plainly rather than showing nothing (Abschnitt
  // 35/36), and fall back to the test case's own current step list as a preview.
  if (latest) {
    return (
      <Stack space="space.100">
        <Text size="small" color="color.text.subtlest">
          Step-level results not reported for this execution.
        </Text>
        {renderPlainSteps(testCase)}
      </Stack>
    );
  }

  return renderPlainSteps(testCase);
}

function renderPlainSteps(testCase) {
  if (!testCase.steps || testCase.steps.length === 0) {
    return <Text size="small" color="color.text.subtlest">No steps recorded.</Text>;
  }
  return (
    <Stack space="space.100">
      {testCase.steps.map((step) => (
        <Stack key={step.order} space="space.025">
          <Text size="small">{step.order}. {step.action}</Text>
          <Text size="small" color="color.text.subtlest">Expected: {step.expectedResult}</Text>
        </Stack>
      ))}
    </Stack>
  );
}

function StepDetail({ step }) {
  const result = step.result;
  const meta = statusMeta(result.status);
  return (
    <Stack space="space.025">
      <Inline space="space.050" alignBlock="center">
        <Icon glyph={meta.glyph} label={meta.label} color={meta.color} />
        <Text weight="bold" size="small">
          Step {step.position} · {meta.label}
        </Text>
      </Inline>
      <Text size="small">
        <Text weight="bold" size="small">Action </Text>
        {step.action}
      </Text>
      <Text size="small">
        <Text weight="bold" size="small">Expected </Text>
        {step.expectedResult}
      </Text>
      {result.actualResult ? (
        <Text size="small">
          <Text weight="bold" size="small">Actual </Text>
          {result.actualResult}
        </Text>
      ) : null}
      {result.failureDetails ? (
        <Text size="small" color="color.text.danger">
          <Text weight="bold" size="small">Failure </Text>
          {result.failureDetails}
        </Text>
      ) : null}
    </Stack>
  );
}

function summarizeSteps(latest) {
  if (!latest || !latest.steps || latest.steps.length === 0) {
    return null;
  }
  const total = latest.steps.length;
  const passed = latest.steps.filter((s) => s.result && s.result.status === "PASSED").length;
  const failed = latest.steps.filter((s) => s.result && s.result.status === "FAILED").length;
  return { total, passed, failed };
}

function formatDate(isoString) {
  if (!isoString) {
    return "not yet run";
  }
  try {
    return new Date(isoString).toLocaleDateString(undefined, { day: "2-digit", month: "short", year: "numeric" });
  } catch (e) {
    return isoString;
  }
}
