# testryn-jira-forge

A read-only Atlassian Forge issue panel: shows Testryn test coverage for the current
Jira Story/Task/Bug/Epic (linked test cases, steps, expected results, latest
execution status) directly inside Jira. Testryn stays the source of truth -- this
app stores nothing itself, it reads Testryn's REST API on every render. See
[docs/jira-forge-integration.md](../../docs/jira-forge-integration.md) for the full
setup/deployment guide and [ADR 0014](../../docs/adr/0014-jira-forge-integration.md)
for the design decisions.

## Structure

```
manifest.yml            Forge app manifest (jira:issuePanel module, permissions, env vars)
src/frontend/           UI Kit panel (App.jsx, TestCaseCard.jsx, statusMeta.js,
                         coverageView.js -- pure rendering-decision logic, kept
                         separate from the UI Kit components so it's unit-testable;
                         UI Kit itself compiles to Forge's bridge protocol, not real
                         DOM, so it cannot be rendered/asserted against in Jest)
src/resolvers/          Backend resolver -- the only thing that ever calls Testryn
test/                   Jest tests for the resolver (issue-key extraction, every
                         Testryn response state, no-token-leakage) and for
                         coverageView.js (step summarization, failure filtering,
                         truncation, executor labeling, attention-first ordering)
```

## Develop

```bash
npm install
npm test
```

## Deploy

See [docs/jira-forge-integration.md](../../docs/jira-forge-integration.md) --
`forge login`, `forge register`, `forge variables set`, `forge deploy`,
`forge install`.
