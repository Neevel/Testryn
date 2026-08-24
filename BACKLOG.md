# BACKLOG.md

Strukturierter Produktbacklog. `Now` = aktueller Meilenstein, `Next` = danach
sinnvoll, `Later` = bewusst zurückgestellt/Out-of-Scope für das MVP.

## Now (JUnit XML Import & CI Adapter, siehe PROJECT_STATUS.md)

- [x] `JUnitXmlResultBatchReader` — Surefire/Failsafe-XML (`<testsuite>`/
      `<testsuites>`), gegen XXE gehärtet, PASSED/FAILED/SKIPPED-Mapping,
      `BigDecimal`-Duration-Konvertierung
- [x] `JUnitReportImporter` — Multi-File/Verzeichnis-Auflösung (nicht-rekursiv),
      Cross-File-Duplikat-Erkennung
- [x] Subcommand `publish-junit` (`--base-url`, `--execution-id`, `--results`,
      `--dry-run`) neben dem unveränderten `publish` (JSON)
- [x] `automationReference`-Konvention `classname#name`, ADR 0013 (inkl. Erweiterung
      des Zeichensatzes aus ADR 0009 um `#`)
- [x] Sichere Ende-zu-Ende-Verifikation mit echtem Maven/JUnit-Projekt, echtem
      `mvn test`, echten Surefire-XML-Dateien, echtem authentifiziertem Bulk-Publish
- [x] `docs/ci-integration.md`/`tools/testryn-publisher/README.md` um JUnit-XML,
      Maven-Beispiel, Jenkins-Beispiel, GitHub-Actions-Beispiel erweitert

## Now (zuvor: API & Service Security, siehe PROJECT_STATUS.md)

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

## Now (Jira Forge Issue Panel, siehe PROJECT_STATUS.md)

- [x] Provider-neutrales Read-API `GET /api/v1/requirement-links/coverage`
      (Test Cases + Steps + letztes Execution-Ergebnis in zwei DB-Roundtrips,
      N+1-Regressionstest über Hibernate-Statistics)
- [x] Forge-App `integrations/jira-forge` — `jira:issuePanel`, UI Kit (kein
      iframe), Resolver hält den Service Token, nie der Browser
- [x] Alle fünf Panel-Zustände (Loading/Ok/Empty/Unavailable/Unauthorized), Status
      immer Text+Icon+Farbe, Coverage-Summary aus echten Daten
- [x] `docs/jira-forge-integration.md`, ADR 0014
- [x] 13 Backend- + 15 Forge-Tests grün; Backend-Erreichbarkeit real per
      `cloudflared`-Tunnel verifiziert
- [ ] **Offen**: `forge deploy`/`forge install` in die echte Jira-Site, Live-Test
      gegen EVAL-47, Empty-State an einem unverlinkten Issue, simulierte
      Testryn-Downtime im echten Panel — bewusst dem Nutzer selbst überlassen
      (Atlassian-Account-Login nötig); Schritt-für-Schritt-Anleitung fertig in
      `docs/jira-forge-integration.md`.

## Next

- **Human User Authentication** (Login, Sessions) — bewusst nicht in diesem Block
  (Abschnitt 9/36); Service Tokens sind explizit Maschinen-Credentials, keine
  Personen-Identität. Das Frontend braucht bis dahin weiterhin die
  Dev-Token-Übergangslösung aus diesem Block. Mit einem echten externen Consumer
  der API (Jira-Nutzer über das Forge-Panel) jetzt zusätzlich relevanter als zuvor.
- **Forge-Panel-Schreibaktionen** (Create Test Case, Link Existing, Start
  Execution) — Architektur bewusst nicht verbaut (eigener `testryn:write`-Token,
  kein stiller Scope-Ausbau des bestehenden Read-Tokens), aber explizit nicht in
  diesem Block gebaut (Abschnitt 31/47). Kein AI-„Generate Tests"-Button (Abschnitt
  32/47) — separater, noch nicht begonnener Block.
- **Weitere Report-Importer** (Playwright, Cypress, Allure, NUnit, pytest) — dieselbe
  `ResultBatchReader`-Schnittstelle wie beim jetzt implementierten JUnit-XML-Adapter,
  jeweils ein kleinerer, eigenständiger Block.
- **Execution-State-Guard**: Bulk-/Einzel-Result-Endpoint lehnen aktuell keine
  Schreibversuche auf eine `COMPLETED`/`ABORTED`-Execution ab — beim JUnit-XML-Block
  entdeckt und bewusst nicht dort mitgelöst (keine Domain-Regel client-seitig
  duplizieren, die serverseitig noch gar nicht existiert).
- **API-Rate-Limiting** — noch nicht nötig, aber jetzt, wo Requests einem Token
  zugeordnet sind, technisch einfacher anzuschließen als vorher.
- **Secret Rotation** für Service Tokens (z. B. "neuen Token erzeugen, alten erst
  nach Umstellung widerrufen" als geführter Workflow statt zweier manueller
  Schritte) — aktuell manuell über Create + Revoke möglich, kein eigener Workflow.
- **Jira OAuth 2.0** — `JiraAuthType.OAUTH2` existiert bereits als Enum-Wert.
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
- **Report-Importer für TestNG, Playwright, Cypress, Allure, NUnit, pytest**
  (automatische Ergebnis-Interpretation) — JUnit-XML ist implementiert (siehe „Now"),
  Architektur (`ResultBatchReader`) für die übrigen Formate vorbereitet, siehe Next.
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
