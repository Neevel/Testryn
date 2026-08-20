# BACKLOG.md

Strukturierter Produktbacklog. `Now` = aktueller Meilenstein, `Next` = danach
sinnvoll, `Later` = bewusst zurückgestellt/Out-of-Scope für das MVP.

## Now (Meilenstein 1 — MVP-Kern-Workflow)

- [x] Backend-Grundgerüst (Spring Boot, Maven, Liquibase, PostgreSQL, Docker Compose)
- [x] Modul `project`: CRUD + API
- [x] Modul `testcase`: Test Case, Versionierung, Steps + API
- [x] Modul `requirement`: RequirementLink + `RequirementProvider`-Interface + API
- [x] Modul `integration.jira`: `JiraRequirementProvider` (lesender Abruf)
- [x] Modul `testplan`: Test Plan + Zuordnung Test Cases + API
- [x] Modul `execution`: Execution-Snapshot, ExecutionResult + API
- [x] Modul `report`: Upload/Download, `ReportStorage`-Abstraktion (Filesystem-Impl)
- [x] Export: JSON, CSV, Markdown (Test Cases inkl. Steps)
- [x] OpenAPI-Dokumentation (springdoc)
- [x] Frontend: Dashboard, Project View, Test Case View, Test Plan View, Execution View
- [x] Workflow-Tests 1–5 (siehe Produktauftrag Abschnitt 12) automatisiert, gegen
      echtes PostgreSQL via Testcontainers grün (`mvn test`, 12/12), siehe
      PROJECT_STATUS.md
- [x] `README.md` Quick Start mit `docker compose up --build` verifiziert; Milestone-
      1-Workflow einmal über REST-API und einmal über die UI durchgespielt, inkl.
      Versions-/Snapshot-Stabilität und byte-genauem Report-Roundtrip

## Next

- Repository-Lesepfade (`TestPlan`, `TestCase`, `Execution`) auf gezielte
  `LEFT JOIN FETCH`/`@EntityGraph`-Queries umstellen, danach `spring.jpa.open-in-view`
  wieder auf `false` setzen (aktuell `true` als Sofortfix gegen
  LazyInitializationException, siehe PROJECT_STATUS.md)
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
