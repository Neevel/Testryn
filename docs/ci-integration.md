# CI Integration

How an external automated-test pipeline reports results back to Testryn, closing the
loop:

```
Jira Story
   -> Testryn Requirement Link
   -> Test Cases (with an automationReference each)
   -> Test Plan / Execution
   -> external test framework runs the tests
   -> JUnit/Surefire XML directly, or a CI step maps results to Testryn's plain JSON
   -> TESTRYN_API_TOKEN
        -> testryn-publisher (publish-junit or publish)
        -> Authorization: Bearer <token>
        -> Bulk Result Update API
   -> PASSED / FAILED / SKIPPED / BLOCKED in Testryn
```

Testryn never executes tests itself. It owns test definitions, traceability,
executions, results, and history -- the automated framework (JUnit, Playwright,
Selenium, whatever) stays entirely outside Testryn's domain model.

## 0. Authenticate

As of [ADR 0012](adr/0012-service-token-authentication.md), every API call needs a
service token with at least the `testryn:write` scope. Create one (requires an
`admin`-scoped token yourself, or use the bootstrap token -- see
[docs/security.md](security.md)):

```bash
curl -X POST http://localhost:8080/api/v1/service-tokens \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TESTRYN_ADMIN_TOKEN" \
  -d '{"name":"CI Pipeline","description":"Publisher token","scopes":["testryn:read","testryn:write"]}'
```

The response includes the raw token **exactly once** -- copy it into your CI
pipeline's secret store as `TESTRYN_API_TOKEN`. It cannot be retrieved again; if
it's lost, revoke it and create a new one. Never put a real token value in a
pipeline config file, a repository, or a log -- use your CI platform's secrets
mechanism (GitHub Actions secrets, GitLab CI/CD variables, ...).

Every example below assumes `TESTRYN_API_TOKEN` is set in the environment.

## 1. One-time setup: map test cases to automated tests

Give each Testryn test case that has an automated counterpart a stable
`automationReference` (ADR 0009) -- a machine-friendly string, e.g.
`auth.login.valid`. Not framework-specific: it's not a JUnit class name or a
Selenium test ID, just a stable label your CI mapping step can look up.

```bash
curl -X PUT http://localhost:8080/api/v1/test-cases/{id} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TESTRYN_API_TOKEN" \
  -d '{"title":"Login with valid credentials","priority":"HIGH","status":"ACTIVE",
       "tags":[],"steps":[...],"automationReference":"auth.login.valid"}'
```

Test cases without an `automationReference` are simply not automatable via this
path -- they stay manual-only, which is the common case and is fine.

## 2. Per pipeline run: create an execution

Either from a test plan (a new iteration, ADR 0003):

```bash
curl -X POST http://localhost:8080/api/v1/test-plans/{planId}/executions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TESTRYN_API_TOKEN" \
  -d '{"name":"Nightly regression #482"}'
```

or ad-hoc, from an explicit set of test case IDs:

```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectKey}/executions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TESTRYN_API_TOKEN" \
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
export TESTRYN_API_TOKEN=<the write-scoped token from step 0, from your CI secret store>

java -jar testryn-publisher.jar publish \
  --base-url http://localhost:8080 \
  --execution-id 6287af96-50fc-460a-a898-d2a2ab419458 \
  --results results.json
```

(`--execution-id` may be omitted if the JSON already carries `executionId`; `--results`
may be omitted, or `-`, to read from stdin instead of a file. `TESTRYN_API_TOKEN` is
read from the environment only -- never pass a token as a CLI argument, it would end
up in shell history and process listings.)

Without a valid token, or with a `read`-only one, the publish fails fast with a clear
message and a non-zero exit code -- never a silent no-op:

```
Authentication is required. (HTTP 401)
```
```
The service token does not have the required scope. (HTTP 403)
```

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

## 5. Maven Surefire / Failsafe: skip the JSON entirely

If your automated tests are plain Maven/JUnit, `publish-junit` reads
`target/surefire-reports` (or `target/failsafe-reports` -- same XML schema, no
special-casing needed) directly, closing the loop without a hand-built results file:

```
mvn test -> target/surefire-reports/*.xml -> testryn-publisher publish-junit -> Testryn
```

### One-time setup: give each test case a `classname#name` reference

```bash
curl -X PUT http://localhost:8080/api/v1/test-cases/{id} \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TESTRYN_API_TOKEN" \
  -d '{"title":"Login with valid credentials","priority":"HIGH","status":"ACTIVE",
       "tags":[],"steps":[...],
       "automationReference":"com.example.LoginTest#successfulLogin"}'
```

