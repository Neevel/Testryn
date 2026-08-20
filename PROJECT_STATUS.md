# PROJECT_STATUS.md

> Momentaufnahme des aktuellen technischen Stands. Keine Historie — siehe Git-Log für
> Verlauf. Wird bei jedem abgeschlossenen, relevanten Task aktualisiert.

Stand: 2026-08-20

## Aktueller Meilenstein

Der Produktauftrag „Testryn – Product Expansion Block" (49 Abschnitte) ist gemäß der
darin vorgegebenen Prioritätenreihenfolge (Abschnitt 48) umgesetzt: Result-PATCH-Fix,
Jira-Integration, Requirement-Workflow, API-Härtung (Suche/Pagination/OpenAPI),
Execution-Runner-UX, Test-Plan-UX, Frontend-Redesign und Dashboard sind fertig,
end-to-end im Browser gegen die echte Docker-Stack verifiziert und committet.
„Kleinere Komfortfeatures" (niedrigste Priorität, Abschnitt 48 Punkt 9) wurde in diesem
Block bewusst nicht angefasst — kein Korrektheits-/Datenintegritätsrisiko, keine
offene Anforderung, die etwas anderes blockiert.

## Implementierte Features

**Backend** (`backend/`, Java 21, Spring Boot 3.3, Maven):

- Module `project`, `testcase`, `requirement`, `testplan`, `execution`, `report`,
  `integration.jira`, `common` — siehe AGENTS.md für Domain-Grenzen.
- **Execution Result PATCH ist jetzt ein echtes partielles Update** (JSON Merge Patch,
  RFC 7396, via `ObjectMapper#readerForUpdating`): weggelassene Felder bleiben
  unverändert, explizites `null` löscht ein Feld gezielt. Vorher (Bug, siehe ADR 0006):
  jedes PATCH war fachlich ein Voll-Replace und hat `durationMs`/`executor`/
  `actualResult`/`failureDetails` stillschweigend genullt, wenn sie im Request-Body
  fehlten — z. B. bei jeder reinen Kommentar-Änderung über die UI. Regressionstests in
  `ExecutionResultPatchTest`.
- **Jira-Integration** (ADR 0007): Verbindungskonfiguration (Name, Base-URL, Auth-Typ
  `API_TOKEN`/`OAUTH2`-vorbereitet, Identity, Secret nur aus Environment, Active-Flag),
  `GET /integrations/jira/connection` liefert **niemals** das Token, `POST
  /integrations/jira/connection/test` prüft Erreichbarkeit live. `JiraIssueClient`
  kapselt HTTP-Zugriff (Basic Auth Email+API-Token), extrahiert Klartext aus Atlassian
  Document Format, wirft `NotFoundException`/`UpstreamServiceException` (→ 404/502)
  statt 500. Token wird nirgends geloggt.
- **Requirement-Workflow**: `RequirementLink` um `issueType`/`status`/`description`
  erweitert; `RequirementLinkService.create()` versucht bei jedem Link eine
  Best-Effort-Anreicherung über den konfigurierten `RequirementProvider` (unabhängig
  davon, ob `url`/`summary` schon vorliegen), schlägt aber nie fehl, wenn Jira
  nicht erreichbar ist — nur `url` ist zwingend (explizit gesetzt oder aus Jira
  aufgelöst; siehe „In diesem Arbeitsblock gefundene und behobene Fehler"
  Punkt 1 zur Frontend-Seite davon). Duplikate (gleicher Provider + externalKey am
  selben Test Case) werden mit 409 abgelehnt. `DELETE .../requirements/{linkId}`
  entfernt nur den Testryn-Link, nie das Jira-Issue. Neuer Endpoint
  `GET /projects/{projectKey}/requirements` (projektweite Aggregatsicht).
- **Suchbare, paginierte Test-Case-Liste** (ADR 0008):
  `GET /projects/{key}/test-cases?query=&tag=&requirementKey=&status=&priority=&page=&size=`,
  Response als `PageResponse<T>` (Breaking Change ggü. der vorherigen bloßen Liste,
  dokumentiert in der ADR). Ermöglicht einem KI-Agenten, vor dem Anlegen eines Test
  Case auf Near-Duplikate zu prüfen.
