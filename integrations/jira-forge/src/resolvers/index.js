import Resolver from "@forge/resolver";
import { getCoverage } from "./testrynClient";

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

export const handler = resolver.getDefinitions();
