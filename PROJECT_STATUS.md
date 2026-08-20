# PROJECT_STATUS.md

> Momentaufnahme des aktuellen technischen Stands. Keine Historie — siehe Git-Log für
> Verlauf. Wird bei jedem abgeschlossenen, relevanten Task aktualisiert.

Stand: 2026-08-21

## Aktueller Meilenstein

**Jira Cloud integration live-verified** (2026-08-21). Die bereits implementierte
Jira-Integration (Verbindungskonfiguration, Issue-Lookup, ADF-Parsing,
Fehlerbehandlung, Requirement-Link-Workflow) wurde gegen eine echte, private
Jira-Cloud-Instanz end-to-end verifiziert — nicht nur gegen Unit-/Integrationstests
ohne Jira-Abhängigkeit. Reiner Verification-Block, keine neuen Features, keine
Refactorings (kein Live-Bug gefunden, der einen Codefix erzwungen hätte — der
initiale Connection-Test-Fehlschlag war ein ungültiges Token, kein Testryn-Bug,
siehe unten).

Der vorherige Block („Next Product Block": Bulk-Result-Update, `automationReference`,
CI-Publisher) ist unverändert gültig — siehe Git-Log für Details.

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

- Kein Auth-/Service-Token-Mechanismus (siehe oben, Backlog → Next).
- Kein JUnit-XML-/Playwright-/Cypress-/Allure-Importer (bewusst außerhalb dieses
  Blocks, Abschnitt 33) — Architektur dafür vorbereitet (`ResultBatchReader`).
- `automationReference` ist nicht Teil des Test-Case-Anlage-Formulars im Frontend
  (`NewTestCaseForm`), nur im Edit-Formular — bewusst minimal gehalten (Abschnitt
  33); ein Test Case bekommt seine Automation-Referenz typischerweise erst, wenn die
  Automatisierung selbst existiert, meist nach der manuellen Erstanlage.

## Nächster sinnvoller Schritt

BACKLOG.md → Next priorisieren: API-/Service-Authentication ist der logische
nächste Schritt, jetzt wo sowohl der CI-Workflow als auch die Jira-Integration
end-to-end gegen echte Systeme verifiziert sind, die API aber weiterhin komplett
offen im Netzwerk steht.
