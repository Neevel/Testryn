import React, { useEffect, useState } from "react";
import { Box, Button, EmptyState, SectionMessage, Spinner, Stack, Text } from "@forge/react";
import { invoke, router } from "@forge/bridge";
import { TestCaseCard } from "./TestCaseCard";
import { orderByAttention, summarizeCoverage } from "./coverageView";

/**
 * The panel's single top-level state machine (Abschnitt 20/21): exactly one
 * `getCoverage` call on mount, rendering one of five states -- loading, ok
 * (possibly with zero test cases -> the empty state), unavailable, or unauthorized.
 * Never more than one of these renders at once, so the layout never jumps between
 * unrelated shapes.
 */
export function App() {
  const [result, setResult] = useState(null);

  useEffect(() => {
    let cancelled = false;
    invoke("getCoverage")
      .then((response) => {
        if (!cancelled) {
          setResult(response);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setResult({ kind: "unavailable" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (result === null) {
    return (
      <Box padding="space.200">
        <Spinner size="medium" label="Loading Testryn coverage" />
      </Box>
    );
  }

  if (result.kind === "unauthorized") {
    return (
      <Box padding="space.100">
        <SectionMessage appearance="warning" title="Testryn connection is not authorized.">
          <Text>Ask a Testryn administrator to check this app's service token.</Text>
        </SectionMessage>
      </Box>
    );
  }

  if (result.kind === "unavailable") {
    return (
      <Box padding="space.100">
        <SectionMessage appearance="error" title="Testryn is currently unavailable.">
          <Text>Existing Jira data is unaffected.</Text>
        </SectionMessage>
      </Box>
    );
  }

  const { testCases, totalCount, appBaseUrl } = result;

  if (testCases.length === 0) {
    return (
      <Box padding="space.100">
        <EmptyState
          header="No Testryn test cases linked"
          description="Link or create test cases in Testryn to see coverage here."
          primaryAction={<Button appearance="primary" onClick={() => router.open(appBaseUrl)}>Open Testryn</Button>}
        />
      </Box>
    );
  }

  const ordered = orderByAttention(testCases);

  return (
    <Box padding="space.100">
      <Stack space="space.150">
        <CoverageSummary testCases={testCases} totalCount={totalCount} />
        <Stack space="space.100">
          {ordered.map((testCase) => (
            <TestCaseCard key={testCase.id} testCase={testCase} appBaseUrl={appBaseUrl} />
          ))}
        </Stack>
        {totalCount > testCases.length ? (
          <Stack space="space.050">
            <Text size="small" color="color.text.subtlest">
              Showing {testCases.length} of {totalCount} linked tests
            </Text>
            <Button appearance="link" onClick={() => router.open(appBaseUrl)}>
              Show all in Testryn
            </Button>
          </Stack>
        ) : null}
      </Stack>
    </Box>
  );
}

/** Counts are derived purely from the real, already-fetched testCases array
 * (Abschnitt 15: no fake KPIs). Compact single line: only non-zero buckets are
 * shown, so a fully green story reads as one short segment. */
function CoverageSummary({ testCases, totalCount }) {
  const counts = summarizeCoverage(testCases);

  const parts = [];
  if (counts.PASSED) parts.push(`${counts.PASSED} passed`);
  if (counts.FAILED) parts.push(`${counts.FAILED} failed`);
  if (counts.BLOCKED) parts.push(`${counts.BLOCKED} blocked`);
  if (counts.NOT_RUN) parts.push(`${counts.NOT_RUN} not run`);
  if (counts.SKIPPED) parts.push(`${counts.SKIPPED} skipped`);

  return (
    <Text weight="bold">
      {totalCount} linked {totalCount === 1 ? "test" : "tests"}
      {parts.length > 0 ? ` · ${parts.join(" · ")}` : ""}
    </Text>
  );
}
