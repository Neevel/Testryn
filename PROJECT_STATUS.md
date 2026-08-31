# PROJECT_STATUS.md

> Momentaufnahme des aktuellen technischen Stands. Keine Historie — siehe Git-Log für
> Verlauf. Wird bei jedem abgeschlossenen, relevanten Task aktualisiert.

Stand: 2026-08-31

## Aktueller Meilenstein

**Task 3 (Forge-Deployment + visuelle Live-Abnahme): Deployment erledigt, visuelle
Abnahme weiterhin offen.**

Erledigt in dieser Session:

- **Vor-Deployment-Checks grün**: Forge-Jest 79/79, `forge lint` ohne Befund,
  Frontend `npm run build` + 7/7, Backend `RequirementCoverageTest` 17/17 und
  `TestCaseDefinitionUpdateTest` 2/2 (die beiden, die die additive
  `projectKey`-DTO-Änderung abdecken). Ein flakiger `409` trat im `@BeforeEach`
  von `RequirementWorkflowTest` beim kombinierten Lauf auf (bekannte, seit Langem
  dokumentierte `System.nanoTime() % 100000`-Projektschlüssel-Kollision); isoliert
  6/6 grün. Kein Branch-Code betroffen.
- **Tunnel unverändert**: der bestehende `cloudflared`-Quick-Tunnel
  (`…trycloudflare.com`, Adresse nicht in Klartext hier wiederholt) läuft weiter,
  erreicht das abgesicherte Backend (`401` ohne Token) und stimmt mit
  `permissions.external.fetch.backend` sowie `TESTRYN_API_BASE_URL` (Forge
  `development`) überein. Keine neue Tunnel-URL, **keine Manifest-Änderung**, kein
  `MAJOR_VERSION_RULE`.
- **Forge-Env**: `TESTRYN_API_TOKEN` ist als verschlüsselte Variable vorhanden
  (`forge variables list` zeigt `✔ / ****`; Wert nie ausgegeben). Der
  `testryn:write`-Scope ist durch frühere verifizierte Schreibpfad-Deployments
  belegt (v3.7.0 Definition-Editing, v3.8.0 Create+Link) und nicht ohne
  Admin-Token erneut introspektierbar.
- **Deployment**: `forge deploy -e development` erfolgreich, **App-Version 5.1.0**;
  `forge lint` im Zuge des Deploys ohne Befund. `forge install list`:
  Installation auf `ki-meets-testautomation.atlassian.net` (Environment
  `development`, App-Version `5`) ist **`Up-to-date`**. Kein
  `forge install --upgrade` nötig — keine neuen Berechtigungen/Egress-Adressen.
- **Endpoint-Erreichbarkeit über den öffentlichen Tunnel geprüft** (nicht visuell):
  `GET /requirement-links/coverage`, `GET /projects/{key}/test-cases`,
  `GET /integrations/jira/connection`, `GET /projects` und
  `POST /projects/{key}/executions` sind über die von Forge Cloud genutzte
  öffentliche Tunnel-URL erreichbar und antworten korrekt mit `401` ohne Token
  (Auth erzwungen, Routing intakt).

