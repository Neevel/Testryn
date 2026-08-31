# ADR 0018: Jira Cloud OAuth 2.0 (3LO) für die direkte Anbindung

- Status: Angenommen
- Datum: 2026-08-31
- Erweitert: ADR 0005, 0007, 0012, 0017 (ersetzt keinen davon)

## Context

Testryns direkte Jira-Cloud-Anbindung (Verbindungstest, Issue-Lookup,
Requirement-Enrichment — ADR 0005/0017) authentifiziert sich bisher ausschließlich
per HTTP Basic mit `email` + klassischem Atlassian-API-Token
(`TESTRYN_JIRA_API_TOKEN`, externe Serverkonfiguration). `JiraAuthType.OAUTH2`
existiert seit ADR 0007 als Platzhalter, ist aber nicht implementiert.

OAuth 2.0 (3LO, Authorization Code Grant) ist der von Atlassian offiziell
empfohlene Weg für serverseitige Integrationen und vermeidet ein langlebiges,
personengebundenes Basic-Auth-Token. Dieser Block implementiert OAUTH2 als
**zweite, gleichwertige Auth-Art derselben einen Jira-Verbindung**. API_TOKEN
bleibt vollständig kompatibel und Default.

Die Jira-Forge-App ist **nicht betroffen**: sie spricht ausschließlich Testryns
eigene REST-API mit ihrem serverseitigen Testryn-Service-Token (ADR 0014/0016) und
ruft die Jira-REST-API nie selbst auf.

### Recherchierte Atlassian-Fakten (offizielle Doku, Stand 2026-08)

- Authorization: `GET https://auth.atlassian.com/authorize` mit
  `audience=api.atlassian.com`, `client_id`, `scope` (space-separiert),
  `redirect_uri`, `state`, `response_type=code`, `prompt=consent`.
- Token: `POST https://auth.atlassian.com/oauth/token` (JSON). Code-Tausch:
  `grant_type=authorization_code` + `client_id` + `client_secret` + `code` +
  `redirect_uri`. Refresh: `grant_type=refresh_token` + `client_id` +
  `client_secret` + `refresh_token`.
- Antwort: `access_token`, `expires_in` (Sekunden), `scope`, sowie `refresh_token`
  **nur wenn** `offline_access` im Scope war.
- **Rotating Refresh Tokens**: jeder Refresh liefert einen neuen Refresh-Token, der
  alte wird ungültig. **Reuse-Leeway 10 Minuten** (in diesem Fenster löst
  mehrfaches Einlösen keine Breach-Detection aus — deckt Netz-/Nebenläufigkeit ab).
  Inaktivitäts-Ablauf 90 Tage, wird bei jedem Refresh zurückgesetzt.
- `GET https://api.atlassian.com/oauth/token/accessible-resources` (Bearer) →
  Array `{ id (cloudId), name, url, scopes }`.
- OAuth-Requests laufen über das **API-Gateway**
  `https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3/...`, **nicht** über
  `https://<tenant>.atlassian.net`. Das ist der zentrale Unterschied zum
  API_TOKEN-Pfad, der weiterhin die Tenant-URL nutzt.
- PKCE ist für 3LO nicht dokumentiert/erforderlich (kein öffentlicher Client; das
  `client_secret` bleibt serverseitig). Schutz gegen Flow-Hijacking erfolgt über
  `state`.
- `redirect_uri` muss **exakt** der im Atlassian Developer Console hinterlegten
  Callback-URL entsprechen.

### Minimal-Scopes

`read:jira-work offline_access`.

- `read:jira-work` deckt `GET /rest/api/3/issue/{key}` (summary, issuetype, status,
  description) für Lookup und Enrichment ab.
- `offline_access` ist Voraussetzung für den Refresh-Token.
- Der Verbindungstest im OAUTH2-Modus nutzt **accessible-resources** (kein
  Jira-Scope nötig) statt `/rest/api/3/myself` (bräuchte zusätzlich
  `read:jira-user`) — hält die angeforderten Scopes minimal.

