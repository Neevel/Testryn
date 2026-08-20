# PROJECT_STATUS.md

> Momentaufnahme des aktuellen technischen Stands. Keine Historie — siehe Git-Log für
> Verlauf. Wird bei jedem abgeschlossenen, relevanten Task aktualisiert.

Stand: 2026-08-20

## Aktueller Meilenstein

Meilenstein 1 (Produktauftrag Abschnitt 15) ist erreicht und end-to-end verifiziert
(siehe „Teststatus"). Aktueller Arbeitsblock: Persistenzhärtung (`open-in-view`
sauber beseitigt statt nur umgangen) und Ausbau der Execution-Ansicht zu einem
nutzbaren manuellen Test Runner.

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
  (`PATCH /executions/{id}/results/{resultId}`, inkl. `actualResult`),
  Status-Übergänge (CREATED → RUNNING automatisch beim ersten Ergebnis,
  COMPLETED/ABORTED manuell). Execution-Responses liefern für jeden Snapshot-Eintrag
  jetzt den vollen Inhalt der gepinnten Version (Titel, Beschreibung, Preconditions,
  alle Steps) statt nur ID/Titel/Versionsnummer — Grundlage des Test Runners.
- Reports: Upload/Download über `ReportStorage`-Abstraktion (ADR 0004),
  MVP-Implementierung `FilesystemReportStorage` (Docker-Volume-fähig), serverseitig
  generierte Storage-Keys (keine Client-Pfade), Größen-/Content-Type-Validierung.
- Export von Test Cases je Projekt als JSON, CSV, Markdown
  (`GET /projects/{key}/test-cases/export?format=...`).
- Einheitliche Fehlerbehandlung (`GlobalExceptionHandler` → `ApiError`), keine
  ungefilterten Stacktraces an Clients.
- OpenAPI/Swagger UI via springdoc (`/swagger-ui.html`).
- Liquibase-Schema: `0001-initial-schema.sql` (alle Kern-Entities) +
  `0002-execution-result-actual-result.sql` (`execution_results.actual_result`).
- `spring.jpa.open-in-view: false` (Spring-Boot-Default ist `true` — hier bewusst
  deaktiviert). Response-relevante Repository-Methoden (`TestCaseRepository`,
  `TestCaseVersionRepository`, `TestPlanRepository`, `ExecutionRepository`) laden
  ihre Assoziationen gezielt über `@EntityGraph`; `ExecutionService` initialisiert
  zusätzlich `TestCaseVersion.steps` explizit innerhalb der Transaktion, da
  `Execution.testCases` und `TestCaseVersion.steps` zwei Hibernate-"Bag"-Collections
  auf unterschiedlichen Ebenen sind und nicht gemeinsam fetch-gejoint werden können
  (`MultipleBagFetchException`). Keine Geschäftslogik dafür in Controller oder
  Persistenz verschoben — reine Lade-Strategie in Repository/Service.

**Frontend** (`frontend/`, React 18, TypeScript, Vite):

- Dashboard (Projekte, Test-Case-Zahl, letzte Executions), Projekt-Anlage.
- Project View mit Tabs: Test Cases (inkl. Anlage-Formular, Export-Links), Test Plans
  (Anlage), Executions (Liste).
- Test Case View: Anzeige + Bearbeitung (Titel/Beschreibung/Preconditions/Status/
  Priority/Tags/Steps), Requirement Links (Anzeige + Anlage), Versionshistorie.
- Test Plan View: enthaltene Test Cases (hinzufügen/entfernen), Execution starten,
  Iterationen-/Execution-Liste.
