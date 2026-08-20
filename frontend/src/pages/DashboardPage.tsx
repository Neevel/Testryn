import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ExecutionsApi, ProjectsApi, TestCasesApi } from "../api/endpoints";
import type { Execution, Project } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { StatusBadge } from "../components/StatusBadge";

interface ProjectSummary {
  project: Project;
  testCaseCount: number;
  latestExecutions: Execution[];
}

export function DashboardPage() {
  const [summaries, setSummaries] = useState<ProjectSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [newKey, setNewKey] = useState("");
  const [newName, setNewName] = useState("");
  const [creating, setCreating] = useState(false);

  async function load() {
    setError(null);
    try {
      const projects = await ProjectsApi.list();
      const summaries = await Promise.all(
        projects.map(async (project) => {
          const [testCases, executions] = await Promise.all([
            TestCasesApi.listForProject(project.key),
            ExecutionsApi.listForProject(project.key),
          ]);
          return { project, testCaseCount: testCases.length, latestExecutions: executions.slice(0, 3) };
        }),
      );
      setSummaries(summaries);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function createProject(e: React.FormEvent) {
    e.preventDefault();
    setCreating(true);
    setError(null);
    try {
      await ProjectsApi.create({ key: newKey, name: newName });
      setNewKey("");
      setNewName("");
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setCreating(false);
    }
  }

  return (
    <div>
      <h1>Dashboard</h1>
      <ErrorBanner message={error} />

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Neues Projekt</h2>
        <form onSubmit={createProject}>
          <div className="toolbar" style={{ alignItems: "flex-end" }}>
            <div className="form-row" style={{ marginBottom: 0 }}>
              <label htmlFor="key">Key</label>
              <input id="key" value={newKey} onChange={(e) => setNewKey(e.target.value.toUpperCase())} placeholder="BITLESS" required />
            </div>
            <div className="form-row" style={{ marginBottom: 0, flex: 1 }}>
              <label htmlFor="name">Name</label>
              <input id="name" value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Bitless" required />
            </div>
            <button className="btn" type="submit" disabled={creating}>
              Projekt anlegen
            </button>
          </div>
        </form>
      </div>

      {summaries === null && !error && <p className="muted">Lade Projekte…</p>}
      {summaries?.length === 0 && <p className="muted">Noch keine Projekte vorhanden.</p>}

      <div className="card-grid">
        {summaries?.map(({ project, testCaseCount, latestExecutions }) => (
          <div className="card" key={project.id}>
            <h2 style={{ marginTop: 0 }}>
              <Link to={`/projects/${project.key}`}>{project.name}</Link>
            </h2>
            <div className="muted" style={{ marginBottom: "0.5rem" }}>
              {project.key}
            </div>
            <div className="stat" style={{ marginBottom: "0.75rem" }}>
              <span className="value">{testCaseCount}</span>
              <span className="label">Test Cases</span>
            </div>
            <div className="muted" style={{ fontSize: "0.8rem", marginBottom: "0.3rem" }}>
              Letzte Executions
            </div>
            {latestExecutions.length === 0 && <div className="muted">Keine Executions</div>}
            {latestExecutions.map((execution) => (
              <div key={execution.id} style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                <Link to={`/executions/${execution.id}`}>{execution.name}</Link>
                <StatusBadge value={execution.status} />
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
