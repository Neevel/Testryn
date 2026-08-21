# testryn-publisher

A small, standalone CLI that reports automated test results to Testryn's bulk
result-update API from a CI pipeline. See [docs/ci-integration.md](../../docs/ci-integration.md)
for the full workflow and [ADR 0011](../../docs/adr/0011-ci-publisher.md) for the
design decisions behind it.

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
