# BACKLOG.md

Strukturierter Produktbacklog. `Now` = aktueller Meilenstein, `Next` = danach
sinnvoll, `Later` = bewusst zurückgestellt/Out-of-Scope für das MVP.

## Now (API & Service Security, siehe PROJECT_STATUS.md)

- [x] Service-Token-Authentifizierung für die gesamte REST-API (Bearer Token,
      stateless, Spring Security), ADR 0012
- [x] Scopes `testryn:read` / `testryn:write` / `testryn:admin` mit klaren
      Implikationsregeln (write ⊇ read, admin ⊇ write ⊇ read + Token-Verwaltung)
- [x] Token-Verwaltungs-API (`/api/v1/service-tokens`, Create/List/Get/Revoke),
      Rohwert nur einmalig bei Erstellung zurückgegeben
- [x] Bootstrap über `TESTRYN_BOOTSTRAP_TOKEN` (nur beim allerersten Start ohne
      bestehende Tokens, danach dauerhaft wirkungslos — kein Backdoor)
- [x] SHA-256-Hashing-Strategie für Token-Secrets (dokumentierte Entscheidung gegen
      eine Passwort-KDF, ADR 0012)
- [x] CI-Publisher: Auth vollständig verifiziert (401/403/Erfolg), Token weiterhin
      nur über `TESTRYN_API_TOKEN`, nie als CLI-Argument
- [x] Frontend: minimale, ehrlich gekennzeichnete Dev-Token-Übergangslösung
      (sessionStorage, kein Bundle/localStorage), Service-Token-Verwaltungs-UI
- [x] `docs/security.md` (neu), `docs/ci-integration.md` um Auth erweitert
- [x] Bestehende Test-Suite auf Auth umgestellt (Standard-Admin-Token je Testlauf),
      alle bisherigen Regressionstests weiterhin grün

## Next

- **Human User Authentication** (Login, Sessions) — bewusst nicht in diesem Block
  (Abschnitt 9/36); Service Tokens sind explizit Maschinen-Credentials, keine
  Personen-Identität. Das Frontend braucht bis dahin weiterhin die
  Dev-Token-Übergangslösung aus diesem Block.
- **JUnit-XML-Import-Adapter** — empfohlener nächster *fachlicher* Block
  (Abschnitt 40): Architektur bereits vorbereitet (`ResultBatchReader` im Publisher,
  ADR 0011), noch nicht gebaut.
- **API-Rate-Limiting** — noch nicht nötig, aber jetzt, wo Requests einem Token
  zugeordnet sind, technisch einfacher anzuschließen als vorher.
- **Secret Rotation** für Service Tokens (z. B. "neuen Token erzeugen, alten erst
  nach Umstellung widerrufen" als geführter Workflow statt zweier manueller
  Schritte) — aktuell manuell über Create + Revoke möglich, kein eigener Workflow.
- **Jira OAuth 2.0** — `JiraAuthType.OAUTH2` existiert bereits als Enum-Wert.
- **Jira Forge App** — Vorbereitung konkretisiert (siehe Later), noch nicht gebaut.
- **RBAC über die drei Service-Token-Scopes hinaus** — bewusst nicht in diesem Block
  (Abschnitt 36); erst bei konkretem Bedarf (z. B. projektspezifische Tokens).
- **Result-Audit-History** — Statusübergangs-Historie (voriger/neuer Status,
  Zeitstempel, optional Executor/Kommentar); mit Service Tokens jetzt zumindest ein
  Akteur (Token-Name) bekannt, aber weiterhin keine Personen-Identität dahinter.
- **CI-Pipeline-Beispiele** (GitHub Actions, GitLab CI) inkl. Token-Handling über
  Secret Stores, gegen den jetzt authentifizierten Publisher/Bulk-API-Workflow.
- **Evidence/Attachments pro Result** — `Report` müsste um eine optionale Referenz auf
  `ExecutionResult` erweitert werden (aktuell nur an `Execution` gehängt); UI bräuchte
  Upload je Test Case statt nur je Execution.
- **Build-URL / Commit-SHA an Execution Results** — sinnvolle Ergänzung zu
  `executor`, sobald ein CI-Anwendungsfall das konkret braucht.
- **Dashboard-Analytics** — Trend über Zeit (Pass-Rate je Woche/Monat), bewusst nicht
  in diesem Block, um „komplexe BI-Dashboards" (explizit ausgeschlossen) nicht
  versehentlich zu bauen.
- **Test-Case-Review-Workflow** — z. B. DRAFT → Review angefordert → ACTIVE, statt
  direktem Statuswechsel.
- Wiederverwendbare Test-Step-Bibliothek / Shared Steps
- Weitere Export-Formate: Excel
- Kommentare/Erwähnungen an Execution Results
- `automationReference` im Test-Case-Anlage-Formular des Frontends (aktuell nur im
  Edit-Formular)

## Later (bewusst Out-of-Scope für dieses Produktstadium)

- **CI-Plugins** (fertige GitHub-Action / GitLab-CI-Component, die den Publisher
  kapselt) — erst sinnvoll, wenn reale Pipeline-Nutzung Muster zeigt.
- **Report-Importer für JUnit-XML, TestNG, Playwright, Cypress, Allure**
  (automatische Ergebnis-Interpretation) — Architektur vorbereitet (siehe Next:
  JUnit-XML-Adapter zuerst), aber alle Parser bewusst außerhalb dieses Blocks.
- **Jira Forge App** — die REST-API liefert bereits alles Nötige
  (`GET /test-cases/{id}/requirements`, `GET /projects/{key}/requirements`,
  Execution-Status/Progress-Endpoints für PASS/FAIL/Coverage) — eine spätere
  Forge-App bräuchte keinen Architekturumbau, nur die App selbst (inkl. eines
  eigenen Service Tokens für ihr Backend, siehe ADR 0012).
- **SSO / SAML / LDAP** — erst relevant, sobald Human User Authentication selbst
  ansteht.
- Eigener MCP Server für Testryn
- Komplexe AI-Engine / automatische Testgenerierung im Backend
- Weitere Requirement-Provider: GitHub Issues, Azure DevOps
- Multi-Tenancy-SaaS, Billing
- Kubernetes-Betrieb
- Komplexe Dashboards / BI-Auswertungen
- Excel-Designer, PDF-Reporting-Engine
- Umfangreiche Plugin-Plattform
- Native Mobile App
- Eigene Test-Execution-Engine / Selenium-Runner
- Per-result Evidence als eigenständige, größere Feature-Fläche (Uploads pro
  Testschritt statt pro Result) — die einfachere Variante (pro Result) steht bereits
  unter Next.