## Decision

### 1. Auth-Art ist persistierte Verbindungskonfiguration, Default `API_TOKEN`

`auth_type` wandert aus der reinen Env-Konfiguration in
`jira_connection_configuration` (Spalte `auth_type`, Default `'API_TOKEN'`) und ist
über das bestehende `PUT /api/v1/integrations/jira/connection` umschaltbar. Der
`JiraConnectionSettings`-Snapshot trägt `authType` weiterhin; nur die Quelle
ändert sich (persistiert statt `JiraProperties`). Fehlt die Spalte/Zeile, gilt
`API_TOKEN`.

### 2. OAuth-Code ausschließlich unter `integration.jira.oauth`

Neues Subpackage `com.testryn.integration.jira.oauth`:
`JiraOAuthProperties`, `SecretCipher`, `JiraOAuthState`/`JiraOAuthToken` (Entities),
Repositories, `JiraOAuthClient` (Atlassian-HTTP), `JiraOAuthService` (Flow +
Refresh + Disconnect). `RequirementProvider`, `RequirementLink`, das
`requirement`-Modul und das Core-Domain-Modell bleiben unverändert und
provider-neutral (ADR 0005). `JiraIssueClient` bekommt einen zweiten Auth-Zweig,
mehr nicht.

### 3. Flow

```
Admin (Service-Token mit testryn:admin, über Settings-UI)
  │  POST /api/v1/integrations/jira/oauth/authorize-url
  ▼
Backend: state=SecureRandom(32B); speichert SHA-256(state) + expires_at(+10min)
         baut https://auth.atlassian.com/authorize?...&state=<state>
  │  { authorizationUrl }
  ▼
Browser des Admins  ──▶  auth.atlassian.com  ──(Zustimmung)──▶
  GET https://<host>/integrations/jira/oauth/callback?code=…&state=…   (öffentlich)
  ▼
Backend: state atomar einlösen (DELETE … WHERE state_hash=? AND expires_at>now RETURNING …; genau 1 Zeile)
         POST auth.atlassian.com/oauth/token (authorization_code)   → access+refresh+expires_in
         GET  api.atlassian.com/oauth/token/accessible-resources    → cloudId je Site
         Site-Match: accessible-resources.url.host == konfigurierte baseUrl.host  (sonst Fehler)
         Tokens AES-256-GCM-verschlüsselt in jira_oauth_token (id='default') upserten
  │  minimale HTML-Seite "Authorization complete" (kein Redirect, keine Secrets)
```

### 4. Endpunkt-Schutz

- `POST …/oauth/authorize-url` und `POST …/oauth/disconnect`: unter `/api/**`,
  erfordern `testryn:admin` (eigener SecurityConfig-Matcher, analog
  `/api/v1/service-tokens/**` — eine instanzweite Credential-Konfiguration ist
  Admin-Grade, ADR 0012 Entscheidung 4).
- `GET /integrations/jira/oauth/callback`: **permitAll** (ein Browser-Redirect von
  Atlassian kann keinen Testryn-Service-Token tragen). Liegt bewusst **außerhalb**
  `/api/**`. Schutz ausschließlich über `state`: 32 zufällige Bytes, nur als
  SHA-256 gespeichert, TTL 10 min, **einmalig** (atomar per `DELETE … RETURNING`
  bzw. betroffene-Zeilen==1), Replay damit unmöglich. `code` ist zusätzlich
  Atlassian-seitig einmalig. Keine Redirect-URL wird je vom Client übernommen —
  `redirect_uri` kommt ausnahmslos aus `TESTRYN_JIRA_OAUTH_REDIRECT_URI`.

### 5. Client-ID / Client-Secret / Redirect-URI

Nur Environment / externe Serverkonfiguration, nie persistiert, nie im Repo, nie in
API-Antworten oder Logs:

