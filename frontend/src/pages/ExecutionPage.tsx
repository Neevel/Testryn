import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { downloadUrl } from "../api/client";
import { ExecutionsApi, ReportsApi } from "../api/endpoints";
import type { Execution, ExecutionResultStatus, Report } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { StatusBadge } from "../components/StatusBadge";

const RESULT_STATUSES: ExecutionResultStatus[] = ["NOT_RUN", "PASSED", "FAILED", "SKIPPED", "BLOCKED"];

export function ExecutionPage() {
  const { id = "" } = useParams();
  const [execution, setExecution] = useState<Execution | null>(null);
  const [reports, setReports] = useState<Report[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
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

  async function updateResult(resultId: string, status: ExecutionResultStatus) {
    setBusy(true);
    setError(null);
    try {
      const comment = window.prompt("Kommentar (optional):") ?? undefined;
      await ExecutionsApi.updateResult(id, resultId, { status, comment: comment || undefined });
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

  async function setExecutionStatus(status: "COMPLETED" | "ABORTED") {
    setBusy(true);
    setError(null);
    try {
      await ExecutionsApi.setStatus(id, status);
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
          <button className="btn btn-secondary" onClick={() => setExecutionStatus("COMPLETED")} disabled={busy}>
            Als abgeschlossen markieren
          </button>
          <button className="btn btn-danger" onClick={() => setExecutionStatus("ABORTED")} disabled={busy}>
            Abbrechen
          </button>
        </div>
      </div>
      <ErrorBanner message={error} />

      <h2>Test Cases</h2>
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Titel</th>
            <th>Version</th>
            <th>Ergebnis</th>
            <th>Kommentar</th>
            <th>Aktion</th>
          </tr>
        </thead>
        <tbody>
          {execution.testCases.map((etc) => (
            <tr key={etc.testCaseId}>
              <td>
                <Link to={`/test-cases/${etc.testCaseId}`}>{etc.testCaseHumanId}</Link>
              </td>
              <td>{etc.title}</td>
              <td>v{etc.testCaseVersionNumber}</td>
              <td>
                <StatusBadge value={etc.result.status} />
              </td>
              <td>{etc.result.comment}</td>
              <td>
                <select
                  value=""
                  onChange={(e) => {
                    if (e.target.value) updateResult(etc.result.id, e.target.value as ExecutionResultStatus);
                  }}
                  disabled={busy}
                >
                  <option value="">Ergebnis setzen…</option>
                  {RESULT_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

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
    </div>
  );
}
