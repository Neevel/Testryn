# PROJECT_STATUS.md

> Momentaufnahme des aktuellen technischen Stands. Keine Historie — siehe Git-Log für
> Verlauf. Wird bei jedem abgeschlossenen, relevanten Task aktualisiert.

Stand: 2026-08-20

## Aktueller Meilenstein

Meilenstein 1 (Produktauftrag Abschnitt 15): kompletter Kern-Workflow (Project → Test
Case → Requirement Link → Test Plan → Execution → Result → Report → Test-Case-Änderung
→ neue Execution mit stabiler Historie) über UI, REST-API und automatisierte Tests.

**Phase:** Meilenstein 1 vollständig implementiert und end-to-end verifiziert —
automatisierte Testsuite grün (inkl. Testcontainers), Stack lokal per
`docker compose up --build` gestartet, kompletter Workflow einmal über die REST-API
und einmal über die UI durchgespielt, inklusive des kritischen Versions-/
Snapshot-Verhaltens. Details siehe „Teststatus" unten.

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

Stand: 2026-08-20, verifiziert mit laufendem Docker Desktop (Docker 29.6.1, WSL2-
Backend) auf der Entwicklungsmaschine.

- **`cd backend && mvn test`: 12/12 Tests grün** (0 Failures, 0 Errors), Laufzeit
  ~21s. Domain-/Service-Unit-Tests (Mockito): `TestCaseServiceTest` (Versionierungsregel
  ADR 0002), `RequirementLinkServiceTest` (Best-Effort-Anreicherung ADR 0005),
  `JiraRequirementProviderTest`. REST-Workflow-Tests gegen echtes PostgreSQL via
  Testcontainers (`CriticalWorkflowsTest`, alle 5 kritischen Workflows aus Abschnitt
  12, inkl. des besonders wichtigen Versions-/Snapshot-Tests Workflow 4): **grün**.
- `docker compose up --build`: **erfolgreich**. Alle drei Container laufen
  (`testryn-db-1` healthy, `testryn-backend-1`, `testryn-frontend-1`). Verifiziert:
  PostgreSQL (`pg_isready` OK), Backend (`GET /api/v1/projects` → 200,
  `GET /v3/api-docs` → 200), Frontend (`GET /` → 200).
- **Meilenstein-1-Workflow über die REST-API** (Projekt `MS1`) einmal vollständig
  durchgespielt: Project anlegen → Test Case mit Steps anlegen (`MS1-TC-1`) →
  Requirement Link (Jira `BIT-27`) → Test Plan → Test Case zum Plan hinzufügen →
  Execution #1 erzeugen (Snapshot v1) → Result `PASSED` setzen (Execution wechselt
  automatisch CREATED → RUNNING) → Report hochladen und **byte-genau** wieder
  heruntergeladen (SHA-256-Prüfsumme vor/nach Download identisch) → Test Case
  bearbeiten (neue Version v2 mit geändertem Titel/zusätzlichem Step) → Execution #2
  erzeugen (Snapshot v2) → **Execution #1 erneut abgerufen: unverändert weiterhin
  Version 1** (`testCaseVersionNumber: 1`, Titel `"Erfolgreiche Anmeldung"`, Result
  `PASSED` unverändert) — Kernanforderung aus ADR 0002/0003 bestätigt.
