import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { downloadUrl } from "../api/client";
import { ExecutionsApi, ReportsApi } from "../api/endpoints";
import type { Execution, ExecutionResultStatus, ExecutionTestCase, Report } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { StatusBadge } from "../components/StatusBadge";
import { summarize } from "./executionSummary";

const RESULT_ACTIONS: ExecutionResultStatus[] = ["PASSED", "FAILED", "BLOCKED", "SKIPPED"];

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
        {!error && <p className="muted">Lade Execution…</p>}
      </div>
    );
  }

  const summary = summarize(execution.testCases);
  const isTerminal = execution.status === "COMPLETED" || execution.status === "ABORTED";

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
      <div className="toolbar">
        <h1 style={{ margin: 0 }}>
          {execution.name} <span className="muted">#{execution.iterationNumber}</span>
        </h1>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          <StatusBadge value={execution.status} />
          {!isTerminal && (
            <>
              <button className="btn btn-secondary" onClick={() => setPendingAction("COMPLETE")} disabled={busy}>
                Als abgeschlossen markieren
              </button>
              <button className="btn btn-danger" onClick={() => setPendingAction("ABORT")} disabled={busy}>
                Abbrechen
              </button>
            </>
          )}
        </div>
      </div>
      <ErrorBanner message={error} />

      <div className="card summary-bar">
        <div className="summary-counts">
          <div className="stat">
            <span className="value">{summary.total}</span>
            <span className="label">Gesamt</span>
          </div>
          {(["NOT_RUN", "PASSED", "FAILED", "BLOCKED", "SKIPPED"] as ExecutionResultStatus[]).map((status) => (
            <div className="stat" key={status}>
              <span className="value">{summary.counts[status]}</span>
              <span className="label">{status.replace("_", " ")}</span>
            </div>
          ))}
        </div>
        <div className="progress-track">
          <div className="progress-fill" style={{ width: `${summary.progress}%` }} />
        </div>
        <span className="progress-label">{summary.progress}% ausgeführt</span>
      </div>

      <h2>Test Cases</h2>
      {execution.testCases.map((etc) => (
        <RunnerCard
          key={etc.testCaseId}
          etc={etc}
          disabled={busy}
          onChooseStatus={(status) => setEditingResult({ testCase: etc, initialStatus: status })}
        />
      ))}

      <h2>Reports</h2>
      <table>
        <thead>
          <tr>
            <th>Datei</th>
            <th>Größe</th>
            <th>Hochgeladen</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {reports?.map((report) => (
            <tr key={report.id}>
              <td>{report.filename}</td>
              <td>{(report.sizeBytes / 1024).toFixed(1)} KB</td>
              <td>{new Date(report.uploadedAt).toLocaleString()}</td>
              <td>
                <a className="btn btn-secondary" href={downloadUrl(ReportsApi.downloadPath(report.id))}>
                  Download
                </a>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {reports?.length === 0 && <p className="muted">Noch keine Reports hochgeladen.</p>}

      <div className="toolbar">
        <input type="file" ref={fileInput} />
        <button className="btn" onClick={uploadReport} disabled={busy}>
          Report hochladen
        </button>
      </div>

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
        <ConfirmModal
          title={pendingAction === "COMPLETE" ? "Execution abschließen?" : "Execution abbrechen?"}
          warning={
            pendingAction === "COMPLETE" && summary.counts.NOT_RUN > 0
              ? `Es sind noch ${summary.counts.NOT_RUN} Test Case(s) mit Status NOT_RUN. Trotzdem als abgeschlossen markieren?`
              : pendingAction === "ABORT"
                ? "Die Execution wird als abgebrochen markiert. Dies kann nicht rückgängig gemacht werden."
                : "Die Execution wird als abgeschlossen markiert."
          }
          confirmLabel={pendingAction === "COMPLETE" ? "Abschließen" : "Execution abbrechen"}
          danger={pendingAction === "ABORT"}
          busy={busy}
          onCancel={() => setPendingAction(null)}
          onConfirm={confirmPendingAction}
        />
      )}
    </div>
  );
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
    <div className={`runner-card status-${result.status}`}>
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
              <th>Aktion</th>
              <th>Erwartetes Ergebnis</th>
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
              <strong>Kommentar:</strong> {result.comment}
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
  onSave: (input: { status: ExecutionResultStatus; comment: string; actualResult: string; failureDetails: string }) => void;
}) {
  const result = testCase.result;
  const [status, setStatus] = useState<ExecutionResultStatus>(initialStatus);
  const [comment, setComment] = useState(result.comment ?? "");
  const [actualResult, setActualResult] = useState(result.actualResult ?? "");
  const [failureDetails, setFailureDetails] = useState(result.failureDetails ?? "");

  function submit(e: React.FormEvent) {
    e.preventDefault();
    onSave({ status, comment, actualResult, failureDetails });
  }

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>
          Ergebnis: {testCase.testCaseHumanId} — {testCase.title}
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
            <label>Kommentar</label>
            <textarea value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Optional" />
          </div>
          <div className="form-row">
            <label>Actual Result</label>
            <textarea
              value={actualResult}
              onChange={(e) => setActualResult(e.target.value)}
              placeholder="Was wurde tatsächlich beobachtet? (optional)"
            />
          </div>
          <div className="form-row">
            <label>Failure Details</label>
            <textarea
              value={failureDetails}
              onChange={(e) => setFailureDetails(e.target.value)}
              placeholder="Stacktrace, Fehlermeldung, … (optional)"
            />
          </div>
          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
              Abbrechen
            </button>
            <button type="submit" className="btn" disabled={busy}>
              Speichern
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function ConfirmModal({
  title,
  warning,
  confirmLabel,
  danger,
  busy,
  onCancel,
  onConfirm,
}: {
  title: string;
  warning: string;
  confirmLabel: string;
  danger: boolean;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        <p>{warning}</p>
        <div className="modal-actions">
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
            Abbrechen
          </button>
          <button type="button" className={danger ? "btn btn-danger" : "btn"} onClick={onConfirm} disabled={busy}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
