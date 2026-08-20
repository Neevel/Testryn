# BACKLOG.md

Strukturierter Produktbacklog. `Now` = aktueller Meilenstein, `Next` = danach
sinnvoll, `Later` = bewusst zurückgestellt/Out-of-Scope für das MVP.

## Now (Meilenstein 1 — MVP-Kern-Workflow)

- [ ] Backend-Grundgerüst (Spring Boot, Maven, Liquibase, PostgreSQL, Docker Compose)
- [ ] Modul `project`: CRUD + API
- [ ] Modul `testcase`: Test Case, Versionierung, Steps + API
- [ ] Modul `requirement`: RequirementLink + `RequirementProvider`-Interface + API
- [ ] Modul `integration.jira`: `JiraRequirementProvider` (lesender Abruf)
- [ ] Modul `testplan`: Test Plan + Zuordnung Test Cases + API
- [ ] Modul `execution`: Execution-Snapshot, ExecutionResult + API
- [ ] Modul `report`: Upload/Download, `ReportStorage`-Abstraktion (Filesystem-Impl)
- [ ] Export: JSON, CSV, Markdown (Test Cases inkl. Steps)
- [ ] OpenAPI-Dokumentation (springdoc)
- [ ] Frontend: Dashboard, Project View, Test Case View, Test Plan View, Execution View
- [ ] Workflow-Tests 1–5 (siehe Produktauftrag Abschnitt 12) automatisiert
- [ ] `README.md` Quick Start funktioniert nachweislich lokal mit wenigen Befehlen

## Next

- Volltextsuche/Filter für Test Cases (Status, Priority, Tags)
- Bulk-Requirement-Link-Abgleich ("existieren bereits ähnliche Test Cases zu Story X?")
- Report-Importer für JUnit-XML (automatische Ergebnis-Interpretation)
- Wiederverwendbare Test-Step-Bibliothek / Shared Steps
- Einfache rollenbasierte Berechtigungen (Reader/Editor je Projekt)
- Aktivitäts-/Audit-Historie pro Test Case (wer hat wann was geändert)
- Weitere Export-Formate: Excel
- Kommentare/Erwähnungen an Execution Results

## Later (bewusst Out-of-Scope für MVP)

- Atlassian Marketplace App / Forge-Integration
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
