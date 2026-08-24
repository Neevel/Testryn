import React, { useState } from "react";
import { Box, Button, Icon, Inline, Link, Lozenge, Stack, Text } from "@forge/react";
import { statusMeta } from "./statusMeta";

/**
 * One linked test case, collapsed by default (Abschnitt 12: not all steps open for
 * every test case at once). Expanding shows preconditions and steps with their
 * expected results; collapsing hides them again -- purely local UI state, no extra
 * Testryn request either way (the coverage response already includes everything).
 */
export function TestCaseCard({ testCase, appBaseUrl }) {
  const [expanded, setExpanded] = useState(false);
  const latest = testCase.latestExecution;
  const meta = latest ? statusMeta(latest.status) : null;

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
          <Inline space="space.050" alignBlock="center">
            <Icon glyph={meta.glyph} label={meta.label} color={meta.color} />
            <Lozenge appearance={meta.appearance}>{meta.label}</Lozenge>
            <Text size="small">Last run: {formatDate(latest.executedAt)}</Text>
          </Inline>
        ) : (
          <Text size="small" color="color.text.subtlest">No execution yet</Text>
        )}

        {expanded ? <TestCaseDetails testCase={testCase} /> : null}

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

function TestCaseDetails({ testCase }) {
  return (
    <Box padding="space.100">
      <Stack space="space.100">
        {testCase.preconditions ? (
          <Stack space="space.025">
            <Text weight="bold" size="small">Preconditions</Text>
            <Text size="small">{testCase.preconditions}</Text>
          </Stack>
        ) : null}

        {testCase.steps && testCase.steps.length > 0 ? (
          <Stack space="space.100">
            {testCase.steps.map((step) => (
              <Stack key={step.order} space="space.025">
                <Text size="small">{step.order}. {step.action}</Text>
                <Text size="small" color="color.text.subtlest">Expected: {step.expectedResult}</Text>
              </Stack>
            ))}
          </Stack>
        ) : (
          <Text size="small" color="color.text.subtlest">No steps recorded.</Text>
        )}
      </Stack>
    </Box>
  );
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
