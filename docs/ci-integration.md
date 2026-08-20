# CI Integration

How an external automated-test pipeline reports results back to Testryn, closing the
loop:

```
Jira Story
   -> Testryn Requirement Link
   -> Test Cases (with an automationReference each)
   -> Test Plan / Execution
   -> external test framework runs the tests
   -> CI pipeline maps results to Testryn's format
   -> testryn-publisher
   -> Bulk Result Update API
   -> PASSED / FAILED / SKIPPED / BLOCKED in Testryn
```

Testryn never executes tests itself. It owns test definitions, traceability,
executions, results, and history -- the automated framework (JUnit, Playwright,
Selenium, whatever) stays entirely outside Testryn's domain model.

## 1. One-time setup: map test cases to automated tests

Give each Testryn test case that has an automated counterpart a stable
`automationReference` (ADR 0009) -- a machine-friendly string, e.g.
`auth.login.valid`. Not framework-specific: it's not a JUnit class name or a
Selenium test ID, just a stable label your CI mapping step can look up.

```bash
curl -X PUT http://localhost:8080/api/v1/test-cases/{id} \
  -H "Content-Type: application/json" \
  -d '{"title":"Login with valid credentials","priority":"HIGH","status":"ACTIVE",
       "tags":[],"steps":[...],"automationReference":"auth.login.valid"}'
```

Test cases without an `automationReference` are simply not automatable via this
path -- they stay manual-only, which is the common case and is fine.

## 2. Per pipeline run: create an execution

Either from a test plan (a new iteration, ADR 0003):

```bash
curl -X POST http://localhost:8080/api/v1/test-plans/{planId}/executions \
  -H "Content-Type: application/json" -d '{"name":"Nightly regression #482"}'
```

or ad-hoc, from an explicit set of test case IDs:

```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectKey}/executions \
  -H "Content-Type: application/json" \
  -d '{"name":"Smoke run","testCaseIds":["<id-1>","<id-2>"]}'
```

Either way, note the returned `id` -- that's the `executionId` the publisher needs.

## 3. Run your tests, map their results

Your CI step (or a small adapter script) turns whatever your test framework produced
into the publisher's plain JSON format. Each entry needs at minimum an
`automationReference` (or `resultId`, if you already have Testryn's internal UUID)
and a `status`.

```json
{
  "executionId": "6287af96-50fc-460a-a898-d2a2ab419458",
  "results": [
    {"automationReference": "auth.login.valid", "status": "PASSED", "durationMs": 1420},
    {"automationReference": "auth.login.invalid", "status": "FAILED",
     "durationMs": 890, "actualResult": "HTTP 500", "failureDetails": "Expected HTTP 200"},
    {"automationReference": "auth.login.locked", "status": "SKIPPED"}
  ]
}
```

A field you leave out of an entry keeps its current value in Testryn -- re-reporting
a test case with only `{"automationReference": "...", "status": "PASSED"}` never
erases a comment or an `actualResult` a person had added by hand. `executor` defaults
to `"ci"` if you don't set it.

Only test cases that are actually part of *this* execution can be targeted; an
unknown or out-of-scope `automationReference` fails the whole request (see step 4) --
it never silently creates a test case.

## 4. Publish

```bash
java -jar testryn-publisher.jar publish \
  --base-url http://localhost:8080 \
  --execution-id 6287af96-50fc-460a-a898-d2a2ab419458 \
  --results results.json
```

(`--execution-id` may be omitted if the JSON already carries `executionId`; `--results`
may be omitted, or `-`, to read from stdin instead of a file.)

The whole batch is atomic (ADR 0010): if any single entry is invalid (unknown
reference, wrong execution, invalid status, ...), nothing in the request is applied,
and the tool exits non-zero with every problem listed:

```
Bulk result update request is invalid: 1 of 3 entries have a problem (HTTP 400)
  - auth.login.typo: no test case with automationReference 'auth.login.typo' is part of execution 6287af96-...
```

On success:

```
Bulk update succeeded (3 result(s))
  BIT-TC-1 [auth.login.valid] -> PASSED (1420ms)
  BIT-TC-2 [auth.login.invalid] -> FAILED (890ms)
  BIT-TC-3 [auth.login.locked] -> SKIPPED
```

Exit codes: `0` success, `1` the request reached Testryn but failed, `2` a usage
error. See [tools/testryn-publisher/README.md](../tools/testryn-publisher/README.md)
for the full CLI reference and [ADR 0011](adr/0011-ci-publisher.md) for the design
decisions behind the tool itself.

## Calling the Bulk API directly (no publisher)

The publisher is a convenience, not a requirement -- any HTTP client can call the
same endpoint directly:

```bash
curl -X PATCH http://localhost:8080/api/v1/executions/{executionId}/results \
  -H "Content-Type: application/json" \
  -d '{
    "results": [
      {"automationReference": "auth.login.valid", "status": "PASSED", "durationMs": 1420, "executor": "ci"},
      {"automationReference": "auth.login.invalid", "status": "FAILED", "durationMs": 890,
       "executor": "ci", "actualResult": "HTTP 500", "failureDetails": "Expected HTTP 200"}
    ]
  }'
```

Full request/response schema and error format: `GET /swagger-ui.html` on a running
Testryn instance, or `docs/adr/0010-bulk-result-update.md`.

## What this block does not cover

No JUnit XML / Playwright / Cypress / Allure report parsing -- the publisher's input
is a plain, framework-agnostic JSON your own CI step produces. `ResultBatchReader`
(see ADR 0011) is the seam a later importer would plug into, without touching the
publisher's core or the Bulk API. No authentication is enforced by the API yet
(ADR 0011, Abschnitt 20) -- do not expose a Testryn instance you use for real CI
results to the public internet until that lands.
