import Resolver from "@forge/resolver";
import { createLinkedTestCase, createProject, getCoverage, getProjects, updateTestCaseDefinition } from "./testrynClient";

const resolver = new Resolver();

/**
 * The panel's single resolver call (Abschnitt 22: one Forge request -> one/few
 * Testryn requests, never one per test case). Reads the current issue's key
 * directly from the platform-supplied invocation context rather than trusting a
 * frontend-supplied payload value -- the same context useProductContext() exposes
 * client-side, just read server-side instead (Abschnitt 29).
 */
resolver.define("getCoverage", async (req) => {
  const issueKey = req?.context?.extension?.issue?.key;
  if (!issueKey) {
    return { kind: "unavailable" };
  }
  return getCoverage(issueKey);
});

resolver.define("updateTestCaseDefinition", async (req) => {
  const issueKey = req?.context?.extension?.issue?.key;
  const { testCaseId, definition } = req?.payload ?? {};
  if (!issueKey || typeof testCaseId !== "string") return { kind: "invalid" };
  return updateTestCaseDefinition(issueKey, testCaseId, definition);
});

resolver.define("getProjects", async () => getProjects());

resolver.define("createProject", async (req) => createProject(req?.payload?.project));

resolver.define("createLinkedTestCase", async (req) => {
  const issueKey = req?.context?.extension?.issue?.key;
  if (!issueKey) return { kind: "invalid" };
  return createLinkedTestCase(issueKey, req?.payload?.testCase);
});

export const handler = resolver.getDefinitions();
