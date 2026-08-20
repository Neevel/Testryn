# BACKLOG.md

Strukturierter Produktbacklog. `Now` = aktueller Meilenstein, `Next` = danach
sinnvoll, `Later` = bewusst zurückgestellt/Out-of-Scope für das MVP.

## Now (CI-/Automations-Workflow, siehe PROJECT_STATUS.md)

- [x] Atomarer Bulk-Result-Update-Endpoint (`PATCH /executions/{id}/results`),
      resultId/automationReference-Dual-Mode, strukturierte Fehler, ADR 0010
- [x] `automationReference`-Feld auf Test Case: optional, projektweit eindeutig,
      maschinenfreundlich, exakte Suche, Duplikat-Schutz, ADR 0009
- [x] Execution-Mapping: automationReference wird nur innerhalb der jeweiligen
      Execution aufgelöst, nie projektweit, nie mit automatischer Anlage
- [x] `tools/testryn-publisher`: eigenständiges CLI-Tool (Java/Maven, ein
      Production-Dependency), Publisher-Core getrennt von Input-Format/HTTP-Transport
      für spätere Importer (z. B. JUnit-XML), ADR 0011
- [x] `docs/ci-integration.md`: vollständiger End-to-End-Workflow mit Beispielen
- [x] REST-API-Auth für CI geprüft, bewusst zurückgestellt statt erzwungen (siehe
      Next) — Publisher sendet bereits einen Bearer-Token, falls konfiguriert
- [x] Frontend: `automationReference` sichtbar/editierbar auf der Test-Case-Seite
      (minimal, gezielt, Abschnitt 33)
- [x] Browser-Verifikation Workflows A–E (A blockiert durch fehlende
      Live-Jira-Credentials, dokumentiert; B–E vollständig verifiziert, inkl.
      echtem Lauf des gebauten Publisher-Jars gegen die laufende Instanz)
- [ ] **Live-Jira-Verifikation (Abschnitt 3)** — nicht durchgeführt, keine
      Zugangsdaten in dieser Umgebung verfügbar; benötigte Environment Variables
      und der auszuführende Workflow sind in PROJECT_STATUS.md dokumentiert

## Next

- **API-/Service-Authentication** — jetzt der logische nächste Schritt: der
  CI-Workflow funktioniert produktionsnah, aber die API steht komplett offen im
  Netzwerk. Ein einfacher statischer Service-Token wurde geprüft und bewusst
  zurückgestellt (ADR 0011) — echte Architekturarbeit (Scope: alle Endpoints vs. nur
  schreibende, Verhältnis zu einer künftigen Nutzerverwaltung), keine Ad-hoc-Lösung.
- **JUnit-XML-Import-Adapter** — Architektur bereits vorbereitet
  (`ResultBatchReader` im Publisher, Abschnitt 17); noch nicht gebaut (Abschnitt 33).
- **Jira OAuth 2.0** — `JiraAuthType.OAUTH2` existiert bereits als Enum-Wert.
- **Jira Forge App** — Vorbereitung konkretisiert (siehe Later), noch nicht gebaut.
- **Result-Audit-History** — Statusübergangs-Historie (voriger/neuer Status,
  Zeitstempel, optional Executor/Kommentar); fachlich einfach vorbereitbar, aber
  ohne Auth-/User-Modell nur eingeschränkt wertvoll.
- **Live-Jira-Verifikation nachholen**, sobald Zugangsdaten verfügbar sind (siehe
  PROJECT_STATUS.md für die genauen Environment Variables und den Workflow).
- **CI-Pipeline-Beispiele** (GitHub Actions, GitLab CI) gegen den jetzt fertigen
  Publisher/Bulk-API-Workflow.
- **Evidence/Attachments pro Result** — `Report` müsste um eine optionale Referenz auf
  `ExecutionResult` erweitert werden (aktuell nur an `Execution` gehängt); UI bräuchte
  Upload je Test Case statt nur je Execution.
- **Build-URL / Commit-SHA an Execution Results** — sinnvolle Ergänzung zu
  `executor`, sobald ein CI-Anwendungsfall das konkret braucht (Abschnitt 19: "nicht
  in diesem Block überladen").
- **Einfache rollenbasierte Berechtigungen** (Reader/Editor je Projekt)
- **Dashboard-Analytics** — Trend über Zeit (Pass-Rate je Woche/Monat), bewusst nicht
  in diesem Block, um „komplexe BI-Dashboards" (explizit ausgeschlossen) nicht
  versehentlich zu bauen.
- **Test-Case-Review-Workflow** — z. B. DRAFT → Review angefordert → ACTIVE, statt
  direktem Statuswechsel.
- Wiederverwendbare Test-Step-Bibliothek / Shared Steps
- Weitere Export-Formate: Excel
- Kommentare/Erwähnungen an Execution Results
- `automationReference` im Test-Case-Anlage-Formular des Frontends (aktuell nur im
  Edit-Formular, siehe PROJECT_STATUS.md — bewusst minimal in diesem Block)

## Later (bewusst Out-of-Scope für dieses Produktstadium)

- **CI-Plugins** (fertige GitHub-Action / GitLab-CI-Component, die den Publisher
  kapselt) — erst sinnvoll, wenn reale Pipeline-Nutzung Muster zeigt.
- **Report-Importer für JUnit-XML, TestNG, Playwright, Cypress, Allure**
  (automatische Ergebnis-Interpretation) — Architektur vorbereitet (siehe Next:
  JUnit-XML-Adapter zuerst), aber alle Parser bewusst außerhalb dieses Blocks
  (Abschnitt 33).
- **Jira Forge App** — die REST-API liefert bereits alles Nötige
  (`GET /test-cases/{id}/requirements`, `GET /projects/{key}/requirements`,
  Execution-Status/Progress-Endpoints für PASS/FAIL/Coverage) — eine spätere
  Forge-App bräuchte keinen Architekturumbau, nur die App selbst.
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
- Per-result Evidence als eigenständige, größere Feature-Fläche (Uploads pro
  Testschritt statt pro Result) — die einfachere Variante (pro Result) steht bereits
  unter Next.
