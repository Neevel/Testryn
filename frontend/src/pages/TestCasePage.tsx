import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { RequirementsApi, TestCasesApi } from "../api/endpoints";
import type { RequirementLink, TestCase, TestCaseVersion } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { StatusBadge } from "../components/StatusBadge";

export function TestCasePage() {
  const { id = "" } = useParams();
  const [testCase, setTestCase] = useState<TestCase | null>(null);
  const [versions, setVersions] = useState<TestCaseVersion[] | null>(null);
  const [links, setLinks] = useState<RequirementLink[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);

  function load() {
    setError(null);
    Promise.all([TestCasesApi.get(id), TestCasesApi.versions(id), RequirementsApi.listForTestCase(id)])
      .then(([tc, vs, ls]) => {
        setTestCase(tc);
        setVersions(vs);
        setLinks(ls);
      })
      .catch((err) => setError(errorMessage(err)));
  }

  useEffect(load, [id]);

  if (!testCase) {
    return (
      <div>
        <ErrorBanner message={error} />
        {!error && <p className="muted">Lade Test Case…</p>}
      </div>
    );
  }

  return (
    <div>
      <div className="breadcrumbs">
        <Link to={`/projects/${testCase.projectKey}`}>{testCase.projectKey}</Link> / {testCase.humanId}
      </div>
      <div className="toolbar">
        <h1 style={{ margin: 0 }}>
          {testCase.humanId} — {testCase.currentVersion?.title}
        </h1>
        <button className="btn btn-secondary" onClick={() => setEditing((e) => !e)}>
          {editing ? "Abbrechen" : "Bearbeiten"}
        </button>
      </div>
      <ErrorBanner message={error} />

      <div className="card">
        <div style={{ display: "flex", gap: "1rem", marginBottom: "0.75rem" }}>
          <StatusBadge value={testCase.status} />
          <span className="badge">{testCase.priority}</span>
          <span className="muted">v{testCase.currentVersion?.versionNumber}</span>
          {testCase.tags.map((tag) => (
            <span className="tag" key={tag}>
              {tag}
            </span>
          ))}
        </div>
        {testCase.currentVersion?.description && <p>{testCase.currentVersion.description}</p>}
        {testCase.currentVersion?.preconditions && (
          <p>
            <strong>Preconditions:</strong> {testCase.currentVersion.preconditions}
          </p>
        )}
      </div>

      {editing ? (
        <EditForm
          testCase={testCase}
          onSaved={() => {
            setEditing(false);
            load();
          }}
        />
      ) : (
        <>
          <h2>Steps</h2>
          <table>
            <thead>
              <tr>
                <th style={{ width: "3rem" }}>#</th>
                <th>Aktion</th>
                <th>Erwartetes Ergebnis</th>
              </tr>
            </thead>
            <tbody>
              {testCase.currentVersion?.steps.map((step) => (
                <tr key={step.order}>
                  <td>{step.order}</td>
                  <td>{step.action}</td>
                  <td>{step.expectedResult}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      <h2>Requirement Links</h2>
      <RequirementLinksSection testCaseId={testCase.id} links={links ?? []} onChanged={load} />

      <h2>Versionshistorie</h2>
      <table>
        <thead>
          <tr>
            <th>Version</th>
            <th>Titel</th>
            <th>Erstellt</th>
          </tr>
        </thead>
        <tbody>
          {versions?.map((v) => (
            <tr key={v.id}>
              <td>v{v.versionNumber}</td>
              <td>{v.title}</td>
              <td>{new Date(v.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function EditForm({ testCase, onSaved }: { testCase: TestCase; onSaved: () => void }) {
  const cv = testCase.currentVersion;
  const [title, setTitle] = useState(cv?.title ?? "");
  const [description, setDescription] = useState(cv?.description ?? "");
  const [preconditions, setPreconditions] = useState(cv?.preconditions ?? "");
  const [status, setStatus] = useState(testCase.status);
  const [priority, setPriority] = useState(testCase.priority);
  const [tags, setTags] = useState(testCase.tags.join(", "));
  const [steps, setSteps] = useState(cv?.steps.map((s) => ({ action: s.action, expectedResult: s.expectedResult })) ?? []);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await TestCasesApi.update(testCase.id, {
        title,
        description,
        preconditions,
        status,
        priority,
        tags: tags
          .split(",")
          .map((t) => t.trim())
          .filter(Boolean),
        steps: steps.map((s, i) => ({ order: i + 1, action: s.action, expectedResult: s.expectedResult })),
      });
      onSaved();
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
        <label>Status</label>
        <select value={status} onChange={(e) => setStatus(e.target.value as TestCase["status"])}>
          <option value="DRAFT">DRAFT</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="DEPRECATED">DEPRECATED</option>
        </select>
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
        <input value={tags} onChange={(e) => setTags(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Steps</label>
        <p className="muted" style={{ margin: "0 0 0.5rem 0" }}>
          Eine inhaltliche Änderung an Steps/Titel/Beschreibung/Preconditions erzeugt eine neue Version (Historie bleibt erhalten).
        </p>
        {steps.map((step, i) => (
          <div className="step-row" key={i}>
            <span className="muted">{i + 1}.</span>
            <input
              value={step.action}
              onChange={(e) => setSteps(steps.map((s, idx) => (idx === i ? { ...s, action: e.target.value } : s)))}
              required
            />
            <input
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
        Speichern
      </button>
    </form>
  );
}

function RequirementLinksSection({
  testCaseId,
  links,
  onChanged,
}: {
  testCaseId: string;
  links: RequirementLink[];
  onChanged: () => void;
}) {
  const [externalKey, setExternalKey] = useState("");
  const [url, setUrl] = useState("");
  const [summary, setSummary] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await RequirementsApi.create(testCaseId, { provider: "JIRA", externalKey, url: url || undefined, summary: summary || undefined });
      setExternalKey("");
      setUrl("");
      setSummary("");
      onChanged();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <ErrorBanner message={error} />
      <table>
        <thead>
          <tr>
            <th>Provider</th>
            <th>Key</th>
            <th>Summary</th>
          </tr>
        </thead>
        <tbody>
          {links.map((link) => (
            <tr key={link.id}>
              <td>{link.provider}</td>
              <td>
                <a href={link.url} target="_blank" rel="noreferrer">
                  {link.externalKey}
                </a>
              </td>
              <td>{link.summary}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {links.length === 0 && <p className="muted">Noch keine Requirement Links.</p>}
      <form className="toolbar" onSubmit={submit}>
        <input placeholder="Jira-Key (z. B. BIT-27)" value={externalKey} onChange={(e) => setExternalKey(e.target.value)} required />
        <input placeholder="URL (optional, wird sonst per Jira-Anbindung ermittelt)" value={url} onChange={(e) => setUrl(e.target.value)} style={{ flex: 1 }} />
        <input placeholder="Summary (optional)" value={summary} onChange={(e) => setSummary(e.target.value)} style={{ flex: 1 }} />
        <button className="btn" type="submit" disabled={saving}>
          Verknüpfen
        </button>
      </form>
    </div>
  );
}
