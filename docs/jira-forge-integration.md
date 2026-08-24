# Jira Forge Integration

A read-only Testryn coverage panel inside every Jira Story/Task/Bug/Epic that has
linked test cases -- "which tests cover this issue, what do they check, and are they
currently green or red?", without leaving Jira. See
[ADR 0014](adr/0014-jira-forge-integration.md) for the design rationale and
[PROJECT_STATUS.md](../PROJECT_STATUS.md) for the live verification record.

```
Jira Issue (Story/Task/Bug/Epic)
   |
   v
Forge Issue Panel (integrations/jira-forge)
   |  invoke("getCoverage")
   v
Forge resolver (backend, never the browser)
   |  GET /api/v1/requirement-links/coverage?provider=jira&externalKey=<issue key>
   |  Authorization: Bearer <TESTRYN_API_TOKEN>
   v
Testryn REST API
   |
   v
RequirementLink -> TestCase (+ current version, steps) -> latest ExecutionResult
```

Testryn is the sole source of truth for test data. Jira stores no copy of it --
the panel is read entirely on demand, on every render, from Testryn's own API.

## Architecture

- **Module**: `integrations/jira-forge`, a standalone Forge app -- not mixed into
  `frontend/` (Abschnitt 33). It has its own `package.json`, `manifest.yml`, and
  test suite.
