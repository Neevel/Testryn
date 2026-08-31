# BACKLOG.md

Strukturierter Produktbacklog. `Now` = aktueller Meilenstein, `Next` = danach
sinnvoll, `Later` = bewusst zurückgestellt/Out-of-Scope für das MVP.

## Now (Vorhandenen Testfall aus dem Jira-Panel verknüpfen — Task 1, siehe PROJECT_STATUS.md)

- [x] Forge-Resolver `searchTestCases` (paginierter `GET /projects/{key}/test-cases`,
      eine Seite je Request) und `linkExistingTestCase`
- [x] Schreibpfad = bestehender `POST /api/v1/test-cases/{id}/requirements`
      (provider-neutral, `409`-Duplikatschutz) — kein neuer Backend-Endpoint
- [x] `externalKey` aus dem Forge Invocation Context, `url` aus
      `GET /api/v1/integrations/jira/connection` — Browser liefert nur die
      Testfall-ID; Service-Token bleibt im Resolver (`testryn:write` genügt)
- [x] `TestCaseLinker.jsx` in Empty- und Ok-State; bereits verknüpfte Treffer als
      „Linked", `markLinkable` als reine, getestete Datenhilfe
- [x] ADR 0016 Erweiterungsabschnitt, `docs/jira-forge-integration.md` aktualisiert
- [x] Forge 64/64 Jest grün, `forge lint` ohne Befund
- [ ] `forge deploy`/`install` und visuelle Browser-Abnahme (zusammen mit Task 3)

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
- [x] Verlinkte Test Cases als standardmäßig geschlossene, unabhängig öffnbare
      Accordions; Steps im geöffneten Zustand als Jira-native Tabelle mit
      Definition, Result/Actual und einklappbaren Failure Details
- [x] Execution-Step-Tabelle nutzt die gepinnte Execution-Version (`position`),
      No-Execution/JUnit/Legacy dagegen die aktuelle Definition (`order`); keine
      Vermischung aktueller und historischer Steps
- [x] `docs/jira-forge-integration.md`, ADR 0014
- [x] 13 Backend- + 15 Forge-Tests grün; Backend-Erreichbarkeit real per
      `cloudflared`-Tunnel verifiziert
- [x] `forge login`/`register`/`deploy`/`install` real durchgeführt (App live in
      `ki-meets-testautomation.atlassian.net`); volle Live-Verifikation inkl.
      echter EVAL-47-Coverage-Daten im Step-Level-Execution-Results-Block
      nachgeholt
- [ ] **Weiterhin offen**: Empty-State an einem unverlinkten Issue,
      simulierte Testryn-Downtime im echten Panel, tatsächliche visuelle
      erneute Browser-Abnahme des korrigierten Accordion-/Tabellen-Panels auf
      EVAL-47 einschließlich Narrow Layout. Die erste echte Dark-Mode-Abnahme hat
      das inzwischen korrigierte Snapshot-Mappingproblem sichtbar gemacht.

## Now (Step-Level Execution Results, siehe PROJECT_STATUS.md)

- [x] `ExecutionStepResult` (referenziert `TestStep` direkt, keine
      Snapshot-Kopie-Tabelle), dieselbe `ExecutionResultStatus` wie Testcase-Ebene
- [x] Aggregationsregel `deriveStatusFromSteps()`, ausgelöst ausschließlich durch
      Step-Level-Writes; Testcase-Level-Direkt-Writes (CI/JUnit) bleiben unverändert
- [x] `PATCH .../step-results/{id}` + atomarer Bulk-`PATCH .../step-results`
      (adressiert über `stepResultId`), Migration `0006`
- [x] Completed/Aborted-Write-Guard (vorheriger Next-Punkt) für Testcase- UND
      Step-Level, einzeln und Bulk
- [x] Runner: Step-für-Step-Bewertung, Ein-Klick PASSED/SKIPPED, Detaildialog für
      FAILED/BLOCKED, „Mark remaining as passed", Step-Fortschritt in der
      Summary-Leiste