| Variable | Zweck |
|---|---|
| `TESTRYN_JIRA_OAUTH_CLIENT_ID` | OAuth-App Client-ID (Atlassian Developer Console) |
| `TESTRYN_JIRA_OAUTH_CLIENT_SECRET` | OAuth-App Secret |
| `TESTRYN_JIRA_OAUTH_REDIRECT_URI` | exakt die in der Console hinterlegte Callback-URL |

Gebunden über `@ConfigurationProperties(prefix = "testryn.jira.oauth")`. Die
GET-Statusantwort meldet nur `oauthConfigured` (bool).

### 6. Token-Speicherung — kein Klartext in PostgreSQL

Tabelle `jira_oauth_token`, genau eine Zeile (`id='default'`, wie
`jira_connection_configuration`). `access_token` und `refresh_token` werden mit
**AES-256-GCM** verschlüsselt gespeichert (`access_token_ciphertext`,
`refresh_token_ciphertext` — Base64 von `iv(12B) || ciphertext || tag(16B)`, IV je
Verschlüsselung frisch aus `SecureRandom`). Klartext existiert nur transient im
Speicher für den einzelnen ausgehenden Request.

Schlüssel ausschließlich aus `TESTRYN_JIRA_OAUTH_ENCRYPTION_KEY` (Base64 von genau 32 Bytes).
Fehlt/ungültig: Anwendung startet normal (API_TOKEN-Modus unberührt), aber jede
OAuth-Operation scheitert mit klarer Meldung „encryption key is not configured".
Der Schlüssel erscheint nie in Antworten, Logs oder Exceptions.

Weitere Spalten: `access_token_expires_at`, `cloud_id`, `site_url`, `scopes`,
`obtained_at`, `updated_at`.

### 7. Refresh — atomar und nebenläufigkeitssicher

`JiraOAuthService.currentAccessToken()`:

1. Zeile per `SELECT … FOR UPDATE` (JPA `@Lock(PESSIMISTIC_WRITE)`) laden — das
   serialisiert alle Refresh-Versuche **einer** Testryn-Instanz auf die eine Zeile.
2. Ist `access_token_expires_at` noch > `now + 60s` Puffer: entschlüsseln,
   zurückgeben, Lock freigeben. Ein parallel wartender zweiter Aufruf sieht danach
   den frischen Token und refresht **nicht** erneut.
3. Sonst: `POST auth.atlassian.com/oauth/token` (`grant_type=refresh_token`).
   Erfolg → **neuen** access- *und* refresh-Token verschlüsselt zurückschreiben
   (Rotation: alten Refresh-Token ersetzen), `expires_at`/`updated_at` setzen,
   committen.
