import { useEffect, useState } from "react";
import { JiraApi } from "../api/endpoints";
import type { JiraConnection, JiraConnectionTestResult } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { LoadingState } from "../components/LoadingState";

export function SettingsPage() {
  const [connection, setConnection] = useState<JiraConnection | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<JiraConnectionTestResult | null>(null);
  const [testing, setTesting] = useState(false);

  function load() {
    setError(null);
    JiraApi.connection()
      .then(setConnection)
      .catch((err) => setError(errorMessage(err)));
  }

  useEffect(load, []);

  async function runTest() {
    setTesting(true);
    setTestResult(null);
    setError(null);
    try {
      const result = await JiraApi.testConnection();
      setTestResult(result);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setTesting(false);
    }
  }

  return (
    <div>
      <div className="page-header">
        <div className="title-group">
          <h1>Settings</h1>
        </div>
      </div>
      <ErrorBanner message={error} />

      <h2 style={{ marginTop: 0 }}>Jira Connection</h2>
      {connection === null && !error && <LoadingState label="Loading connection status…" />}
      {connection && (
        <div className="card" style={{ maxWidth: 560 }}>
          <div className="form-row">
            <label>Name</label>
            <div>{connection.name}</div>
          </div>
          <div className="form-row">
            <label>Base URL</label>
            <div>{connection.baseUrl || <span className="faint">not set</span>}</div>
          </div>
          <div className="form-row">
            <label>Identity</label>
            <div>{connection.email || <span className="faint">not set</span>}</div>
          </div>
          <div className="form-row">
            <label>Auth type</label>
            <div>{connection.authType}</div>
          </div>
          <div className="form-row">
            <label>API token</label>
            <div>
              <span className={`badge ${connection.tokenConfigured ? "badge-success" : ""}`}>
                {connection.tokenConfigured ? "Configured" : "Not configured"}
              </span>
            </div>
          </div>
          <div className="form-row">
            <label>Status</label>
            <div>
              <span className={`badge ${connection.active ? "badge-info" : ""}`}>
                {connection.active ? "Active" : "Inactive"}
              </span>{" "}
              <span className={`badge ${connection.usable ? "badge-success" : "badge-warning"}`}>
                {connection.usable ? "Usable" : "Not usable"}
              </span>
            </div>
          </div>
          <p className="form-hint" style={{ marginBottom: "1rem" }}>
            Configured via environment variables (TESTRYN_JIRA_*) -- see README. The API token itself is never
            shown here or sent to this page.
          </p>
          <button className="btn btn-secondary" onClick={runTest} disabled={testing}>
            {testing ? "Testing…" : "Test connection"}
          </button>
          {testResult && (
            <p style={{ marginTop: "0.75rem" }}>
              <span className={`badge ${testResult.success ? "badge-success" : "badge-danger"}`}>
                {testResult.success ? "Success" : "Failed"}
              </span>{" "}
              <span className="muted">{testResult.message}</span>
            </p>
          )}
        </div>
      )}
    </div>
  );
}
