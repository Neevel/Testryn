import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import {
  ExecutionsApi,
  ProjectsApi,
  RequirementsApi,
  TestCasesApi,
  TestCaseSearchParams,
  TestPlansApi,
} from "../api/endpoints";
import type { Execution, Project, RequirementLink, TestCase, TestCasePriority, TestCaseStatus, TestPlan } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { EmptyState } from "../components/EmptyState";
import { LoadingState } from "../components/LoadingState";
import { StatusBadge } from "../components/StatusBadge";
import { downloadUrl } from "../api/client";
import { summarize } from "./executionSummary";

type Tab = "overview" | "test-cases" | "test-plans" | "executions" | "requirements";

export function ProjectPage() {
  const { projectKey = "" } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = (searchParams.get("tab") as Tab) ?? "overview";

  const [project, setProject] = useState<Project | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError(null);
    ProjectsApi.get(projectKey)
      .then(setProject)
      .catch((err) => setError(errorMessage(err)));
  }, [projectKey]);

  return (
    <div>
      <div className="breadcrumbs">
        <Link to="/">Dashboard</Link> / {projectKey}
      </div>
      <div className="page-header">
        <div className="title-group">
          <h1>{project ? project.name : projectKey}</h1>
        </div>
      </div>
      {project?.description && <p className="page-subtitle" style={{ marginTop: "-1rem" }}>{project.description}</p>}
      <ErrorBanner message={error} />

      <div className="tabs">
        {(["overview", "test-cases", "test-plans", "executions", "requirements"] as Tab[]).map((t) => (
          <a
            key={t}
            className={t === tab ? "active" : ""}
            href="#"
            onClick={(e) => {
              e.preventDefault();
              setSearchParams({ tab: t });
            }}
          >
            {tabLabel(t)}
          </a>
        ))}
      </div>

      {tab === "overview" && <OverviewTab projectKey={projectKey} />}
      {tab === "test-cases" && <TestCasesTab projectKey={projectKey} />}
      {tab === "test-plans" && <TestPlansTab projectKey={projectKey} />}
      {tab === "executions" && <ExecutionsTab projectKey={projectKey} />}
      {tab === "requirements" && <RequirementsTab projectKey={projectKey} />}
    </div>
  );
}

function tabLabel(t: Tab): string {
  switch (t) {
    case "overview":
      return "Overview";
    case "test-cases":
      return "Test Cases";
    case "test-plans":
      return "Test Plans";
    case "executions":
      return "Executions";
    case "requirements":
      return "Requirements";
  }
}

function OverviewTab({ projectKey }: { projectKey: string }) {
  const [testCases, setTestCases] = useState<TestCase[] | null>(null);
  const [testCaseTotal, setTestCaseTotal] = useState(0);
  const [plans, setPlans] = useState<TestPlan[] | null>(null);
  const [executions, setExecutions] = useState<Execution[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError(null);
    Promise.all([
      TestCasesApi.search(projectKey, { size: 100 }),
      TestPlansApi.listForProject(projectKey),
      ExecutionsApi.listForProject(projectKey),
    ])
      .then(([tcPage, plans, executions]) => {
        setTestCases(tcPage.content);
        setTestCaseTotal(tcPage.totalElements);
        setPlans(plans);
        setExecutions(executions);
      })
      .catch((err) => setError(errorMessage(err)));
  }, [projectKey]);

  if (testCases === null || plans === null || executions === null) {
    return (
      <div>
        <ErrorBanner message={error} />
        {!error && <LoadingState label="Loading overview…" />}
      </div>
    );
  }

  const byStatus: Record<TestCaseStatus, number> = { DRAFT: 0, ACTIVE: 0, DEPRECATED: 0 };
  testCases.forEach((tc) => byStatus[tc.status]++);
  const running = executions.filter((e) => e.status === "RUNNING").length;
  const lastExecution = [...executions].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];

  return (
    <div>
      <ErrorBanner message={error} />
      <div className="card-grid">
        <div className="card">
          <div className="stat">
            <span className="value">{testCaseTotal}</span>
            <span className="label">Test cases</span>
          </div>
          <div className="muted" style={{ fontSize: "0.8rem", marginTop: "0.5rem" }}>
            {byStatus.ACTIVE} active · {byStatus.DRAFT} draft · {byStatus.DEPRECATED} deprecated
          </div>
        </div>
        <div className="card">
          <div className="stat">
            <span className="value">{plans.length}</span>
            <span className="label">Test plans</span>
          </div>
        </div>
        <div className="card">
          <div className="stat">
            <span className="value">{executions.length}</span>
            <span className="label">Executions</span>
          </div>
          <div className="muted" style={{ fontSize: "0.8rem", marginTop: "0.5rem" }}>
            {running} running
          </div>
        </div>
      </div>

      <h2>Last execution</h2>
      {lastExecution ? (
        <div className="card">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <Link to={`/executions/${lastExecution.id}`}>{lastExecution.name}</Link>
              <div className="muted" style={{ fontSize: "0.8rem" }}>
                {new Date(lastExecution.createdAt).toLocaleString()}
              </div>
            </div>
            <StatusBadge value={lastExecution.status} />
          </div>
        </div>
      ) : (
        <EmptyState title="No executions yet">Start an execution from a test plan.</EmptyState>
      )}
    </div>
  );
}