4. Refresh-Fehler `4xx` (`invalid_grant` etc.) → Refresh-Token endgültig tot:
   `jira_oauth_token`-Zeile **löschen**, `UpstreamServiceException`
   („Jira authorization expired — re-authorize"). Status wird dadurch
   `AUTHORIZATION_REQUIRED`.
5. Refresh-Fehler `5xx`/Netz → transient: Zeile bleibt,
   `UpstreamServiceException("Jira is not reachable")`.

Der 10-Minuten-Reuse-Leeway von Atlassian deckt den seltenen Fall ab, dass zwei
Instanzen/Prozesse trotzdem gleichzeitig refreshen.

### 8. Site-Bestimmung über `accessible-resources`

Nach dem Code-Tausch liefert `accessible-resources` alle Sites des autorisierenden
Kontos. Testryn wählt die Site, deren `url`-Host dem bereits in
`jira_connection_configuration.base_url` konfigurierten Host entspricht
(case-insensitive, `*.atlassian.net`, HTTPS — dieselbe Validierung wie ADR 0017).
Kein Treffer → Fehler „the authorized Atlassian account cannot access
<baseUrl>". Der `cloud_id` der Treffer-Site wird gespeichert und für alle
Gateway-URLs verwendet. Kein separater UI-Auswahlschritt (die Site ist bereits
Teil der bestehenden Verbindungskonfiguration).

### 9. Disconnect ohne Datenverlust

`POST /api/v1/integrations/jira/oauth/disconnect` (admin): Best-Effort-Revoke bei
`https://auth.atlassian.com/oauth/revoke` (Refresh-Token), danach `DELETE` der
`jira_oauth_token`-Zeile. `auth_type` bleibt auf `OAUTH2` (Admin entscheidet
bewusst über `PUT …/connection`, ob zurück zu `API_TOKEN`); Status wird
`AUTHORIZATION_REQUIRED`. **Niemals** werden Test Cases, Requirement Links,
Executions oder Jira-Issues berührt — der Endpoint kennt nur die eine Token-Zeile.

### 10. `JiraIssueClient` — zwei Auth-Zweige

| | API_TOKEN (unverändert) | OAUTH2 (neu) |
|---|---|---|
| Base-URL | `https://<tenant>.atlassian.net` | `https://api.atlassian.com/ex/jira/{cloudId}` |
| Header | `Authorization: Basic base64(email:token)` | `Authorization: Bearer <access token>` |
| Token-Quelle | `TESTRYN_JIRA_API_TOKEN` (Env) | `JiraOAuthService.currentAccessToken()` (entschlüsselt, auto-refresh) |
| Pfade | `/rest/api/3/issue/{key}`, `/rest/api/3/myself` | identische Pfade, hinter dem Gateway |
| `usable()` | active && baseUrl && email && apiToken | active && baseUrl && oauthConfigured && encryptionKey && Token-Zeile vorhanden |
| Connection-Test | `/rest/api/3/myself` bzw. `/serverInfo` | `accessible-resources` + Site-Match |

Weiterhin `RestClient` mit `SimpleClientHttpRequestFactory` (AGENTS.md §6a) — auch
für `JiraOAuthClient`. Nie der JDK-`HttpClient`.

### 11. Callback-URL lokal vs. Tunnel

`redirect_uri` muss der Console-Registrierung exakt entsprechen. Lokaler
Docker-Betrieb: `http://localhost:8080/integrations/jira/oauth/callback`
(Atlassian erlaubt `http://localhost` für Entwicklung). Über Tunnel:
`https://<tunnel-host>/integrations/jira/oauth/callback`. Der Wert wird über
`TESTRYN_JIRA_OAUTH_REDIRECT_URI` gesetzt und sowohl in die Authorization-URL als
auch in den Code-Tausch eingesetzt; ein Callback-Request liefert ihn nie.

### 12. Single-Connection genügt

Ja. ADR 0007/0017 legen genau eine Verbindung je Instanz fest. OAuth ergänzt genau
eine Token-Zeile. Multi-Connection bleibt ein späterer, eigenständiger Block.

## Alternativen (verworfen)

- **UI-Site-Auswahl nach dem Callback** statt Match gegen `base_url`: zusätzlicher
  Endpoint + UI-Zustand ohne Mehrwert — die Site ist bereits konfiguriert
  (ADR 0017).
- **Refresh per optimistischem `@Version`-Lock statt `FOR UPDATE`**: funktioniert
  auch, aber „bei Konflikt neu lesen und Sieger übernehmen" ist umständlicher zu
  begründen als eine kurze pessimistische Sperre auf genau einer Zeile.
- **`state` im Klartext / im Cookie / in der Session**: Testryn ist stateless
  (ADR 0012); eine kurzlebige DB-Zeile mit gehashtem `state` ist konsistent und
  ohne Session-Infrastruktur replay-sicher.
- **Access-/Refresh-Token per `TESTRYN_JIRA_API_TOKEN`-Muster nur in Env**: OAuth
  rotiert Tokens zur Laufzeit; Env-Only ginge nicht ohne Neustart je Refresh.
  Verschlüsselte DB-Persistenz ist hier zwingend.
- **PKCE ergänzen**: für 3LO mit vertraulichem Client (Secret serverseitig) von
  Atlassian nicht vorgesehen; `state` + Secret genügen.
- **OAuth für Testryns *eigene* API**: unverändert außerhalb des Scopes (ADR 0012)
  — hier geht es nur um Testryn→Jira.

## Security-Auswirkungen

- **Vertrauensmodell**: der Flow autorisiert *ein* Atlassian-Konto und speichert
  dessen Tokens als instanzweite Credentials — exakt analog zum bestehenden
  API-Token (eine Jira-Identität je Instanz, ADR 0007/0017). Der ausführende Admin
  ist über `testryn:admin` abgesichert; Human-User-Auth bleibt separat (ADR 0012).
- **Secrets erreichen nie Frontend/Logs/Antworten**: `client_id`, `client_secret`,
  `TESTRYN_JIRA_OAUTH_ENCRYPTION_KEY`, Access- und Refresh-Token. Antworten liefern nur
  `oauthConfigured`, `oauthConnected`, `authType`, `site`,
  `reauthorizationRequired`. `SecurityLoggingTest`-Muster wird um die neuen
  Secret-Werte erweitert.
- **Kein Klartext-Token in PostgreSQL** (AES-256-GCM, Schlüssel nur aus Env).
- **`state`**: 256 bit Entropie, gehasht gespeichert, TTL 10 min, einmalig, atomar
  eingelöst → kein Replay, kein CSRF auf den Callback.
- **Kein offener Redirect**: Callback rendert statisches HTML, `redirect_uri` nie
  client-gesteuert.
- **SSRF-Grenze bleibt**: Jira-Cloud weiterhin nur HTTPS + `*.atlassian.net`
  (Tenant-URL-Validierung ADR 0017); OAuth-Hosts sind die festen Atlassian-Domains
  `auth.atlassian.com` / `api.atlassian.com`.
- **Disconnect** entfernt nur Credentials, nie fachliche Daten.

## Token-Lifecycle

```
authorize-url ──▶ callback ──▶ [access(≈1h) + refresh(rotierend, 90d Inaktivität)]
                                   │
     Issue-Lookup / Test / Enrichment
                                   │  access abgelaufen?
                                   ├─ nein ─▶ benutzen
                                   └─ ja ──▶ FOR UPDATE ─▶ refresh
                                               ├─ ok ─▶ neuer access + NEUER refresh (alten ersetzen), weiter
                                               ├─ 4xx ─▶ Token-Zeile löschen ─▶ AUTHORIZATION_REQUIRED
                                               └─ 5xx/Netz ─▶ Zeile bleibt ─▶ "not reachable" (transient)
disconnect ──▶ best-effort revoke + Token-Zeile löschen ─▶ AUTHORIZATION_REQUIRED
```

Fehlerzustände (in `GET …/connection` unterscheidbar):

| Zustand | Bedingung |
|---|---|
| `NOT_CONFIGURED` | OAuth-Client oder Encryption-Key oder Site fehlt |
| `AUTHORIZATION_REQUIRED` | `authType=OAUTH2`, keine Token-Zeile |
| `CONNECTED` | Token-Zeile vorhanden, Refresh zuletzt erfolgreich |
| `SITE_UNREACHABLE` | Gateway/`accessible-resources` nicht erreichbar (transient) |

`TOKEN_EXPIRED` ist kein dauerhafter Zustand — es wird transparent gerefresht;
scheitert der Refresh dauerhaft (4xx), wird daraus `AUTHORIZATION_REQUIRED`.

## Datenmodell (Migration `0010-jira-oauth.sql`)

```sql
ALTER TABLE jira_connection_configuration
    ADD COLUMN auth_type VARCHAR(20) NOT NULL DEFAULT 'API_TOKEN';

CREATE TABLE jira_oauth_state (
    state_hash  VARCHAR(64) PRIMARY KEY,          -- SHA-256(hex) des rohen state
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE jira_oauth_token (
    id                       VARCHAR(32) PRIMARY KEY,   -- immer 'default'
    access_token_ciphertext  TEXT NOT NULL,             -- base64(iv||ct||tag), AES-256-GCM
    refresh_token_ciphertext TEXT NOT NULL,
    access_token_expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    cloud_id                 VARCHAR(64) NOT NULL,
    site_url                 VARCHAR(500) NOT NULL,
    scopes                   VARCHAR(500),
    obtained_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL
);
```

Keine Klartext-Token-Spalte. `jira_oauth_state` wird beim Einlösen gelöscht;
abgelaufene Zeilen räumt `JiraOAuthService` beim nächsten `authorize-url`-Aufruf
mit weg (kein Scheduler nötig).

## API-Vertrag

| Methode & Pfad | Scope | Zweck |
|---|---|---|
| `POST /api/v1/integrations/jira/oauth/authorize-url` | `admin` | `{ authorizationUrl }` erzeugen (state serverseitig) |
| `GET /integrations/jira/oauth/callback?code&state[&error]` | — (public, state-geschützt) | Code-Tausch, Token-Speicherung; statische HTML-Antwort |
| `POST /api/v1/integrations/jira/oauth/disconnect` | `admin` | OAuth-Credentials entfernen; keine fachlichen Daten |
| `PUT /api/v1/integrations/jira/connection` | `write` (bestehend) | zusätzlich optionales `authType` (`API_TOKEN`\|`OAUTH2`) |
| `GET /api/v1/integrations/jira/connection` | `read` (bestehend) | zusätzlich `oauthConfigured`, `oauthConnected`, `oauthSiteUrl`, `reauthorizationRequired` |

`GET .../issues/{key}` und `POST .../connection/test` bleiben unverändert im
Vertrag; ihr Verhalten hängt nur noch zusätzlich von `authType` ab.

## Migrations- und Rückfallstrategie

- **Additiv**: `auth_type DEFAULT 'API_TOKEN'` — bestehende Installationen laufen
  ohne jede Änderung unverändert im API-Token-Modus weiter. Kein Backfill nötig.
- **`API_TOKEN` bleibt Default und voll kompatibel**: der OAuth-Pfad liest die
  Token-Tabelle nur im `OAUTH2`-Modus; `TESTRYN_JIRA_API_TOKEN` wird im
  `OAUTH2`-Modus ignoriert, im `API_TOKEN`-Modus wie bisher genutzt.
- **Rückfall**: `PUT …/connection` mit `authType=API_TOKEN` schaltet sofort zurück
  (sofern ein Env-API-Token existiert). Die `jira_oauth_token`-Zeile darf liegen
  bleiben (inaktiv) oder per Disconnect entfernt werden.
- **Ohne `TESTRYN_JIRA_OAUTH_ENCRYPTION_KEY`**: Start unbeeinträchtigt; nur OAuth-Operationen
  scheitern mit klarer Meldung. Kein Krascheln beim Boot.
- **Downgrade** (Migration nicht ausführbar zurück): Spalten/Tabellen sind additiv;
  ein älterer Build ignoriert sie. `auth_type` würde ein Vor-0018-Build nicht
  kennen — Rückfall dort über `TESTRYN_JIRA_AUTH_TYPE`-Env wie zuvor.

## Manuell mit echtem Atlassian-OAuth-Client noch nötig

1. OAuth-2.0-(3LO)-App in der Atlassian Developer Console anlegen, Scopes
   `read:jira-work` + `offline_access`, Callback-URL eintragen.
2. `TESTRYN_JIRA_OAUTH_CLIENT_ID`, `TESTRYN_JIRA_OAUTH_CLIENT_SECRET`,
   `TESTRYN_JIRA_OAUTH_REDIRECT_URI`, `TESTRYN_JIRA_OAUTH_ENCRYPTION_KEY` setzen (lokal:
   `.env`/Compose-Override, gitignored).
3. In den Settings `authType` auf `OAUTH2` stellen, „Connect" auslösen, im
   Atlassian-Dialog zustimmen.
4. End-to-End-Verifikation gegen die echte Site (Verbindungstest, Issue-Lookup,
   Enrichment, Ablauf/Refresh, Disconnect).
