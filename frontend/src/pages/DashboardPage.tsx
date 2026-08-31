import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ExecutionsApi, ProjectsApi, TestCasesApi, TestPlansApi } from "../api/endpoints";
import type { Execution, Project, TestPlan } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { LoadingState } from "../components/LoadingState";
import { StatusBadge } from "../components/StatusBadge";
import { summarize } from "./executionSummary";
import { Icon } from "../components/Icon";
import { EntityIcon } from "../components/EntityIcon";

interface ProjectSummary {
  project: Project;
  activeTestCaseCount: number;
  executions: Execution[];
  plans: TestPlan[];
}

export function DashboardPage() {
  const [summaries, setSummaries] = useState<ProjectSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [newKey, setNewKey] = useState("");
  const [newName, setNewName] = useState("");
  const [creating, setCreating] = useState(false);
  const [showNewProjectForm, setShowNewProjectForm] = useState(false);
  const [projectQuery, setProjectQuery] = useState("");
  const [projectFilter, setProjectFilter] = useState<"all" | "attention" | "running">("all");

  async function load() {
    setError(null);
    try {
      const projects = await ProjectsApi.list();
      const summaries = await Promise.all(
        projects.map(async (project) => {
          const [activePage, executions, plans] = await Promise.all([
            TestCasesApi.search(project.key, { status: "ACTIVE", size: 1 }),
            ExecutionsApi.listForProject(project.key),
            TestPlansApi.listForProject(project.key),
          ]);
          return { project, activeTestCaseCount: activePage.totalElements, executions, plans };
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
  const allPlans = summaries?.flatMap((summary) => summary.plans.map((plan) => ({ plan, project: summary.project, executions: summary.executions.filter((execution) => execution.testPlanId === plan.id) }))) ?? [];
  const recentPlans = [...allPlans].sort((a, b) => b.plan.updatedAt.localeCompare(a.plan.updatedAt)).slice(0, 4);
  const visibleProjects = summaries?.filter(({ project, executions }) => {
    const matchesQuery = `${project.key} ${project.name}`.toLowerCase().includes(projectQuery.toLowerCase());
    const hasFailures = executions.some((execution) => summarize(execution.testCases).counts.FAILED > 0);
    const hasRunning = executions.some((execution) => execution.status === "RUNNING");
    return matchesQuery && (projectFilter === "all" || (projectFilter === "attention" && hasFailures) || (projectFilter === "running" && hasRunning));
  }) ?? [];

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <div className="eyebrow"><span className="fantasy-copy">Quality campaign</span><span className="standard-copy">Quality workspace</span></div>
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
            <Link className="stat metric-link" to="/projects">
              <span className="value">{summaries.length}</span>
              <span className="label">Projects</span>
            </Link>
            <Link className="stat metric-link" to="/test-plans">
              <span className="value">{allPlans.length}</span>
              <span className="label">Test plans</span>
            </Link>
            <Link className="stat metric-link" to="/test-cases?status=ACTIVE">
              <span className="value">{summaries.reduce((n, s) => n + s.activeTestCaseCount, 0)}</span>
              <span className="label">Active test cases</span>
            </Link>
            <Link className="stat metric-link" to="/executions?status=RUNNING">
              <span className="value">{runningCount}</span>
              <span className="label">Running executions</span>
            </Link>
            <Link className="stat stat-success metric-link" to="/executions?result=PASSED">
              <span className="value">{overallCounts.PASSED}</span>
              <span className="label">Passed</span>
            </Link>
            <Link className="stat stat-danger metric-link" to="/executions?result=FAILED">
              <span className="value">{overallCounts.FAILED}</span>
              <span className="label">Failed</span>
            </Link>
            <Link className="stat stat-warning metric-link" to="/executions?result=BLOCKED">
              <span className="value">{overallCounts.BLOCKED}</span>
              <span className="label">Blocked</span>
            </Link>
          </div>

          {recentPlans.length > 0 && <><div className="section-heading"><div><h2>Active test plans</h2><p>Continue planning or open the latest execution.</p></div><Link to="/test-plans">View all plans →</Link></div><div className="dashboard-plan-grid">{recentPlans.map(({ plan, project, executions }) => {
            const latest = [...executions].sort((a,b) => b.createdAt.localeCompare(a.createdAt))[0];
            const counts = latest ? summarize(latest.testCases).counts : null;
            return <Link className="dashboard-plan-card" to={`/test-plans/${plan.id}`} key={plan.id}><div><span className="technical-id">{project.key}</span><h3 className="entity-title"><EntityIcon kind="testPlan"/>{plan.name}</h3></div><div className="overview-plan-meta"><span><strong>{plan.testCases.length}</strong> tests</span><span><strong>{executions.length}</strong> runs</span></div>{latest ? <div className="plan-latest"><StatusBadge value={latest.status}/><span>{counts?.PASSED ?? 0} passed · {counts?.FAILED ?? 0} failed</span></div> : <span className="faint">Ready for first execution</span>}</Link>;
          })}</div></>}

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
                          <Link className="entity-link" to={`/executions/${execution.id}`}><EntityIcon kind="execution"/>{execution.name}</Link>
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

          <div className="section-heading"><div><h2>Projects</h2><p>Find a workspace or focus on projects that need attention.</p></div><Link to="/projects">View all projects →</Link></div>
          <div className="project-dashboard-toolbar"><input className="search-input" placeholder="Search projects…" value={projectQuery} onChange={(e) => setProjectQuery(e.target.value)}/><div className="quick-filter-bar">{(["all","attention","running"] as const).map((filter) => <button key={filter} className={`filter-chip ${projectFilter === filter ? "active" : ""}`} onClick={() => setProjectFilter(filter)}>{filter === "attention" ? "Needs attention" : filter}</button>)}</div><span className="result-count">{visibleProjects.length} projects</span></div>
          <div className="card-grid">
            {visibleProjects.slice(0, 8).map(({ project, activeTestCaseCount, executions }) => {
              const latest = [...executions].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 2);
              return (
                <div className="card project-card" key={project.id}>
                  <div className="project-card-accent" />
                  <h3 style={{ marginBottom: "0.2rem" }}>
                    <Link className="entity-link" to={`/projects/${project.key}`}><EntityIcon kind="project"/>{project.name}</Link>
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
                    <div key={execution.id} className="project-execution-row">
                      <Link className="entity-link" title={execution.name} to={`/executions/${execution.id}`}><EntityIcon kind="execution"/>{execution.name}</Link>
                      <StatusBadge value={execution.status} />
                    </div>
                  ))}
                  <Link className="project-card-link" to={`/projects/${project.key}`}>Open project <Icon name="arrow" /></Link>
                </div>
              );
            })}
          </div>
          {visibleProjects.length > 8 && <div className="show-all-row"><Link className="btn btn-secondary" to="/projects">Show all {visibleProjects.length} projects</Link></div>}
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