function TestCasesTab({ projectKey }: { projectKey: string }) {
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<TestCaseStatus | "">("");
  const [priority, setPriority] = useState<TestCasePriority | "">("");
  const [testCases, setTestCases] = useState<TestCase[] | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  function load() {
    setError(null);
    const params: TestCaseSearchParams = { page, size: 20 };
    if (query) params.query = query;
    if (status) params.status = status;
    if (priority) params.priority = priority;
    TestCasesApi.search(projectKey, params)
      .then((p) => {
        setTestCases(p.content);
        setTotalElements(p.totalElements);
        setTotalPages(p.totalPages);
      })
      .catch((err) => setError(errorMessage(err)));
  }

  useEffect(load, [projectKey, page, query, status, priority]);

  return (
    <div>
      <div className="toolbar">
        <div className="toolbar-filters">
          <input
            className="search-input"
            placeholder="Search by ID or title…"
            value={query}
            onChange={(e) => {
              setPage(0);
              setQuery(e.target.value);
            }}
          />
          <select
            value={status}
            onChange={(e) => {
              setPage(0);
              setStatus(e.target.value as TestCaseStatus | "");
            }}
          >
            <option value="">All statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="ACTIVE">Active</option>
            <option value="DEPRECATED">Deprecated</option>
          </select>
          <select
            value={priority}
            onChange={(e) => {
              setPage(0);
              setPriority(e.target.value as TestCasePriority | "");
            }}
          >
            <option value="">All priorities</option>
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
            <option value="CRITICAL">Critical</option>
          </select>
        </div>
        <div style={{ display: "flex", gap: "0.5rem" }}>
          <a href={downloadUrl(TestCasesApi.exportUrl(projectKey, "json"))} className="btn btn-secondary btn-sm">
            Export JSON
          </a>
          <a href={downloadUrl(TestCasesApi.exportUrl(projectKey, "csv"))} className="btn btn-secondary btn-sm">
            Export CSV
          </a>
          <a href={downloadUrl(TestCasesApi.exportUrl(projectKey, "markdown"))} className="btn btn-secondary btn-sm">
            Export Markdown
          </a>
          <button className="btn" onClick={() => setShowForm((s) => !s)}>
            {showForm ? "Cancel" : "New test case"}
          </button>
        </div>
      </div>
      <ErrorBanner message={error} />
      {showForm && (
        <NewTestCaseForm
          projectKey={projectKey}
          onCreated={() => {
            setShowForm(false);
            load();
          }}
        />
      )}

      {testCases === null && !error && <LoadingState label="Loading test cases…" />}
      {testCases?.length === 0 && (
        <EmptyState title="No test cases found">
          {query || status || priority
            ? "No test cases match these filters."
            : "Create your first test case to start building coverage."}
          {!query && !status && !priority && (
            <div style={{ marginTop: "0.75rem" }}>
              <button className="btn" onClick={() => setShowForm(true)}>
                Create test case
              </button>
            </div>
          )}
        </EmptyState>
      )}

      {testCases && testCases.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Title</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Tags</th>
                  <th>Version</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {testCases.map((tc) => (
                  <tr key={tc.id} className="clickable" onClick={() => (window.location.href = `/test-cases/${tc.id}`)}>
                    <td>
                      <Link to={`/test-cases/${tc.id}`} onClick={(e) => e.stopPropagation()}>
                        {tc.humanId}
                      </Link>
                    </td>
                    <td>{tc.currentVersion?.title}</td>
                    <td>
                      <StatusBadge value={tc.status} />
                    </td>
                    <td>{tc.priority}</td>
                    <td>
                      {tc.tags.map((tag) => (
                        <span className="tag" key={tag}>
                          {tag}
                        </span>
                      ))}
                    </td>
                    <td>v{tc.currentVersion?.versionNumber}</td>
                    <td className="muted">{new Date(tc.updatedAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <div className="toolbar" style={{ marginTop: "0.75rem" }}>
              <span className="muted">
                {totalElements} test case{totalElements === 1 ? "" : "s"} · page {page + 1} of {totalPages}
              </span>
              <div style={{ display: "flex", gap: "0.5rem" }}>
                <button className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  Previous
                </button>
                <button
                  className="btn btn-secondary btn-sm"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function NewTestCaseForm({ projectKey, onCreated }: { projectKey: string; onCreated: () => void }) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [preconditions, setPreconditions] = useState("");
  const [priority, setPriority] = useState<TestCasePriority>("MEDIUM");
  const [tags, setTags] = useState("");
  const [steps, setSteps] = useState([{ action: "", expectedResult: "" }]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await TestCasesApi.create(projectKey, {
        title,
        description,
        preconditions,
        priority,
        tags: tags
          .split(",")
          .map((t) => t.trim())
          .filter(Boolean),
        steps: steps.map((s, i) => ({ order: i + 1, action: s.action, expectedResult: s.expectedResult })),
      });
      onCreated();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <ErrorBanner message={error} />
      <div className="form-row">
        <label>Title</label>
        <input value={title} onChange={(e) => setTitle(e.target.value)} required />
      </div>
      <div className="form-row">
        <label>Description</label>
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Preconditions</label>
        <textarea value={preconditions} onChange={(e) => setPreconditions(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Priority</label>
        <select value={priority} onChange={(e) => setPriority(e.target.value as TestCasePriority)}>
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
          <option value="CRITICAL">Critical</option>
        </select>
      </div>
      <div className="form-row">
        <label>Tags (comma-separated)</label>
        <input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="smoke, regression" />
      </div>
      <div className="form-row">
        <label>Steps</label>
        {steps.map((step, i) => (
          <div className="step-row" key={i}>
            <span className="muted">{i + 1}.</span>
            <input
              placeholder="Action"
              value={step.action}
              onChange={(e) => setSteps(steps.map((s, idx) => (idx === i ? { ...s, action: e.target.value } : s)))}
              required
            />
            <input
              placeholder="Expected result"
              value={step.expectedResult}
              onChange={(e) => setSteps(steps.map((s, idx) => (idx === i ? { ...s, expectedResult: e.target.value } : s)))}
              required
            />
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => setSteps(steps.filter((_, idx) => idx !== i))}
              disabled={steps.length === 1}
            >
              ✕
            </button>
          </div>
        ))}
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => setSteps([...steps, { action: "", expectedResult: "" }])}>
          + Step
        </button>
      </div>
      <button className="btn" type="submit" disabled={saving}>
        Create test case
      </button>
    </form>
  );
}

function TestPlansTab({ projectKey }: { projectKey: string }) {
  const [plans, setPlans] = useState<TestPlan[] | null>(null);
  const [executionsByPlan, setExecutionsByPlan] = useState<Record<string, Execution[]>>({});
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);

  function load() {
    setError(null);
    TestPlansApi.listForProject(projectKey)
      .then(async (plans) => {
        setPlans(plans);
        const entries = await Promise.all(
          plans.map(async (plan) => [plan.id, await ExecutionsApi.listForPlan(plan.id)] as const),
        );
        setExecutionsByPlan(Object.fromEntries(entries));
      })
      .catch((err) => setError(errorMessage(err)));
  }

  useEffect(load, [projectKey]);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await TestPlansApi.create(projectKey, { name });
      setName("");
      load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <ErrorBanner message={error} />
      <form className="toolbar" onSubmit={create}>
        <input placeholder="Name of the new test plan" value={name} onChange={(e) => setName(e.target.value)} required style={{ flex: 1 }} />
        <button className="btn" type="submit" disabled={saving}>
          Create test plan
        </button>
      </form>

      {plans === null && !error && <LoadingState label="Loading test plans…" />}
      {plans?.length === 0 && (
        <EmptyState title="No test plans yet">Group test cases into a plan to start running executions.</EmptyState>
      )}

      {plans && plans.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Test cases</th>
                <th>Executions</th>
                <th>Last execution</th>
              </tr>
            </thead>
            <tbody>
              {plans.map((plan) => {
                const executions = executionsByPlan[plan.id] ?? [];
                const last = [...executions].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
                return (
                  <tr key={plan.id}>
                    <td>
                      <Link to={`/test-plans/${plan.id}`}>{plan.name}</Link>
                    </td>
                    <td>{plan.testCases.length}</td>
                    <td>{executions.length}</td>
                    <td>
                      {last ? (
                        <span style={{ display: "inline-flex", gap: "0.4rem", alignItems: "center" }}>
                          <StatusBadge value={last.status} />
                          <span className="faint">#{last.iterationNumber}</span>
                        </span>
                      ) : (
                        <span className="faint">None yet</span>
                      )}
                    </td>
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

function ExecutionsTab({ projectKey }: { projectKey: string }) {
  const [executions, setExecutions] = useState<Execution[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    ExecutionsApi.listForProject(projectKey)
      .then(setExecutions)
      .catch((err) => setError(errorMessage(err)));
  }, [projectKey]);

  return (
    <div>
      <ErrorBanner message={error} />
      {executions === null && !error && <LoadingState label="Loading executions…" />}
      {executions?.length === 0 && (
        <EmptyState title="No executions yet">Start an execution from a test plan to see it here.</EmptyState>
      )}
      {executions && executions.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Iteration</th>
                <th>Status</th>
                <th>Results</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {executions.map((exec) => {
                const counts = summarize(exec.testCases).counts;
                return (
                  <tr key={exec.id}>
                    <td>
                      <Link to={`/executions/${exec.id}`}>{exec.name}</Link>
                    </td>
                    <td>#{exec.iterationNumber}</td>
                    <td>
                      <StatusBadge value={exec.status} />
                    </td>
                    <td className="muted">
                      {counts.PASSED} passed · {counts.FAILED} failed · {counts.BLOCKED} blocked
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

function RequirementsTab({ projectKey }: { projectKey: string }) {
  const [links, setLinks] = useState<RequirementLink[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    RequirementsApi.listForProject(projectKey)
      .then(setLinks)
      .catch((err) => setError(errorMessage(err)));
  }, [projectKey]);

  return (
    <div>
      <ErrorBanner message={error} />
      {links === null && !error && <LoadingState label="Loading requirements…" />}
      {links?.length === 0 && (
        <EmptyState title="No requirement links yet">
          Link a test case to a Jira issue from the test case page to see it here.
        </EmptyState>
      )}
      {links && links.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Requirement</th>
                <th>Summary</th>
                <th>Status</th>
                <th>Test case</th>
              </tr>
            </thead>
            <tbody>
              {links.map((link) => (
                <tr key={link.id}>
                  <td>
                    <a href={link.url} target="_blank" rel="noreferrer">
                      {link.externalKey}
                    </a>
                    {link.issueType && <div className="faint">{link.issueType}</div>}
                  </td>
                  <td>{link.summary}</td>
                  <td>{link.status && <span className="badge badge-info">{link.status}</span>}</td>
                  <td>
                    <Link to={`/test-cases/${link.testCaseId}`}>{link.testCaseHumanId}</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
