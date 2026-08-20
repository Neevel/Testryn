import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ExecutionsApi, ProjectsApi, TestCasesApi, TestPlansApi } from "../api/endpoints";
import type { Execution, Project, TestCase, TestPlan } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { StatusBadge } from "../components/StatusBadge";
import { downloadUrl } from "../api/client";

type Tab = "test-cases" | "test-plans" | "executions";

export function ProjectPage() {
  const { projectKey = "" } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = (searchParams.get("tab") as Tab) ?? "test-cases";

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
      <h1>{project ? project.name : projectKey}</h1>
      <ErrorBanner message={error} />

      <div className="tabs">
        {(["test-cases", "test-plans", "executions"] as Tab[]).map((t) => (
          <a
            key={t}
            className={t === tab ? "active" : ""}
            href="#"
            onClick={(e) => {
              e.preventDefault();
              setSearchParams({ tab: t });
            }}
          >
            {t === "test-cases" ? "Test Cases" : t === "test-plans" ? "Test Plans" : "Executions"}
          </a>
        ))}
      </div>

      {tab === "test-cases" && <TestCasesTab projectKey={projectKey} />}
      {tab === "test-plans" && <TestPlansTab projectKey={projectKey} />}
      {tab === "executions" && <ExecutionsTab projectKey={projectKey} />}
    </div>
  );
}

function TestCasesTab({ projectKey }: { projectKey: string }) {
  const [testCases, setTestCases] = useState<TestCase[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  function load() {
    TestCasesApi.listForProject(projectKey)
      .then(setTestCases)
      .catch((err) => setError(errorMessage(err)));
  }

  useEffect(load, [projectKey]);

  return (
    <div>
      <div className="toolbar">
        <div>
          <a href={downloadUrl(TestCasesApi.exportUrl(projectKey, "json"))} className="btn btn-secondary" style={{ marginRight: "0.5rem" }}>
            Export JSON
          </a>
          <a href={downloadUrl(TestCasesApi.exportUrl(projectKey, "csv"))} className="btn btn-secondary" style={{ marginRight: "0.5rem" }}>
            Export CSV
          </a>
          <a href={downloadUrl(TestCasesApi.exportUrl(projectKey, "markdown"))} className="btn btn-secondary">
            Export Markdown
          </a>
        </div>
        <button className="btn" onClick={() => setShowForm((s) => !s)}>
          {showForm ? "Abbrechen" : "Neuer Test Case"}
        </button>
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
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Titel</th>
            <th>Status</th>
            <th>Priority</th>
            <th>Version</th>
            <th>Tags</th>
          </tr>
        </thead>
        <tbody>
          {testCases?.map((tc) => (
            <tr key={tc.id}>
              <td>
                <Link to={`/test-cases/${tc.id}`}>{tc.humanId}</Link>
              </td>
              <td>{tc.currentVersion?.title}</td>
              <td>
                <StatusBadge value={tc.status} />
              </td>
              <td>{tc.priority}</td>
              <td>v{tc.currentVersion?.versionNumber}</td>
              <td>
                {tc.tags.map((tag) => (
                  <span className="tag" key={tag}>
                    {tag}
                  </span>
                ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {testCases?.length === 0 && <p className="muted">Noch keine Test Cases.</p>}
    </div>
  );
}

function NewTestCaseForm({ projectKey, onCreated }: { projectKey: string; onCreated: () => void }) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [preconditions, setPreconditions] = useState("");
  const [priority, setPriority] = useState<TestCase["priority"]>("MEDIUM");
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
        <label>Titel</label>
        <input value={title} onChange={(e) => setTitle(e.target.value)} required />
      </div>
      <div className="form-row">
        <label>Beschreibung</label>
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Preconditions</label>
        <textarea value={preconditions} onChange={(e) => setPreconditions(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Priority</label>
        <select value={priority} onChange={(e) => setPriority(e.target.value as TestCase["priority"])}>
          <option value="LOW">LOW</option>
          <option value="MEDIUM">MEDIUM</option>
          <option value="HIGH">HIGH</option>
          <option value="CRITICAL">CRITICAL</option>
        </select>
      </div>
      <div className="form-row">
        <label>Tags (kommagetrennt)</label>
        <input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="smoke, regression" />
      </div>
      <div className="form-row">
        <label>Steps</label>
        {steps.map((step, i) => (
          <div className="step-row" key={i}>
            <span className="muted">{i + 1}.</span>
            <input
              placeholder="Aktion"
              value={step.action}
              onChange={(e) => setSteps(steps.map((s, idx) => (idx === i ? { ...s, action: e.target.value } : s)))}
              required
            />
            <input
              placeholder="Erwartetes Ergebnis"
              value={step.expectedResult}
              onChange={(e) => setSteps(steps.map((s, idx) => (idx === i ? { ...s, expectedResult: e.target.value } : s)))}
              required
            />
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setSteps(steps.filter((_, idx) => idx !== i))}
              disabled={steps.length === 1}
            >
              ✕
            </button>
          </div>
        ))}
        <button type="button" className="btn btn-secondary" onClick={() => setSteps([...steps, { action: "", expectedResult: "" }])}>
          + Step
        </button>
      </div>
      <button className="btn" type="submit" disabled={saving}>
        Test Case anlegen
      </button>
    </form>
  );
}

function TestPlansTab({ projectKey }: { projectKey: string }) {
  const [plans, setPlans] = useState<TestPlan[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);

  function load() {
    TestPlansApi.listForProject(projectKey)
      .then(setPlans)
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
        <input placeholder="Name des Test Plans" value={name} onChange={(e) => setName(e.target.value)} required style={{ flex: 1 }} />
        <button className="btn" type="submit" disabled={saving}>
          Test Plan anlegen
        </button>
      </form>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Test Cases</th>
          </tr>
        </thead>
        <tbody>
          {plans?.map((plan) => (
            <tr key={plan.id}>
              <td>
                <Link to={`/test-plans/${plan.id}`}>{plan.name}</Link>
              </td>
              <td>{plan.testCases.length}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {plans?.length === 0 && <p className="muted">Noch keine Test Plans.</p>}
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
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Iteration</th>
            <th>Status</th>
            <th>Erstellt</th>
          </tr>
        </thead>
        <tbody>
          {executions?.map((exec) => (
            <tr key={exec.id}>
              <td>
                <Link to={`/executions/${exec.id}`}>{exec.name}</Link>
              </td>
              <td>#{exec.iterationNumber}</td>
              <td>
                <StatusBadge value={exec.status} />
              </td>
              <td>{new Date(exec.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {executions?.length === 0 && <p className="muted">Noch keine Executions. Starte eine Execution über einen Test Plan.</p>}
    </div>
  );
}
