import React, { useState } from "react";
import { Box, Button, Icon, Inline, Link, Lozenge, Stack, Text } from "@forge/react";
import { statusMeta } from "./statusMeta";
import { executorLabel, formatDate, getFailedOrBlockedSteps, isAttentionStatus, summarizeSteps, truncate } from "./coverageView";

const FAILURE_DETAILS_PREVIEW_LENGTH = 160;
const PRECONDITIONS_PREVIEW_LENGTH = 160;

/**
 * One linked test case (Forge Panel UX Refinement block). "Summary first, failure
 * first, details on demand": the compact summary below is always fully visible with
 * zero clicks -- id/title, status/priority/version, execution source/date, step
 * counts, and (if the run failed or was blocked) an inline mini-summary of the
 * first such step, with no separate "expand" click needed to see what actually
 * broke. Full step detail (either "failed only" or "all steps") is opt-in via
 * explicit buttons, and collapses cleanly back to the summary. Nothing here ever
 * triggers another Testryn request -- the coverage response already has everything.
 */
export function TestCaseCard({ testCase, appBaseUrl }) {
  const [mode, setMode] = useState("summary"); // "summary" | "failedOnly" | "all"
  const latest = testCase.latestExecution;
  const meta = latest ? statusMeta(latest.status) : null;
  const steps = latest && latest.steps && latest.steps.length > 0 ? latest.steps : null;
  const stepSummary = summarizeSteps(steps);
  const failedOrBlocked = getFailedOrBlockedSteps(steps);
  const firstFailure = failedOrBlocked[0] ?? null;

  return (
    <Box padding="space.100">
      <Stack space="space.100">
        <CardHeader testCase={testCase} />

        {latest ? (
          <Stack space="space.050">
            <Inline space="space.050" alignBlock="center">
              <Icon glyph={meta.glyph} label={meta.label} color={meta.color} />
              <Lozenge appearance={meta.appearance}>{meta.label}</Lozenge>
            </Inline>
            <Inline space="space.050" alignBlock="center">
              <ExecutorLabel executor={latest.executor} />
              <Text size="small" color="color.text.subtlest">{formatDate(latest.executedAt)}</Text>
            </Inline>
            {latest.executionName ? (
              <Text size="small" color="color.text.subtlest">{latest.executionName}</Text>
            ) : null}
            {stepSummary ? <StepCountsLine summary={stepSummary} /> : (
              <Text size="small" color="color.text.subtlest">
                Step-level results not reported by this automation source.
              </Text>
            )}
          </Stack>
        ) : (
          <Text size="small" color="color.text.subtlest">No execution yet</Text>
        )}

        {mode === "summary" && firstFailure ? <InlineFailureSummary step={firstFailure} /> : null}

        {mode !== "summary" ? (
          <ExpandedSteps
            testCase={testCase}
            steps={mode === "failedOnly" ? failedOrBlocked : steps}
          />
        ) : null}

        <ActionRow
          mode={mode}
          setMode={setMode}
          hasSteps={steps !== null}
          failedOrBlockedCount={failedOrBlocked.length}
        />

        <Inline space="space.200">
          <Link href={`${appBaseUrl}/test-cases/${testCase.id}`} openNewTab>
            Open in Testryn
          </Link>
          {latest ? (
            <Link href={`${appBaseUrl}/executions/${latest.executionId}`} openNewTab>
              Open execution
            </Link>
          ) : null}
        </Inline>
      </Stack>
    </Box>
  );
}

/** Technical id and title deliberately on separate lines with different weight
 * (Abschnitt 8) -- previously a single "id  title" line read as one run-on phrase. */
function CardHeader({ testCase }) {
  return (
    <Stack space="space.025">
      <Text size="small" color="color.text.subtlest">{testCase.humanId}</Text>
      <Text weight="bold">{testCase.title}</Text>
      <Text size="small" color="color.text.subtlest">
        {testCase.status} · {testCase.priority} · v{testCase.version}
      </Text>
    </Stack>
  );
}

/** Abschnitt 19: plain compact text, not a badge. */
function ExecutorLabel({ executor }) {
  const label = executorLabel(executor);
  if (!label) return null;
  return <Text size="small" color="color.text.subtlest">{label} ·</Text>;
}

/** Abschnitt 10: "N ✓ · N ✕ · N !" style -- compact, and only the buckets that are
 * actually non-zero are shown, so a fully green run reads as one short segment
 * rather than a row of zeroes. */
function StepCountsLine({ summary }) {
  const segments = [];
  if (summary.passed) segments.push(`${summary.passed} ✓`);
  if (summary.failed) segments.push(`${summary.failed} ✕`);
  if (summary.blocked) segments.push(`${summary.blocked} !`);
  if (summary.notRun) segments.push(`${summary.notRun} ○`);
  if (summary.skipped) segments.push(`${summary.skipped} –`);
  return <Text size="small">{segments.join(" · ")}</Text>;
}

/** Abschnitt 4/34: visible with zero clicks whenever the run failed or was
 * blocked -- the whole point of "failure first". Only the FIRST such step; the
 * "Show failed only" action reveals the rest, if there are more. */
