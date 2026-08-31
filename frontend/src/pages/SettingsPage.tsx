import { useEffect, useState } from "react";
import { JiraApi, ServiceTokensApi } from "../api/endpoints";
import type {
  JiraAuthType,
  JiraConnection,
  JiraConnectionTestResult,
  ServiceToken,
  ServiceTokenScope,
} from "../api/types";
import { clearDevToken, getDevToken, setDevToken } from "../api/devToken";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { EmptyState } from "../components/EmptyState";
import { LoadingState } from "../components/LoadingState";

export function SettingsPage() {
  return (
    <div>
      <div className="page-header">
        <div className="title-group">
          <h1>Settings</h1>
        </div>
      </div>

      <DevTokenSection />
      <ServiceTokensSection />
      <JiraConnectionSection />
    </div>
  );
}

/** Abschnitt 8: this is a browser-tab-scoped convenience, not a login system -- see
 * src/api/devToken.ts and docs/security.md. */
function DevTokenSection() {
  const [hasToken, setHasToken] = useState(() => !!getDevToken());
  const [input, setInput] = useState("");

  function save() {
    if (!input.trim()) return;
    setDevToken(input.trim());
    setInput("");
    setHasToken(true);
  }

  function clear() {
    clearDevToken();
    setHasToken(false);
  }

  return (
    <>
      <h2 style={{ marginTop: 0 }}>Your API Token</h2>
      <div className="card" style={{ maxWidth: 560, marginBottom: "1.5rem" }}>
        <p className="form-hint" style={{ marginTop: 0 }}>
          Testryn's API requires a service token (<code>Authorization: Bearer ...</code>). Paste one below to use
          this browser tab -- it is kept only in this tab's session storage (cleared when the tab closes), never
          in a cookie, never sent anywhere but Testryn's own API, and never baked into the app itself. This is a
          minimal development convenience, not a real login system: a browser cannot keep a long-lived token
          truly secret. See docs/security.md.
        </p>
        <div className="form-row">
          <label>Status</label>
          <div>
            <span className={`badge ${hasToken ? "badge-success" : "badge-warning"}`}>
              {hasToken ? "Token set for this tab" : "No token set"}
            </span>
          </div>
        </div>
        <div className="toolbar" style={{ marginBottom: 0 }}>
          <input
            type="password"
            placeholder="testryn_..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            style={{ flex: 1, fontFamily: "monospace" }}
            autoComplete="off"
          />
          <button className="btn" onClick={save} disabled={!input.trim()}>
            Use this token
          </button>
          {hasToken && (
            <button className="btn btn-ghost" onClick={clear}>
              Clear
            </button>
          )}
        </div>
      </div>
    </>
  );
}

/** `active` alone can't distinguish *why* a token is inactive -- a token past its
 * expiresAt has revokedAt = null, so labeling every inactive token "Revoked" would
 * misrepresent one that simply expired (found during browser verification). */
function tokenStatusLabel(token: ServiceToken): string {
  if (token.active) return "Active";
  if (token.revokedAt) return "Revoked";
  return "Expired";
}

