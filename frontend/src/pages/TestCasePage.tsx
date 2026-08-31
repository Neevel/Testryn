import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { JiraApi, RequirementsApi, TestCasesApi } from "../api/endpoints";
import type { JiraIssuePreview, RequirementLink, TestCase, TestCaseVersion } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { EmptyState } from "../components/EmptyState";
import { LoadingState } from "../components/LoadingState";
import { StatusBadge } from "../components/StatusBadge";
import { EntityIcon } from "../components/EntityIcon";

export function TestCasePage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
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
        {!error && <LoadingState label="Loading test case…" />}
      </div>
    );
  }

  return (
    <div>
      <div className="breadcrumbs">
        <Link to={`/projects/${testCase.projectKey}`}>{testCase.projectKey}</Link> / {testCase.humanId}
      </div>
      <div className="page-header">
        <div className="title-group">
          <div className="testcase-heading">
            <span className="technical-id">{testCase.humanId}</span>
            <h1 className="entity-title"><EntityIcon kind="testCase"/>{testCase.currentVersion?.title}</h1>
          </div>
        </div>
        <div className="actions">
          <button className="btn btn-danger" onClick={async () => { if (prompt(`Type ${testCase.humanId} to permanently delete this test case. Execution snapshots remain.`) !== testCase.humanId) return; await TestCasesApi.remove(id); navigate(`/projects/${testCase.projectKey}?tab=test-cases`); }}>Delete</button>
          <button className="btn btn-secondary" onClick={() => setEditing((e) => !e)}>
            {editing ? "Cancel" : "Edit"}
          </button>
        </div>
      </div>
      <ErrorBanner message={error} />

      <div className="card testcase-overview">
        <div style={{ display: "flex", gap: "0.75rem", marginBottom: "0.75rem", alignItems: "center", flexWrap: "wrap" }}>
          <StatusBadge value={testCase.status} />
          <span className="badge">{testCase.priority}</span>
          <span className="muted">v{testCase.currentVersion?.versionNumber}</span>
          {testCase.automationReference && (
            <span className="badge" title="Automation reference (ADR 0009)" style={{ fontFamily: "monospace" }}>
              🤖 {testCase.automationReference}
            </span>
          )}
          {testCase.tags.map((tag) => (
            <span className="tag" key={tag}>
              {tag}
            </span>
          ))}
        </div>
        {testCase.currentVersion?.description && <p style={{ marginTop: 0 }}>{testCase.currentVersion.description}</p>}
        {testCase.currentVersion?.preconditions && (
          <p style={{ marginBottom: 0 }}>
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
          {testCase.currentVersion?.steps.length ? (
            <div className="test-steps">
              {testCase.currentVersion.steps.map((step) => (
                <div className="test-step" key={step.order}>
                  <span className="step-number">{String(step.order).padStart(2, "0")}</span>
                  <div className="step-content"><span className="step-label">Action</span><div>{step.action}</div></div>
                  <div className="step-content"><span className="step-label">Input / Data</span><div>{step.inputData || "—"}</div></div>
                  <div className="step-content step-expected"><span className="step-label">Expected result</span><div>{step.expectedResult}</div></div>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState title="No steps defined" />
          )}
        </>
      )}

      <h2>Requirements</h2>
      <RequirementLinksSection testCaseId={testCase.id} links={links ?? []} onChanged={load} />

      <h2>History</h2>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Version</th>
              <th>Title</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {versions?.map((v) => (
              <tr key={v.id}>
                <td>v{v.versionNumber}</td>
                <td>{v.title}</td>
                <td className="muted">{new Date(v.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
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
  const [automationReference, setAutomationReference] = useState(testCase.automationReference ?? "");
  const [steps, setSteps] = useState(cv?.steps.map((s) => ({ action: s.action, inputData: s.inputData ?? "", expectedResult: s.expectedResult })) ?? []);
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
        automationReference: automationReference.trim() || null,
        steps: steps.map((s, i) => ({ order: i + 1, action: s.action, inputData: s.inputData || null, expectedResult: s.expectedResult })),
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
        <label>Status</label>
        <select value={status} onChange={(e) => setStatus(e.target.value as TestCase["status"])}>
          <option value="DRAFT">Draft</option>
          <option value="ACTIVE">Active</option>
          <option value="DEPRECATED">Deprecated</option>
        </select>
      </div>
      <div className="form-row">
        <label>Priority</label>
        <select value={priority} onChange={(e) => setPriority(e.target.value as TestCase["priority"])}>
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
          <option value="CRITICAL">Critical</option>
        </select>
      </div>
      <div className="form-row">
        <label>Tags (comma-separated)</label>
        <input value={tags} onChange={(e) => setTags(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Automation reference</label>
        <input
          value={automationReference}
          onChange={(e) => setAutomationReference(e.target.value)}
          placeholder="e.g. auth.login.valid (optional, for CI result mapping)"
          style={{ fontFamily: "monospace" }}
        />
      </div>
      <div className="form-row">
        <label>Steps</label>
        <p className="form-hint" style={{ margin: "0 0 0.5rem 0" }}>
          Changing the title, description, preconditions or steps creates a new version -- history is preserved.
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
              placeholder="Input / Data (optional)"
              value={step.inputData}
              onChange={(e) => setSteps(steps.map((s, idx) => (idx === i ? { ...s, inputData: e.target.value } : s)))}
            />
            <input
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
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => setSteps([...steps, { action: "", inputData: "", expectedResult: "" }])}>
          + Step
        </button>
      </div>
      <button className="btn" type="submit" disabled={saving}>
        Save
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
  const [showForm, setShowForm] = useState(false);
  const [removing, setRemoving] = useState<RequirementLink | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function confirmRemove() {
    if (!removing) return;
    setBusy(true);
    setError(null);
    try {
      await RequirementsApi.remove(testCaseId, removing.id);
      setRemoving(null);
      onChanged();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <ErrorBanner message={error} />
      {links.length === 0 && !showForm && (
        <EmptyState title="No requirements linked yet">
          Link this test case to a Jira issue to keep coverage traceable.
          <div style={{ marginTop: "0.75rem" }}>
            <button className="btn" onClick={() => setShowForm(true)}>
              Link requirement
            </button>
          </div>
        </EmptyState>
      )}

      {links.map((link) => (
        <div className="requirement-card" key={link.id}>
          <div>
            <div>
              <a className="key" href={link.url} target="_blank" rel="noreferrer">
                {link.externalKey}
              </a>
            </div>
            {link.summary && <div style={{ margin: "0.2rem 0" }}>{link.summary}</div>}
            <div className="muted" style={{ fontSize: "0.8rem" }}>
              {link.issueType && <span>{link.issueType}</span>}
              {link.issueType && link.status && <span> · </span>}
              {link.status && <span>{link.status}</span>}
            </div>
          </div>
          <div style={{ display: "flex", gap: "0.5rem", flexShrink: 0 }}>
            <a className="btn btn-secondary btn-sm" href={link.url} target="_blank" rel="noreferrer">
              Open in Jira
            </a>
            <button className="btn btn-ghost btn-sm" onClick={() => setRemoving(link)}>
              Remove
            </button>
          </div>
        </div>
      ))}

      {links.length > 0 && !showForm && (
        <button className="btn btn-secondary btn-sm" style={{ marginTop: "0.5rem" }} onClick={() => setShowForm(true)}>
          + Link another requirement
        </button>
      )}

      {showForm && <LinkRequirementForm testCaseId={testCaseId} onDone={() => setShowForm(false)} onChanged={onChanged} />}

      {removing && (
        <div className="modal-overlay" onClick={() => setRemoving(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2>Remove requirement link?</h2>
            <p>
              This only removes the link between this test case and <strong>{removing.externalKey}</strong> in Testryn.
              The Jira issue itself is not affected.
            </p>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setRemoving(null)} disabled={busy}>
                Cancel
              </button>
              <button className="btn btn-danger" onClick={confirmRemove} disabled={busy}>
                Remove link
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function LinkRequirementForm({
  testCaseId,
  onDone,
  onChanged,
}: {
  testCaseId: string;
  onDone: () => void;
  onChanged: () => void;
}) {
  const [key, setKey] = useState("");
  const [preview, setPreview] = useState<JiraIssuePreview | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [linking, setLinking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showManualFallback, setShowManualFallback] = useState(false);
  const [manualUrl, setManualUrl] = useState("");
  const [manualSummary, setManualSummary] = useState("");

  async function fetchPreview(e: React.FormEvent) {
    e.preventDefault();
    if (!key.trim()) return;
    setLoadingPreview(true);
    setError(null);
    setPreview(null);
    try {
      const result = await JiraApi.previewIssue(key.trim());
      setPreview(result);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoadingPreview(false);
    }
  }

  async function confirmLink() {
    if (!preview) return;
    setLinking(true);
    setError(null);
    try {
      await RequirementsApi.create(testCaseId, {
        provider: "JIRA",
        externalKey: preview.externalKey,
        url: preview.url,
        summary: preview.summary ?? undefined,
      });
      onChanged();
      onDone();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLinking(false);
    }
  }

  async function submitManualLink(e: React.FormEvent) {
    e.preventDefault();
    if (!key.trim() || !manualUrl.trim()) return;
    setLinking(true);
    setError(null);
    try {
      await RequirementsApi.create(testCaseId, {
        provider: "JIRA",
        externalKey: key.trim(),
        url: manualUrl.trim(),
        summary: manualSummary.trim() || undefined,
      });
      onChanged();
      onDone();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLinking(false);
    }
  }

  return (
    <div className="card" style={{ marginTop: "0.75rem" }}>
      <ErrorBanner message={error} />
      <form onSubmit={fetchPreview} className="toolbar" style={{ marginBottom: preview ? "0.75rem" : 0 }}>
        <input
          placeholder="Jira key (e.g. BIT-27)"
          value={key}
          onChange={(e) => {
            setKey(e.target.value);
            setPreview(null);
          }}
          style={{ flex: 1 }}
          autoFocus
        />
        <button className="btn btn-secondary" type="submit" disabled={loadingPreview || !key.trim()}>
          {loadingPreview ? "Looking up…" : "Preview"}
        </button>
        <button type="button" className="btn btn-ghost" onClick={onDone}>
          Cancel
        </button>
      </form>

      {preview && (
        <div className="jira-preview">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "0.75rem" }}>
            <div>
              <div className="key">{preview.externalKey}</div>
              {preview.summary && <div style={{ margin: "0.3rem 0" }}>{preview.summary}</div>}
              <div className="muted" style={{ fontSize: "0.8rem" }}>
                {preview.issueType} {preview.issueType && preview.status && "· "} {preview.status}
              </div>
            </div>
            <button className="btn" onClick={confirmLink} disabled={linking}>
              {linking ? "Linking…" : "Link this issue"}
            </button>
          </div>
        </div>
      )}

      {!preview && !loadingPreview && key.trim() && !showManualFallback && (
        <p className="form-hint">
          Can't reach Jira right now?{" "}
          <button type="button" className="btn-ghost btn btn-sm" onClick={() => setShowManualFallback(true)}>
            Link "{key.trim()}" without a preview
          </button>
        </p>
      )}

      {showManualFallback && (
        <form onSubmit={submitManualLink} style={{ marginTop: "0.5rem" }}>
          <p className="form-hint" style={{ marginTop: 0 }}>
            Enter the issue URL manually -- Testryn can't reach Jira to look it up right now.
          </p>
          <div className="form-row">
            <label htmlFor="manual-url">Issue URL</label>
            <input
              id="manual-url"
              placeholder="https://your-domain.atlassian.net/browse/BIT-27"
              value={manualUrl}
              onChange={(e) => setManualUrl(e.target.value)}
              autoFocus
              required
            />
          </div>
          <div className="form-row">
            <label htmlFor="manual-summary">Summary (optional)</label>
            <input
              id="manual-summary"
              value={manualSummary}
              onChange={(e) => setManualSummary(e.target.value)}
            />
          </div>
          <div className="toolbar" style={{ marginBottom: 0 }}>
            <button className="btn" type="submit" disabled={linking || !manualUrl.trim()}>
              {linking ? "Linking…" : `Link "${key.trim()}"`}
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setShowManualFallback(false)} disabled={linking}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
