import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { downloadUrl } from "../api/client";
import { ExecutionsApi, ReportsApi } from "../api/endpoints";
import type { Execution, ExecutionResultStatus, ExecutionTestCase, Report } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { EmptyState } from "../components/EmptyState";
import { LoadingState } from "../components/LoadingState";
import { StatusBadge } from "../components/StatusBadge";
import { summarize } from "./executionSummary";

const RESULT_ACTIONS: ExecutionResultStatus[] = ["PASSED", "FAILED", "BLOCKED", "SKIPPED"];
const STATUS_ICON: Record<ExecutionResultStatus, string> = {
  PASSED: "✓",
  FAILED: "✕",
  BLOCKED: "⛔",
  SKIPPED: "»",
  NOT_RUN: "○",
};

interface EditingResult {
  testCase: ExecutionTestCase;
  initialStatus: ExecutionResultStatus;
}

type PendingAction = "COMPLETE" | "ABORT" | null;

export function ExecutionPage() {
  const { id = "" } = useParams();
  const [execution, setExecution] = useState<Execution | null>(null);
  const [reports, setReports] = useState<Report[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [editingResult, setEditingResult] = useState<EditingResult | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  function load() {
    setError(null);
    Promise.all([ExecutionsApi.get(id), ReportsApi.listForExecution(id)])
      .then(([exec, reps]) => {
        setExecution(exec);
        setReports(reps);
      })
      .catch((err) => setError(errorMessage(err)));
  }

  useEffect(load, [id]);

  async function saveResult(input: {
    status: ExecutionResultStatus;
    comment: string;
    actualResult: string;
    failureDetails: string;
    durationMs: string;
    executor: string;
  }) {
    if (!editingResult) return;
    setBusy(true);
    setError(null);
    try {
      await ExecutionsApi.updateResult(id, editingResult.testCase.result.id, {
        status: input.status,
        comment: input.comment || undefined,
        actualResult: input.actualResult || undefined,
        failureDetails: input.failureDetails || undefined,
        durationMs: input.durationMs ? Number(input.durationMs) : undefined,
        executor: input.executor || undefined,
      });
      setEditingResult(null);
      load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function uploadReport() {
    const file = fileInput.current?.files?.[0];
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      await ReportsApi.upload(id, file);
      if (fileInput.current) fileInput.current.value = "";
      load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function confirmPendingAction() {
    if (!pendingAction) return;
    setBusy(true);
    setError(null);
    try {
      await ExecutionsApi.setStatus(id, pendingAction === "COMPLETE" ? "COMPLETED" : "ABORTED");
      setPendingAction(null);
      load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  if (!execution) {
    return (
      <div>
        <ErrorBanner message={error} />
        {!error && <LoadingState label="Loading execution…" />}
      </div>
    );
  }

  const summary = summarize(execution.testCases);
  const isTerminal = execution.status === "COMPLETED" || execution.status === "ABORTED";
  const durationLabel = formatDuration(execution.startedAt, execution.finishedAt);

  return (
    <div>
      <div className="breadcrumbs">
        <Link to={`/projects/${execution.projectKey}`}>{execution.projectKey}</Link>
        {execution.testPlanId && (
          <>
            {" / "}
            <Link to={`/test-plans/${execution.testPlanId}`}>Test Plan</Link>
          </>
        )}
        {" / "}
        {execution.name}
      </div>
      <div className="page-header">
        <div className="title-group">
          <h1>
            {execution.name} <span className="muted">#{execution.iterationNumber}</span>
          </h1>
          <StatusBadge value={execution.status} />
        </div>
        {!isTerminal && (
          <div className="actions">
            <button className="btn btn-secondary" onClick={() => setPendingAction("COMPLETE")} disabled={busy}>
              Mark as completed
            </button>
            <button className="btn btn-danger" onClick={() => setPendingAction("ABORT")} disabled={busy}>
              Abort
            </button>
          </div>
        )}
      </div>
      <ErrorBanner message={error} />

      <div className="card summary-bar">
        <div className="summary-counts">
          <div className="stat">
            <span className="value">{summary.total}</span>
            <span className="label">Total</span>
          </div>
          {(["PASSED", "FAILED", "BLOCKED", "SKIPPED", "NOT_RUN"] as ExecutionResultStatus[]).map((status) => (
            <div className="stat" key={status}>
              <span className="value">{summary.counts[status]}</span>
              <span className="label">{status.replace("_", " ")}</span>
            </div>
          ))}
        </div>
        <div className="progress-track">
          <div className="progress-fill" style={{ width: `${summary.progress}%` }} />
        </div>
        <span className="progress-label">{summary.progress}% executed</span>
      </div>

      <div className="result-meta" style={{ marginBottom: "1.5rem" }}>
        <span>Created: {new Date(execution.createdAt).toLocaleString()}</span>
        <span>Started: {execution.startedAt ? new Date(execution.startedAt).toLocaleString() : "not started"}</span>
        <span>Finished: {execution.finishedAt ? new Date(execution.finishedAt).toLocaleString() : "not finished"}</span>
        {durationLabel && <span>Duration: {durationLabel}</span>}
      </div>

      <h2>Test Cases</h2>
      {execution.testCases.length > 1 && (
        <div className="quicknav">
          {execution.testCases.map((etc) => (
            <a
              key={etc.testCaseId}
              className={`quicknav-chip status-${etc.result.status}`}
              href={`#tc-${etc.testCaseId}`}
              onClick={(e) => {
                e.preventDefault();
                document.getElementById(`tc-${etc.testCaseId}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
              }}
            >
              <span aria-hidden="true">{STATUS_ICON[etc.result.status]}</span>
              {etc.testCaseHumanId}
            </a>
          ))}
        </div>
      )}
      {execution.testCases.map((etc) => (
        <RunnerCard
          key={etc.testCaseId}
          etc={etc}
          disabled={busy}
          onChooseStatus={(status) => setEditingResult({ testCase: etc, initialStatus: status })}
        />
      ))}

      <h2>Reports</h2>
      {reports === null && !error && <LoadingState label="Loading reports…" />}
      {reports?.length === 0 && <EmptyState title="No reports uploaded yet">Upload evidence for this execution below.</EmptyState>}
      {reports && reports.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>File</th>
                <th>Type</th>
                <th>Size</th>
                <th>Uploaded</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {reports.map((report) => (
                <tr key={report.id}>
                  <td>{report.filename}</td>
                  <td className="muted">{report.contentType}</td>
                  <td className="muted">{(report.sizeBytes / 1024).toFixed(1)} KB</td>
                  <td className="muted">{new Date(report.uploadedAt).toLocaleString()}</td>
                  <td>
                    <a className="btn btn-secondary btn-sm" href={downloadUrl(ReportsApi.downloadPath(report.id))}>
                      Download
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="toolbar" style={{ marginTop: "0.75rem" }}>
        <input type="file" ref={fileInput} />
        <button className="btn" onClick={uploadReport} disabled={busy}>
          Upload report
        </button>
      </div>
      <p className="form-hint">Max. 50 MB per file. Content type is validated server-side.</p>

      {editingResult && (
        <ResultEditorModal
          testCase={editingResult.testCase}
          initialStatus={editingResult.initialStatus}
          busy={busy}
          onCancel={() => setEditingResult(null)}
          onSave={saveResult}
        />
      )}

      {pendingAction && (
        <CompletionModal
          action={pendingAction}
          summary={summary}
          busy={busy}
          onCancel={() => setPendingAction(null)}
          onConfirm={confirmPendingAction}
        />
      )}
    </div>
  );
}

function formatDuration(startedAt: string | null, finishedAt: string | null): string | null {
  if (!startedAt || !finishedAt) return null;
  const ms = new Date(finishedAt).getTime() - new Date(startedAt).getTime();
  if (ms < 0) return null;
  const totalSeconds = Math.round(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
}

function RunnerCard({
  etc,
  disabled,
  onChooseStatus,
}: {
  etc: ExecutionTestCase;
  disabled: boolean;
  onChooseStatus: (status: ExecutionResultStatus) => void;
}) {
  const result = etc.result;
  return (
    <div id={`tc-${etc.testCaseId}`} className={`runner-card status-${result.status}`}>
      <div className="runner-card-header">
        <div>
          <h3>
            {etc.testCaseHumanId} — {etc.title} <span className="muted">v{etc.testCaseVersionNumber}</span>
          </h3>
        </div>
        <StatusBadge value={result.status} />
      </div>

      {etc.description && <p style={{ marginTop: 0 }}>{etc.description}</p>}
      {etc.preconditions && (
        <p className="muted" style={{ marginTop: 0 }}>
          <strong>Preconditions:</strong> {etc.preconditions}
        </p>
      )}

      {etc.steps.length > 0 && (
        <table>
          <thead>
            <tr>
              <th style={{ width: "3rem" }}>#</th>
              <th>Action</th>
              <th>Expected Result</th>
            </tr>
          </thead>
          <tbody>
            {etc.steps.map((step) => (
              <tr key={step.order}>
                <td>{step.order}</td>
                <td>{step.action}</td>
                <td>{step.expectedResult}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {(result.comment || result.actualResult || result.failureDetails) && (
        <div className="result-summary">
          {result.comment && (
            <span>
              <strong>Comment:</strong> {result.comment}
            </span>
          )}
          {result.actualResult && (
            <span>
              <strong>Actual Result:</strong> {result.actualResult}
            </span>
          )}
          {result.failureDetails && (
            <span>
              <strong>Failure Details:</strong> {result.failureDetails}
            </span>
          )}
        </div>
      )}

      {(result.executedAt || result.executor || result.durationMs != null) && (
        <div className="result-meta">
          {result.executedAt && <span>Executed: {new Date(result.executedAt).toLocaleString()}</span>}
          {result.executor && <span>By: {result.executor}</span>}
          {result.durationMs != null && <span>Duration: {(result.durationMs / 1000).toFixed(1)}s</span>}
        </div>
      )}

      <div className="result-actions">
        {RESULT_ACTIONS.map((status) => (
          <button
            key={status}
            type="button"
            className={`btn btn-outcome-${status}${result.status === status ? " btn-outcome-active" : ""}`}
            disabled={disabled}
            onClick={() => onChooseStatus(status)}
          >
            {result.status === status ? `✓ ${status}` : status}
          </button>
        ))}
      </div>
    </div>
  );
}

function ResultEditorModal({
  testCase,
  initialStatus,
  busy,
  onCancel,
  onSave,
}: {
  testCase: ExecutionTestCase;
  initialStatus: ExecutionResultStatus;
  busy: boolean;
  onCancel: () => void;
  onSave: (input: {
    status: ExecutionResultStatus;
    comment: string;
    actualResult: string;
    failureDetails: string;
    durationMs: string;
    executor: string;
  }) => void;
}) {
  const result = testCase.result;
  const [status, setStatus] = useState<ExecutionResultStatus>(initialStatus);
  const [comment, setComment] = useState(result.comment ?? "");
  const [actualResult, setActualResult] = useState(result.actualResult ?? "");
  const [failureDetails, setFailureDetails] = useState(result.failureDetails ?? "");
  const [durationMs, setDurationMs] = useState(result.durationMs != null ? String(result.durationMs) : "");
  const [executor, setExecutor] = useState(result.executor ?? "");

  function submit(e: React.FormEvent) {
    e.preventDefault();
    onSave({ status, comment, actualResult, failureDetails, durationMs, executor });
  }

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>
          Result: {testCase.testCaseHumanId} — {testCase.title}
        </h2>
        <form onSubmit={submit}>
          <div className="form-row">
            <label>Status</label>
            <select value={status} onChange={(e) => setStatus(e.target.value as ExecutionResultStatus)}>
              {(["NOT_RUN", ...RESULT_ACTIONS] as ExecutionResultStatus[]).map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <label>Comment</label>
            <textarea value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Optional" />
          </div>
          <div className="form-row">
            <label>Actual Result</label>
            <textarea
              value={actualResult}
              onChange={(e) => setActualResult(e.target.value)}
              placeholder="What was actually observed? (optional)"
            />
          </div>
          <div className="form-row">
            <label>Failure Details</label>
            <textarea
              value={failureDetails}
              onChange={(e) => setFailureDetails(e.target.value)}
              placeholder="Stack trace, error message, … (optional)"
            />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
            <div className="form-row">
              <label>Duration (ms)</label>
              <input
                type="number"
                min={0}
                value={durationMs}
                onChange={(e) => setDurationMs(e.target.value)}
                placeholder="Optional"
              />
            </div>
            <div className="form-row">
              <label>Executor</label>
              <input value={executor} onChange={(e) => setExecutor(e.target.value)} placeholder="Optional" />
            </div>
          </div>
          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
              Cancel
            </button>
            <button type="submit" className="btn" disabled={busy}>
              Save
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function CompletionModal({
  action,
  summary,
  busy,
  onCancel,
  onConfirm,
}: {
  action: "COMPLETE" | "ABORT";
  summary: ReturnType<typeof summarize>;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const title = action === "COMPLETE" ? "Complete execution?" : "Abort execution?";
  const confirmLabel = action === "COMPLETE" ? "Complete" : "Abort execution";

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        <div className="card" style={{ marginBottom: "1rem" }}>
          <div className="stat" style={{ marginBottom: "0.5rem" }}>
            <span className="value">{summary.total}</span>
            <span className="label">Tests</span>
          </div>
          <div className="muted" style={{ fontSize: "0.85rem", lineHeight: 1.8 }}>
            {summary.counts.PASSED} passed
            <br />
            {summary.counts.FAILED} failed
            <br />
            {summary.counts.BLOCKED} blocked
            <br />
            {summary.counts.SKIPPED} skipped
            <br />
            {summary.counts.NOT_RUN} not run
          </div>
        </div>
        {action === "COMPLETE" && summary.counts.NOT_RUN > 0 && (
          <div className="confirm-banner">
            <strong>Heads up:</strong> {summary.counts.NOT_RUN} test case(s) are still NOT_RUN. You can complete the
            execution anyway, but confirm this is intentional.
          </div>
        )}
        {action === "ABORT" && (
          <p className="muted">This marks the execution as aborted. This cannot be undone.</p>
        )}
        <div className="modal-actions">
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button type="button" className={action === "ABORT" ? "btn btn-danger" : "btn"} onClick={onConfirm} disabled={busy}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