function ServiceTokensSection() {
  const [tokens, setTokens] = useState<ServiceToken[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  function load() {
    setError(null);
    ServiceTokensApi.list()
      .then(setTokens)
      .catch((err) => setError(errorMessage(err)));
  }

  useEffect(load, []);

  async function revoke(token: ServiceToken) {
    if (!confirm(`Revoke "${token.name}"? Anything using it will stop working immediately.`)) return;
    try {
      await ServiceTokensApi.revoke(token.id);
      load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <>
      <h2>Service Tokens</h2>
      <p className="page-subtitle" style={{ marginTop: "-0.5rem" }}>
        Machine-to-machine credentials for the CI publisher, scripts, or future integrations. Requires an API
        token with the <code>testryn:admin</code> scope to manage (ADR 0012).
      </p>
      <ErrorBanner message={error} />

      {tokens === null && !error && <LoadingState label="Loading service tokens…" />}

      {tokens?.length === 0 && (
        <EmptyState title="No service tokens yet">
          Create one for the CI publisher or another integration.
          <div style={{ marginTop: "0.75rem" }}>
            <button className="btn" onClick={() => setShowCreate(true)}>
              New service token
            </button>
          </div>
        </EmptyState>
      )}

      {tokens && tokens.length > 0 && (
        <>
          <div className="table-wrap" style={{ marginBottom: "1rem" }}>
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Scopes</th>
                  <th>Created</th>
                  <th>Last used</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {tokens.map((token) => (
                  <tr key={token.id}>
                    <td>
                      {token.name}
                      {token.description && <div className="muted" style={{ fontSize: "0.8rem" }}>{token.description}</div>}
                    </td>
                    <td className="muted">{token.scopes.map((s) => s.replace("testryn:", "")).join(" · ")}</td>
                    <td className="muted">{new Date(token.createdAt).toLocaleDateString()}</td>
                    <td className="muted">{token.lastUsedAt ? new Date(token.lastUsedAt).toLocaleString() : "Never"}</td>
                    <td>
                      <span className={`badge ${token.active ? "badge-success" : ""}`}>
                        {tokenStatusLabel(token)}
                      </span>
                    </td>
                    <td>
                      {token.active && (
                        <button className="btn btn-secondary btn-sm" onClick={() => revoke(token)}>
                          Revoke
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button className="btn" onClick={() => setShowCreate(true)}>
            New service token
          </button>
        </>
      )}

      {showCreate && (
        <CreateServiceTokenModal
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            load();
          }}
        />
      )}
    </>
  );
}

function CreateServiceTokenModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [scopes, setScopes] = useState<Set<ServiceTokenScope>>(new Set(["testryn:read", "testryn:write"]));
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  // Abschnitt 27 (Copy-Safety): held only in local component state, never persisted
  // (no localStorage/sessionStorage), never logged -- gone the moment this modal unmounts.
  const [revealedToken, setRevealedToken] = useState<string | null>(null);

  function toggleScope(scope: ServiceTokenScope) {
    setScopes((prev) => {
      const next = new Set(prev);
      if (next.has(scope)) next.delete(scope);
      else next.add(scope);
      return next;
    });
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim() || scopes.size === 0) return;
    setCreating(true);
    setError(null);
    try {
      const created = await ServiceTokensApi.create({
        name: name.trim(),
        description: description.trim() || undefined,
        scopes: Array.from(scopes),
      });
      setRevealedToken(created.token);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setCreating(false);
    }
  }

  if (revealedToken) {
    return (
      <div className="modal-overlay">
        <div className="modal">
          <h3 style={{ marginTop: 0 }}>Service token created</h3>
          <p className="form-hint" style={{ color: "var(--color-danger, #b91c1c)" }}>
            Copy this token now. It will not be shown again.
          </p>
          <textarea
            readOnly
            value={revealedToken}
            style={{ fontFamily: "monospace", width: "100%", height: "4.5rem" }}
            onFocus={(e) => e.currentTarget.select()}
          />
          <div className="toolbar" style={{ marginTop: "1rem", marginBottom: 0, justifyContent: "flex-end" }}>
            <button
              className="btn"
              onClick={() => {
                setRevealedToken(null); // drop it from state before closing
                onCreated();
              }}
            >
              Done
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-overlay">
      <div className="modal">
        <h3 style={{ marginTop: 0 }}>New service token</h3>
        <form onSubmit={submit}>
          <ErrorBanner message={error} />
          <div className="form-row">
            <label>Name</label>
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="CI Pipeline" required autoFocus />
          </div>
          <div className="form-row">
            <label>Description (optional)</label>
            <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Publisher token for nightly regression" />
          </div>
          <div className="form-row">
            <label>Scopes</label>
            <div style={{ display: "flex", gap: "1rem" }}>
              {(["testryn:read", "testryn:write", "testryn:admin"] as ServiceTokenScope[]).map((scope) => (
                <label key={scope} style={{ display: "flex", alignItems: "center", gap: "0.35rem", fontWeight: "normal" }}>
                  <input type="checkbox" checked={scopes.has(scope)} onChange={() => toggleScope(scope)} />
                  {scope.replace("testryn:", "")}
                </label>
              ))}
            </div>
            <p className="form-hint" style={{ marginBottom: 0 }}>
              write includes read; admin includes both plus service-token management.
            </p>
          </div>
          <div className="toolbar" style={{ marginBottom: 0, justifyContent: "flex-end" }}>
            <button type="button" className="btn btn-ghost" onClick={onClose}>
              Cancel
            </button>
            <button className="btn" type="submit" disabled={creating || !name.trim() || scopes.size === 0}>
              {creating ? "Creating…" : "Create token"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function JiraConnectionSection() {
  const [connection, setConnection] = useState<JiraConnection | null>(null);
  const [name, setName] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [email, setEmail] = useState("");
  const [active, setActive] = useState(true);
  const [authType, setAuthType] = useState<JiraAuthType>("API_TOKEN");
  const [error, setError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<JiraConnectionTestResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [oauthBusy, setOauthBusy] = useState(false);

  function load() {
    setError(null);
    JiraApi.connection()
      .then((result) => {
        setConnection(result);
        setName(result.name);
        setBaseUrl(result.baseUrl ?? "");
        setEmail(result.email ?? "");
        setActive(result.active);
        setAuthType(result.authType);
      })
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

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setTestResult(null);
    try {
      const result = await JiraApi.updateConnection({
        name: name.trim(),
        baseUrl: baseUrl.trim(),
        email: email.trim() || undefined,
        active,
        authType,
      });
      setConnection(result);
      setName(result.name);
      setBaseUrl(result.baseUrl ?? "");
      setEmail(result.email ?? "");
      setActive(result.active);
      setAuthType(result.authType);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function connectOAuth() {
    setOauthBusy(true);
    setError(null);
    try {
      const { authorizationUrl } = await JiraApi.oauthAuthorizeUrl();
      window.location.href = authorizationUrl;
    } catch (err) {
      setError(errorMessage(err));
      setOauthBusy(false);
    }
  }

  async function disconnectOAuth() {
    setOauthBusy(true);
    setError(null);
    try {
      const result = await JiraApi.oauthDisconnect();
      setConnection(result);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setOauthBusy(false);
    }
  }

  return (
    <>
      <h2>Jira Cloud Integration</h2>
      <p className="page-subtitle" style={{ marginTop: "-0.5rem" }}>
        Connect Testryn to any Jira Cloud site. New requirement previews and links use this configuration.
      </p>
      <ErrorBanner message={error} />
      {connection === null && !error && <LoadingState label="Loading connection status…" />}
      {connection && (
        <form className="card integration-settings-card" onSubmit={save}>
          <div className="integration-settings-heading">
            <div>
              <strong>Jira Cloud</strong>
              <div className="form-hint">Requirement source for Jira stories, tasks and bugs</div>
            </div>
            <label className="toggle-label">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Active
            </label>
          </div>
          <div className="form-row">
            <label htmlFor="jira-name">Connection name</label>
            <input id="jira-name" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <div className="form-row">
            <label htmlFor="jira-base-url">Jira Cloud URL</label>
            <input
              id="jira-base-url"
              type="url"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              placeholder="https://company.atlassian.net"
              required
            />
            <p className="form-hint">Use the site root without <code>/browse</code> or an issue key.</p>
          </div>
          <div className="form-row">
            <label htmlFor="jira-email">Atlassian account email</label>
            <input id="jira-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="form-row">
            <label htmlFor="jira-auth-type">Auth type</label>
            <select
              id="jira-auth-type"
              value={authType}
              onChange={(e) => setAuthType(e.target.value as JiraAuthType)}
            >
              <option value="API_TOKEN">API token (HTTP Basic)</option>
              <option value="OAUTH2">OAuth 2.0 (3LO)</option>
            </select>
            <p className="form-hint">Save to apply. API token stays available as a fallback.</p>
          </div>
          <div className="form-row">
            <label>Jira Cloud site</label>
            <div>
              <span className={`badge ${connection.siteConfigured ? "badge-success" : "badge-warning"}`}>
                {connection.siteConfigured ? "Connected" : "Not configured"}
              </span>
            </div>
          </div>
          <div className="form-row">
            <label>Direct API enrichment</label>
            <div>
              <span className={`badge ${connection.usable ? "badge-success" : "badge-warning"}`}>
                {connection.usable ? "Available" : authType === "OAUTH2" ? "Authorization required" : "API token required"}
              </span>
            </div>
          </div>
          {authType === "API_TOKEN" && (
            <div className="form-row">
              <label>Server-side API token</label>
              <div><span className={`badge ${connection.tokenConfigured ? "badge-success" : "badge-warning"}`}>
                {connection.tokenConfigured ? "Configured" : "Not configured"}
              </span></div>
            </div>
          )}
          {authType === "OAUTH2" && (
            <>
              <div className="form-row">
                <label>OAuth client</label>
                <div><span className={`badge ${connection.oauthConfigured ? "badge-success" : "badge-warning"}`}>
                  {connection.oauthConfigured ? "Configured" : "Not configured"}
                </span></div>
              </div>
              <div className="form-row">
                <label>OAuth connection</label>
                <div>
                  <span className={`badge ${connection.oauthConnected ? "badge-success" : "badge-warning"}`}>
                    {connection.oauthConnected ? "Connected" : "Not connected"}
                  </span>
                  {connection.oauthSiteUrl && <span className="muted"> {connection.oauthSiteUrl}</span>}
                  {connection.reauthorizationRequired && (
                    <span className="badge badge-warning" style={{ marginLeft: "0.5rem" }}>Re-authorization required</span>
                  )}
                </div>
              </div>
              <div className="toolbar" style={{ marginBottom: "0.75rem" }}>
                <button
                  className="btn btn-secondary"
                  type="button"
                  onClick={connectOAuth}
                  disabled={oauthBusy || saving || !connection.oauthConfigured}
                >
                  {oauthBusy ? "Working…" : connection.oauthConnected ? "Re-authorize with Atlassian" : "Connect with Atlassian"}
                </button>
                {connection.oauthConnected && (
                  <button className="btn btn-secondary" type="button" onClick={disconnectOAuth} disabled={oauthBusy || saving}>
                    Disconnect
                  </button>
                )}
              </div>
              <p className="form-hint" style={{ marginBottom: "1rem" }}>
                The OAuth client id/secret and the token encryption key are server environment configuration
                (<code>TESTRYN_JIRA_OAUTH_*</code>) and are never shown or sent to this page. Access and refresh
                tokens are stored encrypted. Disconnecting removes only those credentials — no test cases,
                requirement links or Jira issues are touched.
              </p>
            </>
          )}
          {authType === "API_TOKEN" && (
            <p className="form-hint" style={{ marginBottom: "1rem" }}>
              The installed Jira Forge app and this Jira Cloud site selection are valid without exposing a secret here.
              Direct issue previews and enrichment additionally use <code>TESTRYN_JIRA_API_TOKEN</code> in the server
              environment; that token is never shown or sent to this page.
            </p>
          )}
          <div className="toolbar" style={{ marginBottom: 0 }}>
            <button className="btn" type="submit" disabled={saving || !name.trim() || !baseUrl.trim()}>
              {saving ? "Saving…" : "Save Jira configuration"}
            </button>
            <button className="btn btn-secondary" type="button" onClick={runTest} disabled={testing || saving}>
              {testing ? "Testing…" : "Test Jira site / API access"}
            </button>
          </div>
          {testResult && (
            <p style={{ marginTop: "0.75rem" }}>
              <span className={`badge ${testResult.success ? "badge-success" : "badge-danger"}`}>
                {testResult.success ? "Success" : "Failed"}
              </span>{" "}
              <span className="muted">{testResult.message}</span>
            </p>
          )}
        </form>
      )}
    </>
  );
}