- [x] Forge-Panel: Step-Details mit Failure-First-UX, CI/Manual-Badge,
      Rückwärtskompatibilität für Alt-Executions und JUnit-only-Ergebnisse
- [x] `TESTRYN_JIRA_*`-Testisolation (`AbstractIntegrationTest`) — `mvn test`
      jetzt deterministisch unabhängig vom lokalen Environment
- [x] `docs/execution-model.md` (neu), ADR 0015
- [x] Vollständiger Pflicht-Workflow (Testcase mit 4 Steps → Execution →
      2×PASSED/1×FAILED/1×NOT_RUN → Testcase FAILED → Jira-Panel zeigt dieselben
      Daten → Testcase-Versionsupdate → alte Execution unverändert) live gegen
      den echten Stack UND die echte Jira-Site durchgespielt, nicht nur getestet
- [ ] **Weiterhin offen**: dieselbe visuelle Browser-Lücke wie beim Jira-Forge-Block
      oben — Coverage-Daten inkl. Step-Ebene sind live bestätigt, die UI-Darstellung
      selbst blieb visuell ungeprüft.

## Next

- **Human User Authentication** (Login, Sessions) — bewusst nicht in diesem Block
  (Abschnitt 9/36); Service Tokens sind explizit Maschinen-Credentials, keine
  Personen-Identität. Das Frontend braucht bis dahin weiterhin die
  Dev-Token-Übergangslösung. Mit einem echten externen Consumer der API
  (Jira-Nutzer über das Forge-Panel) jetzt zusätzlich relevanter als zuvor.
- **Framework-natives Step-Reporting** (Selenium, Playwright, eigenes Harness) —
  die Step-Result-API ist dafür vorbereitet (dieselbe `PATCH .../step-results`, die
  auch der Runner nutzt), aber kein Adapter dafür in diesem Block gebaut
  (Abschnitt 21/54).
- **Weitere Forge-Panel-Schreibaktionen** (Start Execution) — das Erstellen und
  automatische Verknüpfen neuer Testfälle, das versionssichere Bearbeiten (ADR 0016)
  und das Verknüpfen bereits vorhandener Testfälle (Task 1) sind umgesetzt; „Start
  Execution aus dem Panel" ist der nächste eigenständige Block (Task 2). Kein
  AI-„Generate Tests"-Button — separater, noch nicht begonnener Block.
- **Weitere Report-Importer** (Playwright, Cypress, Allure, NUnit, pytest) — dieselbe
  `ResultBatchReader`-Schnittstelle wie beim jetzt implementierten JUnit-XML-Adapter,
  jeweils ein kleinerer, eigenständiger Block.
- **Test-Key-Kollisionsfix**: `System.nanoTime() % 100000` als Projekt-Key-Generator
  in vielen Backend-Testklassen kollidiert selten, aber real bei sehr vielen Tests
  in einem Lauf (im Step-Level-Execution-Results-Block einmalig reproduziert) — ein
  gemeinsamer, kollisionsfreier Test-Key-Helfer (z. B. `UUID`-basiert) wäre ein
  kleiner, eigenständiger Aufräum-Block.
- **API-Rate-Limiting** — noch nicht nötig, aber jetzt, wo Requests einem Token
  zugeordnet sind, technisch einfacher anzuschließen als vorher.
- **Secret Rotation** für Service Tokens (z. B. "neuen Token erzeugen, alten erst
  nach Umstellung widerrufen" als geführter Workflow statt zweier manueller
  Schritte) — aktuell manuell über Create + Revoke möglich, kein eigener Workflow.
- **Jira OAuth 2.0** — `JiraAuthType.OAUTH2` existiert bereits als Enum-Wert.
- **Jira Server/Data Center** — die neue Laufzeitkonfiguration akzeptiert aus
  Sicherheitsgründen zunächst ausschließlich Jira Cloud (`*.atlassian.net`).
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
