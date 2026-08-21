import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ExecutionsApi, ProjectsApi, TestCasesApi } from "../api/endpoints";
import type { Execution, Project } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { LoadingState } from "../components/LoadingState";
import { StatusBadge } from "../components/StatusBadge";
import { summarize } from "./executionSummary";
import { Icon } from "../components/Icon";

interface ProjectSummary {
  project: Project;
  activeTestCaseCount: number;
  executions: Execution[];
}

export function DashboardPage() {
  const [summaries, setSummaries] = useState<ProjectSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [newKey, setNewKey] = useState("");
  const [newName, setNewName] = useState("");
  const [creating, setCreating] = useState(false);
  const [showNewProjectForm, setShowNewProjectForm] = useState(false);

  async function load() {
    setError(null);
    try {
      const projects = await ProjectsApi.list();
      const summaries = await Promise.all(
        projects.map(async (project) => {
          const [activePage, executions] = await Promise.all([
            TestCasesApi.search(project.key, { status: "ACTIVE", size: 1 }),
            ExecutionsApi.listForProject(project.key),
          ]);
          return { project, activeTestCaseCount: activePage.totalElements, executions };
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
      setShowNewProjectForm(false);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setCreating(false);
    }
  }

  const allExecutions = summaries?.flatMap((s) => s.executions.map((e) => ({ execution: e, project: s.project }))) ?? [];
  const runningCount = allExecutions.filter((r) => r.execution.status === "RUNNING").length;
  const overallCounts = allExecutions.reduce(
    (acc, r) => {
      const c = summarize(r.execution.testCases).counts;
      acc.PASSED += c.PASSED;
      acc.FAILED += c.FAILED;
      acc.BLOCKED += c.BLOCKED;
      return acc;
    },
    { PASSED: 0, FAILED: 0, BLOCKED: 0 },
  );
  const recentFailed = allExecutions
    .filter((r) => summarize(r.execution.testCases).counts.FAILED > 0)
    .sort((a, b) => b.execution.createdAt.localeCompare(a.execution.createdAt))
    .slice(0, 5);

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <div className="eyebrow">Quality workspace</div>
          <div className="title-group">
            <h1>Quality at a glance</h1>
          </div>
          <p className="page-subtitle">Trace requirements, tests and results in one focused workspace.</p>
        </div>
        <div className="actions">
          <button className="btn" onClick={() => setShowNewProjectForm((s) => !s)}>
            {!showNewProjectForm && <Icon name="plus" />}
            {showNewProjectForm ? "Cancel" : "New project"}
          </button>
        </div>
      </div>
      <ErrorBanner message={error} />

      {showNewProjectForm && (
        <form className="card" onSubmit={createProject}>
          <div className="toolbar" style={{ alignItems: "flex-end", marginBottom: 0 }}>
            <div className="form-row" style={{ marginBottom: 0 }}>
              <label htmlFor="key">Key</label>
              <input id="key" value={newKey} onChange={(e) => setNewKey(e.target.value.toUpperCase())} placeholder="BITLESS" required />
            </div>
            <div className="form-row" style={{ marginBottom: 0, flex: 1 }}>
              <label htmlFor="name">Name</label>
              <input id="name" value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Bitless" required />
            </div>
            <button className="btn" type="submit" disabled={creating}>
              Create project
            </button>
          </div>
        </form>
      )}

      {summaries === null && !error && <LoadingState label="Loading projects…" />}

      {summaries?.length === 0 && (
        <div className="welcome-panel">
          <div className="welcome-copy">
            <span className="eyebrow">Welcome to Testryn</span>
            <h2>Build a quality system your team can trust.</h2>
            <p>Connect requirements to versioned test cases, reusable plans and durable execution results.</p>
            <button className="btn" onClick={() => setShowNewProjectForm(true)}><Icon name="plus" /> Create your first project</button>
          </div>
          <TraceIllustration />
        </div>
      )}

      {summaries && summaries.length > 0 && (
        <>
          <div className="metric-grid">
            <div className="stat">
              <span className="value">{summaries.length}</span>
              <span className="label">Projects</span>
            </div>
            <div className="stat">
              <span className="value">{summaries.reduce((n, s) => n + s.activeTestCaseCount, 0)}</span>
              <span className="label">Active test cases</span>
            </div>
            <div className="stat">
              <span className="value">{runningCount}</span>
              <span className="label">Running executions</span>
            </div>
            <div className="stat stat-success">
              <span className="value">{overallCounts.PASSED}</span>
              <span className="label">Passed</span>
            </div>
            <div className="stat stat-danger">
              <span className="value">{overallCounts.FAILED}</span>
              <span className="label">Failed</span>
            </div>
            <div className="stat stat-warning">
              <span className="value">{overallCounts.BLOCKED}</span>
              <span className="label">Blocked</span>
            </div>
          </div>

          {recentFailed.length > 0 && (
            <>
              <h2 style={{ marginTop: 0 }}>Recently failed executions</h2>
              <div className="table-wrap" style={{ marginBottom: "1.5rem" }}>
                <table>
                  <thead>
                    <tr>
                      <th>Execution</th>
                      <th>Project</th>
                      <th>Failed</th>
                      <th>When</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentFailed.map(({ execution, project }) => (
                      <tr key={execution.id}>
                        <td>
                          <Link to={`/executions/${execution.id}`}>{execution.name}</Link>
                        </td>
                        <td>{project.name}</td>
                        <td>
                          <span className="badge badge-danger">{summarize(execution.testCases).counts.FAILED} failed</span>
                        </td>
                        <td className="muted">{new Date(execution.createdAt).toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          <h2 style={{ marginTop: 0 }}>Projects</h2>
          <div className="card-grid">
            {summaries.map(({ project, activeTestCaseCount, executions }) => {
              const latest = [...executions].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 3);
              return (
                <div className="card project-card" key={project.id}>
                  <div className="project-card-accent" />
                  <h3 style={{ marginBottom: "0.2rem" }}>
                    <Link to={`/projects/${project.key}`}>{project.name}</Link>
                  </h3>
                  <div className="muted" style={{ marginBottom: "0.75rem", fontSize: "0.8rem" }}>
                    {project.key}
                  </div>
                  <div className="stat" style={{ marginBottom: "0.75rem" }}>
                    <span className="value">{activeTestCaseCount}</span>
                    <span className="label">Active test cases</span>
                  </div>
                  <div className="muted" style={{ fontSize: "0.75rem", marginBottom: "0.35rem", textTransform: "uppercase", letterSpacing: "0.03em" }}>
                    Latest executions
                  </div>
                  {latest.length === 0 && <div className="faint">No executions yet</div>}
                  {latest.map((execution) => (
                    <div key={execution.id} style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.3rem" }}>
                      <Link to={`/executions/${execution.id}`}>{execution.name}</Link>
                      <StatusBadge value={execution.status} />
                    </div>
                  ))}
                  <Link className="project-card-link" to={`/projects/${project.key}`}>Open project <Icon name="arrow" /></Link>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

function TraceIllustration() {
  return (
    <div className="trace-illustration" aria-hidden="true">
      <div className="trace-orbit trace-orbit-one" />
      <div className="trace-orbit trace-orbit-two" />
      <div className="trace-path" />
      <span className="trace-point p1"><i>R</i></span>
      <span className="trace-point p2"><i>T</i></span>
      <span className="trace-point p3"><i>E</i></span>
      <span className="trace-check">✓</span>
    </div>
  );
}
