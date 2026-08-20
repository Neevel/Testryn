import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ExecutionsApi, TestCasesApi, TestPlansApi } from "../api/endpoints";
import type { Execution, TestCase, TestPlan } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { EmptyState } from "../components/EmptyState";
import { LoadingState } from "../components/LoadingState";
import { StatusBadge } from "../components/StatusBadge";
import { summarize } from "./executionSummary";

export function TestPlanPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [plan, setPlan] = useState<TestPlan | null>(null);
  const [availableTestCases, setAvailableTestCases] = useState<TestCase[]>([]);
  const [executions, setExecutions] = useState<Execution[] | null>(null);
  const [selectedTestCaseId, setSelectedTestCaseId] = useState("");
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

  async function addTestCase(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedTestCaseId) return;
    setBusy(true);
    setError(null);
    try {
      await TestPlansApi.addTestCase(id, selectedTestCaseId);
      setSelectedTestCaseId("");
      load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

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
          <h1>{plan.name}</h1>
        </div>
        <div className="actions">
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

      <form className="toolbar" onSubmit={addTestCase} style={{ marginTop: "1rem" }}>
        <select value={selectedTestCaseId} onChange={(e) => setSelectedTestCaseId(e.target.value)} style={{ flex: 1 }}>
          <option value="">Select a test case…</option>
          {availableTestCases.map((tc) => (
            <option key={tc.id} value={tc.id}>
              {tc.humanId} — {tc.currentVersion?.title}
            </option>
          ))}
        </select>
        <button className="btn" type="submit" disabled={busy || !selectedTestCaseId}>
          Add
        </button>
      </form>

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
