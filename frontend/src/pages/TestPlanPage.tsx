import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ExecutionsApi, TestCasesApi, TestPlansApi } from "../api/endpoints";
import type { Execution, TestCase, TestPlan } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { StatusBadge } from "../components/StatusBadge";

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
        const [allTestCases, execs] = await Promise.all([
          TestCasesApi.listForProject(p.projectKey),
          ExecutionsApi.listForPlan(id),
        ]);
        const inPlan = new Set(p.testCases.map((e) => e.testCaseId));
        setAvailableTestCases(allTestCases.filter((tc) => !inPlan.has(tc.id)));
        setExecutions(execs);
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
        {!error && <p className="muted">Lade Test Plan…</p>}
      </div>
    );
  }

  return (
    <div>
      <div className="breadcrumbs">
        <Link to={`/projects/${plan.projectKey}`}>{plan.projectKey}</Link> / {plan.name}
      </div>
      <div className="toolbar">
        <h1 style={{ margin: 0 }}>{plan.name}</h1>
        <button className="btn" onClick={startExecution} disabled={busy || plan.testCases.length === 0}>
          Neue Execution starten
        </button>
      </div>
      {plan.description && <p className="muted">{plan.description}</p>}
      <ErrorBanner message={error} />

      <h2>Enthaltene Test Cases</h2>
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Titel</th>
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
              <td>
                <button className="btn btn-secondary" onClick={() => removeTestCase(entry.testCaseId)} disabled={busy}>
                  Entfernen
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {plan.testCases.length === 0 && <p className="muted">Noch keine Test Cases im Plan.</p>}

      <form className="toolbar" onSubmit={addTestCase}>
        <select value={selectedTestCaseId} onChange={(e) => setSelectedTestCaseId(e.target.value)} style={{ flex: 1 }}>
          <option value="">Test Case auswählen…</option>
          {availableTestCases.map((tc) => (
            <option key={tc.id} value={tc.id}>
              {tc.humanId} — {tc.currentVersion?.title}
            </option>
          ))}
        </select>
        <button className="btn" type="submit" disabled={busy || !selectedTestCaseId}>
          Hinzufügen
        </button>
      </form>

      <h2>Iterationen / Executions</h2>
      <table>
        <thead>
          <tr>
            <th>Iteration</th>
            <th>Name</th>
            <th>Status</th>
            <th>Erstellt</th>
          </tr>
        </thead>
        <tbody>
          {executions?.map((exec) => (
            <tr key={exec.id}>
              <td>#{exec.iterationNumber}</td>
              <td>
                <Link to={`/executions/${exec.id}`}>{exec.name}</Link>
              </td>
              <td>
                <StatusBadge value={exec.status} />
              </td>
              <td>{new Date(exec.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {executions?.length === 0 && <p className="muted">Noch keine Executions für diesen Plan.</p>}
    </div>
  );
}