- **UI technology**: [UI Kit](https://developer.atlassian.com/platform/forge/ui-kit/)
  (`@forge/react`), not Custom UI. UI Kit renders Jira-native components
  server-declared as React, with **no iframe** at all (Abschnitt 27) -- Custom UI's
  iframe-based rendering was deliberately not used, since UI Kit's component set
  (`Box`, `Stack`, `Inline`, `Text`, `Lozenge`, `Icon`, `Link`, `Button`,
  `SectionMessage`, `EmptyState`, `Spinner`) is entirely sufficient for this panel's
  needs (Abschnitt 25).
- **Module type**: [`jira:issuePanel`](https://developer.atlassian.com/platform/forge/manifest-reference/modules/jira-issue-panel/)
  (Abschnitt 3) -- renders on Story/Task/Bug/Epic wherever Jira places the module,
  never hardcoded to one issue type or one issue key (Abschnitt 28/29). The current
  issue's key comes from the platform-supplied invocation context
  (`context.extension.issue.key`), read **server-side** in the resolver -- not from
  a manually-entered field, and not trusted from a frontend-supplied payload value
  (Abschnitt 29).
- **Data flow**: exactly one `invoke("getCoverage")` call per panel render, which
  resolves to exactly one Testryn HTTP request
  (`GET /api/v1/requirement-links/coverage`, Abschnitt 22) -- never one request per
  linked test case. See "Testryn API" below for how that single endpoint avoids N+1
  queries on the Testryn side too.
- **Security model**: the Forge **resolver** (a backend function, never the
  browser) is the only thing that ever calls Testryn, and the only place
  `TESTRYN_API_TOKEN` is read (Abschnitt 7/8). The frontend never sees the token,
  never sees an `Authorization` header, and cannot reach Testryn directly.

```
Forge backend/resolver  -->  Testryn API      (this is what's built)
Jira Browser  --X-->  raw Testryn service token  -->  Testryn API   (never this)
```

## Testryn API: the requirement-coverage read view

`GET /api/v1/requirement-links/coverage?provider=jira&externalKey=EVAL-47[&limit=20]`

Provider-neutral by design (Abschnitt 6): `provider` is a plain, case-insensitive
query parameter, not baked into the path -- Jira is simply the first (and, for now,
only) caller. Nothing in the Testryn core knows anything Jira-specific; the endpoint
lives in the existing `requirement` module next to the rest of the requirement-link
API. Empty result (no linked test cases) is `200 OK` with `testCases: []`, not an
error. Capped at `limit` (default 20, max 100); `totalCount` reports the real total
so a caller can offer "view all" instead of silently truncating (Abschnitt 23).

```json
{
  "requirement": { "provider": "JIRA", "externalKey": "EVAL-47" },
  "testCases": [
    {
      "id": "6287af96-...",
      "humanId": "BIT-TC-14",
      "title": "Successful login",
      "status": "ACTIVE",
      "priority": "HIGH",
      "version": 2,
      "preconditions": "User is on the login page",
      "steps": [
        { "order": 1, "action": "Enter valid username", "expectedResult": "Username is accepted" }
      ],
      "latestExecution": {
        "executionId": "9e1c...",
        "executionName": "Regression Login - Iteration 2",
        "status": "PASSED",
        "executedAt": "2026-08-24T10:00:00Z",
        "durationMs": 1420,
        "executor": "ci",
        "steps": [
          {
            "position": 1,
            "action": "Enter valid username",
            "expectedResult": "Username is accepted",
            "result": { "status": "PASSED", "actualResult": "Username is accepted", "failureDetails": null }
          }
        ]
      }
    }
  ],
  "totalCount": 1
}
```

`latestExecution` is `null` only when the test case has never been added to any
execution at all -- a test case added to an execution that has not run yet still has
a real `latestExecution` with `status: "NOT_RUN"` (Abschnitt 14: that is a real
execution, not "no execution yet"). `latestExecution.steps` is that execution's own
pinned step snapshot with its actual result each (ADR 0015) -- an empty list means
step-level results were never reported for that execution (an execution that
predates the Step-Level Execution Results block, or a testcase-level-only
automation result such as JUnit, Abschnitt 20/36/35) -- the panel shows "Step-level
results not reported for this execution" rather than fabricating a per-step status.

**Query strategy** (Abschnitt 22/25): the whole response is built from exactly two
database round trips regardless of how many test cases are linked -- one batch
fetch for the test cases (with their current version and steps, via the existing
entity graph), and one batch "latest execution per test case" query (a single JPQL
correlated-subquery, `ExecutionTestCaseRepository.findLatestByTestCaseIds`, now also
fetch-joining each step result and its step) -- never one query per test case, and
adding step data did not add a third round trip. Covered by a dedicated regression
test
(`RequirementCoverageTest.theQueryCountDoesNotGrowLinearlyWithTheNumberOfLinkedTestCases`)
using Hibernate's own query-execution counter, not just an assumption about the
code's shape.

Requires `testryn:read` like every other `GET` under `/api/**` (ADR 0012) -- no new
security rule was needed.

## Installation

```bash
cd integrations/jira-forge
npm install
```

## Forge Setup

1. Install the Forge CLI (already a project devDependency, or globally):
   ```bash
   npm install -g @forge/cli
   ```
2. Log in with the Atlassian account that will own this app:
   ```bash
   forge login
   ```
   (Non-interactively, e.g. for CI: `forge login --non-interactive -u <email> -t <Atlassian API token>` --
   the API token is created the same way as any other Atlassian API token, at
   `id.atlassian.com` → Account Settings → Security → API tokens.)
3. Register a real app identity (the `manifest.yml` in this repo ships with a
   placeholder `app.id` -- Abschnitt 48 forbids committing a real one anyway, since
   it is tied to one Atlassian account):
   ```bash
   forge register
   ```
   This rewrites `app.id` in `manifest.yml` to a real
   `ari:cloud:ecosystem::app/<uuid>`.

## Testryn Base URL

Two plain (non-secret) environment variables, declared in `manifest.yml` with
localhost defaults for local development:

| Variable | Purpose | Example |
|---|---|---|
| `TESTRYN_API_BASE_URL` | Where the resolver sends its REST call | `https://testryn.example.com` |
| `TESTRYN_APP_BASE_URL` | Where "Open in Testryn" links point (the frontend, not the API) | `https://app.testryn.example.com` |

**Localhost problem** (Abschnitt 10): Forge Cloud runs its resolver in Atlassian's
own infrastructure, which cannot reach a developer's `http://localhost:8080`. For
live verification against a local Testryn stack, expose it first through a secure
tunnel (e.g. `cloudflared tunnel --url http://localhost:8080`, or `ngrok http 8080`)
and use that tunnel's HTTPS hostname as `TESTRYN_API_BASE_URL` -- never leave a
tunnel open longer than the verification session, and never point it at an
unauthenticated backend (Testryn's own service-token auth, ADR 0012, still applies
in full over the tunnel). For anything beyond a one-off dev verification, point
these variables at an already-deployed, real Testryn instance instead.

Set them for a real (non-default) value:

```bash
forge variables set -e development TESTRYN_API_BASE_URL "https://<your-tunnel-or-host>"
forge variables set -e development TESTRYN_APP_BASE_URL "https://<your-tunnel-or-host-frontend>"
```

`manifest.yml`'s `permissions.external.fetch.backend` entry must also list this same
host -- Forge rejects any outbound `fetch` to a domain not explicitly allow-listed
there (Abschnitt 7's egress control, enforced by the platform itself, not just this
app's own code). Update that entry, then re-run `forge deploy`.

## Service Token

Create a **dedicated**, `testryn:read`-scoped token for this app -- never reuse a
token issued for the CI publisher or any other client (Abschnitt 8):

```bash
curl -X POST https://<testryn-host>/api/v1/service-tokens \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TESTRYN_ADMIN_TOKEN" \
  -d '{"name":"Jira Forge Panel","description":"Read-only, MVP issue panel","scopes":["testryn:read"]}'
```

Copy the raw token value (shown exactly once) and store it as an **encrypted**
Forge environment variable -- never in `manifest.yml`, never in source, never in
Git:

```bash
forge variables set --encrypt -e development TESTRYN_API_TOKEN "<the raw token>"
forge deploy   # required after any variable change
```

No `write` scope is requested anywhere: this MVP performs no writes to Testryn or to
Jira (Abschnitt 8/47).

## Deployment

```bash
forge deploy -e development
forge install -e development --site <your-site>.atlassian.net --product jira
```

For active local development against a running Testryn stack, `forge tunnel`
streams your local resolver/frontend code changes live into the installed app
without a full `forge deploy` each time:

```bash
forge tunnel
```

## Issue Panel

Renders on Story/Task/Bug/Epic (wherever Jira places a `jira:issuePanel`, Abschnitt
28), titled "Testryn" with the Testryn signet (Jira renders this panel chrome
itself from `manifest.yml`'s `title`/`icon`, Abschnitt 26 -- the panel's own content
does not repeat it). States:

- **Loading**: a single, fixed-height spinner -- no layout jump once data arrives
  (Abschnitt 20).
- **Coverage summary + list**: real counts only (`N linked tests · X passed · Y
  failed · ...`, Abschnitt 15), each linked test case collapsed by default
  (Abschnitt 12/28) showing id/title/status/version/priority, its latest-execution
  status as text + icon + color together (Abschnitt 13/30, never color alone), and
  a `X / Y steps passed` line when step data exists.
  Expanding a card (Abschnitt 29) reveals preconditions and, for each step: its own
  status (✓/✕/!/–/○, Abschnitt 30), action, expected result, actual result, and
  failure details if it failed. A failed test case never truncates its step list --
  the failing step is visible the instant the card expands, no extra click
  (Abschnitt 32); an all-green case with many steps still collapses to a short
  preview with a "Show all N steps" link (Abschnitt 31). Executor is shown as a
  compact `CI`/`Manual` badge next to the status (Abschnitt 33/34, from the same
  `executor` field the runner and publisher already write -- no new source-of-truth
  concept). A test case whose latest execution has no step data (pre-ADR-0015, or a
  JUnit/testcase-level-only automation result) shows "Step-level results not
  reported for this execution" and falls back to the test case's plain current-step
  list as a preview, rather than showing nothing or fabricating a status (Abschnitt
  35/36). Every card has an "Open in Testryn" link; a card with a latest execution
  also gets "Open latest execution" (Abschnitt 16).
- **Empty** (no linked test cases): "No Testryn test cases linked" with a link to
  Testryn -- no create-from-Jira action in this MVP (Abschnitt 17).
- **Error** (Testryn unreachable): "Testryn is currently unavailable. Existing Jira
  data is unaffected." -- no stack trace, no technical detail (Abschnitt 18).
- **Unauthorized** (bad/missing/revoked token): "Testryn connection is not
  authorized." -- no detail about the secret itself (Abschnitt 19).

## Security

- The Forge **resolver**, not the browser, holds and uses `TESTRYN_API_TOKEN`
  (Abschnitt 7) -- see `src/resolvers/testrynClient.js`.
- `TESTRYN_API_TOKEN` is declared nowhere in `manifest.yml` (only the two plain
  base-URL variables are); it exists only as an encrypted Forge environment
  variable, set via `forge variables set --encrypt` (Abschnitt 8/36).
- The resolver's response to the frontend never includes the token, an
  `Authorization` header, or a raw upstream error/exception object -- every failure
  path returns one of exactly three small, tagged results (`unavailable`,
  `unauthorized`, or `ok` with only the fields the panel renders). Verified by
  `test/testrynClient.test.js`'s `theConfiguredTokenValueNeverAppearsInAFailureOutcome`-style
  assertions (Abschnitt 38/43).
- `permissions.scopes: []` -- no Jira scope is requested at all; the panel only
  ever reads the current issue's own key from the invocation context it is already
  given (Abschnitt 29), never calling the Jira REST API itself.
- No `write` scope requested from Testryn either (Abschnitt 8/47) -- a read-only
  token cannot be used to perform writes even if the app's own code had a bug that
  attempted one.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Panel stuck on "Loading" | Check `forge tunnel`/deploy logs (`forge logs`) for a resolver exception; the frontend only ever sees `unavailable`/`unauthorized`/`ok`, so a stuck spinner means the `invoke()` promise itself never resolved. |
| "Testryn is currently unavailable." | `TESTRYN_API_BASE_URL` unreachable from Forge's runtime (classic cause: pointed at `localhost` instead of a tunnel/real host, Abschnitt 10), or the host is not in `permissions.external.fetch.backend` (Forge silently rejects the fetch with a `REQUEST_EGRESS_ALLOWLIST_ERR`-style error, which this app maps to the same generic "unavailable" state). |
| "Testryn connection is not authorized." | `TESTRYN_API_TOKEN` missing, wrong, expired, or revoked -- check via `docs/security.md`'s service-token management, not this doc's own logs (the token itself is never logged). |
| "No Testryn test cases linked" on an issue you know has links | Requirement links use `externalKey` case-insensitively but must match the Jira issue key exactly otherwise (e.g. project key typo); confirm via `GET /api/v1/projects/{key}/requirements` on the Testryn side. |
| Changes to `manifest.yml` not taking effect | `forge deploy` (not just saving the file) is required for both module/permission changes and any `forge variables set` — see Deployment above. |
