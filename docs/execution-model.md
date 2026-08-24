# Execution Model

The domain model behind running a test case and recording what actually happened,
now down to individual step results (ADR 0015). Read this alongside
[ADR 0003](adr/0003-plan-execution-snapshot.md) (why an execution is an immutable
snapshot) and [ADR 0006](adr/0006-execution-result-patch-semantics.md) (JSON Merge
Patch semantics for results).

## The shape

```
Execution                              (a concrete test run, immutable snapshot)
  └─ ExecutionTestCase                 (one test case, pinned to one TestCaseVersion)
       ├─ ExecutionResult              (that test case's overall outcome)
       └─ ExecutionStepResult[]        (one per step of that pinned version)
            └─ references TestStep     (the step as it existed at that version)
```

`ExecutionTestCase.testCaseVersion` is set once, at execution-creation time, and
never changes -- this is what makes the whole snapshot historically stable even if
the test case is edited or re-versioned afterwards (ADR 0003).

## Why there is no separate step-snapshot table

A naive reading of "the execution must not be affected by later test case edits"
suggests copying each step's `action`/`expectedResult` text into a new snapshot
table at execution-creation time. That copy would be redundant: **`TestStep` rows
are already immutable once persisted** (see its own javadoc) and permanently scoped
to one specific, already-immutable `TestCaseVersion`. Editing a test case's steps
never modifies an existing `TestStep` row -- it creates a brand new
`TestCaseVersion` with brand new `TestStep` rows (ADR 0002). So `ExecutionStepResult`
simply references the `TestStep` row directly (`step_id`); that reference can never
point at content that later changes out from under it. See
[ADR 0015](adr/0015-step-level-execution-results.md) for the full reasoning and the
alternative that was considered and rejected.

## Status values

Both `ExecutionResult.status` and `ExecutionStepResult.status` use the exact same
`ExecutionResultStatus` enum: `NOT_RUN`, `PASSED`, `FAILED`, `BLOCKED`, `SKIPPED`.
Deliberately one shared model, not two parallel ones.

## Testcase-level status: two independent write paths

There are two ways a testcase-level `ExecutionResult.status` gets set, and they are
kept deliberately separate:

1. **Direct write** -- `PATCH .../results/{resultId}` or the bulk
   `PATCH .../results` (used by the CI publisher, JUnit imports, and any manual
   "just mark the whole thing PASSED" action). Sets exactly what the caller sent;
   step results are never touched, never inferred.
2. **Derived from steps** -- only as a side effect of a step-level write
   (`PATCH .../step-results/{id}` or the bulk `PATCH .../step-results`). After
   applying the step change, the owning test case's status is recomputed from ALL
   of its step results and applied via `ExecutionResult.deriveStatus(...)`, which
   touches only `status`/`executedAt` -- any comment, duration, or executor already
   on that result from path 1 survives untouched.

**Aggregation rule** (applied literally, in this order):

1. any step `FAILED` → `FAILED`
2. else any step `BLOCKED` → `BLOCKED`
3. else every step `PASSED` → `PASSED`
4. else every step that has actually been run (not `NOT_RUN`) is `SKIPPED`, and at
   least one is → `SKIPPED`
5. else → `NOT_RUN` (still incomplete)

This means a single failed step immediately fails the test case even while other
steps remain `NOT_RUN` -- correct, since the outcome is already known.

## Automation without step reports (JUnit and friends)

`testryn-publisher publish-junit` (and any future framework-native adapter) only
ever calls the testcase-level write path. Step results for that execution stay
exactly as initialized -- `NOT_RUN` -- because no automation source told Testryn
otherwise. This is intentional, not a gap: fabricating step-level PASSED values from
a testcase-level JUnit result would misrepresent what actually happened. The Jira
Forge panel and the Testryn runner both show this state honestly ("Step-level
results not reported for this execution"), never as if every step had passed.

A future framework-native step reporter (Selenium, Playwright, a custom harness)
would write to the same step-result API real testers use -- `PATCH
.../step-results` -- nothing in this block's design prevents that; building the
adapter itself is explicitly out of scope here (see BACKLOG.md).

## Initialization

Every `ExecutionStepResult` for every step of every test case in an execution's
snapshot is created eagerly, as `NOT_RUN`, at the moment the execution itself is
created -- no lazy auto-creation on first click. This keeps "how many steps does
this execution have, and how many have run" a simple, always-available count,
rather than something that depends on which steps a tester happened to touch first.

## The COMPLETED/ABORTED write guard

Once an `Execution` is `COMPLETED` or `ABORTED`, no result write is accepted any
more -- testcase-level, step-level, single, or bulk, manual or CI. `CREATED` and
`RUNNING` both accept writes (`CREATED` transitions to `RUNNING` automatically on
the first one). A write attempt against a finished execution gets a clear `409
Conflict`, not a silent no-op or a stale success. The CI publisher needs no special
handling for this -- it calls the same bulk testcase-level endpoint every other
caller does.

## Old executions (created before this block)

An execution created before step-level results existed has zero
`ExecutionStepResult` rows for any of its test cases. Every API response and every
UI (Runner, Forge panel) treats this the same way: an empty/`null` step list, with a
plain "not available for this execution" message -- never fabricated NOT_RUN rows,
never a crash. No migration backfills historical step data; there is none to
backfill.