- **Execution View — manueller Test Runner:**
  - Zusammenfassung: Gesamtzahl, Anzahl je Status (NOT_RUN/PASSED/FAILED/BLOCKED/
    SKIPPED), Fortschrittsbalken in Prozent (Anteil nicht mehr NOT_RUN).
  - Je Test Case: menschenlesbare ID + Titel, verwendete Version, Beschreibung,
    Preconditions, alle Steps (Action/Expected Result) — immer aus dem zur
    Execution-Erstellung gepinnten Snapshot, nie aus dem aktuellen Test-Case-Stand.
  - Ergebnis setzen: permanent sichtbare Aktionen für PASSED/FAILED/BLOCKED/SKIPPED
    (auch auf bereits gesetzten Ergebnissen — bleiben bearbeitbar), aktuelles
    Ergebnis farblich/mit Haken hervorgehoben. Öffnet ein Formular-Modal (Status,
    Kommentar, Actual Result, Failure Details) — kein `window.prompt` mehr.
  - Abschluss/Abbruch: beide Aktionen öffnen ein Bestätigungs-Modal (kein
    `window.confirm`); beim Abschließen mit verbleibenden NOT_RUN-Tests wird deren
    Anzahl explizit genannt, bevor bestätigt werden kann.
  - Report-Upload/-Download unverändert.
- Reiner API-Client (`src/api/`), keine Geschäftslogik in der UI (API-first).

**Infrastruktur:**

- `docker-compose.yml` (PostgreSQL + Backend + Frontend), Dockerfiles für Backend
  (Maven-Multi-Stage) und Frontend (Node-Build → nginx), persistente Volumes für
  Datenbank und Report-Storage.

## Architekturstand

Modularer Monolith wie in AGENTS.md/ADRs beschrieben. Keine Abweichungen von den
getroffenen Architekturentscheidungen (ADR 0001–0005).

## Aktuelles Datenmodell

Siehe `backend/src/main/resources/db/changelog/changes/`:
`projects`, `test_cases`, `test_case_tags`, `test_case_versions`, `test_steps`,
`requirement_links`, `test_plans`, `test_plan_entries`, `executions`,
`execution_test_cases`, `execution_results` (inkl. `actual_result`), `reports`.

## Teststatus

Stand: 2026-08-20, verifiziert mit laufendem Docker Desktop (Docker 29.6.1, WSL2-
Backend) auf der Entwicklungsmaschine.

- **`cd backend && mvn test`: 12/12 Tests grün** (0 Failures, 0 Errors), Laufzeit
  ~18–21s. Unit-Tests (Mockito): `TestCaseServiceTest` (ADR 0002),
  `RequirementLinkServiceTest` (ADR 0005), `JiraRequirementProviderTest`.
  REST-Workflow-Tests gegen echtes PostgreSQL via Testcontainers
  (`CriticalWorkflowsTest`, alle 5 kritischen Workflows aus Abschnitt 12) —
  erweitert um Assertions für `description`/`preconditions`/`steps` im
  Execution-Snapshot (auch nach Test-Case-Änderung weiterhin auf der alten Version),
  `actualResult`-Round-Trip und Bearbeitbarkeit eines bereits gesetzten Ergebnisses.
- `cd frontend && npm run build` (TypeScript-Typecheck + Vite-Build): fehlerfrei.
- **`cd frontend && npm test` (neu, Vitest): 5/5 Tests grün** — Unit-Tests für die
  Zusammenfassungs-/Fortschrittsberechnung des Runners (Zählung je Status, 0 %/100 %/
  gerundete Teil-Fortschritte, leere Execution ohne Division durch 0).
- `docker compose up --build`: Backend- und Frontend-Image neu gebaut, alle drei
  Container liefen anschließend fehlerfrei (`testryn-db-1` healthy). Der Runner wurde
  manuell im Browser gegen die bestehenden Projekte `MS1`/`MS2` durchgespielt:
  Ergebnis auf FAILED gesetzt inkl. Kommentar/Actual Result/Failure Details über das
  neue Modal, Abschluss-Warnung bei verbleibendem NOT_RUN-Test korrekt angezeigt und
  über "Abbrechen" verworfen, Abbruch-Bestätigung korrekt angezeigt und verworfen;
  die alte Execution (Iteration 1, Version 1) und die neue (Iteration 2, Version 2)
  zeigten weiterhin unterschiedliche, korrekt gepinnte Inhalte.

### In diesem Arbeitsblock gefundene und behobene Fehler

