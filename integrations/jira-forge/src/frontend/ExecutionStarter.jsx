import React, { useMemo, useState } from "react";
import { Box, Button, Checkbox, Inline, Link, SectionMessage, Select, Stack, Text, Textfield, xcss } from "@forge/react";
import { invoke } from "@forge/bridge";
import { groupByProject } from "./coverageView";

const panelStyles = xcss({ borderColor: "color.border", borderStyle: "solid", borderWidth: "border.width", borderRadius: "border.radius.200" });

/**
 * Starts a Testryn execution for the test cases linked to the current Jira issue.
 * The execution is a plain ad-hoc Testryn execution (immutable snapshot of each
 * selected test case's current version) -- this panel adds no special path around
 * the snapshot or versioning rules. The issue key is never sent from here; the
 * resolver reads it from the trusted invocation context and re-checks every
 * selected id against that issue's coverage before creating anything.
 */
export function ExecutionStarter({ testCases, appBaseUrl, onCancel, onStarted }) {
  const projectGroups = useMemo(() => [...groupByProject(testCases).entries()], [testCases]);
  const [projectKey, setProjectKey] = useState(projectGroups[0]?.[0] ?? "");
  const groupForProject = projectGroups.find(([key]) => key === projectKey)?.[1] ?? [];

  const [selectedIds, setSelectedIds] = useState(() => new Set((projectGroups[0]?.[1] ?? []).map((testCase) => testCase.id)));
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [started, setStarted] = useState(null);

  const switchProject = (nextKey) => {
    setProjectKey(nextKey);
    const next = projectGroups.find(([key]) => key === nextKey)?.[1] ?? [];
    setSelectedIds(new Set(next.map((testCase) => testCase.id)));
    setError(null);
  };

  const toggle = (id) => setSelectedIds((current) => {
    const next = new Set(current);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  const selectedForProject = groupForProject.filter((testCase) => selectedIds.has(testCase.id));

  const start = async () => {
    if (busy || selectedForProject.length === 0) return;
    setBusy(true);
    setError(null);
    try {
      const result = await invoke("startExecution", {
        testCaseIds: selectedForProject.map((testCase) => testCase.id),
        name: name.trim() || null,
      });
      if (result.kind === "ok") {
        setStarted({ id: result.executionId, name: result.executionName });
      } else if (result.kind === "invalid" && result.reason === "multiProject") {
        setError("Select test cases from a single Testryn project for one execution.");
      } else if (result.kind === "invalid") {
        setError("The execution could not be started. Reload the panel and try again.");
      } else if (result.kind === "unauthorized") {
        setError("The Testryn token needs write access to start an execution.");
      } else {
        setError("Testryn is currently unavailable. Please try again.");
      }
    } catch (ignored) {
      setError("Testryn is currently unavailable. Please try again.");
    } finally {
      setBusy(false);
    }
  };

  if (started) {
    return <Box padding="space.150" xcss={panelStyles}><Stack space="space.150">
      <SectionMessage appearance="success" title="Execution started in Testryn">
        <Text>{started.name ? `"${started.name}" is ready to run.` : "The new execution is ready to run."}</Text>
      </SectionMessage>
      <Inline space="space.200" alignBlock="center">
        <Link href={`${appBaseUrl}/executions/${started.id}`} openNewTab>Open execution in Testryn</Link>
        <Button appearance="primary" onClick={onStarted}>Back to coverage</Button>
      </Inline>
    </Stack></Box>;
  }

  return <Box padding="space.150" xcss={panelStyles}><Stack space="space.150">
    <Inline spread="space-between" alignBlock="center">
      <Text weight="bold">Start a Testryn execution</Text>
      <Button appearance="subtle" onClick={onCancel}>Cancel</Button>
    </Inline>

    {projectGroups.length > 1 ? (
      <Stack space="space.050">
        <Text size="small" weight="bold">Project</Text>
        <Select
          options={projectGroups.map(([key, group]) => ({ value: key, label: `${key || "(unknown project)"} · ${group.length} linked` }))}
          value={{ value: projectKey, label: `${projectKey || "(unknown project)"} · ${groupForProject.length} linked` }}
          onChange={(option) => switchProject(option.value)} />
        <Text size="small" color="color.text.subtlest">One execution covers one project. Run the others separately.</Text>
      </Stack>
    ) : null}

    <Stack space="space.075">
      <Text size="small" weight="bold">Test cases</Text>
      {groupForProject.length === 0
        ? <Text size="small" color="color.text.subtlest">No linked test cases in this project.</Text>
        : groupForProject.map((testCase) => (
          <Checkbox key={testCase.id} isChecked={selectedIds.has(testCase.id)} onChange={() => toggle(testCase.id)}
            label={`${testCase.humanId} · ${testCase.title} (v${testCase.version})`} />
        ))}
    </Stack>

    <Stack space="space.050">
      <Text size="small" weight="bold">Execution name (optional)</Text>
      <Textfield value={name} onChange={(event) => setName(event.target.value)} placeholder="Defaults to an auto-generated iteration name" />
    </Stack>

    {error ? <SectionMessage appearance="error"><Text>{error}</Text></SectionMessage> : null}

    <Inline space="space.100" alignBlock="center">
      <Button appearance="primary" isDisabled={busy || selectedForProject.length === 0} onClick={start}>
        {busy ? "Starting..." : `Start execution (${selectedForProject.length})`}
      </Button>
      <Button appearance="subtle" isDisabled={busy} onClick={onCancel}>Cancel</Button>
    </Inline>
  </Stack></Box>;
}
