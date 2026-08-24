# PROJECT_STATUS.md

> Momentaufnahme des aktuellen technischen Stands. Keine Historie — siehe Git-Log für
> Verlauf. Wird bei jedem abgeschlossenen, relevanten Task aktualisiert.

Stand: 2026-08-24

## Aktueller Meilenstein

**Jira Forge Issue Panel: implementiert, gegen den echten Stack unit- und
integrationsgetestet, Backend-Erreichbarkeit live per Tunnel verifiziert —
`forge deploy`/`forge install` in die reale Jira-Site bewusst dem Nutzer selbst
überlassen (siehe „Jira Forge Issue Panel" unten für den genauen Verifikationsstand
und was noch offen ist).** Ein neues, provider-neutrales Read-API
(`GET /api/v1/requirement-links/coverage`) plus eine eigenständige Forge-App
(`integrations/jira-forge`, UI Kit, kein iframe) zeigen verlinkte Testryn-Test-Cases
samt Steps, Expected Results und letztem Execution-Status direkt in der
Jira-Story/Task/Bug/Epic-Ansicht — read-only, Testryn bleibt Source of Truth, Jira
speichert keine Kopie der Testdaten (ADR 0014).

Die vorherigen Blöcke (JUnit XML Publisher Adapter; API & Service Security; Jira
Cloud live-verifiziert; Bulk-Result-Update, `automationReference`, CI-Publisher)
sind unverändert gültig — siehe Git-Log und die Abschnitte weiter unten in dieser
Datei für Details.

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
- **Damit noch offen**: die eigentliche Live-Verifikation gegen EVAL-47 in einer
  echten Jira-Story (Abschnitt 39/40), der Empty-State an einem unverlinkten Issue
  (Abschnitt 41), die simulierte Testryn-Downtime im echten Panel (Abschnitt 42) und
  die Browser-seitige Security-Prüfung „Token nie im UI-Payload sichtbar"
  (Abschnitt 43, Teil „Browser") — all das erfordert die tatsächliche
  Forge-Installation, die der Nutzer nach dieser Session selbst durchführt. Die
  Token-Handling-Garantien selbst (nie im Resolver-Ergebnis, nie geloggt) sind
  bereits vollständig durch `test/testrynClient.test.js` abgedeckt und geben hohe
  Zuversicht, dass die Live-Prüfung dieselben Garantien bestätigen wird.

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
- Kein Execution-State-Guard, der das Schreiben von Results in eine
  `COMPLETED`/`ABORTED`-Execution verhindert — geprüft, existiert aktuell weder für
  den Bulk- noch den Einzel-Endpoint; außerhalb des Scopes dieses Blocks
  („nicht Client-seitig Domainregeln duplizieren, die serverseitig noch gar nicht
  existieren"), als Next-Punkt vorgemerkt.
- `RequirementWorkflowTest` schlägt in dieser lokalen Entwicklungsumgebung mit 3
  Fehlern fehl, wenn `TESTRYN_JIRA_*`-Umgebungsvariablen mit echten Zugangsdaten
  gesetzt sind (die Tests nehmen „Jira nicht konfiguriert" an) — reproduziert
  isoliert mit und ohne diese Variablen, bestätigt umgebungsbedingt, keine
  Regression dieses Blocks, kein Fix hier vorgenommen.
- `automationReference` ist nicht Teil des Test-Case-Anlage-Formulars im Frontend
  (`NewTestCaseForm`), nur im Edit-Formular — bewusst minimal gehalten; ein Test
  Case bekommt seine Automation-Referenz typischerweise erst, wenn die
  Automatisierung selbst existiert, meist nach der manuellen Erstanlage.
- Vereinzelt beobachtete Testflakiness bei sehr schnell aufeinanderfolgenden
  `mvn test`-Läufen in derselben Session (`System.nanoTime() % 100000` als
  Projekt-Key-Generator in einigen älteren Testklassen, seltene Kollision) — nicht
  sicherheitsrelevant, kein Codefix in diesem Block, bei Bedarf später auf
  `UUID`-basierte Testschlüssel umstellen.

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

## Nächster sinnvoller Schritt

BACKLOG.md → Next: **Jira Forge App / Issue Panel** ist der empfohlene nächste
fachliche Block — die REST-API liefert bereits alles Nötige (Requirement-Links,
Execution-Status/-Progress), der CI-Kreislauf ist jetzt mit JSON- **und**
JUnit-XML-Publish sowie Authentifizierung produktionsnah geschlossen. Weitere
Report-Importer (Playwright, Cypress, Allure, NUnit, pytest) bleiben spätere,
kleinere Erweiterungen derselben `ResultBatchReader`-Schnittstelle. Human User
Authentication bleibt der nächste *Security*-Block, sobald ein konkreter Bedarf
für Personen- statt Maschinen-Identität entsteht.
