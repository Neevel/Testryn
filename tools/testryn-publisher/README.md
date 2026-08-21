# testryn-publisher

A small, standalone CLI that reports automated test results to Testryn's bulk
result-update API from a CI pipeline. Two input formats, same underlying publish
path: Testryn's own plain JSON (`publish`), or JUnit-compatible XML straight out of
`target/surefire-reports`/`target/failsafe-reports` (`publish-junit`). See
[docs/ci-integration.md](../../docs/ci-integration.md) for the full workflow,
[ADR 0011](../../docs/adr/0011-ci-publisher.md) for the publisher's own design
decisions, and [ADR 0013](../../docs/adr/0013-junit-xml-automation-reference.md) for
the JUnit `automationReference` convention specifically.

## Build

```bash
cd tools/testryn-publisher
mvn package
```

Produces `target/testryn-publisher.jar` — a self-contained, executable jar (Jackson
is bundled; no other runtime dependency beyond the JDK).

## Use

```bash
java -jar target/testryn-publisher.jar publish \
  --base-url http://localhost:8080 \
  --execution-id <execution-id> \
  --results results.json
```

- `--results` may be omitted (or set to `-`) to read the results JSON from stdin.
- `--execution-id` may be omitted if the results JSON itself carries an
  `"executionId"` field.
- Set `TESTRYN_API_TOKEN` in the environment to send it as a bearer token (never as a
  CLI argument -- CLI arguments end up in shell history and process listings). As of
  ADR 0012, the backend now enforces this: a `testryn:write`-scoped token is
  required, or the publish fails with a clean 401/403 message. See
  [docs/security.md](../../docs/security.md) for how to create one.

Exit codes: `0` success, `1` the request reached Testryn but failed (validation
error, HTTP error, missing/invalid/insufficiently-scoped token, or a transport
failure), `2` a usage error (bad/missing arguments).

## Results JSON format

```json
{
  "executionId": "6287af96-50fc-460a-a898-d2a2ab419458",
  "results": [
    {"automationReference": "auth.login.valid", "status": "PASSED", "durationMs": 1420},
    {"automationReference": "auth.login.invalid", "status": "FAILED",
     "actualResult": "HTTP 500", "failureDetails": "Expected HTTP 200"}
  ]
}
```

Each entry identifies its target test case's result via `resultId` and/or
`automationReference` (at least one required). A field left out of an entry keeps its
current value on the server -- only fields you actually include are changed. `executor`
defaults to `"ci"` if not given.

## JUnit XML (`publish-junit`)

Reads Maven Surefire/Failsafe XML directly -- no hand-built JSON needed:

```bash
mvn test
java -jar target/testryn-publisher.jar publish-junit \
  --base-url http://localhost:8080 \
  --execution-id <execution-id> \
  --results target/surefire-reports
```

- `--execution-id` is **required** (JUnit XML never carries one, unlike the JSON
  workflow).
- `--results` accepts one or more files and/or directories: a single report file, a
  directory (its direct `*.xml` children only, not recursive -- exactly the shape of
  `target/surefire-reports`), several `--results` flags, or one `--results` followed
  by a shell-expanded glob (`target/surefire-reports/*.xml`) -- all three forms work.
- `--dry-run` parses everything, prints a preview (file/test counts and every
  resolved `automationReference`), and sends nothing.
- Supports both a bare `<testsuite>` root and a `<testsuites>` wrapper.
- **automationReference**: `classname#name`, taken verbatim from the XML, e.g.
  `com.example.LoginTest#successfulLogin` -- see ADR 0013 for the full rationale,
  including the deliberate choice not to reverse-engineer parameterized-test display
  names.
- **Status mapping**: no `<failure>`/`<error>`/`<skipped>` child -> `PASSED`;
  `<failure>` or `<error>` -> `FAILED` (message -> `actualResult`, type + body/stack
  trace -> `failureDetails`); `<skipped>` -> `SKIPPED` (its message, or body text if
  no message attribute, -> `actualResult`). `BLOCKED` is never produced automatically.
- **Duration**: the `time` attribute (fractional seconds) is converted to whole
  milliseconds via `BigDecimal`, not `double`/`float` parsing -- no precision drift.
- **`<system-out>`/`<system-err>`**: never read or forwarded -- can be huge, can
  contain secrets, and Testryn's result model has no field for raw console output.
- **Duplicate `automationReference` across the given report(s)**: rejected with a
  clear error naming every file involved, nothing is published -- a stale/duplicated
  `target/` directory across multiple runs is the far more likely cause than a
  legitimate need to pick one silently.
- **Unknown `automationReference`**: not checked client-side -- the same atomic Bulk
  API (ADR 0010) the JSON workflow already relies on rejects the whole request and
  lists every unresolved reference; nothing is ever partially published.
- Security: the XML parser is hardened against XXE/DTD/external-entity attacks
  (JUnit XML is untrusted input) -- see `JUnitXmlResultBatchReader`'s javadoc for the
  full configuration.

The existing `publish` (JSON) workflow, its flags, and its behavior are entirely
unchanged by any of this.
