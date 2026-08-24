import React, { useState } from "react";
import { Box, Button, DynamicTable, Icon, Inline, Link, Lozenge, Stack, Text, xcss } from "@forge/react";
import { statusMeta } from "./statusMeta";
import { executorLabel, formatDate, stepsForDisplay, summarizeSteps, truncate } from "./coverageView";

const PREVIEW_LENGTH = 160;
const cardStyles = xcss({ borderColor: "color.border", borderStyle: "solid", borderWidth: "border.width", borderRadius: "border.radius.200" });
const failedStyles = xcss({ backgroundColor: "color.background.danger", borderRadius: "border.radius.100", padding: "space.075" });

/** Independent, default-collapsed accordion. Each instance owns its state, so
 * several tests can remain open for comparison. */
export function TestCaseCard({ testCase, appBaseUrl }) {
  const [expanded, setExpanded] = useState(false);
  const latest = testCase.latestExecution;
  const executionSteps = latest?.steps?.length > 0 ? latest.steps : null;

  return (
    <Box padding="space.100" xcss={cardStyles}>
      <Stack space="space.100">
        <Inline space="space.075" alignBlock="center" shouldWrap>
          <Button appearance="subtle" iconBefore={expanded ? "chevron-down" : "chevron-right"}
            onClick={() => setExpanded((current) => !current)}>
            {testCase.humanId}
          </Button>
          <Text weight="bold">{testCase.title}</Text>
        </Inline>
        <Summary testCase={testCase} stepSummary={summarizeSteps(executionSteps)} expanded={expanded} />
        {expanded ? <Details testCase={testCase} appBaseUrl={appBaseUrl} executionSteps={executionSteps} /> : null}
      </Stack>
    </Box>
  );
}

function Summary({ testCase, stepSummary, expanded }) {
  const latest = testCase.latestExecution;
  const parts = [testCase.priority, `v${testCase.version}`];
  if (latest && !expanded) {
    if (executorLabel(latest.executor)) parts.push(executorLabel(latest.executor));
    if (latest.executedAt) parts.push(formatDate(latest.executedAt));
  }
  return (
    <Stack space="space.050">
      <Inline space="space.050" alignBlock="center">
        {latest && !expanded ? <Status status={latest.status} /> : null}
        <Text size="small" color="color.text.subtlest">{parts.join(" · ")}</Text>
      </Inline>
      {!expanded && stepSummary ? <StepCounts summary={stepSummary} /> : !expanded ? (
        <Text size="small" color="color.text.subtlest">{latest ? "Step-level results not reported" : "No execution yet"}</Text>
      ) : null}
    </Stack>
  );
}

function Details({ testCase, appBaseUrl, executionSteps }) {
  const latest = testCase.latestExecution;
  return (
    <Stack space="space.150">
      {latest ? (
        <Stack space="space.050">
          <Text weight="bold" size="small">Last execution</Text>
          {latest.executionName ? <Text size="small">{latest.executionName}</Text> : null}
          <Inline space="space.050" alignBlock="center">
            <Status status={latest.status} />
            <Text size="small" color="color.text.subtlest">
              {[executorLabel(latest.executor), latest.executedAt ? formatDate(latest.executedAt) : null].filter(Boolean).join(" · ")}
            </Text>
          </Inline>
        </Stack>
      ) : null}
      {testCase.preconditions ? <ExpandableText title="Preconditions" text={testCase.preconditions} /> : null}
      {latest && !executionSteps ? (
        <Text size="small" color="color.text.subtlest">Step-level results were not reported by this automation source.</Text>
      ) : null}
      <StepTable definitions={testCase.steps ?? []} executionSteps={executionSteps} />
      <Inline space="space.200">
        <Link href={`${appBaseUrl}/test-cases/${testCase.id}`} openNewTab>Open in Testryn</Link>
        {latest ? <Link href={`${appBaseUrl}/executions/${latest.executionId}`} openNewTab>Open execution</Link> : null}
      </Inline>
    </Stack>
  );
}