function InlineFailureSummary({ step }) {
  const meta = statusMeta(step.result.status);
  return (
    <Box padding="space.100">
      <Stack space="space.050">
        <Inline space="space.050" alignBlock="center">
          <Icon glyph={meta.glyph} label={meta.label} color={meta.color} />
          <Text weight="bold" size="small">
            {step.result.status === "FAILED" ? "Failed" : "Blocked"} at Step {step.position}
          </Text>
        </Inline>
        <Text size="small">{step.action}</Text>
        <Field label="EXPECTED" value={step.expectedResult} compact />
        {step.result.actualResult ? <Field label="ACTUAL" value={step.result.actualResult} compact /> : null}
        {step.result.status === "BLOCKED" && step.result.comment ? (
          <Field label="REASON" value={step.result.comment} compact />
        ) : null}
      </Stack>
    </Box>
  );
}

function ActionRow({ mode, setMode, hasSteps, failedOrBlockedCount }) {
  if (!hasSteps) return null;

  if (mode === "summary") {
    return (
      <Inline space="space.100">
        {/* Redundant with the inline summary when there's exactly one attention
         * step -- only offered when it would reveal something new. */}
        {failedOrBlockedCount > 1 ? (
          <Button appearance="link" onClick={() => setMode("failedOnly")}>
            Show failed only
          </Button>
        ) : null}
        <Button appearance="link" onClick={() => setMode("all")}>
          Show all steps
        </Button>
      </Inline>
    );
  }

  return (
    <Inline space="space.100">
      <Button appearance="link" onClick={() => setMode("summary")}>
        Collapse
      </Button>
      {mode === "failedOnly" ? (
        <Button appearance="link" onClick={() => setMode("all")}>
          Show all steps
        </Button>
      ) : failedOrBlockedCount > 0 ? (
        <Button appearance="link" onClick={() => setMode("failedOnly")}>
          Show failed only
        </Button>
      ) : null}
    </Inline>
  );
}

function ExpandedSteps({ testCase, steps }) {
  return (
    <Box padding="space.100">
      <Stack space="space.150">
        {testCase.preconditions ? <Preconditions text={testCase.preconditions} /> : null}
        {steps.length === 0 ? (
          <Text size="small" color="color.text.subtlest">No failed or blocked steps.</Text>
        ) : (
          <Stack space="space.150">
            {steps.map((step) => (
              <StepRow key={step.position} step={step} />
            ))}
          </Stack>
        )}
      </Stack>
    </Box>
  );
}

function Preconditions({ text }) {
  const [expanded, setExpanded] = useState(false);
  const { shown, isTruncated } = truncate(text, PRECONDITIONS_PREVIEW_LENGTH);
  return (
    <Stack space="space.025">
      <Text weight="bold" size="small">Preconditions</Text>
      <Text size="small">{expanded ? text : shown}</Text>
      {isTruncated ? (
        <Button appearance="link" onClick={() => setExpanded(!expanded)}>
          {expanded ? "Show less" : "Show more"}
        </Button>
      ) : null}
    </Stack>
  );
}

/** Abschnitt 15/16/17/18: a FAILED/BLOCKED step gets the full Action/Expected/
 * Actual/Failure treatment; PASSED/SKIPPED/NOT_RUN stay a single compact line --
 * failure earns more attention than a routine pass, not the other way round. */
function StepRow({ step }) {
  const result = step.result;
  const meta = statusMeta(result.status);
  const detailed = isAttentionStatus(result.status);

  if (!detailed) {
    return (
      <Inline space="space.050" alignBlock="center">
        <Icon glyph={meta.glyph} label={meta.label} color={meta.color} />
        <Text size="small">
          Step {step.position} · {meta.label} · {step.action}
        </Text>
      </Inline>
    );
  }

  return (
    <Stack space="space.075">
      <Inline space="space.050" alignBlock="center">
        <Icon glyph={meta.glyph} label={meta.label} color={meta.color} />
        <Text weight="bold" size="small">Step {step.position} · {meta.label}</Text>
      </Inline>
      <Field label="ACTION" value={step.action} />
      <Field label="EXPECTED" value={step.expectedResult} />
      {result.actualResult ? <Field label="ACTUAL" value={result.actualResult} /> : null}
      {result.status === "BLOCKED" && result.comment ? <Field label="REASON" value={result.comment} /> : null}
      {result.failureDetails ? <FailureDetailsField text={result.failureDetails} /> : null}
    </Stack>
  );
}

/** Abschnitt 12: label and value are visually distinct (small, subtle, uppercase
 * label above; normal-weight value below) -- not two near-identical text blocks. */
function Field({ label, value, compact }) {
  return (
    <Stack space="space.025">
      <Text size="small" color="color.text.subtlest">{label}</Text>
      <Text size={compact ? "small" : "medium"}>{value}</Text>
    </Stack>
  );
}

/** Abschnitt 13/14: failure details can be arbitrarily long (a full stacktrace) --
 * never lost, only collapsed. Default preview is a short prefix with a toggle. */
function FailureDetailsField({ text }) {
  const [expanded, setExpanded] = useState(false);
  const { shown, isTruncated } = truncate(text, FAILURE_DETAILS_PREVIEW_LENGTH);
  return (
    <Stack space="space.025">
      <Text size="small" color="color.text.subtlest">FAILURE</Text>
      <Text size="small" color="color.text.danger">{expanded ? text : shown}</Text>
      {isTruncated ? (
        <Button appearance="link" onClick={() => setExpanded(!expanded)}>
          {expanded ? "Hide details" : "Show details"}
        </Button>
      ) : null}
    </Stack>
  );
}