- Test Cases: Anlage mit Steps, projektbezogene menschenlesbare ID (`KEY-TC-n`),
  Update mit automatischer Versionierung bei inhaltlicher Änderung (ADR 0002),
  Versionshistorie. **Kein physisches Löschen** von Test Cases — Statuswechsel
  DRAFT/ACTIVE/DEPRECATED über `updateMetadata`, kein `DELETE`-Endpoint. Damit ist
  Abschnitt 12 der Anforderung ohne Codeänderung bereits erfüllt; das Modell war
  bereits aus einem früheren Arbeitsblock vorhanden und wurde in diesem Block nur
  geprüft, nicht neu gebaut.
- Test Plans: Anlage, Test Cases hinzufügen/entfernen.
- Executions: Snapshot-Erzeugung aus einem Test Plan (pinnt Test-Case-Versionen, ADR
  0003), ad-hoc-Erzeugung ohne Plan, Status-Übergänge (CREATED → RUNNING automatisch
  beim ersten Ergebnis, COMPLETED/ABORTED manuell).
- Reports: Upload/Download über `ReportStorage`-Abstraktion (ADR 0004),
  `FilesystemReportStorage` (Docker-Volume-fähig), serverseitig generierte
  Storage-Keys (keine Client-Pfade, Directory-Traversal ausgeschlossen — siehe
  Sicherheitsreview unten), Größen-Limit 50 MB, sichere `Content-Disposition`
  (RFC-5987-kodierter Dateiname).
- Export von Test Cases je Projekt als JSON, CSV, Markdown.
- Einheitliche Fehlerbehandlung (`GlobalExceptionHandler` → `ApiError`), inkl. neuem
  `UpstreamServiceException` (→ 502) für nicht erreichbare externe Systeme (Jira).
- OpenAPI/Swagger UI via springdoc (`/swagger-ui.html`); `@Operation`-Beschreibungen
  für alle in Abschnitt 35 explizit genannten Endpoints (Test Case anlegen,
  Requirement verknüpfen, Plan anlegen, Execution starten, Result PATCH, Report
  hochladen) sowie die neue Such-Query.
- Liquibase-Schema: `0001-initial-schema.sql`, `0002-execution-result-actual-result.sql`,
  `0003-requirement-link-metadata.sql` (issue_type/status/description).
- `spring.jpa.open-in-view: false`, gezielte `@EntityGraph`-Queries +
  `Hibernate.initialize()` für Bag-Collections, die nicht gemeinsam fetch-gejoint
  werden können (`MultipleBagFetchException`) — unverändert aus dem letzten Block.

**Frontend** (`frontend/`, React 18, TypeScript, Vite):

- **Design System**: CSS-Custom-Property-Tokens (Farbe/Spacing/Radius/Shadow),
  konsistente Komponenten für Buttons/Tabellen/Badges/Modals/Formulare/Tabs/
  Leer-/Lade-/Fehlerzustände. Kein externes UI-Framework — bewusst leichtgewichtig
  gehalten, da der bestehende Umfang das rechtfertigt.
- **App-Shell**: linke Sidebar-Navigation (Dashboard/Executions/Settings) + Content-
  Bereich statt der vorherigen Top-Nav.
- **Sprache vereinheitlicht auf Englisch** (vorher gemischt Deutsch/Englisch in
  UI-Strings, z. B. „Als abgeschlossen markieren"/„RUNNING").
- **Dashboard**: Kennzahlenleiste (Projects/Active Test Cases/Running
  Executions/Passed/Failed/Blocked), Tabelle „Recently failed executions",
  Projekt-Karten mit aktiver Test-Case-Zahl und letzten Executions.
- **Neue globale Executions-Seite** (`/executions`): projektübergreifende Liste.
- **Neue Settings-Seite** (`/settings`): Jira-Verbindungsstatus-Karte inkl. Live-
  „Test connection"-Aktion; das Token wird nie im Frontend gehalten oder angezeigt.
- **Project View**: 5 Tabs (Overview/Test Cases/Test Plans/Executions/Requirements).
  Test-Cases-Tab mit Such-/Status-/Priority-Filter, Pagination, Export-Links.
  Neuer Requirements-Tab (projektweite Aggregatsicht).