1. **`LazyInitializationException`-Workaround (`open-in-view: true`) durch die
   eigentliche Lösung ersetzt:** gezielte `@EntityGraph`-Annotationen auf den
   betroffenen Repository-Methoden plus expliziter `Hibernate.initialize()` für die
   eine Assoziation (`TestCaseVersion.steps`), die aus einem
   `MultipleBagFetchException`-Grund nicht mitgejoint werden kann. `open-in-view`
   ist jetzt wieder `false` (Details/Begründung als Kommentar in
   `application.yml`).
2. **Testcontainers-Startfehler nach Testcontainers-Upgrade behoben** (bereits im
   vorigen Arbeitsblock gelöst, hier nur weiterhin grün verifiziert).
3. **MockMvc-Testcode las nicht-ASCII-Antworten falsch:**
   `MockHttpServletResponse#getContentAsString()` (ohne Argument) fällt auf
   ISO-8859-1 zurück, wenn eine Response keinen expliziten Charset trägt — was
   `application/json` laut RFC 8259 zulässigerweise nie tut (JSON ist immer UTF-8).
   Das erzeugte in neu hinzugefügten Testassertions eine Doppel-Encoding-Mojibake
   bei deutschen Umlauten. **Kein Anwendungsfehler** — reale HTTP-Clients (Browser,
   curl, Jackson-Clients) gehen korrekt von UTF-8 aus, wie zuvor bereits per curl
   verifiziert. Fix: `getContentAsString(StandardCharsets.UTF_8)` explizit in
   `CriticalWorkflowsTest`; zusätzlich `project.build.sourceEncoding=UTF-8` und
   `-Dfile.encoding=UTF-8` für die Surefire-JVM als Absicherung ergänzt.

## Bekannte Einschränkungen / technische Schulden

- Lombok wurde bewusst **nicht** verwendet: Version 1.18.36 ist mit dem hier
  installierten JDK 25 nicht kompatibel (Byte-Buddy-/javac-Interna geändert) — alle
  Entities haben daher explizite Getter/Setter statt generierter.
- Authentifizierung ist im MVP bewusst minimal (kein Enterprise-Rollenmodell).
- Report-Interpretation (JUnit/TestNG/Allure-Parsing) ist nicht implementiert — MVP
  nimmt Dateien nur entgegen und ordnet sie zu.
- Jira-Integration ist rein lesend (Anreicherung von Requirement Links); keine
  Rückschreib-Synchronisation.
- `PATCH /executions/{id}/results/{resultId}` ist fachlich ein Voll-Replace der
  veränderlichen Result-Felder, kein partielles Merge: Felder, die der Aufrufer
  weglässt (z. B. `durationMs`, `executor` — im neuen Runner-Formular nicht
  exponiert), werden auf `null` zurückgesetzt. Bestand bereits vor diesem
  Arbeitsblock und war nicht Teil des Auftrags; als Next-Item vermerkt.
- `npm install` meldet Advisories in transitiven Dev-Dependencies (u. a. durch das
  neu hinzugefügte Vitest); kein bekannter Production-Impact, nicht weiter geprüft.
- Der lokale Docker-Desktop-Socket-Ordner enthält noch ein Altverzeichnis
  (`%LOCALAPPDATA%\Docker\run_broken_*`) aus einer früheren Fehlerbehebung; kann
  gefahrlos gelöscht werden, ist aber keine Repository-Angelegenheit.

## Nächster sinnvoller Schritt

1. `PATCH .../results/{resultId}` auf echtes partielles Merge-Verhalten umstellen
   (oder Runner-Formular um Duration/Executor ergänzen), damit wiederholte
   Teil-Updates keine zuvor gesetzten Felder verlieren.
2. Testryn läuft aktuell lokal via `docker compose up --build`
   (http://localhost:3000, http://localhost:8080) — für weitere manuelle Erkundung
   nutzbar oder mit `docker compose down` beenden.
3. Backlog „Next" priorisieren (z. B. Filter/Suche für Test Cases,
   JUnit-XML-Importer).
