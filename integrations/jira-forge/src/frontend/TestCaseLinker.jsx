import React, { useEffect, useState } from "react";
import { Box, Button, Inline, Lozenge, SectionMessage, Select, Spinner, Stack, Text, Textfield, xcss } from "@forge/react";
import { invoke } from "@forge/bridge";
import { markLinkable } from "./coverageView";

const panelStyles = xcss({ borderColor: "color.border", borderStyle: "solid", borderWidth: "border.width", borderRadius: "border.radius.200" });
const rowStyles = xcss({ borderColor: "color.border", borderStyle: "solid", borderWidth: "border.width", borderRadius: "border.radius.100" });

/**
 * Links an already existing Testryn test case to the current Jira issue. Search is
 * scoped to one project (the paginated project search endpoint, reused as-is); the
 * issue key is never taken from here -- the resolver reads it from the trusted Forge
 * context. Test cases already in this issue's coverage are shown as linked and not
 * offered again (`markLinkable`), though Testryn still rejects a real duplicate.
 */
export function TestCaseLinker({ linkedIds, onCancel, onLinked }) {
  const [projects, setProjects] = useState(null);
  const [selectedProject, setSelectedProject] = useState(null);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState(null);
  const [totalCount, setTotalCount] = useState(0);
  const [searching, setSearching] = useState(false);
  const [linkingId, setLinkingId] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  useEffect(() => {
    invoke("getProjects").then((result) => {
      if (result.kind === "ok") {
        setProjects(result.projects);
        if (result.projects.length) setSelectedProject(toOption(result.projects[0]));
      } else setError("Projects could not be loaded from Testryn.");
    }).catch(() => setError("Testryn is currently unavailable."));
  }, []);

  const search = async () => {
    if (!selectedProject?.value || searching) return;
    setSearching(true);
    setError(null);
    setNotice(null);
    try {
      const result = await invoke("searchTestCases", { projectKey: selectedProject.value, query: query.trim() });
      if (result.kind === "ok") {
        setResults(markLinkable(result.testCases, linkedIds));
        setTotalCount(result.totalCount);
      } else {
        setResults(null);
        setError(result.kind === "unauthorized" ? "The Testryn token is not authorized." : "Test cases could not be loaded.");
      }
    } catch (ignored) {
      setResults(null);
      setError("Testryn is currently unavailable. Please try again.");
    } finally {
      setSearching(false);
    }
  };

  const link = async (testCase) => {
    if (linkingId) return;
    setLinkingId(testCase.id);
    setError(null);
    setNotice(null);
    try {
      const result = await invoke("linkExistingTestCase", { testCaseId: testCase.id });
      if (result.kind === "ok") {
        onLinked();
      } else if (result.kind === "conflict") {
        setResults((current) => (current ?? []).map((row) => row.id === testCase.id ? { ...row, alreadyLinked: true } : row));
        setNotice(`${testCase.humanId} is already linked to this issue.`);
      } else {
        setError(result.kind === "unauthorized" ? "The Testryn token needs write access." : "The test case could not be linked.");
      }
    } catch (ignored) {
      setError("Testryn is currently unavailable. Please try again.");
    } finally {
      setLinkingId(null);
    }
  };

  if (projects === null && !error) return <Spinner size="medium" label="Loading Testryn projects" />;

  return <Box padding="space.150" xcss={panelStyles}><Stack space="space.150">
    <Inline spread="space-between" alignBlock="center">
      <Text weight="bold">Link an existing Testryn test case</Text>
      <Button appearance="subtle" onClick={onCancel}>Cancel</Button>
    </Inline>
    <Inline space="space.100" shouldWrap alignBlock="end">
      <Stack space="space.050"><Text size="small" weight="bold">Project</Text>
        <Select options={(projects ?? []).map(toOption)} value={selectedProject} onChange={(option) => { setSelectedProject(option); setResults(null); }} isSearchable /></Stack>
      <Stack space="space.050"><Text size="small" weight="bold">Search</Text>
        <Textfield value={query} onChange={(event) => setQuery(event.target.value)} placeholder="ID or title" /></Stack>
      <Button appearance="primary" isDisabled={!selectedProject?.value || searching} onClick={search}>{searching ? "Searching..." : "Search"}</Button>
    </Inline>
    {error ? <SectionMessage appearance="error"><Text>{error}</Text></SectionMessage> : null}
    {notice ? <SectionMessage appearance="information"><Text>{notice}</Text></SectionMessage> : null}
    {results !== null ? (
      results.length === 0 ? <Text color="color.text.subtlest">No matching test cases in this project.</Text> : <Stack space="space.075">
        {results.map((testCase) => (
          <Box key={testCase.id} padding="space.100" xcss={rowStyles}>
            <Inline spread="space-between" alignBlock="center" space="space.100">
              <Stack space="space.025">
                <Text weight="bold">{testCase.humanId}</Text>
                <Text size="small">{testCase.title}</Text>
              </Stack>
              {testCase.alreadyLinked
                ? <Lozenge appearance="success">Linked</Lozenge>
                : <Button appearance="primary" isDisabled={linkingId !== null} onClick={() => link(testCase)}>
                    {linkingId === testCase.id ? "Linking..." : "Link"}
                  </Button>}
            </Inline>
          </Box>
        ))}
        {totalCount > results.length
          ? <Text size="small" color="color.text.subtlest">Showing {results.length} of {totalCount} matches. Narrow the search to see the rest.</Text>
          : null}
      </Stack>
    ) : null}
  </Stack></Box>;
}

function toOption(project) {
  return { value: project.key, label: `${project.name} (${project.key})` };
}