- **Test Case View**: Requirement-Links als Karten (Key, Summary, Type · Status,
  „Open in Jira", „Remove" mit Bestätigung); Link-Workflow mit Jira-Preview
  (Key eingeben → Preview → Bestätigen) und manuellem Fallback (URL selbst eingeben),
  wenn Jira nicht erreichbar ist.
- **Test Plan View**: Kennzahlen (Test-Case-Zahl, Iterationen, letzter Iterations-
  Status), Iterationen-Tabelle mit Pass-/Fail-Zahlen je Iteration.
- **Execution View (Runner)**: volle Fortschrittsanzeige (Total/Passed/Failed/
  Blocked/Skipped/NotRun/Prozent, Created/Started/Finished/Duration), Quicknav mit
  Status-Icons zum Springen zwischen Test Cases, Ergebnis-Modal mit
  Status/Comment/Actual Result/Failure Details/**Duration/Executor** (neu), Abschluss-
  Modal mit vollständiger Zahlen-Übersicht (X Tests / N Passed / N Failed / N Blocked /
  N Skipped / N Not Run) zusätzlich zur bestehenden NOT_RUN-Warnung.
- Reiner API-Client (`src/api/`), keine Geschäftslogik in der UI (API-first).

**Infrastruktur:** unverändert — `docker-compose.yml` (PostgreSQL + Backend +
Frontend), Dockerfiles, persistente Volumes.

## Architekturstand

Modularer Monolith wie in AGENTS.md/ADRs beschrieben. Neue ADRs in diesem Block:
0006 (Result-PATCH-Semantik), 0007 (Jira-Verbindungskonfiguration),
0008 (Test-Case-Suche/Pagination). Keine sonstigen Abweichungen von den bestehenden
Architekturentscheidungen.

## Analysierte, aber zurückgestellte Erweiterungen (Abschnitte 16, 20, 36, 37)

Bewusste Entscheidung gegen Implementierung in diesem Block — Details und Nachfolge-
Items siehe BACKLOG.md → Next:

- **Evidence/Attachments pro Result** (Abschnitt 16): `Report` hängt aktuell an
  `Execution`, nicht an `ExecutionResult`. Eine saubere Umsetzung bräuchte eine
  Fremdschlüssel-Erweiterung plus UI-Änderungen je Test Case — kein trivialer Anbau,
  daher zurückgestellt statt erzwungen.
- **Audit/Result-History** (Abschnitt 20): Es existiert kein Auth-/User-Modell, an das
  ein „wer hat geändert" sinnvoll anknüpfen könnte; `executor` (freies Textfeld) und
  `comment` decken den MVP-Bedarf für Rückverfolgbarkeit ab. Eine echte
  Statusübergangs-Historie (voriger Status, neuer Status, Zeitstempel, optional
  Executor/Kommentar) ist fachlich einfach vorbereitbar, aber als eigenes Feature
  zurückgestellt statt einer „leichten" Variante, die später doch neu gebaut werden
  müsste.
- **Bulk-Result-Update** (Abschnitt 36): Die bestehende Einzel-PATCH-Route reicht für
  CI-Pipelines mit überschaubarer Testanzahl aus. Ein Bulk-Endpoint
  (`PATCH /executions/{id}/results` mit mehreren Ergebnissen) ist ein klar
  umrissenes, unabhängiges Feature — als Next-Item vorgemerkt statt spontan
  mitgezogen.
- **`automationReference`-Feld** (Abschnitt 37): Konzept geprüft (Test Case Key vs.
  externe Automation-ID vs. generische Automation-Reference). Kein Framework-Kopplung
  gewünscht laut Auftrag; ein optionales Freitextfeld auf `TestCase` wäre die
  richtige Form, sobald ein konkreter CI-Anwendungsfall ansteht. Ohne einen solchen
  Anwendungsfall würde das Feld nur ungenutzt im Schema stehen — daher zurückgestellt.

## Sicherheitsreview (Abschnitt 39)

Durchgeführt als gezielte Prüfung, keine Neuarchitektur:

- **Directory Traversal**: ausgeschlossen — `FilesystemReportStorage` verwendet
  ausschließlich serverseitig generierte Storage-Keys (`projectKey/UUID.ext`),
  `resolveWithinBase()` normalisiert und prüft `startsWith(basePath)` vor jedem
  Dateizugriff.
- **Content-Disposition**: RFC-5987-konform URL-kodiert (`filename*=UTF-8''...`),
  kein Header-Injection-Vektor über den Dateinamen.
- **Dateinamen**: nur die Extension (max. 10 alphanumerische Zeichen) wird aus dem
  Client-Dateinamen übernommen, alles andere wird verworfen.
- **Größenlimits**: `multipart.max-file-size`/`max-request-size` = 50 MB.
- **Content-Type**: kein Whitelist-Zwang (reine Anzeige-/Download-Metadatum, keine
  serverseitige Interpretation), Fallback `application/octet-stream` bei fehlendem
  Wert.
- **Secret-Logging**: Jira-API-Token wird an keiner Stelle geloggt (siehe Javadoc auf
  `JiraIssueClient`); alle Log-Statements bei Jira-Fehlern loggen nur Issue-Key und
  Fehlermeldung, nie Header/Credentials.
- **Jira-Token-Exposure**: `GET /integrations/jira/connection` liefert nie das Token,
  nur einen Konfiguriert-Ja/Nein-Status; das Token wird nie an das Frontend
  übertragen oder dort gehalten.
- **API-Validierung**: Bean-Validation auf Request-DTOs, `GlobalExceptionHandler`
  liefert strukturierte 400er ohne Stacktrace-Leak.

Keine konkreten Findings, die einen Fix erfordert hätten.

## UI-Review (Abschnitt 42, Selbstkritik nach Browser-Verifikation)

- Der Kern-Workflow (Projekt → Test Case → Steps → Jira-Requirement → Test Plan →
  Execution → PASSED → Abschluss) ist ohne Erklärung nachvollziehbar: jede Seite hat
  einen klaren primären Call-to-Action, leere Zustände erklären den nächsten Schritt.
  Status und Fortschritt sind auf jeder relevanten Seite sofort sichtbar (Badges,
  Fortschrittsbalken, Zahlen).
- **Ein echter UX-Bruch wurde gefunden während der Verifikation und noch im selben
  Durchlauf behoben** (siehe unten) — der „Jira nicht erreichbar"-Fallback beim
  Requirement-Verknüpfen bot keinen Weg, tatsächlich zu verknüpfen, weil das Backend
  zurecht eine URL verlangt und das Frontend keine abgefragt hat. Jetzt: manuelles
  URL-Feld im Fallback-Pfad.
- Keine weiteren unnötigen Klickpfade oder verwirrenden Leerzustände in den
  verifizierten Bereichen (Dashboard, Executions, Settings, Project/5 Tabs, Test
  Case Detail, Test Plan, Execution Runner) festgestellt.

## Aktuelles Datenmodell

Siehe `backend/src/main/resources/db/changelog/changes/`:
`projects`, `test_cases`, `test_case_tags`, `test_case_versions`, `test_steps`,
`requirement_links` (+ `issue_type`/`status`/`description`), `test_plans`,
`test_plan_entries`, `executions`, `execution_test_cases`, `execution_results`
(inkl. `actual_result`), `reports`.

## Teststatus

Stand: 2026-08-20, verifiziert lokal (Windows, Docker Desktop 29.6.1) sowie via
Docker-Compose-Stack.

- **`cd backend && mvn test`: 41/41 Tests grün** (0 Failures, 0 Errors). Neu in diesem
  Block: `ExecutionResultPatchTest` (7, PATCH-Partial-Update inkl. Regressionstest für
  den behobenen Bug), `JiraIssueClientTest`/`JiraIssueClientHttpTest` (nicht
  konfiguriert, nicht erreichbar, 404, ADF-Parsing — via `MockRestServiceServer`, kein
  echter Socket), `RequirementWorkflowTest` (6, Duplikat-Erkennung inkl.
  Case-Insensitivität, Link-Entfernung, Jira-Status-Endpoints ohne Konfiguration),
  `TestCaseSearchTest` (8, alle Filterkombinationen + Pagination).
- `cd frontend && npm run build`: fehlerfrei (TypeScript-Typecheck + Vite-Build).
- **Browser-Verifikation (Abschnitt 41) gegen `docker compose up --build`, alle vier
  Workflows durchgespielt:**
  - **Workflow A** (Projekt → Test Case → Steps → Jira-Requirement → Test Plan →
    Execution → PASSED → Abschluss): vollständig durchlaufen, inkl. Requirement-Link
    über den manuellen Fallback (Jira in dieser Umgebung nicht konfiguriert).
  - **Workflow B** (Test Case v1 → Execution → Test Case ändern → v2/v3 → alte
    Execution zeigt weiterhin die ursprüngliche Version): verifiziert — Test Case auf
    v3 gebracht, abgeschlossene Execution zeigt weiterhin unverändert „v2".
  - **Workflow C** (Result hat Duration+Executor → UI ändert nur Comment → Duration+
    Executor bleiben erhalten): verifiziert end-to-end über echte UI → echte API →
    echte DB — bestätigt den PATCH-Fix reproduzierbar, nicht nur in Unit-Tests.
  - **Workflow D** (Jira-Key eingeben → Issue-Preview → Verknüpfen → Requirement
    sichtbar am Test Case): verifiziert über den Fallback-Pfad (Jira unkonfiguriert in
    dieser Umgebung) — Preview-Pfad selbst ist durch `JiraIssueClientHttpTest`
    abgedeckt.

### In diesem Arbeitsblock gefundene und behobene Fehler

1. **Result-PATCH war fachlich ein Voll-Replace** (Kernauftrag dieses Blocks, siehe
   ADR 0006) — behoben durch JSON Merge Patch.
2. **Requirement-Link-Fallback ohne Jira-Erreichbarkeit war ein Dead End**: das
   Frontend rief beim „Kann Jira nicht erreichen"-Pfad `create()` nur mit
   `externalKey` auf; das Backend verlangt zurecht eine `url` (ein Requirement-Link
   ohne Link ist für die „Open in Jira"-Anzeige witzlos) und lehnte mit 400 ab, ohne
   dass die UI einen Weg zum Fortfahren bot. Gefunden während der Browser-
   Verifikation (Abschnitt 41), noch im selben Durchlauf behoben: der Fallback-Pfad
   fragt jetzt die Issue-URL (Pflichtfeld) und optional eine Summary manuell ab.

## Bekannte Einschränkungen / technische Schulden

- Lombok bewusst nicht verwendet (JDK-Kompatibilität, siehe frühere Einträge).
- Authentifizierung weiterhin bewusst minimal im MVP (kein Enterprise-Rollenmodell).
- Report-Interpretation (JUnit/TestNG/Allure-Parsing) nicht implementiert.
- Jira-Integration ist rein lesend; keine Rückschreib-Synchronisation, kein OAuth 2.0
  (Architektur lässt es zu — `JiraAuthType.OAUTH2` existiert bereits als Enum-Wert,
  ist aber nicht implementiert).
- Evidence/Attachments pro Result, Audit-Historie, Bulk-Result-Update,
  `automationReference` — analysiert, bewusst zurückgestellt (siehe oben und
  BACKLOG.md → Next).
- Jira-Forge-App-Vorbereitung (Abschnitt 43): nicht implementiert, aber die
  bestehende REST-API (`GET /test-cases/{id}/requirements`,
  `GET /projects/{key}/requirements`, Execution-Status/Progress-Endpoints) liefert
  bereits alles, was eine spätere Forge-App bräuchte, ohne Architekturumbau — siehe
  BACKLOG.md → Later.

## Nächster sinnvoller Schritt

„Kleinere Komfortfeatures" (Abschnitt 48, Punkt 9, niedrigste Priorität dieses
Blocks) sowie die in BACKLOG.md → Next neu aufgenommenen Punkte (Bulk-Result-Update,
Audit-Trail, Evidence pro Result) sind die logischen nächsten Kandidaten — in dieser
Reihenfolge, weil sie auf der jetzt stabilen PATCH-/Such-/Jira-Basis aufsetzen, ohne
weitere Grundlagenarbeit zu erfordern.
