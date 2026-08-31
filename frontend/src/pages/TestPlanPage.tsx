import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ExecutionsApi, TestCasesApi, TestPlansApi } from "../api/endpoints";
import type { Execution, TestCase, TestPlan } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { EmptyState } from "../components/EmptyState";
import { LoadingState } from "../components/LoadingState";
import { StatusBadge } from "../components/StatusBadge";
import { summarize } from "./executionSummary";
import { EntityIcon } from "../components/EntityIcon";

export function TestPlanPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [plan, setPlan] = useState<TestPlan | null>(null);
  const [availableTestCases, setAvailableTestCases] = useState<TestCase[]>([]);
  const [executions, setExecutions] = useState<Execution[] | null>(null);
  const [testCaseQuery, setTestCaseQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function load() {
    setError(null);
    TestPlansApi.get(id)
      .then(async (p) => {
        setPlan(p);
        const [allTestCases, execs] = await Promise.all([TestCasesApi.listAll(p.projectKey), ExecutionsApi.listForPlan(id)]);
        const inPlan = new Set(p.testCases.map((e) => e.testCaseId));
        setAvailableTestCases(allTestCases.filter((tc) => !inPlan.has(tc.id)));
        setExecutions(execs.sort((a, b) => b.iterationNumber - a.iterationNumber));
      })
      .catch((err) => setError(errorMessage(err)));
  }

  useEffect(load, [id]);

  async function removeTestCase(testCaseId: string) {
    setBusy(true);
    setError(null);
    try {
      await TestPlansApi.removeTestCase(id, testCaseId);
      load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function startExecution() {
    setBusy(true);
    setError(null);
    try {
      const execution = await ExecutionsApi.createFromPlan(id);
      navigate(`/executions/${execution.id}`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  if (!plan) {
    return (
      <div>
        <ErrorBanner message={error} />
        {!error && <LoadingState label="Loading test plan…" />}
      </div>
    );
  }

  const lastExecution = executions?.[0];

  return (
    <div>
      <div className="breadcrumbs">
        <Link to={`/projects/${plan.projectKey}`}>{plan.projectKey}</Link> / {plan.name}
      </div>
      <div className="page-header">
        <div className="title-group">
          <h1 className="entity-title"><EntityIcon kind="testPlan"/>{plan.name}</h1>
        </div>
        <div className="actions">
          <button className="btn btn-danger" onClick={async () => { if (prompt(`Type ${plan.name} to delete this test plan. Existing executions remain.`) !== plan.name) return; await TestPlansApi.remove(id); navigate(`/projects/${plan.projectKey}?tab=test-plans`); }}>Delete plan</button>
          <button className="btn" onClick={startExecution} disabled={busy || plan.testCases.length === 0}>
            Start new execution
          </button>
        </div>
      </div>
      {plan.description && <p className="page-subtitle" style={{ marginTop: "-1rem" }}>{plan.description}</p>}
      <ErrorBanner message={error} />

      <div className="card-grid" style={{ marginBottom: "1.5rem" }}>
        <div className="card">
          <div className="stat">
            <span className="value">{plan.testCases.length}</span>
            <span className="label">Test cases</span>
          </div>
        </div>
        <div className="card">
          <div className="stat">
            <span className="value">{executions?.length ?? 0}</span>
            <span className="label">Iterations run</span>
          </div>
        </div>
        <div className="card">
          <div className="stat">
            <span className="value" style={{ fontSize: "1rem" }}>
              {lastExecution ? <StatusBadge value={lastExecution.status} /> : <span className="faint">None yet</span>}
            </span>
            <span className="label">Last iteration</span>
          </div>
        </div>
      </div>

      <h2>Test cases in this plan</h2>
      {plan.testCases.length === 0 ? (
        <EmptyState title="No test cases in this plan yet">Add test cases below to build the plan.</EmptyState>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Title</th>
                <th>Version</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {plan.testCases.map((entry) => (
                <tr key={entry.testCaseId}>
                  <td>
                    <Link to={`/test-cases/${entry.testCaseId}`}>{entry.testCaseHumanId}</Link>
                  </td>
                  <td>{entry.testCaseTitle}</td>
                  <td className="muted">current</td>
                  <td>
                    <button className="btn btn-secondary btn-sm" onClick={() => removeTestCase(entry.testCaseId)} disabled={busy}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="plan-builder">
        <div className="plan-builder-heading"><div><h2>Add tests</h2><p>Search the project library and add an existing test, or create a missing one.</p></div><Link className="btn btn-secondary" to={`/projects/${plan.projectKey}?tab=test-cases&create=true`}>+ Create test case</Link></div>
        <input className="search-input catalog-search" value={testCaseQuery} onChange={(e) => setTestCaseQuery(e.target.value)} placeholder="Search test case ID, title or tag…" />
        <div className="testcase-picker-list">
          {availableTestCases.filter((tc) => `${tc.humanId} ${tc.currentVersion?.title ?? ""} ${tc.tags.join(" ")}`.toLowerCase().includes(testCaseQuery.toLowerCase())).slice(0, 8).map((tc) => <form className="testcase-picker-row" onSubmit={(e) => { e.preventDefault(); setBusy(true); TestPlansApi.addTestCase(id, tc.id).then(() => { setTestCaseQuery(""); load(); }).catch((err) => setError(errorMessage(err))).finally(() => setBusy(false)); }} key={tc.id}><div><Link to={`/test-cases/${tc.id}`}>{tc.humanId}</Link><strong>{tc.currentVersion?.title}</strong><span>{tc.priority} · {tc.status}{tc.tags.length ? ` · ${tc.tags.join(", ")}` : ""}</span></div><button className="btn btn-secondary btn-sm" type="submit" disabled={busy}>Add to plan</button></form>)}
          {availableTestCases.length === 0 && <span className="muted">Every test case in this project is already in the plan.</span>}
        </div>
      </div>

      <h2>Iterations</h2>
      <p className="page-subtitle" style={{ marginTop: "-0.5rem" }}>
        Every past run of this plan is preserved independently -- editing the plan or its test cases never changes a
        past iteration's results.
      </p>
      {executions === null && !error && <LoadingState label="Loading iterations…" />}
      {executions?.length === 0 && (
        <EmptyState title="No iterations yet">Start a new execution above to run this plan for the first time.</EmptyState>
      )}
      {executions && executions.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Iteration</th>
                <th>Name</th>
                <th>Status</th>
                <th>Results</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {executions.map((exec) => {
                const counts = summarize(exec.testCases).counts;
                const total = exec.testCases.length;
                return (
                  <tr key={exec.id}>
                    <td>#{exec.iterationNumber}</td>
                    <td>
                      <Link to={`/executions/${exec.id}`}>{exec.name}</Link>
                    </td>
                    <td>
                      <StatusBadge value={exec.status} />
                    </td>
                    <td className="muted">
                      {counts.PASSED}/{total} passed
                      {counts.FAILED > 0 && <span style={{ color: "var(--color-danger)" }}> · {counts.FAILED} failed</span>}
                    </td>
                    <td className="muted">{new Date(exec.createdAt).toLocaleString()}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