**Weiterhin offen — visuelle Live-Abnahme im Jira-Browser** (in dieser Session
nicht durchführbar: kein angemeldeter Atlassian-Browser verbunden — weder der
isolierte Session-Browser noch „Claude in Chrome"):

- EVAL-47 / Issue mit Coverage: Panel-Laden, Testfälle/Schritte/letzte Ergebnisse,
  Light + Dark Mode, normales + schmales Layout, Accordions und Step-Tabellen.
- „Link existing test case": Projektauswahl, Suche/Pagination/Total Count,
  „Linked"-Markierung, echtes Verknüpfen, Coverage-Reload ohne Seitenwechsel,
  kein Duplikat bei erneutem Verknüpfen, Jira unverändert.
- „Start execution": Gruppierung nach Projekt, Vorauswahl + optionaler Name,
  Verhinderung projektübergreifender Auswahl, Doppelklick-Schutz, erfolgreicher
  Start, Erfolgsmeldung mit Name + Link, „Open execution in Testryn", Prüfung der
  gepinnten Versionen in Testryn, anschließender Coverage-Reload.
- Empty State an einem unverlinkten Issue: „No Testryn test cases linked",
  „Link existing test case" verfügbar, „Start execution" **nicht** angeboten.
- Fehlerzustände: kontrollierte Testryn-Downtime → „Testryn is currently
  unavailable" ohne technische Details, danach Wiederherstellung; Unauthorized mit
  temporärem ungültigem Token. Bewusst nicht angefasst, da nur gepaart mit der
  visuellen Prüfung aussagekräftig und ein Token-/Backend-Eingriff ohne visuelle
  Bestätigung der Wiederherstellung riskant wäre.

**Execution direkt aus dem Jira-Panel starten: implementiert (Task 2).** Im
befüllten Panel öffnet „Start execution" einen Picker: die zum Issue verknüpften
Testfälle, nach Projekt gruppiert (Testryns Ad-hoc-Execution-Endpoint ist
einprojektig — ein Issue mit projektübergreifenden Links startet je Projekt
separat), jeweils vorausgewählt, plus optionaler Execution-Name. Ein Klick startet;
der Button ist während des Requests deaktiviert (In-Flight-Guard), ein Doppelklick
kann keine zweite Execution anlegen. Bei Erfolg zeigt das Panel einen
„Open execution in Testryn"-Link (`<app-base>/executions/<id>`) und lädt die
Coverage neu.

**Analyseentscheidung**: keine neue Backend-API. Der bestehende, provider-neutrale
`POST /api/v1/projects/{projectKey}/executions` (Ad-hoc-Execution) deckt den Fall
fachlich vollständig ab — er erzeugt bereits einen unveränderlichen Snapshot, der
pro Testfall auf dessen aktuelle `TestCaseVersion` gepinnt ist (ADR 0003). Einzige
Backend-Änderung: `CoverageTestCaseResponse` trägt zusätzlich `projectKey`
(additiv, provider-neutral, kein Domain-/Migrationsschritt — `project` wird vom
Entity-Graph der Coverage-Abfrage ohnehin geladen; der N+1-Regressionstest blieb
unverändert grün). Der Ad-hoc-Endpoint ist projekt-scoped, daher braucht der
Resolver den Key, um nicht pro Testfall einen Extra-Request zu stellen
(Abschnitt 22).

**Vertrauensmodell** wie bei `updateTestCaseDefinition`: der Browser übergibt nur
Testfall-IDs; der Resolver liest den Issue-Key aus dem Invocation Context, ruft die
Coverage erneut ab und lehnt jede nicht enthaltene ID sowie jede
projektübergreifende Auswahl ab, bevor etwas erzeugt wird. Jira bleibt read-only,
der Service-Token bleibt im Resolver (`testryn:write` genügt), keine Jira-REST-
Scopes, keine Tokens/Authorization-Header/rohen Backend-Fehler an den Browser
(jede Fehlerantwort ist eines von `invalid`/`unauthorized`/`unavailable`).

Neuer Resolver `startExecution`, neue Komponente `ExecutionStarter.jsx`, reine
Datenhilfe `groupByProject` in `coverageView.js`. ADR 0016 um einen weiteren
Erweiterungsabschnitt ergänzt, `docs/jira-forge-integration.md` aktualisiert.
Verifikation: **Forge 79/79 Jest grün** (64 vorher + 15 neu), `forge lint` ohne
Befund. Backend `RequirementCoverageTest` 17/17 + `TestCaseDefinitionUpdateTest`
2/2 isoliert grün; Frontend `npm run build` + 7/7 grün (nicht betroffen). Voller
Backend-Lauf 162/163 — der eine Fehlschlag ist erneut die dokumentierte
`System.nanoTime() % 100000`-Test-Key-Kollision (diesmal im Setup von
`BulkResultUpdateTest`, isoliert 16/16 grün; in diesem Task kein Execution-Modul-
Code angefasst). Task 1 unverändert grün. `forge deploy`/`install` und die visuelle
Jira-Abnahme stehen weiter aus (eigener Task).

**Vorhandenen Testfall aus dem Jira-Panel verknüpfen: implementiert (Forge-only,
Task 1).** Neben „Create test case" bietet das Panel jetzt „Link existing test
case": Projekt wählen, Testfälle des Projekts nach Human-ID/Titel durchsuchen
(bestehender paginierter `GET /api/v1/projects/{key}/test-cases`-Such-Endpoint,
eine Seite je Forge-Request, Abschnitt 22), einen Treffer per Klick mit dem
aktuellen Issue verknüpfen. Kein neuer Backend-Endpoint: der Schreibpfad ist der
bestehende provider-neutrale `POST /api/v1/test-cases/{id}/requirements`
(Duplikatschutz `409`, Best-Effort-Enrichment). Vertrauensmodell wie beim
Erstellen (ADR 0016): der Browser liefert nur die Testfall-ID, `externalKey` kommt
aus dem Forge Invocation Context, die `url` aus Testryns persistierter
Jira-Connection (`GET /api/v1/integrations/jira/connection`) — der Service-Token
bleibt im Resolver, sein vorhandener `testryn:write`-Scope reicht, keine
Jira-REST-Scopes. Bereits verknüpfte Treffer zeigt das Panel als „Linked" und
bietet sie nicht erneut an; die Durchsetzung bleibt der `409`. Neuer Resolver
`searchTestCases`/`linkExistingTestCase`, neue Komponente `TestCaseLinker.jsx`,
reine Datenhilfe `markLinkable` in `coverageView.js`. ADR 0016 um einen
Erweiterungsabschnitt ergänzt, `docs/jira-forge-integration.md` aktualisiert.
Verifikation: **Forge 64/64 Jest grün** (46 vorher + 18 neu), `forge lint` ohne
Befund. Frontend `npm run build` + 7/7 grün und `tools`-Suite nicht betroffen
(keine Änderung dort). Voller Backend-Lauf 162/163 — der eine Fehlschlag ist die
seit Langem dokumentierte `System.nanoTime() % 100000`-Test-Key-Kollision in
`BulkResultUpdateTest` (isoliert 16/16 grün, kein Backend-Code in diesem Task
angefasst). `forge deploy`/`install` und die visuelle Browser-Abnahme stehen noch
aus (Task 3).

**Reproduzierbarer lokaler Rechnerumzug: umgesetzt.** `scripts/backup-local.ps1`
exportiert die PostgreSQL-Datenbank und das Report-Volume zusammen mit
SHA-256-Prüfsummen in einen von Git ausgeschlossenen Backup-Ordner.
`scripts/restore-local.ps1` validiert dieses Paket und ersetzt nur mit explizitem
`-Force` die lokale Datenbank und Report-Dateien. Der vollständige Ablauf für
Repository-Clone, Jira-Secrets, Cloudflare-Quick-Tunnel sowie Forge-Deployment und
Installations-Upgrade ist im README dokumentiert. Ein echtes Backup des laufenden
Stacks wurde erfolgreich erzeugt; der destruktive Restore wurde bewusst nicht gegen
den aktiven Datenbestand ausgeführt.

**Forge Accordion Test Details UX: implementiert und nach Development deployed.**
Alle verlinkten Test Cases starten geschlossen und lassen sich unabhängig voneinander
öffnen. Der geöffnete Test nutzt eine Jira-native `DynamicTable` für Definition und
Ergebnis (`#`, Action, Input/Data, Expected Result, Result/Actual); weil das aktuelle
`TestStep`-Modell kein Input-Feld besitzt, wird dort ehrlich `—` gezeigt. FAILED,
BLOCKED, SKIPPED und NOT_RUN bleiben über Text, Icon und Farbe unterscheidbar;
Actual ist direkt am Ergebnis sichtbar, Failure Details erst nach einem separaten
Toggle. Tests ohne Execution sowie JUnit-/Legacy-Executions ohne Step Results zeigen
die aktuelle Definition ohne erfundene Ergebnisse; Executions rendern dagegen
konsequent ihre gepinnte Definition zusammen mit den Step Results, statt aktuelle
und historische Versionen zu vermischen. Eine echte EVAL-47-Sichtprüfung im Dark
Mode deckte das frühere `order`/`position`-Mappingproblem auf; der Fix ist mit
38/38 Forge-Jest-Tests und `forge lint` abgesichert und nach Development deployed.
Eine erneute visuelle Abnahme sowie Narrow Layout bleiben offen.

**Step-Level Execution Results: implementiert und live gegen den echten Stack UND
die echte Jira-Site verifiziert.** Ein Test Case zeigt jetzt nicht mehr nur
`FAILED`, sondern welcher Step genau fehlgeschlagen ist, mit erwartetem/tatsächlichem
Ergebnis und Failure Details — im Testryn Runner (Step-für-Step-Bewertung, ein Klick
für PASSED/SKIPPED, Detaildialog für FAILED/BLOCKED) und im Jira Forge Panel
(dieselben Daten, „Failure First"-Darstellung). Testcase-Level-Status wird beim
Bearbeiten von Steps automatisch aus deren Ergebnissen abgeleitet, ohne den
bestehenden, unabhängigen Testcase-Level-Schreibpfad (CI/JUnit) zu verändern —
Automatisierung ohne Step-Reports bleibt ehrlich als „nicht berichtet" sichtbar,
nie als erfundenes PASSED. Der seit Block 4 offene Completed/Aborted-Write-Guard
wurde dabei mitgelöst. Details siehe „Step-Level Execution Results" unten.

**Nachtrag zum vorherigen Block**: das dort als offen dokumentierte `forge
deploy`/`forge install` wurde in diesem Block nachgeholt — die Forge-App ist jetzt
tatsächlich live in der echten Jira-Site installiert und wurde in diesem Block
direkt gegen echte EVAL-47-Daten (inkl. der neuen Step-Ebene) verifiziert.

Die vorherigen Blöcke (Jira Forge Issue Panel; JUnit XML Publisher Adapter; API &
Service Security; Jira Cloud live-verifiziert; Bulk-Result-Update,
`automationReference`, CI-Publisher) sind unverändert gültig — siehe Git-Log und die
Abschnitte weiter unten in dieser Datei für Details.

## Step-Level Execution Results (ADR 0015)

**Domain** (`backend/`, Modul `execution`):

- Neue Entity `ExecutionStepResult` — referenziert `TestStep` direkt statt dessen
  Inhalt zu kopieren: `TestStep`-Zeilen sind bereits unveränderlich und an genau
  eine, bereits gepinnte `TestCaseVersion` gebunden (ADR 0002/0003) — eine separate
  Snapshot-Kopie-Tabelle wäre redundant gewesen (vollständige Analyse in ADR 0015).
  Dasselbe `ExecutionResultStatus`-Enum wie auf Testcase-Ebene (kein Parallelmodell).
- `ExecutionTestCase.initializeStepResults()` — eager, ein `NOT_RUN`-Result je Step,
  beim Execution-Anlegen (kein Lazy-Auto-Create).
- `ExecutionTestCase.deriveStatusFromSteps()` — die Aggregationsregel aus Abschnitt 9
  wörtlich umgesetzt: ein FAILED-Step → `FAILED`; sonst ein BLOCKED-Step → `BLOCKED`;
  sonst alle PASSED → `PASSED`; sonst alle ausgeführten Steps SKIPPED (und
  mindestens einer) → `SKIPPED`; sonst `NOT_RUN`/unvollständig.
- `ExecutionResult.deriveStatus(status)` — neue, bewusst schmale Methode, die NUR
  `status`/`executedAt` setzt, nie `comment`/`durationMs`/`executor`/`actualResult`/
  `failureDetails` — verhindert, dass eine aus Steps abgeleitete Statusänderung
  einen bereits gesetzten manuellen Kommentar oder eine CI-gemeldete Dauer löscht.
- Zwei unabhängige Schreibpfade zum Testcase-Level-Status: direkter Write (CI/JUnit/
  manuell, unverändert) berührt Steps nie; Ableitung aus Steps läuft ausschließlich
  als Seiteneffekt eines Step-Level-Writes (löst Abschnitt 9 und Abschnitt 20/36
  gleichzeitig, ohne dritte Konzeptschicht).

**API:**

- `PATCH /api/v1/executions/{id}/step-results/{stepResultId}` — Einzel-Step,
  JSON-Merge-Patch, dieselbe Semantik wie der bestehende Testcase-Level-Endpoint.
- `PATCH /api/v1/executions/{id}/step-results` — atomarer Bulk-Update, adressiert
  ausschließlich über `stepResultId` (bewusst nicht die im Auftrag skizzierte
  `executionTestCaseId`+`stepReference`-Komposition — ein Weg, keine unnötige
  REST-Tiefe). Alles-oder-nichts wie der bestehende Bulk-Testcase-Endpoint.
- Bestehender `GET /api/v1/executions/{id}` liefert jetzt je Step ein eingebettetes
  `result` (bzw. `null` für Alt-Executions) — kein separater Step-Listen-Endpoint
  nötig, da die volle Execution-Antwort das bereits abdeckt (Abschnitt 17 erlaubt
  das explizit, wenn das bestehende Design bereits passt).
- **Completed/Aborted-Guard (Abschnitt 50, seit Block 4 offener Backlog-Punkt)**:
  `ExecutionService.requireWritable(execution)` lehnt jeden Result-Write — Testcase-
  Level, Step-Level, einzeln oder Bulk — mit `409` ab, sobald eine Execution
  `COMPLETED`/`ABORTED` ist. `CREATED`/`RUNNING` erlauben Writes. Der CI-Publisher
  brauchte keine eigene Anpassung — er nutzt denselben, jetzt geschützten
  Bulk-Endpoint.
- **Jira-Forge-Coverage-API erweitert**: `latestExecution` trägt jetzt `executor`
  und `steps` (Position, Action, Expected, Result mit Status/Actual/Failure).
  `ExecutionTestCaseRepository.findLatestByTestCaseIds` fetch-joint Step-Ergebnisse
  in derselben Abfrage — der bestehende N+1-Regressionstest lief unverändert
  weiter grün, mit Step-Daten in der Antwort.

**Runner** (`frontend/`):

- Jeder Step ist eine eigene Zeile mit vier Quick-Actions. PASSED/SKIPPED wenden
  sich mit einem Klick an, ohne Dialog (Abschnitt 12). FAILED/BLOCKED öffnen einen
  kleinen Detaildialog für Actual Result/Comment/Failure Details (Abschnitt 11).
  „Mark remaining as passed" markiert alle noch offenen Steps eines Testcases in
  einer atomaren Bulk-Anfrage (Abschnitt 13).
  Status immer als Icon + Text + Farbe (nie nur Farbe, Abschnitt 30/49).
- Testcase-Karte zeigt „X / Y steps passed"; die Execution-Summary-Leiste zeigt
  jetzt „Test Cases: N / M completed" UND „Steps: N / M executed" (Abschnitt 14/15);
  die Completion-Warnung hat jetzt zusätzlich einen Step-bezogenen Hinweis
  (Abschnitt 16).
- Rückwärtskompatibel: Executions ohne Step-Daten (vor diesem Block) fallen sauber
  auf die alte, reine Lesetabelle zurück, mit explizitem Hinweis statt Absturz.

**Forge:**

- Aufgeklappte Testcase-Karte zeigt jeden Step des `latestExecution`-Snapshots mit
  echtem Ergebnis (Status-Icon+Text+Farbe, Action, Expected, Actual, Failure bei
  Fehlschlag). Ein fehlgeschlagener Testcase truncatet seine Step-Liste nie —
  der fehlgeschlagene Step ist sofort sichtbar (Abschnitt 32, „Failure First"); ein
  komplett grüner Fall mit vielen Steps bleibt kompakt mit „Show all N steps"
  (Abschnitt 31). Kompaktes CI/Manual-Badge aus dem bestehenden `executor`-Feld
  (Abschnitt 33/34, keine neue Identitätsdomäne). Fehlende Step-Daten (Alt-Execution
  oder JUnit-testcase-level-only) → „Step-level results not reported for this
  execution" statt Absturz oder erfundenem Status (Abschnitt 35/36).

**Automatisierung (JUnit):** `publish-junit` schreibt weiterhin ausschließlich den
Testcase-Level-Status; Step-Ergebnisse bleiben `NOT_RUN`, nie erfunden. Live mit dem
echten Publisher-Jar gegen die reale Surefire-Fixture aus dem vorigen Block
verifiziert (siehe Live-Verifikation unten). Ein framework-natives Step-Reporting
(Selenium, Playwright, eigenes Harness) könnte künftig dieselbe Step-API nutzen —
kein Adapter dafür in diesem Block gebaut (BACKLOG.md).

**Tests:**

- `StepResultTest` (23), `ExecutionWriteGuardTest` (7),
  `ExecutionSnapshotRegressionTest` (1, die in Abschnitt 43 geforderte
  Pflicht-Regression), `RequirementCoverageTest` (+5).
- `AbstractIntegrationTest`: `TESTRYN_JIRA_*`-Isolation hinzugefügt (Abschnitt 51) —
  drei zuvor umgebungsabhängig fehlschlagende `RequirementWorkflowTest`-Assertions
  liefen mit echten Jira-Env-Vars weiterhin deterministisch grün.

**`cd backend && mvn test`: 155/155 grün** (150 vorher + 5 neu in
`RequirementCoverageTest`, plus 23+7+1 neue Testklassen). Eine vorbestehende,
bereits vor diesem Block dokumentierte Testflakiness (`System.nanoTime() % 100000`
als Projekt-Key-Generator, seltene Kollision bei sehr vielen Tests in Folge) trat
einmalig bei einem Volllauf auf, verschwand beim erneuten Lauf, in eigenen neuen
Testklassen durch einen zusätzlichen Zähler abgesichert — kein Codefix am
bestehenden, gemeinsamen Muster in diesem Block (siehe „Bekannte Einschränkungen").
**`cd integrations/jira-forge && npm test`: 15/15 grün** (unverändert, Resolver-Ebene
reicht neue Felder nur transparent durch). **`cd frontend`: `npm run build`
fehlerfrei, `npm run test`: 7/7 grün** (5 vorher + 2 neu für Step-Progress).
**`cd tools/testryn-publisher && mvn test`: 82/82 grün** (unverändert, Regression
bestätigt).

### Live-Verifikation gegen den echten Stack UND die echte Jira-Site

- **Backend live** (`docker compose build backend frontend && ... up -d`): echte
  Migration `0006` angewendet, neuer Endpoint erreichbar.
- **Kompletter Pflicht-Workflow (Abschnitt 56) live durchgespielt**, nicht nur per
  Unit-Test: Testcase mit 4 Steps angelegt (`STEP1-TC-1`), mit EVAL-47 verlinkt
  (echte Jira-Anreicherung bestätigt), Execution erstellt, Step 1+2 PASSED, Step 3
  FAILED (mit `actualResult`/`failureDetails`), Step 4 bewusst `NOT_RUN` gelassen —
  Testcase-Level-Status automatisch auf `FAILED` abgeleitet, per echtem `GET`
  bestätigt. Testcase danach auf v2 aktualisiert (Step 1 umbenannt, Step 5 neu) —
  Execution #1, erneut abgefragt, zeigte weiterhin exakt die ursprünglichen 4 Steps
  mit Original-Wortlaut und Original-Ergebnissen; eine neue Execution sah korrekt
  v2 mit 5 frischen NOT_RUN-Steps.
- **Completed-Guard live bestätigt**: Execution auf `COMPLETED` gesetzt (`200`),
  anschließender Step-Write → echtes `409`.
- **JUnit-Pfad live bestätigt** (Abschnitt 57): echter `testryn-publisher.jar`-Lauf
  (`publish-junit`) gegen eine reale, aus `mvn test` erzeugte Surefire-XML (aus dem
  vorigen Block wiederverwendete Fixture) → Testcase-Level `PASSED`/`executor: ci`,
  Step-Ergebnis blieb `NOT_RUN` — keine erfundenen Step-Passes.
- **Jira-Forge-Coverage-API live bestätigt**: `GET .../requirement-links/coverage`
  gegen EVAL-47 lieferte die neuen `steps`/`executor`-Felder korrekt für die neue
  Execution UND zeigte für eine echte, ältere Execution aus einem früheren Block
  (`MS2-TC-1`) korrekt eine leere `steps`-Liste (echter, nicht simulierter
  Rückwärtskompatibilitäts-Fall).
- **Forge-App live deployed und aktualisiert**: `forge lint` (im Rahmen von `forge
  deploy`) fand keine Probleme; `forge deploy -e development` erfolgreich (App-Version
  3.1.0); `forge install list` bestätigt „Up-to-date" für die echte Installation in
  `ki-meets-testautomation.atlassian.net`.
- **Bekannte Lücke dieser Verifikation**: die tatsächliche visuelle Kontrolle des
  Panels im Jira-Browser-UI konnte in dieser Session nicht durchgeführt werden — der
  isolierte Browser dieser Session hat keine angemeldete Atlassian-Session, und
  „Claude in Chrome" (echter, angemeldeter Browser) war in dieser Umgebung nicht
  verbunden. Die vom Panel konsumierten Daten sind jedoch vollständig über die
  Coverage-API live bestätigt (identisch zu dem, was die UI Kit-Komponenten
  rendern), und `forge lint`/`deploy` liefen ohne Fehler durch. Der Nutzer wurde
  gebeten, bei Gelegenheit selbst einen kurzen visuellen Blick auf EVAL-47 zu
  werfen.
- Für die Verifikation wurde ein vom Nutzer selbst über die Settings-UI erzeugter,
  temporärer Token verwendet (nicht per Direkt-DB-Insert dieses Mal) — Revoke liegt
  beim Nutzer, da die Token-Liste mehrere Kandidaten ohne eindeutige Zuordnung
  zeigte und ein Blind-Revoke-Risiko vermieden wurde.

## Jira Forge Issue Panel (ADR 0014)

**Backend** (`backend/`, Modul `requirement`):

- Neuer Read-Endpoint `GET /api/v1/requirement-links/coverage?provider=jira&externalKey=EVAL-47[&limit=20]`
  — provider-neutral (`provider` ein gewöhnlicher Query-Parameter, kein
  Jira-Spezifikum im Core, Abschnitt 6), löst alle zu einer `provider`+`externalKey`
  verlinkten Test Cases samt aktueller Version/Steps und jeweils letztem
  Execution-Ergebnis in **zwei** Datenbank-Roundtrips auf (nicht pro Test Case
  einer) — verifiziert durch einen dedizierten Hibernate-Statistics-Regressionstest,
  nicht nur durch Code-Inspektion.
- Neu: `ExecutionTestCaseRepository.findLatestByTestCaseIds` (eine JPQL-Korrelated-
  Subquery, "neueste Execution je Test Case" für einen ganzen Batch von IDs in
  einer Abfrage), `TestCaseRepository.findByIdIn` (Batch-Variante der bestehenden
  Entity-Graph-Ladepfade), `RequirementLinkRepository.findByProviderAndExternalKeyOrderByCreatedAtAsc`.
- Kein `RequirementCoverageService`-in-`RequirementLinkService` — eigener Service,
  weil dieser eine Anfrage tatsächlich drei Aggregate übergreift (Requirement
  Links, Test Cases, Execution-Ergebnisse).
- Leere Trefferliste (kein Link für den Key) → `200` mit leerem `testCases`-Array,
  kein Fehler. `limit` (Default 20, Max 100) begrenzt, `totalCount` bleibt der
  echte Gesamtwert (Abschnitt 23 — kein stilles Abschneiden ohne Hinweis).
- Benötigt `testryn:read` wie jedes andere `GET` unter `/api/**` (ADR 0012) — keine
  neue Security-Regel nötig.

**Forge-App** (`integrations/jira-forge`, eigenständiges Modul, nicht im
React-Frontend gemischt, Abschnitt 33):

- `jira:issuePanel`-Modul, **UI Kit** (`@forge/react`) statt Custom UI — rendert
  Jira-nativ, komplett ohne iframe (ADR 0014 Entscheidung 2, erfüllt Abschnitt 27
  strukturell statt zufällig).
- Aktuelle Issue-Referenz kommt serverseitig aus dem von der Plattform
  bereitgestellten Aufruf-Kontext (`context.extension.issue.key`), nicht aus einem
  manuellen Feld oder einem vertrauten Frontend-Payload-Wert (Abschnitt 29).
- Genau ein `invoke("getCoverage")`-Aufruf pro Panel-Rendering → genau ein
  Testryn-HTTP-Request (Abschnitt 22).
- Resolver (`src/resolvers/`) ist die einzige Stelle, die je mit Testryn spricht —
  nie der Browser (Abschnitt 7). `TESTRYN_API_TOKEN` ist eine verschlüsselte Forge-
  Umgebungsvariable, wird ausschließlich serverseitig gelesen, erscheint in keiner
  Resolver-Antwort, egal ob Erfolg oder Fehler.
- Panel-Zustände: Loading (fixe Höhe, kein Layout-Sprung), Ok (echte Coverage-
  Summary + eine standardmäßig eingeklappte Karte je Test Case mit Status als
  Text+Icon+Farbe, Abschnitt 13), Empty ("No Testryn test cases linked" + Link),
  Unavailable ("Testryn is currently unavailable. Existing Jira data is
  unaffected."), Unauthorized ("Testryn connection is not authorized.") — nie ein
  Stacktrace, nie ein Tokendetail.
- `permissions.scopes: []` (keine Jira-REST-Aufrufe nötig), kein `write`-Scope
  gegenüber Testryn irgendwo (Abschnitt 8/47 — dieser Block ist vollständig
  read-only).

**Tests:**

- `RequirementCoverageTest` (Backend, 13): Einzel-/Mehrfach-Treffer, keine Links,
  Case-Insensitivität bei provider/externalKey, mit/ohne Execution, Pagination-Cap
  mit echtem `totalCount`, kein Token → 401, Read-Token ausreichend (dokumentiert
  explizit: kein "wrong scope" 403 möglich, da READ bereits der niedrigste Scope
  ist), unbekannter Provider → 400, N+1-Regressionstest über Hibernate-Statistics.
- `test/testrynClient.test.js` + `test/resolver.test.js` (Forge, 15, Jest gegen die
  echte `@forge/resolver`-Bibliothek + gemocktes `@forge/api`-fetch): Issue-Key-
  Extraktion, fehlender Issue-Key → kein Testryn-Aufruf, erfolgreiche Antwort,
  leere Antwort, Testryn 401/403 → "unauthorized", Netzwerkfehler/5xx →
  "unavailable", malformed Response → "unavailable" statt Crash, Token erscheint
  in keinem einzigen Ergebnis (Erfolg oder Fehler), fehlender Token → kein Crash.

**`cd backend && mvn test`: 119/119 grün** (108 vorher + 13 neu — mit
`TESTRYN_JIRA_*` bewusst aus der Shell entfernt, um die bekannte, umgebungs-
bedingte `RequirementWorkflowTest`-Störung zu vermeiden, Abschnitt 45).
**`cd integrations/jira-forge && npm test`: 15/15 grün.**
**`cd tools/testryn-publisher && mvn test`: 82/82 grün** (unverändert, Regression
bestätigt). **`cd frontend && npm run build`/`npm run test`: fehlerfrei bzw. 5/5
grün** (unverändert, nicht von diesem Block betroffen).

### Live-Verifikationsstand (Abschnitt 39-43)

- **Backend-Erreichbarkeit real verifiziert**: `docker compose build backend &&
  docker compose up -d backend` mit dem neuen Endpoint; `GET
  /api/v1/requirement-links/coverage` liefert `401` ohne Token über
  `http://localhost:8080` **und** über einen echten, öffentlich erreichbaren
  `cloudflared`-Quick-Tunnel (`https://temporarily-mating-kodak-sources.trycloudflare.com`,
  ephemer, ohne Cloudflare-Account) — bestätigt, dass Forge Cloud den Endpoint
  tatsächlich erreichen könnte (Abschnitt 10).
- **`forge deploy`/`forge install` bewusst nicht von dieser Session durchgeführt**:
  erfordert einen echten Atlassian-Account-Login (`forge login`) und registriert
  eine reale App unter dem Account des Nutzers sowie eine reale Installation in
  dessen Jira-Site — dem Nutzer zur expliziten Entscheidung vorgelegt; gewählt
  wurde „Tunnel öffnen, Deployment selbst durchführen". `manifest.yml` ist bereits
  mit dem echten Tunnel-Host in der Egress-Allowlist vorbereitet;
  `docs/jira-forge-integration.md` enthält die vollständige Schritt-für-Schritt-
  Anleitung (`forge login` → `forge register` → dedizierter `testryn:read`-Token
  → `forge variables set --encrypt` → `forge deploy` → `forge install`).
- **Nachtrag (Step-Level-Execution-Results-Block)**: `forge login`/`forge register`/
  `forge deploy`/`forge install` wurden vom Nutzer zwischen den Blöcken tatsächlich
  durchgeführt — die App ist real installiert in `ki-meets-testautomation.atlassian.net`.
  Die volle Live-Verifikation gegen EVAL-47 (inkl. der neuen Step-Ebene) erfolgte im
  Step-Level-Execution-Results-Block, siehe dessen eigenen Abschnitt oben. Die
  Empty-State- und Downtime-Simulation-Prüfungen sowie die tatsächliche visuelle
  Browser-Kontrolle des Panels stehen weiterhin aus (siehe dortige „Bekannte Lücke").

## JUnit XML Import & CI Adapter (ADR 0013)

**Publisher** (`tools/testryn-publisher/`):

- Neuer `ResultBatchReader`: `JUnitXmlResultBatchReader` — liest genau eine
  JUnit-kompatible XML-Datei (bare `<testsuite>` oder `<testsuites>`-Wrapper,
  identisches Schema für Surefire **und** Failsafe, keine Sonderbehandlung nötig).
  `javax.xml.parsers.DocumentBuilderFactory` gegen XXE gehärtet (Doctype verboten,
  externe Entities/DTDs deaktiviert, `ACCESS_EXTERNAL_DTD`/`_SCHEMA` leer, eigener
  `EntityResolver`, der jede externe Auflösung verwirft) — keine neue Abhängigkeit,
  alles JDK-Bordmittel. Statusmapping: kein `<failure>`/`<error>`/`<skipped>` →
  `PASSED`; `<failure>`/`<error>` → `FAILED` (`message` → `actualResult`, `type` +
  Stacktrace/Body → `failureDetails`); `<skipped>` → `SKIPPED`. `BLOCKED` wird nie
  automatisch erzeugt. Duration über `BigDecimal` (nicht `double`) konvertiert — keine
  Rundungsdrift bei `time="1.273"` → 1273 ms. `<system-out>`/`<system-err>` werden nie
  gelesen oder weitergereicht.
- Neuer Orchestrator: `JUnitReportImporter` — löst `--results`-Eingaben (Datei(en)
  und/oder Verzeichnis(se), Verzeichnisse nicht rekursiv) auf, parst jede Datei
  einzeln, führt zu einem kombinierten Batch zusammen. Cross-File-Duplikate derselben
  `automationReference` werden mit einer Fehlermeldung abgelehnt, die jede betroffene
  Datei nennt — bewusste Entscheidung nach Analyse des echten
  Surefire-Rerun-XML-Formats (`<rerunFailure>`/`<flakyFailure>` verschachtelt sich
  *innerhalb* eines `<testcase>`, erzeugt also nie echte Duplikate auf Element-Ebene;
  ein tatsächliches Duplikat deutet auf ein reales Problem hin, z. B. ein
  wiederverwendetes `target/`-Verzeichnis).
- Neuer Subcommand `publish-junit` (`PublisherCli.parseJUnit`,
  `PublisherMain.runJUnitPublish`) neben dem unveränderten `publish` (JSON):
  `--base-url`, `--execution-id` (**erforderlich** — JUnit-XML trägt nie eine
  Execution-ID), `--results` (mehrfach angebbar **und** greedy-multi-value pro
  Vorkommen, damit sowohl ein Verzeichnis als auch ein vom Shell bereits expandiertes
  Glob-Pattern funktionieren), `--dry-run` (parst alles, druckt eine Vorschau mit
  Datei-/Test-/Status-Zählungen und jeder aufgelösten `automationReference`, sendet
  nichts).
- Unbekannte `automationReference`: **keine** Client-seitige Vorab-Prüfung — dieselbe
  atomare Bulk-API (ADR 0010) lehnt die gesamte Anfrage ab und listet jede nicht
  auflösbare Referenz; nichts wird je teilweise veröffentlicht. Dieselbe
  `TestrynApiClient`/`PublishOutcome`-Pipeline wie der bestehende JSON-Workflow — kein
  paralleler Code-Pfad, keine duplizierte Domain-Logik.
- `TestrynApiClient` selbst: **keine Änderung nötig** — bestätigt, dass
  `ResultBatchReader` (ADR 0011) tatsächlich die richtige Erweiterungsstelle war.

**Backend** — eine gezielte, dokumentierte Änderung: `TestCaseService`s
`automationReference`-Zeichensatz um `#` erweitert (ADR 0009 → ADR 0013), damit die
`classname#name`-Konvention überhaupt persistierbar ist. Live am echten Stack
entdeckt: der erste Versuch, `com.example.PublisherDemoTest#passedTest` über die API
anzulegen, schlug mit `400 Bad Request` fehl, bevor diese Änderung vorgenommen wurde.

**automationReference-Konvention** (ADR 0013): `classname + "#" + name`, wörtlich aus
dem XML, z. B. `com.example.LoginTest#successfulLogin` — nicht der bloße
Methodenname (kollidiert projektweit), `#` statt `.` als Trenner (ein weiterer Punkt
wäre nicht vom Package-Pfad unterscheidbar). Für parametrisierte/dynamische Tests
bewusst **keine** Rückübersetzung generierter Display-Namen — was im XML steht, wird
eins zu eins übernommen; dokumentierte, akzeptierte Grenze statt riskanter Heuristik.
Kein separates YAML-Mapping-Subsystem gebaut (erwogen, aber für den Normalfall nicht
nötig).

**Tests:**

- `JUnitXmlResultBatchReaderTest` (20): bare `<testsuite>`, `<testsuites>`-Wrapper,
  PASSED/FAILED/ERROR/SKIPPED-Mapping, Duration-Präzision, Failure-Message/Type/
  Stacktrace, classname/name-Mapping inkl. fehlendem `classname`, fehlender
  `name`+`classname`, malformed XML, leere Eingabe, falsches Root-Element, Binärdaten,
  drei verschiedene XXE-/Doctype-Angriffsversuche (jeweils abgelehnt).
- `JUnitReportImporterTest` (9): einzelne Datei, mehrere Dateien, Verzeichnis
  (nicht-rekursiv bestätigt), Datei+Verzeichnis gemischt, Cross-File-Duplikat,
  leeres Verzeichnis, nicht existierender Pfad, leere Eingabeliste,
  Datei-spezifische Fehlermeldung bei Parse-Fehler.
- `PublisherCliTest` (+8): `publish-junit`-Flag-Parsing, `--dry-run`, wiederholtes
  `--results`, Shell-Glob-Simulation, fehlende Pflichtfelder.
- `PublisherMainTest` (+7, `@Nested PublishJunit`): unbekannter Command, Usage-Fehler,
  nicht existierender Pfad ohne rohen Stacktrace, Dry-Run-Ausgabeformat inkl.
  Token-Abwesenheit, Cross-File-Duplikat → Exit 2, leerer Report → Exit 1.
- `AutomationReferenceTest` (Backend, +1): akzeptiert die `classname#name`-Konvention.
- Bestehende Tests (JSON-`publish`-Workflow, `TestrynApiClient` inkl. 401/403/
  Token-nie-in-Fehlermeldung) unverändert grün — bestätigt keine Regression.

**`cd tools/testryn-publisher && mvn test`: 82/82 grün.**
**`cd backend && mvn test`: 108/111 grün** (3 vorbestehende, umgebungsbedingte
Fehlschläge in `RequirementWorkflowTest`, nicht durch diesen Block verursacht —
siehe „Bekannte Einschränkungen" unten).

### Echte Ende-zu-Ende-Verifikation (nicht nur handgeschriebenes XML)

- Neues, echtes Maven/JUnit-5-Projekt `tools/testryn-publisher/fixtures/publisher-demo-project`
  (kein Teil des Produkt-Reactors, nicht in CI) mit vier realen Tests: `passedTest`,
  `failedTest` (bewusst fehlschlagend), `skippedTest` (`@Disabled`),
  `anotherPassedTest`. Echter `mvn test`-Lauf erzeugt echtes Surefire-XML
  (`target/surefire-reports/TEST-com.example.PublisherDemoTest.xml`) — dieses reale,
  nicht handgeschriebene XML wurde für die folgende Verifikation verwendet.
- Vier reale Testryn-Test-Cases im neuen Projekt `JUNIT1` angelegt, mit
  `automationReference` `com.example.PublisherDemoTest#{passedTest,failedTest,
  skippedTest,anotherPassedTest}`, zu einer echten Execution hinzugefügt.
- Vor dem Publish ein echter manueller Kommentar auf einem Result gesetzt
  („pre-existing manual comment, must survive JUnit publish").
- **Echter Publish-Lauf** des gebauten `testryn-publisher.jar` gegen die laufende,
  authentifizierte Instanz (`publish-junit --results .../surefire-reports`):
  `Published successfully.`, Exit 0. Im Execution-Objekt danach per REST bestätigt:
  `passedTest`→PASSED (1 ms), `failedTest`→FAILED (4 ms, `actualResult`="deliberate
  failure...", `failureDetails` enthält Exception-Typ+Stacktrace),
  `skippedTest`→SKIPPED (0 ms, `actualResult`=Skip-Message),
  `anotherPassedTest`→PASSED (21 ms) — alle vier mit `executor: "ci"`. Der vorab
  gesetzte Kommentar auf `passedTest` **blieb unverändert erhalten** (Merge-Patch-
  Semantik bestätigt, kein Feld wurde ungewollt genullt).
- **Fehler-Ende-zu-Ende** (Abschnitt 17/34): ein zweiter Report mit einer bewusst
  unbekannten `automationReference`
  (`com.example.PublisherDemoTest#thisTestDoesNotExistInTheExecution`) gemischt mit
  einer gültigen Referenz gepublisht → Exit-Code 1, klare Fehlermeldung mit exakt der
  einen unauflösbaren Referenz, **kein** Feld in der Execution verändert (Vorher-/
  Nachher-Snapshot per REST byte-identisch — auch die an sich gültige Referenz im
  selben Request wurde nicht teilweise übernommen, volle Atomizität bestätigt).
- Für diese Verifikation wurde ein temporärer, ausschließlich `write`-scoped Service
  Token direkt in der DB angelegt (Bootstrap griff nicht mehr, da aus einem
  vorherigen Block bereits Tokens existierten — nach ausdrücklicher Rückfrage beim
  Nutzer und dessen Zustimmung) und nach Abschluss der Verifikation sofort widerrufen
  (Revoke sofort wirksam bestätigt: derselbe Token → `401` direkt danach).
- UI-Sichtprüfung im Browser nicht durchgeführt: das Eintragen des Verifikations-
  Tokens in das Settings-Feld wurde vom Sicherheits-Classifier dieser Session als
  Credential-Eingabe blockiert (korrektes Verhalten, keine Umgehung versucht) — die
  Daten, die die UI anzeigen würde, sind identisch mit den oben per authentifiziertem
  REST-Aufruf verifizierten (die UI ist ein reiner Client über dieselbe API).

## API & Service Security (ADR 0012)

**Backend:**

- Neues Modul `security` (`domain`/`repository`/`service`/`web`/`config`):
  `ServiceToken`-Entity (Name, Description, `lookupId` + `tokenHash` statt
  Klartext, Scopes, `createdAt`/`lastUsedAt`/`expiresAt`/`revokedAt`),
  `ServiceTokenService` (Erzeugung, SHA-256-Hashing, Verifikation — siehe ADR 0012
  für die Begründung gegen eine Passwort-KDF), `ServiceTokenBootstrap`
  (`TESTRYN_BOOTSTRAP_TOKEN`, nur beim allerersten Start ohne bestehende Tokens).
- Token-Format `testryn_<lookupId>_<secret>` — `lookupId` (12 Byte, nicht geheim)
  ermöglicht einen indexierten O(1)-Lookup statt eines Tabellenscans.
- Spring Security, stateless, ohne Form-Login/HTTP-Basic/Session, CSRF für die
  reine Bearer-Token-API deaktiviert (kein Cookie-/Session-basierter
  Angriffsvektor). Ein `OncePerRequestFilter` löst den Header auf und flacht
  Scope-Implikationen (`write` ⊇ `read`, `admin` ⊇ `write` ⊇ `read`) zu konkreten
  Authorities ab, damit die eigentlichen Zugriffsregeln einfache
  `hasAuthority(...)`-Deklarationen bleiben.
- `GET`/`HEAD` unter `/api/**` → `testryn:read`; `POST`/`PUT`/`PATCH`/`DELETE` →
  `testryn:write`; `/api/v1/service-tokens/**` → immer `testryn:admin`,
  unabhängig von der Methode. `/v3/api-docs`, `/swagger-ui/**` bleiben bewusst
  offen (kein bestehendes Dev/Prod-Profil, das hier sauber anzuknüpfen wäre).
  Scope-Wire-Format ist `"testryn:read"`/`"testryn:write"`/`"testryn:admin"`
  (Jackson `@JsonValue`/`@JsonCreator` auf dem Enum, DB-Spalte bleibt unberührt
  bei den einfachen `READ`/`WRITE`/`ADMIN`-Namen) — ein Mismatch zwischen diesem
  Wire-Format und der ursprünglichen Enum-Implementierung wurde durch die eigene
  Integrationstestsuite gefunden und noch in diesem Block korrigiert.
- 401/403 über eigene `AuthenticationEntryPoint`/`AccessDeniedHandler` im
  bestehenden `ApiError`-Format (jetzt mit optionalem `code`-Feld, z. B.
  `"UNAUTHORIZED"`/`"FORBIDDEN"` — additiv, kein Breaking Change).
- Token-Verwaltungs-API (`POST`/`GET`/`GET /{id}`/`POST /{id}/revoke` unter
  `/api/v1/service-tokens`), Rohwert nur in der Create-Response, danach nie wieder.
- Migration `0005-service-tokens.sql` (`service_tokens`, `service_token_scopes`).

**Publisher:** unverändert im Code (unterstützte `TESTRYN_API_TOKEN` bereits aus dem
vorigen Block vorbereitend) — jetzt end-to-end gegen eine tatsächlich
durchsetzende API verifiziert: fehlender/ungültiger Token → 401 → Exit-Code 1;
`read`-Token gegen den Bulk-Endpoint → 403 → Exit-Code 1; `write`-Token → Erfolg →
Exit-Code 0. Token-Wert erscheint in keiner Fehlerausgabe.

**Frontend:** `Authorization`-Header wird aus einem `sessionStorage`-gehaltenen
Dev-Token angehängt (nie im gebauten Bundle, nie `localStorage`) — Settings-Seite
bekommt dafür einen neuen „Your API Token"-Bereich sowie eine vollständige
Service-Tokens-Verwaltung (Liste, Anlage mit einmaliger Anzeige des Rohwerts,
Revoke). Ausdrücklich als Entwicklungs-Übergangslösung gekennzeichnet, keine
vorgetäuschte Login-Funktion (Abschnitt 8/ADR 0012).

**Tests:** bestehende Testsuite umgestellt (`AbstractIntegrationTest` hängt jedem
Request per Default einen frisch erzeugten ADMIN-Token an, sofern ein Test seinen
eigenen `Authorization`-Header nicht explizit setzt) — alle bisherigen
Regressionstests bleiben ohne Änderung an ihren eigentlichen Testkörpern grün.
Neu: `ServiceTokenServiceTest` (17, reine Domain-/Hashing-/Verifikationslogik),
`ServiceTokenAuthenticationTest`, `ServiceTokenManagementTest`,
`BulkResultUpdateAuthTest`, `SecurityLoggingTest` (Log-Leakage-Prüfung mit
Logback-`ListAppender`). Publisher: 3 neue Tests (401/403/Token-nie-in-Fehlermeldung).

**`mvn test`: 107/107 grün.** `tools/testryn-publisher && mvn test`: 38/38 grün.
`npm run build`/`npm run test`: fehlerfrei bzw. 5/5 grün. Ein pre-existing
Testcontainer-Detail dabei entdeckt (nicht security-bezogen): mehrfach
hintereinander in derselben Session laufende `mvn test`-Aufrufe gegen dieselbe
Postgres-Instanz können bei zwei Testklassen mit `System.nanoTime() % 100000` als
Projekt-Key-Generator sehr selten kollidieren (409 statt 201) — bei einem sauberen
Einzellauf nicht reproduzierbar, kein Codefix in diesem Block (nicht
sicherheitsrelevant, siehe „Bekannte Einschränkungen").

### Browser-Verifikation (Abschnitt 30-32) — gegen `docker compose up --build`

- **Workflow A** (kein Token → 401): `GET /api/v1/projects` ohne Header →
  `401`/`UNAUTHORIZED`.
- **Workflow B** (Read-Token): `GET` → `200`; `POST` → `403`/`FORBIDDEN`.
- **Workflow C** (Write-Token): Bulk Result Update über einen echten
  `write`-Token → `200`, Ergebnis korrekt persistiert.
- **Workflow D** (Revoke): gültiger Token funktioniert, wird widerrufen, derselbe
  Token danach → `401`; `lastUsedAt` dabei live in der DB/UI bestätigt aktualisiert.
- **Workflow E** (Expiration): Token mit `expiresAt` in 2 Sekunden → vor Ablauf
  `200`, nach Ablauf `401`.
- **Workflow F** (Publisher): echtes `testryn-publisher.jar` — kein Token → `401`
  → Exit 1; `read`-Token → `403` → Exit 1; `write`-Token → Erfolg → Exit 0,
  Ergebnis sichtbar in der UI (Execution-Detailseite zeigt FAILED/HTTP 500/
  Expected HTTP 200/By: ci/Duration: 0.9s — exakt wie vom Publisher gesendet).
- **Bootstrap real verifiziert**: `TESTRYN_BOOTSTRAP_TOKEN` erzeugt beim ersten
  Start einen Admin-Token; nach einem Backend-Neustart (Daten blieben im
  Postgres-Volume erhalten) mit demselben Bootstrap-Wert wurde **kein** zweiter
  Bootstrap-Token erzeugt (Tokens existierten bereits) — bestätigt „kein
  Dauer-Backdoor" nicht nur im Code, sondern im echten Neustart-Verhalten.
- **Log-Leakage real verifiziert**: `docker compose logs backend` nach Dutzenden
  echter Requests (inkl. mehrerer 401/403) auf `testryn_` und `authorization:`
  durchsucht — 0 Treffer.
- **CI-Workflow-Regression (Abschnitt 31)**: Execution → Publisher →
  `automationReference` → Bulk Results → UI, jetzt mit Auth — vollständig
  bestätigt (siehe Workflow F).
- **Jira-Workflow-Regression (Abschnitt 32)**: `mvn test` grün ohne Live-Jira-
  Abhängigkeit; zusätzlich bei dieser Gelegenheit ein echter Live-Verbindungstest
  gegen die reale Jira-Cloud-Instanz erneut erfolgreich (`Connected to Jira`) —
  die Security-Änderungen haben die Jira-Integration nicht beeinträchtigt.
- **OpenAPI (Abschnitt 28)**: `bearerAuth`-Security-Scheme im echten `/v3/api-docs`
  bestätigt (`type: http, scheme: bearer`), global angewendet, kein Beispiel-Token.
- **Copy-Safety (Abschnitt 27)**: Token-Anlage im Frontend geprüft — der neue
  Rohwert erscheint nirgends in `localStorage`, `sessionStorage` enthält nur den
  eigenen, bewusst gesetzten Dev-Token, sonst nichts.
- **Ein echter UI-Bug gefunden und noch im selben Durchlauf behoben**: ein
  abgelaufener (nicht widerrufener) Token wurde in der Service-Tokens-Tabelle
  fälschlich als „Revoked" statt „Expired" angezeigt (`active` allein
  unterscheidet nicht zwischen den beiden Ursachen) — korrigiert, neu gebaut,
  erneut verifiziert.
- **Bekannte Einschränkung dieser Verifikation**: der Revoke-Button im Frontend
  nutzt einen nativen `window.confirm()`-Dialog, den das Browser-Automatisierungs-
  Tool nicht zuverlässig bedienen kann (kein Netzwerk-Request beobachtbar nach
  Klick) — die zugrunde liegende Funktionalität ist unabhängig davon sowohl per
  echtem REST-Aufruf (Workflow D) als auch per Backend-Test
  (`ServiceTokenManagementTest.revokeSetsRevokedAtAndDeactivatesTheToken`)
  vollständig verifiziert; nur die UI-Interaktion selbst blieb hier ungeprüft.

## Jira Live-Verifikation — abgeschlossen

Verifiziert gegen die reale, privat konfigurierte Jira-Cloud-Instanz dieses Projekts,
Zugangsdaten aus dem lokalen PowerShell-SecretStore des Nutzers (nie in Code, Git,
Logs oder dieser Datei). Verwendetes Test-Issue: **EVAL-47** (reguläre Jira Story,
kein Xray-Test-Issue).

Verifizierte Workflows:

- **Connection Test**: `POST /integrations/jira/connection/test` → `success: true`,
  sowohl über die REST-API als auch über die Settings-Seite im Frontend.
- **Live Issue Lookup**: `GET /integrations/jira/issues/EVAL-47` liefert korrekt
  `externalId`, `externalKey=EVAL-47`, `issueType=Story`, `status`, `url`,
  `summary` und eine korrekt von ADF nach Plain Text konvertierte `description`
  (mehrzeilige Akzeptanzkriterien, keine ADF-JSON-Artefakte).
- **Unbekanntes Issue** (`EVAL-999999`): sauberer `404` mit strukturierter
  `ApiError`, kein `500`, Backend blieb stabil.
- **Requirement Link End-to-End** (sowohl über die UI — Preview → Confirm — als
  auch rein über REST, mit einem zweiten, dedizierten Test Case): `externalId`,
  `externalKey`, `summary`, `url`, `provider` korrekt persistiert; bei der
  REST-Variante wurde die Anreicherung ohne vorab übergebene `url`/`summary`
  getestet (nur `provider`+`externalKey`) — funktioniert wie entworfen.
- **UI-Darstellung**: Requirement-Karte zeigt genau die geforderte Struktur (Key,
  Summary, `Story · <Status>`, „Open in Jira"), der Link öffnet tatsächlich
  EVAL-47 in Jira.
- **Duplicate-Schutz**: zweiter Link-Versuch auf denselben Test Case + EVAL-47
  wird mit einer klaren Fehlermeldung abgelehnt (weder UI noch REST erzeugen einen
  zweiten `RequirementLink`).
- **Removal + Restore**: Link entfernt (Jira-Issue selbst nachweislich unverändert,
  erneut per Live-Lookup bestätigt), danach sauber erneut verknüpft — finaler
  Zustand ist sinnvoll (Requirement wieder sichtbar).
- **Jira-Ausfall-Simulation**: Base-URL temporär auf einen unerreichbaren Host
  gesetzt (echte Zugangsdaten dabei unverändert) → Connection Test und Issue-Lookup
  liefern verständliche Fehler (`502`/„Jira is not reachable"), bestehende Test
  Cases, Requirement Links und ihre Anzeige in der UI blieben währenddessen
  vollständig nutzbar. Danach echte Verbindung wiederhergestellt und erneut als
  `success: true` bestätigt.
- **REST-only-Nachweis** (Abschnitt 14, nicht nur UI): Jira Lookup, Requirement
  Preview, Requirement Create, Requirement Read, Requirement Delete je einzeln per
  `curl` gegen die laufende Instanz verifiziert.

**Diagnose-Hinweis (kein Testryn-Bug)**: der erste Connection-Test-Versuch schlug
mit HTTP 401 fehl. Ursache reproduziert durch einen direkten HTTP-Aufruf mit
identischer Basic-Auth-Konstruktion außerhalb von Testryns Code — derselbe 401,
also kein Implementierungsfehler in `JiraIssueClient`. Root Cause: das zunächst
verwendete Atlassian-API-Token war ein neuerer "scoped" Token, der klassische
Basic Auth gegen `<tenant>.atlassian.net` nicht unterstützt. Nach Ersetzen durch
ein klassisches API-Token war die Verbindung sofort erfolgreich. Für zukünftige
Jira-Token-Einrichtung: ein klassisches API-Token verwenden (id.atlassian.com →
Account Settings → Security → API tokens → "Create classic API token"), kein
"API token with scopes".

**Sicherheit während der Live-Verifikation geprüft**: Token erscheint in keiner
API-Response (nur `tokenConfigured: true`/`usable: true`), in keinem
Backend-Log (volle Log-Historie nach Token-Präfix-Mustern und
`Authorization:`-Headern durchsucht, 0 Treffer), und in keiner im Browser
sichtbaren Netzwerk-Response. Keine Secrets in dieser Datei, in Git oder in
Terminal-Ausgaben dieses Blocks.

Die für eine erneute Live-Verifikation benötigten Environment Variables (Namen
bereits exakt wie erwartet) stehen weiterhin in `README.md`/`docker-compose.yml`:
`TESTRYN_JIRA_BASE_URL`, `TESTRYN_JIRA_EMAIL`, `TESTRYN_JIRA_API_TOKEN`.

## Implementierte Features (dieser Block)

**Backend** (`backend/`):

- **Bulk Result Update** (ADR 0010):
  `PATCH /api/v1/executions/{executionId}/results` — mehrere Ergebnisse in einem
  atomaren Request. Jeder Eintrag folgt derselben JSON-Merge-Patch-Semantik wie der
  bestehende Einzel-Endpoint (ADR 0006); Ziel-Result wird über `resultId` und/oder
  `automationReference` bestimmt (müssen bei beidseitiger Angabe übereinstimmen).
  Dreiphasig implementiert (parsen → Referenzen auflösen → Merges validieren, erst
  danach mutieren) — garantiert Alles-oder-nichts unabhängig vom
  `@Transactional`-Rollback und sammelt **alle** Verstöße einer Anfrage, nicht nur
  den ersten. Strukturierte Fehler über das bestehende `ApiError.fieldErrors`
  (keine neue Fehlerstruktur). `durationMs < 0` wird jetzt auch beim
  Einzel-PATCH-Endpoint abgelehnt (dieselbe Merge-Logik, konsistent gemacht).
- **`automationReference`** (ADR 0009): optionales, projektweit eindeutiges,
  maschinenfreundliches Feld auf `TestCase` (Migration
  `0004-test-case-automation-reference.sql`, partieller Unique-Index). Setzen/Ändern
  über den bestehenden `PUT /test-cases/{id}`-Endpoint (keine Versionierung
  ausgelöst — Identitäts-/Metadatenfeld wie `status`/`priority`/`tags`). Exakte,
  case-sensitive Suche über `GET /projects/{key}/test-cases?automationReference=...`.
  Duplikat-Erkennung auf Service- **und** DB-Ebene.
- **Execution-Mapping**: `automationReference` wird im Bulk-Endpoint ausschließlich
  gegen die Test Cases der jeweiligen Execution aufgelöst — nie projektweit, nie mit
  automatischer Test-Case-Anlage. Eine Referenz, die real existiert, aber nicht Teil
  dieser Execution ist, wird korrekt abgelehnt (eigener Testfall dafür).
- OpenAPI-Beschreibungen für den Bulk-Endpoint und den neuen Suchparameter ergänzt,
  gegen die laufende Instanz verifiziert (`/v3/api-docs`).

**Neu: `tools/testryn-publisher`** (ADR 0011) — eigenständiges Maven-Modul, kein
Spring Boot, eine Produktionsabhängigkeit (Jackson):

- CLI `testryn-publisher publish --base-url <url> [--execution-id <id>] --results
  <file|->`, liest eine kleine JSON-Datei oder stdin, sendet sie als einen
  Bulk-Request.
- Publisher-Core (`TestrynApiClient`) getrennt vom Input-Format
  (`ResultBatchReader`, Abschnitt 17) und vom HTTP-Transport (`HttpTransport`) — ein
  späterer JUnit-XML-Reader würde nur Ersteres implementieren.
- `executor` defaultet auf `"ci"`, wenn ein Eintrag keinen eigenen Wert mitbringt.
  `TESTRYN_API_TOKEN` (nur Umgebungsvariable, nie CLI-Argument) wird als Bearer-Token
  mitgeschickt, falls gesetzt — das Backend ignoriert ihn aktuell (siehe REST-API-Auth
  unten), der Publisher ist aber bereits vorbereitet.
- Reale HTTP-Transportschicht nutzt `HttpURLConnection` (nicht
  `java.net.http.HttpClient` — derselbe NIO-Selector-Konflikt wie beim
  Jira-Client, AGENTS.md #6a) mit dem klassischen Reflection-Workaround für PATCH,
  ausgeliefert über einen `Add-Opens`-Manifest-Eintrag im Shaded-Jar — funktioniert
  dadurch mit einem einfachen `java -jar ...`, ohne zusätzliche JVM-Flags. Empirisch
  gegen dieses genaue Problem getestet (siehe ADR 0011).
- Exit-Codes: `0` Erfolg, `1` API-/Transport-Fehler, `2` Usage-Fehler.

**REST-API-Auth für CI (Abschnitt 20)** — geprüft, bewusst zurückgestellt statt
erzwungen (ADR 0011): Die API ist aktuell komplett offen. Ein einfacher, globaler
Service-Token hätte das bestehende (tokenlose) Frontend gebrochen und wirft echte
Scope-Fragen auf (gilt er für alle Endpoints? Bricht er anonymen UI-Zugriff? Wie
verhält er sich zu einer künftigen echten Nutzerverwaltung?) — laut Abschnitt 20
genau die Art Entscheidung, die dokumentiert zurückgestellt statt spontan
mitimplementiert werden soll. Als Next-Backlog-Item vorgemerkt.

**Frontend** (`frontend/`) — minimal, gezielt (Abschnitt 33: keine unnötigen
UI-Änderungen):

- `TestCase.automationReference` im API-Client/Typen ergänzt.
- Test-Case-Detailseite zeigt einen `🤖 <reference>`-Badge neben Status/Priority/
  Version, wenn gesetzt; das bestehende Edit-Formular hat ein neues, optionales Feld
  dafür.

## Sicherheits-/Scope-relevante Entscheidungen dieses Blocks

- Kein JUnit-XML-Parser (Abschnitt 33 explizit ausgeschlossen) — aber die
  `ResultBatchReader`-Schnittstelle im Publisher ist genau die vorbereitete
  Erweiterungsstelle dafür (Abschnitt 17).
- Kein Auth-System (siehe oben) — dokumentierter Backlog-Punkt statt stillem
  Sicherheitsloch.
- Keine Secrets im Repository oder in Logs: `TESTRYN_API_TOKEN` ausschließlich aus
  der Umgebung, nie als CLI-Argument, nie geloggt (Publisher-Code enthält keine
  Log-Ausgabe des Tokens); dieselbe Regel galt bereits für `TESTRYN_JIRA_API_TOKEN`.

## Aktuelles Datenmodell (Ergänzung)

`test_cases` hat jetzt zusätzlich `automation_reference` (nullable, partieller
Unique-Index je `project_id`). Sonst unverändert gegenüber dem vorigen Block.

## Teststatus

Stand: 2026-08-20, verifiziert lokal (Windows, Docker Desktop) sowie via
Docker-Compose-Stack.

- **`cd backend && mvn test`: 67/67 grün** (0 Failures, 0 Errors). Neu in diesem
  Block: `BulkResultUpdateTest` (16 — gültige Updates per resultId/
  automationReference, CREATED→RUNNING, Partial-Semantik über einen manuellen Edit +
  CI-Re-Report hinweg, atomarer Rollback bei einem ungültigen Eintrag, fremdes
  Result aus anderer Execution, unbekanntes Result, doppelte Result-IDs, ungültiger
  Status, negative Duration, übereinstimmende/widersprüchliche resultId+
  automationReference, fehlende Referenz, automationReference außerhalb dieser
  Execution, unbekannte automationReference, leeres results-Array),
  `AutomationReferenceTest` (10 — Setzen, optional, Formatvalidierung, exakte Suche
  inkl. Case-Sensitivität, Duplikat bei Anlage/Update, Selbst-Update ohne
  Selbst-Konflikt, keine Versionserhöhung).
- **`cd tools/testryn-publisher && mvn test`: 35/35 grün** — komplett gegen
  `FakeHttpTransport`/In-Memory-Streams, kein echter Socket im Test (Abschnitt 31):
  JSON-Parsing (7), CLI-Parsing (8), `TestrynApiClient`-Logik inkl. Bearer-Token,
  executor-Default, Fehlerinterpretation (11), `PublisherMain`-Exitcodes/
  Execution-ID-Auflösung (9).
- `cd frontend && npm run build`: fehlerfrei. `npm run test`: 5/5 grün (unverändert
  gegenüber vorigem Block).
- **Browser-Verifikation (Abschnitt 32) gegen `docker compose up --build`, alle
  fünf Workflows durchgespielt** (neues Projekt `CIWF`, 3 Test Cases mit
  `automationReference` `auth.login.valid`/`invalid`/`locked`, Plan, Execution — via
  UI und API angelegt):
  - **Workflow A** (Jira Issue lookup → Requirement Link): blockiert durch fehlende
    Live-Credentials (siehe oben); Settings-Seite zeigt weiterhin korrekt „not
    configured"/„Not usable", „Test connection" liefert die saubere
    Fehlermeldung „Jira connection is not configured or not active".
  - **Workflow B** (Test Case → automationReference → Plan → Execution): verifiziert
    — Badge `🤖 auth.login.valid` auf der Detailseite, Feld im Edit-Formular korrekt
    vorbefüllt, alle drei Test Cases im Plan und in der Execution sichtbar.
  - **Workflow C** (Publisher → 3 Bulk Results → UI zeigt korrekte Ergebnisse):
    verifiziert — **echter Lauf des gebauten `testryn-publisher.jar`** (nicht nur
    Unit-Tests) gegen die laufende Instanz: PASSED/FAILED/SKIPPED, Duration und
    Executor (`ci`) korrekt in der UI sichtbar, Execution automatisch auf RUNNING,
    Fortschritt 100 %, Quicknav-Icons (✓/✗/») korrekt.
  - **Workflow D** (manuelle Result-Daten vorhanden → CI-Partial-Update → Daten
    bleiben erhalten): verifiziert end-to-end über echte UI (Kommentar manuell
    gesetzt) → echter erneuter Publisher-Lauf (nur status/durationMs/executor,
    kein comment) → Kommentar in der UI weiterhin vorhanden, Duration aktualisiert.
  - **Workflow E** (ungültige Bulk-Anfrage → kein Result verändert): verifiziert —
    Publisher-Lauf mit einem gültigen + einem unbekannten Eintrag liefert Exit-Code 1
    und lässt das gültige Ergebnis (`auth.login.locked`) unverändert auf SKIPPED
    stehen, nicht auf das im selben Request fälschlich angeforderte PASSED.
- **Pipeline-Simulation (Abschnitt 23)**: durchgespielt als Teil der obigen
  Browser-Verifikation (Execution erstellen → lokale Results-JSON erzeugen →
  Publisher ausführen → Bulk API → UI öffnen → Ergebnisse/Duration/Executor
  geprüft → vorhandene manuelle Felder blieben erhalten).

## Bekannte Einschränkungen / technische Schulden (Ergänzung)

- Kein Human User Authentication (Login/Sessions) — Service Tokens sind bewusst
  Maschinen-Credentials, keine Personen-Identität; das Frontend nutzt bis dahin die
  Dev-Token-Übergangslösung (siehe oben, Backlog → Next).
- Kein API-Rate-Limiting.
- Kein geführter Secret-Rotation-Workflow für Service Tokens (Create + Revoke
  manuell möglich, kein eigener "Rotate"-Endpoint).
- Der Revoke-Button im Frontend war mit dem Browser-Automatisierungstool dieser
  Session nicht zuverlässig testbar (nativer `confirm()`-Dialog) — Funktionalität
  anderweitig vollständig verifiziert, siehe Browser-Verifikation oben.
- Kein Playwright-/Cypress-/Allure-Importer (JUnit-XML ist jetzt implementiert,
  siehe oben) — Architektur weiterhin vorbereitet (`ResultBatchReader`) für die
  übrigen Formate.
- ~~Kein Execution-State-Guard...~~ **Gelöst im Step-Level-Execution-Results-Block**:
  `ExecutionService.requireWritable` lehnt Result-Writes (Testcase- und Step-Level,
  einzeln und Bulk) in `COMPLETED`/`ABORTED`-Executions jetzt mit `409` ab, live
  bestätigt.
- ~~`RequirementWorkflowTest` schlägt bei gesetzten `TESTRYN_JIRA_*`-Variablen
  fehl...~~ **Gelöst im Step-Level-Execution-Results-Block**: `AbstractIntegrationTest`
  erzwingt jetzt eine „nicht konfiguriert"-Jira-Verbindung für die gesamte
  Integrationstestsuite unabhängig vom echten Environment (Abschnitt 51), verifiziert
  durch einen erneuten Lauf mit echten Zugangsdaten weiterhin gesetzt.
- `automationReference` ist nicht Teil des Test-Case-Anlage-Formulars im Frontend
  (`NewTestCaseForm`), nur im Edit-Formular — bewusst minimal gehalten; ein Test
  Case bekommt seine Automation-Referenz typischerweise erst, wenn die
  Automatisierung selbst existiert, meist nach der manuellen Erstanlage.
- Vereinzelt beobachtete Testflakiness bei sehr schnell aufeinanderfolgenden
  `mvn test`-Läufen in derselben Session (`System.nanoTime() % 100000` als
  Projekt-Key-Generator in einigen älteren Testklassen, seltene Kollision) — im
  Step-Level-Execution-Results-Block real reproduziert (ein Fehlschlag bei einem
  von zwei Volläufen, isoliert immer grün), in den dort neu hinzugekommenen
  Testklassen bereits durch einen zusätzlichen Zähler abgesichert; ein
  repo-weiter Fix (z. B. `UUID`-basierte Testschlüssel überall) bleibt bewusst
  außerhalb des jeweiligen Blocks, siehe BACKLOG.md.
- Visuelle Browser-Kontrolle des Jira-Forge-Panels konnte in keiner Session bisher
  durch Claude selbst durchgeführt werden (kein angemeldeter Atlassian-Browser
  verfügbar) — die vom Panel konsumierten Daten sind jedoch vollständig über die
  Coverage-API live bestätigt; der Nutzer wurde jeweils um einen kurzen eigenen
  Blick gebeten.

## Premium UI, Branding & Visual Design (21.08.2026)

- Eigenständige Testryn-Identität mit Trace-/Checkpoint-Signet, Wordmark, App-Icon und
  SVG-Favicon eingeführt; alle Assets sind repo-nativ und skalierbar.
- Zentrales visuelles System um vollständige Surface-, Text-, Border-, Status-,
  Spacing-, Radius-, Shadow- und Typografie-Tokens erweitert. Systemgesteuerter Dark
  Mode, sichtbare Fokuszustände und `prefers-reduced-motion` sind berücksichtigt.
- App-Shell mit gruppierter, iconbasierter Navigation, klarer aktiver Auswahl,
  Produktunterzeile und responsivem horizontalem Layout für kleinere Fenster neu
  gestaltet.
- Dashboard mit echter Informationshierarchie, datengetriebenem KPI-Raster,
  hochwertigeren Projektkarten und einem gebrandeten Welcome-/Traceability-Visual
  überarbeitet. Keine erfundenen Daten oder funktionslosen Aktionen ergänzt.
- Test-Case-Detail trennt technische ID und Titel deutlich; Testschritte werden als
  fokussierte Action-/Expected-Result-Blöcke statt als generische Tabelle dargestellt.
- Tabellen, Formulare, Buttons, Status-Badges, Requirements, Execution Runner,
  Progress, Modals, Loading- und Empty-States wurden über gemeinsame Styles visuell
  konsolidiert. Alle bestehenden API- und Navigationspfade blieben unverändert.
- Browser-QA des frischen Production-Builds bei 1440×1000 und 800×1000 bestätigt
  Alignment, responsive Navigation, Typografie und fehlerfreies Rendering der App-Shell.
  Datenabhängige Unterseiten waren im isolierten Headless-Profil ohne API-Token nicht
  vollständig erreichbar.
- Verifikation: `frontend/npm run build` erfolgreich; `frontend/npm test` 5/5 grün;
  `backend/mvn test` 107/107 grün.

## Frontend: testmanagement-zentrierte Navigation und Kataloge (24.08.2026)

- `Project` bleibt die fachliche Klammer für Testbibliothek, Pläne, Requirements und
  Executions; `TestPlan` wird nicht fälschlich mit dem Projekt gleichgesetzt.
- Projekte, Test Cases und Test Plans sind nun als eigenständige Hauptbereiche in der
  Navigation erreichbar, statt ausschließlich in Projekt-Tabs verborgen zu sein.
- Projektübergreifender Test-Case-Katalog mit Suche nach ID/Titel/Tag sowie Filtern nach
  Projekt, Status und Priorität ergänzt; Testplan-Katalog mit Suche und Projektfilter.
- Testplan-Detail zum operativen Plan-Builder ausgebaut: Test Cases werden direkt nach
  ID, Titel oder Tag gesucht und einzeln in den Plan übernommen. Fehlende Test Cases
  lassen sich aus diesem Kontext mit bereits geöffnetem Anlageformular erstellen.
- Neue Katalogansichten und Plan-Builder sind responsiv im bestehenden Designsystem
  umgesetzt; keine neuen Abhängigkeiten und keine Änderungen am Domain-/API-Modell.
- Verifikation: `frontend/npm run build` erfolgreich; `frontend/npm test` 7/7 grün.

## Theme-Umschaltung und sichere Lösch-Workflows (24.08.2026)

- Persistenter Frontend-Schalter für `System`, `Light` und `Dark` in der Navigation.
- Einzelnes Löschen von Test Cases, Test Plans und Executions über REST API und UI;
  destructive Aktionen verlangen die Eingabe einer ID, eines Namens oder `DELETE`.
- Einzelnes Test-Case-Löschen erhält historische Execution-/Versions-Snapshots und
  entfernt aktive Plan-/Requirement-Zuordnungen. Testplan-Löschen erhält Executions.
- Projekt-Löschen entfernt nach Eingabe des Projektschlüssels alle fachlichen Daten,
  Execution-Historien, Report-Metadaten und gespeicherten Reportdateien des Projekts.
- Kein globaler Instanz-Reset ergänzt; dieser bleibt eine separate Admin-Funktion.
- Verifikation: Backend `mvn test` 156/156 grün (inkl. Liquibase-Migration), Frontend
  Production-Build erfolgreich und `npm test` 7/7 grün.

## UX-Audit gegen Xray/Testiny und Dashboard-Schnellzugriffe (24.08.2026)

- Projekt-Overview um operative Testplan-Karten mit Testanzahl, Run-Anzahl, letzter
  Ausführung und Ergebniszusammenfassung erweitert.
- Dashboard-KPIs sind echte Schnellzugriffe: Projects, aktive Test Cases, laufende
  Executions sowie Executions mit Passed-/Failed-/Blocked-Ergebnissen öffnen jeweils
  eine bereits gefilterte Liste.
- Globale Execution-Liste um kombinierbare Schnellfilter für Status und Ergebnisse
  ergänzt; aktive Filter und Trefferzahl bleiben sichtbar.
- Projektkarten auf zwei letzte Executions begrenzt und mit Grid, Ellipsis und
  in-flow Footer gegen Text-/Badge-Überlappungen abgesichert.
- Expliziter Light Mode überschreibt nun auch alle zuvor system-dark geerbten
  Tabellen-, Karten-, Formular-, Empty-State- und Hover-Flächen vollständig.
- Offizielle Xray-/Testiny-Muster geprüft: planzentrierter Workflow, schnelle Filter
  und Planfortschritt übernommen. Test Repository/Folders, dynamische Pläne und Test
  Environments bewusst nicht als reine UI-Fassade ergänzt, da dafür Domain-/API-
  Entscheidungen nötig sind.
- Jira-Forge-Panel geprüft und bewusst unverändert gelassen: es folgt bereits dem
  nativen Atlassian Theme und zeigt Status, Version, Execution und Step-Ergebnisse
  platzsparend im Issue-Kontext.
- Verifikation: Frontend Production-Build erfolgreich; `npm test` 7/7 grün.

## Wide-Screen-Dashboard und kompaktes Jira-Panel (24.08.2026)

- App-Inhalt auf großen Monitoren innerhalb der Fläche rechts der Sidebar zentriert;
  responsives Wachstum bis 1920 px statt linksbündiger 1500-px-Insel.
- Dashboard priorisiert nun aktive Testpläne vor Fehlern/Projekten und zeigt je Plan
  Testanzahl, Runs, letzte Execution und Passed-/Failed-Zusammenfassung.
- Projektbereich auf acht Karten begrenzt, mit Suche sowie Filtern `All`, `Needs
  attention` und `Running`; vollständige Projektliste bleibt direkt erreichbar.
- KPI-Raster um `Test plans` ergänzt und für sieben Schnellzugriffe responsiv als
  Auto-Fit-Grid umgesetzt.
- Jira-Panel um Statusfilter und Trefferzahl ergänzt; FAILED/BLOCKED Tests sind
  initial geöffnet. Leere `Input / Data`-Spalte entfernt, normale Schrittzeilen
  dadurch breiter und kompakter.
- Jira-Fehlerdetails aus der engen Result-Zelle in vollbreite Attention-Panels unter
  der Schritttabelle verlagert; Actual Result bleibt sofort sichtbar, lange Details
  bleiben explizit aufklappbar.
- Verifikation: Frontend Production-Build und 7/7 Tests grün; Jira Forge 38/38 Tests
  grün; offizieller Forge-Linter ohne Befund; Wide-Screen-Rendering bei 2560×1200
  geprüft. Docker-Frontend neu gebaut und gestartet.
- Jira-Panel als Forge-App-Version `3.6.0` erfolgreich nach `development` deployt;
  bestehende Jira-Installation auf `ki-meets-testautomation.atlassian.net` ist laut
  Forge CLI `Up-to-date`.

## Versionssicherer Testschritt-Editor im Jira-Panel (24.08.2026)

- Verknüpfte Testryn-Testfälle lassen sich im Jira-Issue direkt bearbeiten: Titel,
  Vorbedingungen sowie Actions und Expected Results; Schritte können hinzugefügt,
  entfernt und nach oben/unten sortiert werden.
- Speichern erzeugt sichtbar eine neue immutable Testfallversion. Historische
  Executions und deren gepinnte Schritte/Ergebnisse bleiben unverändert.
- Neuer enger `PATCH /api/v1/test-cases/{id}/definition`-Endpunkt verändert keine
  Metadaten. Optimistic Concurrency über `expectedVersion` verhindert stille
  Überschreibungen aus veralteten Jira-Tabs (`409 Conflict`).
- Forge akzeptiert Testfall-IDs nicht blind aus dem Browser, sondern prüft vor dem
  Schreiben, dass der Test mit dem aktuellen Issue verknüpft und in dessen Coverage-
  Ergebnis enthalten ist. Das Service Token bleibt ausschließlich im Resolver.
- Architekturentscheidung in ADR 0016 dokumentiert. Jira selbst bleibt read-only und
  Testryn die einzige Source of Truth; der dedizierte Forge-Service-Token benötigt
  für den neuen Pfad künftig `testryn:write`.
- Verifikation: Backend-Service 5/5 und neuer REST-Integrationstest 1/1 grün; Forge
  42/42 Tests grün; offizieller Forge-Linter ohne Befund. Im vollständigen Backend-
  Lauf waren 158/159 grün; ausschließlich die bereits im Backlog dokumentierte
  zufällige `System.nanoTime() % 100000`-Test-Key-Kollision schlug fehl.
- Jira-Editor als Forge-App-Version `3.7.0` erfolgreich nach `development` deployt;
  die Installation auf `ki-meets-testautomation.atlassian.net` ist `Up-to-date`.
  Der bereits verschlüsselt hinterlegte Service Token besitzt laut Betreiber den
  benötigten Write-Scope; keine Token-Rotation war erforderlich.

## Jira-native Projekt- und Testfallerstellung (24.08.2026)

- Jira-Panel kann vorhandene Testryn-Projekte durchsuchen/auswählen oder direkt ein
  neues Projekt mit Key und Name anlegen.
- Neuer Testfall wird mit Titel, Beschreibung, Preconditions, Priorität, Tags und
  sortierbaren Action-/Expected-Result-Schritten im Jira-Kontext erstellt und
  automatisch mit dem aktuellen Issue verknüpft.
- Atomarer provider-neutraler Endpoint `POST /api/v1/requirement-links/test-cases`:
  Testfall und RequirementLink entstehen in einer Transaktion; kein verwaister
  Testfall bei einem Verknüpfungsfehler.
- Issue-Key wird ausschließlich aus dem Forge Invocation Context übernommen; der
  Browser kann keinen fremden Jira-Key einschleusen. Die Requirement-URL wird aus
  der nicht geheimen `JIRA_BASE_URL`-Konfiguration aufgebaut.
- Testbeschreibung ist nun Teil der Coverage-Darstellung, separat aufklappbar und
  gemeinsam mit Preconditions und Steps versionssicher editierbar.
- Verifikation: Backend-Service 5/5, PostgreSQL-REST-Integration 2/2, Forge 46/46;
  offizieller Forge-Linter ohne Befund.
- Jira-Erstellungsworkflow als Forge-App-Version `3.8.0` erfolgreich nach
  `development` deployt; Installation auf `ki-meets-testautomation.atlassian.net`
  ist `Up-to-date`. Aktualisiertes Backend-Image wurde lokal gebaut und gestartet.

## Versionierte Step-Eingabedaten (24.08.2026)

- Optionales Feld `Input / Data` zwischen Action und Expected Result ergänzt;
  geeignet für Testwerte, URLs, Credentials-Hinweise oder sonstige Eingabedaten.
- Feld ist Bestandteil der immutable `TestCaseVersion`; Änderungen erzeugen eine
  neue Version, während historische Executions ihre gepinnten Eingabedaten behalten.
- Additive Liquibase-Migration `0008-test-step-input-data.sql`; bestehende Daten und
  Clients bleiben kompatibel, da das Feld optional/nullable ist.
- REST-DTOs, Coverage-API, Execution-Snapshots und JSON/CSV/Markdown-Exporte führen
  Input/Data vollständig mit.
- Testryn-Frontend und Jira-Panel unterstützen Erstellen, Bearbeiten und Anzeigen
  direkt zwischen Action und Expected Result.
- Verifikation: Backend-Service 5/5, PostgreSQL-Integration 2/2, Frontend Production-
  Build und 7/7 Tests, Forge 46/46 Tests.
- Jira-Erweiterung als Forge-App-Version `3.9.0` erfolgreich nach `development`
  deployt; Installation auf `ki-meets-testautomation.atlassian.net` ist
  `Up-to-date`. Backend und Frontend wurden inklusive Migration neu gebaut und
  gestartet.

## Konfigurierbare Jira-Cloud-Site und Step-Layout-Fix (24.08.2026)

- Settings enthält nun einen editierbaren Jira-Cloud-Integrationsbereich für Name,
  Basis-URL, Atlassian-Mailadresse und Aktiv/Inaktiv sowie Speichern und Verbindungstest.
- Nicht geheime Einstellungen werden über eine API-first-Service-Schicht und
  Liquibase-Migration `0009-jira-connection-configuration.sql` in PostgreSQL
  gespeichert; Änderungen gelten ohne Backend-Neustart.
- Der API-Token bleibt ausschließlich in `TESTRYN_JIRA_API_TOKEN`; weder REST-API
  noch Browser erhalten den Wert. Jira-URLs sind auf HTTPS und `*.atlassian.net`
  beschränkt.
- Die Forge-App bezieht die Jira-Basis-URL beim Erstellen eines Testfalls aus
  Testryn statt aus einer fest deployten kundenspezifischen Variable.
- Testfall-Step-Anzeige auf vier echte Spalten (Nummer, Action, Input/Data,
  Expected Result) korrigiert; unter 900 px wechselt sie in eine lesbare vertikale
  Darstellung.
- Verifikation: vollständiges Backend 162/162, Frontend Production-Build und 7/7,
  Forge 46/46; offizieller Forge-Linter ohne Befund. Migration gegen PostgreSQL
  erfolgreich und Docker-Backend/-Frontend neu gebaut und gestartet.
- Forge-App-Version `3.10.0` erfolgreich nach `development` deployt; Installation
  auf `ki-meets-testautomation.atlassian.net` ist `Up-to-date`.

## Guild-Theme für das eigenständige Frontend (24.08.2026)

- Eigenständige Testryn-Oberfläche auf eine zurückhaltende D&D/RPG-inspirierte
  „Quality Guild“-Designsprache umgestellt; Jira Forge bleibt bewusst Jira-nativ.
- Neues D20-Wappen, Palatino/Georgia-Displaytypografie, goldene Gildenführung,
  arkano-violette Akzente, gravierte Bedienelemente und subtile Kartenraster-Textur.
- Dark Mode nutzt Obsidian/Leder-Flächen; Light Mode eine eigenständige
  Pergamentvariante. Statusfarben und fachliche Begriffe bleiben unverändert und
  eindeutig.
- Navigation, Dashboard-Metriken, Projekte, Testpläne, Tabellen, Formulare,
  Testschritte, Buttons, Tabs und leere Zustände folgen denselben Theme-Tokens.
- Frontend Production-Build und 7/7 Tests grün; beide Modi mit Chrome headless bei
  1920×1080 visuell geprüft. Frontend-Container neu gebaut und gestartet.

## 17 Designs, Entity-Symbole und ehrlicher Jira-Status (24.08.2026)

- Theme-Auswahl auf fünf persistierte Designs erweitert: die Fantasy-Varianten
  `Guild Chronicle`, `Arcane Observatory` und `Dragonforge` sowie die schlichten
  Varianten `Focus` und `Slate`. Jedes Design unterstützt unabhängig System-, Hell-
  und Dunkelmodus; Jira Forge bleibt bewusst im nativen Atlassian-Stil.
- Nach Nutzerfeedback um vier weitere Kollektionen mit je drei Designs ergänzt:
  `Space & Cosmos` (Nebula Command, Lunar Colony, Solar Vanguard), `Animated
  Worlds` (Cel Quest, Neon Shonen, Cozy Studio), `Cyber Realms` (Synthwave Grid,
  Holo Terminal, Mecha Core) und `Natural Worlds` (Emerald Grove, Ocean Depths,
  Desert Dawn). Damit stehen 17 Designs mit jeweils System/Hell/Dunkel bereit.
- `Animated Worlds` enthält echte, rein dekorative Bewegung: driftende Wolken und
  Lichtpunkte in Cel Quest, Speedlines/Energiepulse in Neon Shonen sowie langsam
  schwebende Lichtpartikel in Cozy Studio. `prefers-reduced-motion` schaltet sämtliche
  Bewegung barrierefrei ab.
- Nach Web-/Referenzrecherche Cozy Studio als zurückhaltenden, warmen „supportive
  frame“ mit weichen Karten, Pollen-/Lichtbewegung und geringer visueller Dominanz
  verfeinert. Das kurzzeitig vorhandene Neon-Shonen-Design wurde auf Nutzerwunsch
  vollständig durch `Mythic Overdrive · Animated D&D` ersetzt: rotierende arkane
  Kreise, Partikel-/Runensturm, pulsierende Kartenauren, Zauber-Shimmer, animiertes
  Wappen und kräftige magische Hover-Reaktionen. Cel Quest blieb unverändert.
- Mythic Overdrive anschließend auf Nutzerfeedback visuell beruhigt: die als zu wild
  empfundenen Linien, Beschwörungskreise und der Runensturm wurden entfernt. Ein
  eigenständiges SVG-Drachenmotiv schwebt nun groß im Hintergrund, mit separat
  schlagenden Flügeln, Körperbewegung, Aura und glimmendem Auge. Reduced Motion zeigt
  den Drachen statisch.
- Cozy Studio und Mythic Overdrive nutzen jetzt zusätzlich zwei eigens erzeugte,
  lokal ausgelieferte Cinematic-Backgrounds: regnerisches Apartment mit warmem
  Lampenlicht beziehungsweise ein obsidianfarbenes Drachenreich hinter Burgbögen.
  Nach Nutzerfeedback wurde der künstlich wirkende Cozy-CSS-Regen vollständig
  entfernt. Ein reproduzierbar gerendertes transparentes Animated WebP bewegt
  unregelmäßige Tropfen ausschließlich innerhalb der vier Glasscheiben und lässt
  die beiden gemalten Laternen samt lokalem Licht flackern. Mythic nutzt weiterhin
  fallende Asche und Glut; Light, Dark, System und Reduced Motion bleiben unterstützt.
  Die YouTube-Referenzen werden aus Datenschutz-, Verfügbarkeits- und Lizenzgründen
  nicht direkt eingebettet.
- Projekt, Test Case, Testplan und Execution besitzen konsistente, farblich
  differenzierte Entity-Symbole in zentralen Übersichten, Listen und Detailköpfen;
  ihre SVG-Geometrie entspricht exakt den jeweiligen Symbolen der Sidebar.
- Jira-Settings trennen jetzt den konfigurierten/erreichbaren Jira-Cloud-Standort von
  optionalem direktem REST-API-Enrichment. Eine installierte Forge-App bzw. erreichbare
  Site wird nicht mehr fälschlich als vollständig „nicht konfiguriert“ dargestellt,
  nur weil `TESTRYN_JIRA_API_TOKEN` serverseitig fehlt.
- Der Verbindungstest prüft ohne Credentials die Site-Reichweite über Jira
  `serverInfo`; mit Credentials weiterhin die authentifizierte Identität über
  `myself`. Der API-Token wird unverändert nie persistiert oder an den Browser gegeben.
- Verifikation: Frontend Production-Build und 7/7 Tests; Jira- und Bulk-Regression
  isoliert 26/26; vollständiger Backend-Wiederholungslauf 163/163. Der erste
  Komplettlauf hatte einmalig einen nicht reproduzierbaren Testdaten-409.

## Nächster sinnvoller Schritt

BACKLOG.md → Next: **Human User Authentication** (Login, Sessions) ist der
empfohlene nächste Block. Mit dem Jira Forge Panel existiert jetzt ein echter,
produktiver externer Consumer der API; das Frontend selbst läuft weiterhin auf der
bewusst als Übergangslösung gekennzeichneten Dev-Token-Eingabe (ADR 0012). Step-Level
Execution Results und der Completed/Aborted-Guard schließen die zuvor offenen
fachlichen Lücken der Execution-Domain — die nächste sinnvolle Investition ist jetzt
eine echte Personen-Identität für das Frontend, nicht ein weiterer Report-Importer
oder eine weitere Forge-Panel-Erweiterung (beide bleiben kleinere, unabhängige
Next-Punkte in BACKLOG.md).