function Status({ status }) {
  const meta = statusMeta(status);
  return <Inline space="space.050" alignBlock="center"><Icon glyph={meta.glyph} label={meta.label} color={meta.color} /><Lozenge appearance={meta.appearance}>{meta.label}</Lozenge></Inline>;
}

function StepCounts({ summary }) {
  const total = summary.passed + summary.failed + summary.blocked + summary.notRun + summary.skipped;
  const parts = [`${summary.passed} / ${total} passed`];
  if (summary.failed) parts.push(`${summary.failed} failed`);
  if (summary.blocked) parts.push(`${summary.blocked} blocked`);
  if (summary.notRun) parts.push(`${summary.notRun} not run`);
  if (summary.skipped) parts.push(`${summary.skipped} skipped`);
  return <Text size="small">{parts.join(" · ")}</Text>;
}

function StepTable({ definitions, executionSteps }) {
  const displaySteps = stepsForDisplay(definitions, executionSteps);
  const hasReportedResults = Boolean(executionSteps);
  const head = { cells: [
    { key: "position", content: "#", width: 7 },
    { key: "action", content: "Action", width: hasReportedResults ? 28 : 42 },
    { key: "input", content: "Input / Data", width: 17 },
    { key: "expected", content: "Expected Result", width: hasReportedResults ? 28 : 34 },
    ...(hasReportedResults ? [{ key: "result", content: "Result / Actual", width: 20 }] : []),
  ] };
  const rows = displaySteps.map((step) => {
    return { key: `step-${step.position}`, cells: [
      { key: "position", content: <StepNumber position={step.position} result={step.result} /> },
      { key: "action", content: <Text size="small">{step.action}</Text> },
      { key: "input", content: <Text size="small" color="color.text.subtlest">—</Text> },
      { key: "expected", content: <Text size="small">{step.expectedResult}</Text> },
      ...(hasReportedResults ? [{ key: "result", content: <ResultCell result={step.result} /> }] : []),
    ] };
  });
  if (!displaySteps.length) return <Text size="small" color="color.text.subtlest">No steps in this test definition.</Text>;
  return <DynamicTable head={head} rows={rows} isFixedSize={false} label="Test steps" />;
}

function StepNumber({ position, result }) {
  if (!result) return <Text size="small">{position}</Text>;
  const meta = statusMeta(result.status);
  return <Inline space="space.025" alignBlock="center"><Icon glyph={meta.glyph} label={meta.label} color={meta.color} /><Text size="small">{position}</Text></Inline>;
}

function ResultCell({ result }) {
  if (!result) return <Text size="small" color="color.text.subtlest">NOT REPORTED</Text>;
  const content = <Stack space="space.050">
    <Status status={result.status} />
    {result.actualResult ? <LabeledValue label="Actual" value={result.actualResult} /> : null}
    {result.status === "BLOCKED" && result.comment ? <LabeledValue label="Reason" value={result.comment} /> : null}
    {result.failureDetails ? <FailureDetails text={result.failureDetails} /> : null}
  </Stack>;
  return result.status === "FAILED" ? <Box xcss={failedStyles}>{content}</Box> : content;
}

function LabeledValue({ label, value }) {
  return <Stack space="space.025"><Text size="small" color="color.text.subtlest">{label}</Text><Text size="small">{value}</Text></Stack>;
}

function ExpandableText({ title, text }) {
  const [expanded, setExpanded] = useState(false);
  const { shown, isTruncated } = truncate(text, PREVIEW_LENGTH);
  return <Stack space="space.025"><Text weight="bold" size="small">{title}</Text><Text size="small">{expanded ? text : shown}</Text>
    {isTruncated ? <Button appearance="link" onClick={() => setExpanded(!expanded)}>{expanded ? "Show less" : "Show more"}</Button> : null}</Stack>;
}

function FailureDetails({ text }) {
  const [expanded, setExpanded] = useState(false);
  return <Stack space="space.025">
    {expanded ? <Text size="small" color="color.text.danger">{text}</Text> : null}
    <Button appearance="link" onClick={() => setExpanded(!expanded)}>{expanded ? "Hide failure details" : "Show failure details"}</Button>
  </Stack>;
}
