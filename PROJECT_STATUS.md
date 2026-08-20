# PROJECT_STATUS.md

> Momentaufnahme des aktuellen technischen Stands. Keine Historie — siehe Git-Log für
> Verlauf. Wird bei jedem abgeschlossenen, relevanten Task aktualisiert.

Stand: 2026-08-20

## Aktueller Meilenstein

Meilenstein 1 (Produktauftrag Abschnitt 15): kompletter Kern-Workflow (Project → Test
Case → Requirement Link → Test Plan → Execution → Result → Report → Test-Case-Änderung
→ neue Execution mit stabiler Historie) über UI, REST-API und automatisierte Tests.

**Phase:** Backend- und Frontend-Implementierung für Meilenstein 1 vollständig
umgesetzt. Automatisierte REST-Workflow-Tests sind geschrieben; ihre Ausführung in
dieser Session war durch eine Umgebungseinschränkung blockiert (siehe „Bekannte
Einschränkungen" unten) — lokale Verifikation durch den Menschen empfohlen, bevor der
Meilenstein als vollständig abgenommen gilt.

## Implementierte Features

**Backend** (`backend/`, Java 21, Spring Boot 3.3, Maven):

- Module `project`, `testcase`, `requirement`, `testplan`, `execution`, `report`,
  `integration.jira`, `common` — siehe AGENTS.md für Domain-Grenzen.
- Projects: CRUD (Create/List/Get/Update) über `/api/v1/projects`.
- Test Cases: Anlage mit Steps, projektbezogene menschenlesbare ID (`KEY-TC-n`),
  Update mit automatischer Versionierung bei inhaltlicher Änderung (ADR 0002),
  Versionshistorie (`GET /test-cases/{id}/versions`).
- Requirement Links: generisches Modell + `RequirementProvider`-Abstraktion (ADR
  0005), `JiraRequirementProvider` als optionale, lesende Anreicherung (inaktiv ohne
  Konfiguration).
- Test Plans: Anlage, Test Cases hinzufügen/entfernen.
- Executions: Snapshot-Erzeugung aus einem Test Plan (pinnt Test-Case-Versionen, ADR
  0003), ad-hoc-Erzeugung ohne Plan, Ergebnis-Update pro Test Case
  (`PATCH /executions/{id}/results/{resultId}`), Status-Übergänge (CREATED → RUNNING
  automatisch beim ersten Ergebnis, COMPLETED/ABORTED manuell).
- Reports: Upload/Download über `ReportStorage`-Abstraktion (ADR 0004),
  MVP-Implementierung `FilesystemReportStorage` (Docker-Volume-fähig), serverseitig
  generierte Storage-Keys (keine Client-Pfade), Größen-/Content-Type-Validierung.
- Export von Test Cases je Projekt als JSON, CSV, Markdown
  (`GET /projects/{key}/test-cases/export?format=...`).
- Einheitliche Fehlerbehandlung (`GlobalExceptionHandler` → `ApiError`), keine
  ungefilterten Stacktraces an Clients.
- OpenAPI/Swagger UI via springdoc (`/swagger-ui.html`).
- Liquibase-Schema (`db/changelog/changes/0001-initial-schema.sql`) für alle
  Kern-Entities.

**Frontend** (`frontend/`, React 18, TypeScript, Vite):

- Dashboard (Projekte, Test-Case-Zahl, letzte Executions), Projekt-Anlage.
- Project View mit Tabs: Test Cases (inkl. Anlage-Formular, Export-Links), Test Plans
  (Anlage), Executions (Liste).
- Test Case View: Anzeige + Bearbeitung (Titel/Beschreibung/Preconditions/Status/
  Priority/Tags/Steps), Requirement Links (Anzeige + Anlage), Versionshistorie.
- Test Plan View: enthaltene Test Cases (hinzufügen/entfernen), Execution starten,
  Iterationen-/Execution-Liste.
- Execution View: enthaltene Test Cases mit verwendeter Version, Ergebnis setzen
  (PASS/FAIL/SKIPPED/BLOCKED/NOT_RUN) inkl. Kommentar, Report-Upload/-Download,
  Execution-Status setzen.
- Reiner API-Client (`src/api/`), keine Geschäftslogik in der UI (API-first).

**Infrastruktur:**

- `docker-compose.yml` (PostgreSQL + Backend + Frontend), Dockerfiles für Backend
  (Maven-Multi-Stage) und Frontend (Node-Build → nginx), persistente Volumes für
  Datenbank und Report-Storage.
- `docker compose config` erfolgreich validiert.

## Architekturstand

Modularer Monolith wie in AGENTS.md/ADRs beschrieben. Keine Abweichungen von den
getroffenen Architekturentscheidungen (ADR 0001–0005).

## Aktuelles Datenmodell

Siehe `backend/src/main/resources/db/changelog/changes/0001-initial-schema.sql`:
`projects`, `test_cases`, `test_case_tags`, `test_case_versions`, `test_steps`,
`requirement_links`, `test_plans`, `test_plan_entries`, `executions`,
`execution_test_cases`, `execution_results`, `reports`.

## Teststatus

- Domain-/Service-Unit-Tests (Mockito, kein Spring-Kontext): **grün**
  (`TestCaseServiceTest` — Versionierungsregel ADR 0002; `RequirementLinkServiceTest`
  — Best-Effort-Anreicherung ADR 0005; `JiraRequirementProviderTest`).
- REST-Workflow-Tests gegen echtes PostgreSQL via Testcontainers
  (`CriticalWorkflowsTest`, deckt die 5 kritischen Workflows aus Abschnitt 12 ab,
  inkl. des besonders wichtigen Versions-/Snapshot-Tests Workflow 4): **geschrieben,
  in dieser Session nicht ausführbar**, da der Docker-Daemon in dieser Sandbox nicht
  gestartet werden konnte (`docker info` schlägt fehl, kein laufender
  Docker-Desktop-Prozess feststellbar). `mvn compile`, `mvn test-compile` und die
  Unit-Tests laufen fehlerfrei; `mvn package` erzeugt ein lauffähiges Jar.
  **Empfehlung:** `cd backend && mvn test` einmal lokal mit laufendem Docker Desktop
  ausführen, um die Testcontainers-Tests zu verifizieren.
- Frontend: `npm run build` (TypeScript-Typecheck + Vite-Build) erfolgreich, keine
  Type-Fehler. Keine Component-/E2E-Tests im MVP (siehe BACKLOG „Next").

## Bekannte Einschränkungen / technische Schulden

- **Docker in dieser Session nicht verfügbar:** `docker compose up` und die
  Testcontainers-Workflow-Tests konnten nicht end-to-end verifiziert werden. Code und
  Konfiguration sind vollständig; Verifikation steht aus.
- Lombok wurde bewusst **nicht** verwendet: Version 1.18.36 ist mit dem hier
  installierten JDK 25 nicht kompatibel (Byte-Buddy-/javac-Interna geändert) — alle
  Entities haben daher explizite Getter/Setter statt generierter.
- Authentifizierung ist im MVP bewusst minimal (kein Enterprise-Rollenmodell).
- Report-Interpretation (JUnit/TestNG/Allure-Parsing) ist nicht implementiert — MVP
  nimmt Dateien nur entgegen und ordnet sie zu.
- Jira-Integration ist rein lesend (Anreicherung von Requirement Links); keine
  Rückschreib-Synchronisation.
- `npm install` meldet 4 (meist transitive, dev-only) Advisories; kein bekannter
  Production-Impact, aber nicht weiter geprüft.

## Nächster sinnvoller Schritt

1. Mit laufendem Docker Desktop `cd backend && mvn test` ausführen und die
   Testcontainers-Workflow-Tests verifizieren.
2. `docker compose up --build` durchführen und den Meilenstein-1-Workflow einmal
   manuell über die UI durchspielen (Abschnitt 15).
3. Danach: Backlog „Next" priorisieren (z. B. Filter/Suche für Test Cases,
   JUnit-XML-Importer).
