import React, { useState } from "react";
import { Box, Button, DynamicTable, Icon, Inline, Link, Lozenge, SectionMessage, Stack, Text, TextArea, Textfield, xcss } from "@forge/react";
import { invoke } from "@forge/bridge";
import { statusMeta } from "./statusMeta";
import { executorLabel, formatDate, stepsForDisplay, summarizeSteps, truncate } from "./coverageView";

const PREVIEW_LENGTH = 160;
const cardStyles = xcss({ borderColor: "color.border", borderStyle: "solid", borderWidth: "border.width", borderRadius: "border.radius.200" });
const failedStyles = xcss({ backgroundColor: "color.background.danger", borderRadius: "border.radius.100", padding: "space.075" });

/** Independent, default-collapsed accordion. Each instance owns its state, so
 * several tests can remain open for comparison. */
export function TestCaseCard({ testCase, appBaseUrl, onSaved, initialExpanded = false }) {
  const [expanded, setExpanded] = useState(initialExpanded);
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
        {expanded ? <Details testCase={testCase} appBaseUrl={appBaseUrl} executionSteps={executionSteps} onSaved={onSaved} /> : null}
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

function Details({ testCase, appBaseUrl, executionSteps, onSaved }) {
  const latest = testCase.latestExecution;
  const [editing, setEditing] = useState(false);
  return (
    <Stack space="space.150">
      <Inline spread="space-between" alignBlock="center">
        <Text weight="bold">Test definition</Text>
        {!editing ? <Button appearance="subtle" iconBefore="edit" onClick={() => setEditing(true)}>Edit steps</Button> : null}
      </Inline>
      {editing ? <DefinitionEditor testCase={testCase} onCancel={() => setEditing(false)} onSaved={onSaved} /> : (
        <Stack space="space.100">
          {testCase.description ? <ExpandableText title="Description" text={testCase.description} /> : null}
          {testCase.preconditions ? <ExpandableText title="Preconditions" text={testCase.preconditions} /> : null}
          <StepTable definitions={testCase.steps ?? []} executionSteps={null} />
        </Stack>
      )}
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
      {latest && !executionSteps ? (
        <Text size="small" color="color.text.subtlest">Step-level results were not reported by this automation source.</Text>
      ) : null}
      {executionSteps ? <Stack space="space.050"><Text weight="bold">Last execution results</Text>
        <StepTable definitions={testCase.steps ?? []} executionSteps={executionSteps} /></Stack> : null}
      <Inline space="space.200">
        <Link href={`${appBaseUrl}/test-cases/${testCase.id}`} openNewTab>Open in Testryn</Link>
        {latest ? <Link href={`${appBaseUrl}/executions/${latest.executionId}`} openNewTab>Open execution</Link> : null}
      </Inline>
    </Stack>
  );
}

function DefinitionEditor({ testCase, onCancel, onSaved }) {
  const [title, setTitle] = useState(testCase.title ?? "");
  const [description, setDescription] = useState(testCase.description ?? "");
  const [preconditions, setPreconditions] = useState(testCase.preconditions ?? "");
  const [steps, setSteps] = useState((testCase.steps ?? []).map((step) => ({ action: step.action, inputData: step.inputData ?? "", expectedResult: step.expectedResult })));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const isValid = Boolean(title.trim() && steps.length > 0
    && steps.every((step) => step.action.trim() && step.expectedResult.trim()));
  const originalSteps = testCase.steps ?? [];
  const isDirty = title.trim() !== (testCase.title ?? "").trim()
    || (description.trim() || "") !== (testCase.description ?? "").trim()
    || (preconditions.trim() || "") !== (testCase.preconditions ?? "").trim()
    || steps.length !== originalSteps.length
    || steps.some((step, index) => step.action.trim() !== (originalSteps[index]?.action ?? "").trim()
      || step.inputData.trim() !== (originalSteps[index]?.inputData ?? "").trim()
      || step.expectedResult.trim() !== (originalSteps[index]?.expectedResult ?? "").trim());

  const updateStep = (index, field, value) => setSteps((current) => current.map((step, position) => position === index ? { ...step, [field]: value } : step));
  const moveStep = (index, offset) => setSteps((current) => {
    const target = index + offset;
    if (target < 0 || target >= current.length) return current;
    const next = [...current];
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  });
  const removeStep = (index) => setSteps((current) => current.filter((_, position) => position !== index));
  const save = async () => {
    if (!isValid || saving) return;
    setSaving(true);
    setError(null);
    try {
      const result = await invoke("updateTestCaseDefinition", { testCaseId: testCase.id, definition: {
        expectedVersion: testCase.version,
        title: title.trim(),
        description: description.trim() || null,
        preconditions: preconditions.trim() || null,
        steps: steps.map((step) => ({ action: step.action.trim(), inputData: step.inputData.trim() || null, expectedResult: step.expectedResult.trim() })),
      } });
      if (result.kind === "ok") {
        onCancel();
        onSaved();
      } else if (result.kind === "conflict") {
        setError("This test case changed while you were editing. Reload the panel and try again.");
      } else if (result.kind === "unauthorized") {
        setError("The Testryn connection needs a service token with write access.");
      } else {
        setError("The test case could not be saved. Check all fields and try again.");
      }
    } catch (ignored) {
      setError("Testryn is currently unavailable. Your unsaved changes are still here.");
    } finally {
      setSaving(false);
    }
  };

  return <Stack space="space.150">
    <Stack space="space.050"><Text weight="bold" size="small">Title</Text>
      <Textfield value={title} onChange={(event) => setTitle(event.target.value)} /></Stack>
    <Stack space="space.050"><Text weight="bold" size="small">Description</Text>
      <TextArea value={description} onChange={(event) => setDescription(event.target.value)} resize="vertical" /></Stack>
    <Stack space="space.050"><Text weight="bold" size="small">Preconditions</Text>
      <TextArea value={preconditions} onChange={(event) => setPreconditions(event.target.value)} resize="vertical" /></Stack>
    <Stack space="space.100">
      {steps.map((step, index) => <Box key={`edit-step-${index}`} padding="space.100" xcss={cardStyles}><Stack space="space.075">
        <Inline spread="space-between" alignBlock="center"><Text weight="bold">Step {index + 1}</Text><Inline space="space.050">
          <Button appearance="subtle" iconBefore="arrow-up" isDisabled={index === 0} onClick={() => moveStep(index, -1)}>Up</Button>
          <Button appearance="subtle" iconBefore="arrow-down" isDisabled={index === steps.length - 1} onClick={() => moveStep(index, 1)}>Down</Button>
          <Button appearance="subtle" iconBefore="delete" isDisabled={steps.length === 1} onClick={() => removeStep(index)}>Remove</Button>
        </Inline></Inline>
        <Stack space="space.050"><Text size="small" weight="bold">Action</Text><TextArea value={step.action} onChange={(event) => updateStep(index, "action", event.target.value)} /></Stack>
        <Stack space="space.050"><Text size="small" weight="bold">Input / Data</Text><TextArea value={step.inputData} onChange={(event) => updateStep(index, "inputData", event.target.value)} placeholder="Optional test data, credentials or values" /></Stack>
        <Stack space="space.050"><Text size="small" weight="bold">Expected result</Text><TextArea value={step.expectedResult} onChange={(event) => updateStep(index, "expectedResult", event.target.value)} /></Stack>
      </Stack></Box>)}
      <Button appearance="subtle" iconBefore="add" onClick={() => setSteps((current) => [...current, { action: "", inputData: "", expectedResult: "" }])}>Add step</Button>
    </Stack>
    {error ? <SectionMessage appearance="error"><Text>{error}</Text></SectionMessage> : null}
    {!isValid ? <Text size="small" color="color.text.danger">Title, action and expected result are required. At least one step must remain.</Text> : null}
    {isValid && !isDirty ? <Text size="small" color="color.text.subtlest">No changes to save.</Text> : null}
    <Inline space="space.100"><Button appearance="primary" isDisabled={!isValid || !isDirty || saving} onClick={save}>{saving ? "Saving..." : `Save as version ${testCase.version + 1}`}</Button>
      <Button appearance="subtle" isDisabled={saving} onClick={onCancel}>Cancel</Button></Inline>
  </Stack>;
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
    { key: "action", content: "Action", width: hasReportedResults ? 25 : 32 },
    { key: "input", content: "Input / Data", width: hasReportedResults ? 20 : 27 },
    { key: "expected", content: "Expected Result", width: hasReportedResults ? 23 : 34 },
    ...(hasReportedResults ? [{ key: "result", content: "Result / Actual", width: 25 }] : []),
  ] };
  const rows = displaySteps.map((step) => {
    return { key: `step-${step.position}`, cells: [
      { key: "position", content: <StepNumber position={step.position} result={step.result} /> },
      { key: "action", content: <Text size="small">{step.action}</Text> },
      { key: "input", content: <Text size="small" color={step.inputData ? "color.text" : "color.text.subtlest"}>{step.inputData || "—"}</Text> },
      { key: "expected", content: <Text size="small">{step.expectedResult}</Text> },
      ...(hasReportedResults ? [{ key: "result", content: <ResultCell result={step.result} /> }] : []),
    ] };
  });
  const failures = displaySteps.filter((step) => step.result?.failureDetails || (step.result?.status === "BLOCKED" && step.result?.comment));
  if (!displaySteps.length) return <Text size="small" color="color.text.subtlest">No steps in this test definition.</Text>;
  return <Stack space="space.100"><DynamicTable head={head} rows={rows} isFixedSize={false} label="Test steps" />
    {failures.map((step) => <FailurePanel key={`failure-${step.position}`} step={step} />)}
  </Stack>;
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

function FailurePanel({ step }) {
  const [expanded, setExpanded] = useState(false);
  const result = step.result;
  return <Box xcss={failedStyles}><Stack space="space.050">
    <Inline space="space.075" alignBlock="center" spread="space-between"><Text weight="bold" size="small">Step {step.position} needs attention</Text><Status status={result.status} /></Inline>
    {result.actualResult ? <LabeledValue label="Actual result" value={result.actualResult} /> : null}
    {expanded && result.failureDetails ? <LabeledValue label="Failure details" value={result.failureDetails} /> : null}
    {expanded && result.comment ? <LabeledValue label="Comment" value={result.comment} /> : null}
    {(result.failureDetails || result.comment) ? <Button appearance="link" onClick={() => setExpanded(!expanded)}>{expanded ? "Hide details" : "Show details"}</Button> : null}
  </Stack></Box>;
}