`classname#name`, taken verbatim from the XML `<testcase>` attributes -- not just the
bare method name, since method names alone collide across classes project-wide. See
[ADR 0013](adr/0013-junit-xml-automation-reference.md) for the full convention,
including how it handles parameterized/dynamic test display names.

### Per pipeline run

```bash
mvn test  # produces target/surefire-reports/*.xml, testFailureIgnore is your call

export TESTRYN_API_TOKEN=<write-scoped token from step 0>

java -jar testryn-publisher.jar publish-junit \
  --base-url http://localhost:8080 \
  --execution-id 4711 \
  --results target/surefire-reports
```

`--results` also accepts individual files, several `--results` flags, or a
shell-expanded glob (`--results target/surefire-reports/*.xml`) -- see
[tools/testryn-publisher/README.md](../tools/testryn-publisher/README.md) for the
full flag reference, dry-run preview format, and security hardening notes (JUnit XML
is untrusted input -- the parser is hardened against XXE).

### Result semantics

| JUnit XML | Testryn status |
|---|---|
| no `<failure>`/`<error>`/`<skipped>` child | `PASSED` |
| `<failure>` | `FAILED` |
| `<error>` | `FAILED` |
| `<skipped>` | `SKIPPED` |
| *(none of the above -- always a manual/system decision)* | `BLOCKED` |

`BLOCKED` is never produced by the JUnit importer -- there is no JUnit XML construct
that means "blocked", and guessing one would misrepresent the actual test outcome.

**Step-level results** (ADR 0015): JUnit XML has no concept of a Testryn test
case's individual steps, so `publish-junit` only ever writes the testcase-level
result above -- it never touches step results, which stay exactly `NOT_RUN`. This
is intentional, not a gap: a JUnit `PASSED` does not imply any particular step
passed, and inventing per-step PASSED values from a testcase-level result would
misrepresent the actual test outcome the same way a fabricated `BLOCKED` would. Both
the Testryn runner and the Jira Forge panel show this state honestly ("Step-level
results not reported for this execution"), not as if every step had run. A future
framework-native adapter that *does* report real step outcomes (Selenium,
Playwright, a custom harness) would write to the same step-result API a manual
tester uses (`PATCH .../executions/{id}/step-results`, see
`docs/execution-model.md`) -- nothing in this block's design forecloses that; no
such adapter is built here.

### Jenkins

```groovy
pipeline {
    agent any
    environment {
        TESTRYN_API_TOKEN = credentials('testryn-ci-token') // Jenkins credential ID, never a literal value here
    }
    stages {
        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }
        stage('Publish to Testryn') {
            steps {
                sh '''
                    java -jar testryn-publisher.jar publish-junit \
                      --base-url https://testryn.internal \
                      --execution-id ${TESTRYN_EXECUTION_ID} \
                      --results target/surefire-reports
                '''
            }
        }
    }
}
```

`credentials('testryn-ci-token')` pulls the token from Jenkins' own credential store
at runtime and masks it in the build log -- no real token value is ever written into
the pipeline definition itself.

### GitHub Actions

```yaml
- run: mvn test
- run: |
    java -jar testryn-publisher.jar publish-junit \
      --base-url https://testryn.internal \
      --execution-id ${{ inputs.execution_id }} \
      --results target/surefire-reports
  env:
    TESTRYN_API_TOKEN: ${{ secrets.TESTRYN_API_TOKEN }}
```

## Calling the Bulk API directly (no publisher)

The publisher is a convenience, not a requirement -- any HTTP client can call the
same endpoint directly:

```bash
curl -X PATCH http://localhost:8080/api/v1/executions/{executionId}/results \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TESTRYN_API_TOKEN" \
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

## What this covers, and what's still not built

JUnit-compatible XML (Maven Surefire and Failsafe) is a supported input format as of
`publish-junit` (see section 5 above and ADR 0013) -- `ResultBatchReader` (ADR 0011)
was exactly the seam this plugged into, without any change to the publisher's core or
the Bulk API. Playwright, Cypress, and Allure report parsing are not built -- the
same `ResultBatchReader` seam is where a later importer for any of those would go.
Authentication is enforced on every endpoint (ADR 0012) -- see
[docs/security.md](security.md) for scopes, token management, and bootstrap. Still
not built: rate limiting, human user login for the frontend (a service token is a
machine credential, not a personal one -- see `docs/security.md` for the frontend's
interim story), and any execution-state guard that would reject publishing into a
`COMPLETED`/`ABORTED` execution (see `BACKLOG.md`).
