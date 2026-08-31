import React, { useEffect, useState } from "react";
import { Box, Button, Inline, SectionMessage, Select, Spinner, Stack, Text, TextArea, Textfield, xcss } from "@forge/react";
import { invoke } from "@forge/bridge";

const panelStyles = xcss({ borderColor: "color.border", borderStyle: "solid", borderWidth: "border.width", borderRadius: "border.radius.200" });
const emptyStep = () => ({ action: "", inputData: "", expectedResult: "" });

export function TestCaseCreator({ onCancel, onCreated }) {
  const [projects, setProjects] = useState(null);
  const [selectedProject, setSelectedProject] = useState(null);
  const [creatingProject, setCreatingProject] = useState(false);
  const [projectKey, setProjectKey] = useState("");
  const [projectName, setProjectName] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [preconditions, setPreconditions] = useState("");
  const [priority, setPriority] = useState({ label: "Medium", value: "MEDIUM" });
  const [tags, setTags] = useState("");
  const [steps, setSteps] = useState([emptyStep()]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    invoke("getProjects").then((result) => {
      if (result.kind === "ok") {
        setProjects(result.projects);
        if (result.projects.length) setSelectedProject(toOption(result.projects[0]));
        else setCreatingProject(true);
      } else setError("Projects could not be loaded from Testryn.");
    }).catch(() => setError("Testryn is currently unavailable."));
  }, []);

  const projectOptions = (projects ?? []).map(toOption);
  const validProject = creatingProject ? projectKey.trim() && projectName.trim() : selectedProject?.value;
  const validTest = title.trim() && steps.length > 0 && steps.every((step) => step.action.trim() && step.expectedResult.trim());

  const updateStep = (index, field, value) => setSteps((current) => current.map((step, position) => position === index ? { ...step, [field]: value } : step));
  const moveStep = (index, offset) => setSteps((current) => {
    const target = index + offset;
    if (target < 0 || target >= current.length) return current;
    const next = [...current];
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  });

  const create = async () => {
    if (!validProject || !validTest || busy) return;
    setBusy(true);
    setError(null);
    try {
      let selectedKey = selectedProject?.value;
      if (creatingProject) {
        const result = await invoke("createProject", { project: {
          key: projectKey.trim().toUpperCase(), name: projectName.trim(), description: null,
        } });
        if (result.kind !== "ok") {
          setError(result.kind === "unauthorized" ? "The Testryn token needs write access." : "The project key may already exist or is invalid.");
          return;
        }
        selectedKey = result.project.key;
        setProjects((current) => [...(current ?? []), result.project]);
        setSelectedProject(toOption(result.project));
        setCreatingProject(false);
      }
      const result = await invoke("createLinkedTestCase", { testCase: {
        projectKey: selectedKey,
        title: title.trim(),
        description: description.trim() || null,
        preconditions: preconditions.trim() || null,
        priority: priority.value,
        tags: tags.split(",").map((tag) => tag.trim()).filter(Boolean),
        steps: steps.map((step) => ({ action: step.action.trim(), inputData: step.inputData.trim() || null, expectedResult: step.expectedResult.trim() })),
        automationReference: null,
      } });
      if (result.kind === "ok") onCreated();
      else setError(result.kind === "unauthorized" ? "The Testryn token needs write access." : "The test case could not be created and linked.");
    } catch (ignored) {
      setError("Testryn is currently unavailable. Please try again.");
    } finally {
      setBusy(false);
    }
  };

  if (projects === null && !error) return <Spinner size="medium" label="Loading Testryn projects" />;

  return <Box padding="space.150" xcss={panelStyles}><Stack space="space.150">
    <Inline spread="space-between" alignBlock="center"><Text weight="bold">Create and link a Testryn test case</Text><Button appearance="subtle" onClick={onCancel}>Cancel</Button></Inline>
    <Stack space="space.075">
      <Inline space="space.075"><Button appearance={!creatingProject ? "primary" : "subtle"} onClick={() => setCreatingProject(false)} isDisabled={!projectOptions.length}>Existing project</Button>
        <Button appearance={creatingProject ? "primary" : "subtle"} onClick={() => setCreatingProject(true)}>New project</Button></Inline>
      {creatingProject ? <Inline space="space.100" shouldWrap>
        <Stack space="space.050"><Text size="small" weight="bold">Project key</Text><Textfield value={projectKey} onChange={(event) => setProjectKey(event.target.value.toUpperCase())} placeholder="EVAL" /></Stack>
        <Stack space="space.050"><Text size="small" weight="bold">Project name</Text><Textfield value={projectName} onChange={(event) => setProjectName(event.target.value)} placeholder="Evaluation tests" /></Stack>
      </Inline> : <Stack space="space.050"><Text size="small" weight="bold">Project</Text><Select options={projectOptions} value={selectedProject} onChange={setSelectedProject} isSearchable /></Stack>}
    </Stack>
    <Stack space="space.050"><Text size="small" weight="bold">Test title</Text><Textfield value={title} onChange={(event) => setTitle(event.target.value)} /></Stack>
    <Stack space="space.050"><Text size="small" weight="bold">Description</Text><TextArea value={description} onChange={(event) => setDescription(event.target.value)} /></Stack>
    <Stack space="space.050"><Text size="small" weight="bold">Preconditions</Text><TextArea value={preconditions} onChange={(event) => setPreconditions(event.target.value)} /></Stack>
    <Inline space="space.100" shouldWrap>
      <Stack space="space.050"><Text size="small" weight="bold">Priority</Text><Select options={["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((value) => ({ value, label: value.charAt(0) + value.slice(1).toLowerCase() }))} value={priority} onChange={setPriority} /></Stack>
      <Stack space="space.050"><Text size="small" weight="bold">Tags (comma separated)</Text><Textfield value={tags} onChange={(event) => setTags(event.target.value)} placeholder="smoke, regression" /></Stack>
    </Inline>
    <Stack space="space.100"><Text weight="bold">Test steps</Text>
      {steps.map((step, index) => <Box key={`new-step-${index}`} padding="space.100" xcss={panelStyles}><Stack space="space.075">
        <Inline spread="space-between" alignBlock="center"><Text weight="bold">Step {index + 1}</Text><Inline space="space.050">
          <Button appearance="subtle" isDisabled={index === 0} onClick={() => moveStep(index, -1)}>Up</Button>
          <Button appearance="subtle" isDisabled={index === steps.length - 1} onClick={() => moveStep(index, 1)}>Down</Button>
          <Button appearance="subtle" isDisabled={steps.length === 1} onClick={() => setSteps((current) => current.filter((_, position) => position !== index))}>Remove</Button>
        </Inline></Inline>
        <Stack space="space.050"><Text size="small" weight="bold">Action</Text><TextArea value={step.action} onChange={(event) => updateStep(index, "action", event.target.value)} /></Stack>
        <Stack space="space.050"><Text size="small" weight="bold">Input / Data</Text><TextArea value={step.inputData} onChange={(event) => updateStep(index, "inputData", event.target.value)} placeholder="Optional test data, credentials or values" /></Stack>
        <Stack space="space.050"><Text size="small" weight="bold">Expected result</Text><TextArea value={step.expectedResult} onChange={(event) => updateStep(index, "expectedResult", event.target.value)} /></Stack>
      </Stack></Box>)}
      <Button appearance="subtle" onClick={() => setSteps((current) => [...current, emptyStep()])}>Add step</Button>
    </Stack>
    {error ? <SectionMessage appearance="error"><Text>{error}</Text></SectionMessage> : null}
    <Inline space="space.100"><Button appearance="primary" isDisabled={!validProject || !validTest || busy} onClick={create}>{busy ? "Creating..." : "Create and link test case"}</Button><Button appearance="subtle" isDisabled={busy} onClick={onCancel}>Cancel</Button></Inline>
  </Stack></Box>;
}

function toOption(project) {
  return { value: project.key, label: `${project.name} (${project.key})` };
}
