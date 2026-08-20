# BACKLOG.md

Strukturierter Produktbacklog. `Now` = aktueller Meilenstein, `Next` = danach
sinnvoll, `Later` = bewusst zurückgestellt/Out-of-Scope für das MVP.

## Now (Product Expansion Block, siehe PROJECT_STATUS.md)

- [x] Backend-Grundgerüst, Module `project`/`testcase`/`requirement`/`testplan`/
      `execution`/`report`/`integration.jira` (aus vorherigen Blöcken)
- [x] Execution View zu einem nutzbaren manuellen Test Runner ausgebaut (aus
      vorherigem Block)
- [x] Result-PATCH auf echtes partielles Update (JSON Merge Patch) umgestellt, ADR 0006
- [x] Jira-Verbindungskonfiguration (Name/Base-URL/Auth-Typ/Identity/Secret aus Env/
      Active-Flag, nie im Klartext zurückgegeben), Verbindungstest-Endpoint, ADR 0007
- [x] Requirement-Workflow: Preview → Bestätigen → Verknüpfen, Duplikat-Schutz,
      reiche Anzeige (Key/Summary/Type/Status/Open-in-Jira), Entfernen mit Bestätigung
- [x] Suchbare, paginierte Test-Case-Liste für KI-/CI-Agenten (Duplikat-Check vor
      Anlage), ADR 0008
- [x] Execution Runner: Duration/Executor im Ergebnis-Formular, Quicknav mit Status-
      Icons, vollständige Abschluss-Zusammenfassung
- [x] Test Plan View: Iterationen mit Pass-/Fail-Zahlen je Iteration
- [x] Frontend-Redesign: Design System, Sidebar-Layout, durchgängig Englisch,
      Leer-/Lade-/Fehlerzustände überall
- [x] Dashboard: Kennzahlenleiste, zuletzt fehlgeschlagene Executions, Projekt-Karten
- [x] OpenAPI-Beschreibungen für alle in Abschnitt 35 genannten Kern-Endpoints
- [x] Sicherheitsreview (Directory Traversal, Content-Disposition, Secret-Logging,
      Jira-Token-Exposure) — keine Findings, siehe PROJECT_STATUS.md
- [x] Test-Case-Status/Archivierungsmodell geprüft (DRAFT/ACTIVE/DEPRECATED, kein
      physisches Löschen) — bereits vorhanden, nicht neu gebaut
- [x] Browser-Verifikation Workflows A–D gegen `docker compose up --build`,
      1 realer UX-Bug dabei gefunden und behoben (Requirement-Link-Fallback ohne
      URL-Eingabe), siehe PROJECT_STATUS.md

## Next

- **Bulk-Result-Update-Endpoint** (`PATCH /executions/{id}/results` mit mehreren
  Ergebnissen) — analysiert (Abschnitt 36), zurückgestellt bis ein konkreter
  CI-Anwendungsfall ansteht; die bestehende Einzel-PATCH-Route deckt kleinere
  Pipelines bereits ab.
- **`automationReference`-Feld auf Test Case** — Konzept geprüft (Abschnitt 37,
  Test Case Key vs. externe Automation-ID vs. generische Referenz), zurückgestellt
  bis ein konkretes CI-Integrationsszenario den Bedarf zeigt.
- **Audit-/Result-History** — Statusübergangs-Historie (voriger/neuer Status,
  Zeitstempel, optional Executor/Kommentar); fachlich einfach vorbereitbar, aber
  ohne Auth-/User-Modell nur eingeschränkt wertvoll (Abschnitt 20).
- **Evidence/Attachments pro Result** — `Report` müsste um eine optionale Referenz auf
  `ExecutionResult` erweitert werden (aktuell nur an `Execution` gehängt); UI bräuchte
  Upload je Test Case statt nur je Execution (Abschnitt 16).
- **CI-Integrationen**: konkrete Pipeline-Beispiele (GitHub Actions, GitLab CI) gegen
  die bestehende API, sobald Bulk-Update und/oder `automationReference` stehen.
- **OAuth 2.0 für Jira** — `JiraAuthType.OAUTH2` existiert bereits als Enum-Wert;
  Implementierung zurückgestellt (Abschnitt 7/43 explizit: kein Forge-App-Bedarf in
  diesem Block).
- **Einfache rollenbasierte Berechtigungen** (Reader/Editor je Projekt)
- **Dashboard-Analytics** — Trend über Zeit (Pass-Rate je Woche/Monat), bewusst nicht
  in diesem Block, um „komplexe BI-Dashboards" (explizit ausgeschlossen) nicht
  versehentlich zu bauen.
- **Test-Case-Review-Workflow** — z. B. DRAFT → Review angefordert → ACTIVE, statt
  direktem Statuswechsel.
- Report-Importer für JUnit-XML (automatische Ergebnis-Interpretation)
- Wiederverwendbare Test-Step-Bibliothek / Shared Steps
- Weitere Export-Formate: Excel
- Kommentare/Erwähnungen an Execution Results

## Later (bewusst Out-of-Scope für dieses Produktstadium)

- **Jira Forge App** — Vorbereitung konkretisiert: die REST-API liefert bereits alles
  Nötige (`GET /test-cases/{id}/requirements` für verknüpfte Test Cases je Issue,
  `GET /projects/{key}/requirements` für die Rückrichtung, Execution-Status/
  Progress-Endpoints für PASS/FAIL/Coverage) — eine spätere Forge-App, die in einer
  Jira Story die verknüpften Test Cases mit Status/Steps/letzter Execution/Coverage
  anzeigt, bräuchte keinen Architekturumbau, nur die App selbst.
- Eigener MCP Server für Testryn
- Komplexe AI-Engine / automatische Testgenerierung im Backend
- Weitere Requirement-Provider: GitHub Issues, Azure DevOps
- LDAP / SAML / Enterprise SSO
- Multi-Tenancy-SaaS, Billing
- Kubernetes-Betrieb
- Komplexe Dashboards / BI-Auswertungen
- Excel-Designer, PDF-Reporting-Engine
- Umfangreiche Plugin-Plattform
- Native Mobile App
- Eigene Test-Execution-Engine / Selenium-Runner
- Report-Importer für TestNG, Playwright, Cypress, Allure (JUnit-XML zuerst, siehe Next)