- **Derselbe Workflow über die UI** (Projekt `MS2`, http://localhost:3000) einmal
  vollständig durchgespielt: Projekt anlegen → Test Case anlegen (`MS2-TC-1`) → Test
  Plan anlegen → Test Case hinzufügen → Execution starten → Result `PASSED` setzen →
  Test Case bearbeiten (neue Version v2) → zweite Execution starten (Snapshot v2) →
  erste Execution erneut geöffnet: zeigt weiterhin Version 1 / Originaltitel. Alle
  Schritte über Klicks/Formulare, keine direkten API-Aufrufe.
- Frontend: `npm run build` (TypeScript-Typecheck + Vite-Build) weiterhin
  fehlerfrei. Keine Component-/E2E-Tests im MVP (siehe BACKLOG „Next").

### In dieser Verifikationsrunde gefundene und behobene Fehler

1. **Testcontainers fand keine gültige Docker-Umgebung** (`docker info` über die
   CLI funktionierte, Testcontainers 1.20.1 erhielt aber HTTP 400 vom Docker-
   Desktop-29.6.1-Pipe-Proxy). Fix: `testcontainers.version` auf `1.21.4` angehoben
   ([backend/pom.xml](backend/pom.xml)).
2. **`org.hibernate.LazyInitializationException` (HTTP 500)** beim Hinzufügen eines
   Test Case zu einem Test Plan und generell bei jedem Endpoint, das eine zuvor
   geladene Entity mit noch nicht berührten lazy Assoziationen in eine Response-DTO
   abbildet — reproduziert über `POST /test-plans/{id}/test-cases`, betraf u. a.
   auch `GET /test-plans/{id}`. Ursache: `spring.jpa.open-in-view: false` in
   Kombination mit DTO-Mapping in der Web-Schicht *nach* Abschluss der
   `@Transactional`-Service-Methode. Fix: `open-in-view: true`
   ([application.yml](backend/src/main/resources/application.yml)) — Begründung im
   Kommentar an Ort und Stelle.
3. **Docker Desktop startete nicht** (`starting services: initializing Inference
   manager: ... The filename, directory name, or volume label syntax is
   incorrect.`) — korrupte/unlesbare Unix-Socket-Reparse-Points in
   `%LOCALAPPDATA%\Docker\run`. Fix (Maschine, nicht Repo): Verzeichnis umbenannt,
   Docker Desktop legt es beim Neustart sauber neu an.
4. **UI: `window.prompt()` beim Setzen eines Execution-Ergebnisses wirft in
   automatisierten Browser-/Webview-Kontexten** statt `null` zurückzugeben und
   bricht dadurch das gesamte Ergebnis-Update ab, bevor der PATCH-Request gesendet
   wird. Fix: `window.prompt` in try/catch gekapselt, Fehlschlag wird wie ein
   abgebrochener Prompt behandelt (kein Kommentar)
   ([ExecutionPage.tsx](frontend/src/pages/ExecutionPage.tsx)).
5. Ein zunächst beobachteter HTTP 500 beim Anlegen eines Test Cases mit Umlauten
   war **kein Anwendungsfehler**, sondern ein UTF-8-Encoding-Artefakt der
   verwendeten Bash/curl-Kombination beim Zusammenbauen des JSON-Bodys inline;
   mit einer UTF-8-Datei als Payload funktionierte derselbe Request einwandfrei.
   Keine Code-Änderung nötig.

## Bekannte Einschränkungen / technische Schulden

- `open-in-view: true` (siehe Fix oben) hält die Hibernate-Session für die Dauer des
  gesamten Requests offen. Das behebt die LazyInitializationException zuverlässig,
  kann aber N+1-Queries in der Web-Schicht verschleiern. Sauberer wäre mittelfristig,
  Repository-Methoden für Response-relevante Lesepfade mit gezieltem
  `LEFT JOIN FETCH`/`@EntityGraph` auszustatten und `open-in-view` wieder zu
  deaktivieren — bewusst zurückgestellt, um in dieser Runde nicht über die
  angeforderte Fehlerbehebung hinaus zu refactoren (siehe BACKLOG „Next").
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
- Der lokale Docker-Desktop-Socket-Ordner enthält noch ein Altverzeichnis
  (`%LOCALAPPDATA%\Docker\run_broken_*`) aus der Fehlerbehebung; kann gefahrlos
  gelöscht werden, ist aber keine Repository-Angelegenheit.

## Nächster sinnvoller Schritt

1. Testryn läuft aktuell lokal via `docker compose up --build`
   (http://localhost:3000, http://localhost:8080) — für weitere manuelle Erkundung
   nutzbar oder mit `docker compose down` beenden.
2. Repository-Lesepfade auf gezielte Fetch-Joins umstellen und `open-in-view` wieder
   auf `false` setzen (siehe „Bekannte Einschränkungen").
3. Backlog „Next" priorisieren (z. B. Filter/Suche für Test Cases,
   JUnit-XML-Importer).
