# PROJECT_STATUS.md

> Momentaufnahme des aktuellen technischen Stands. Keine Historie — siehe Git-Log für
> Verlauf. Wird bei jedem abgeschlossenen, relevanten Task aktualisiert.

Stand: 2026-08-20

## Aktueller Meilenstein

Der Produktauftrag „Testryn – Next Product Block" (CI-/Automations-Workflow) ist
gemäß der vorgegebenen Priorität (Abschnitt 38) umgesetzt: Bulk-Result-Update,
`automationReference`, Execution-Mapping, CI-Publisher, Dokumentation und
Browser-Verifikation sind fertig, end-to-end (inkl. echtem CLI-Lauf gegen die
laufende Docker-Stack) verifiziert und committet. Der vollständige Workflow „Jira
Requirement → Test Case → Automation Mapping → Execution → CI → Bulk Results →
Testryn" funktioniert jetzt technisch, ohne Browser-Automation und ohne
Framework-Kopplung im Core-Domain-Modell.

**Live-Jira-Verifikation (Abschnitt 3) ist NICHT durchgeführt** — kein Blocker für
den Rest des Blocks, aber ein offener Punkt: siehe „Jira Live-Verifikation" unten.

Der vorherige Block („Product Expansion Block": Result-PATCH-Fix, Jira-Integration,
Requirement-Workflow, API-Härtung, Frontend-Redesign, Dashboard) ist unverändert
gültig — siehe Git-Log für Details, hier nur noch das, was sich in diesem Block
geändert hat.

## Jira Live-Verifikation (Abschnitt 3) — dokumentierter Blocker

Diese Entwicklungsumgebung hat keinen Zugriff auf einen Secret Store und keine
`TESTRYN_JIRA_*`-Umgebungsvariablen gesetzt (`.env` existiert nicht, `env | grep
JIRA` liefert nichts). Die Jira-Integration selbst (Verbindungskonfiguration,
Issue-Lookup, ADF-Parsing, Fehlerbehandlung) ist vollständig implementiert und
unit-/integrationsgetestet **ohne echte Jira-Abhängigkeit** (siehe voriger Block).
Was in diesem Block fehlt, ist ausschließlich die Verifikation gegen eine *echte*
Jira-Cloud-Instanz.

**Um sie nachzuholen**, folgende Environment Variables vor `docker compose up
--build` bzw. vor `mvn spring-boot:run` setzen (Namen bereits exakt wie in Abschnitt 3
gefordert, siehe auch `README.md` und `docker-compose.yml`):

```bash
TESTRYN_JIRA_BASE_URL=https://<tenant>.atlassian.net
TESTRYN_JIRA_EMAIL=<email>
TESTRYN_JIRA_API_TOKEN=<api-token>
```

Danach in der UI: Settings → Jira Connection → „Test connection" muss `SUCCESS`
zeigen; anschließend ein echter Requirement-Link-Workflow mit einem existierenden
und einem nicht-existierenden Issue-Key durchspielen (Preview-Karte bzw. sauberer
404-Fehler). Der komplette Verifikations-Workflow ist in Abschnitt 3/21 beschrieben
und mit den vorhandenen Frontend-Bausteinen (Settings-Seite, Link-Requirement-Form
mit Preview) bereits vollständig UI-unterstützt — es fehlen nur die Zugangsdaten.

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

- Jira Live-Verifikation aussteht (siehe oben) — reine Frage fehlender lokaler
  Zugangsdaten, keine bekannte Implementierungslücke.
- Kein Auth-/Service-Token-Mechanismus (siehe oben, Backlog → Next).
- Kein JUnit-XML-/Playwright-/Cypress-/Allure-Importer (bewusst außerhalb dieses
  Blocks, Abschnitt 33) — Architektur dafür vorbereitet (`ResultBatchReader`).
- `automationReference` ist nicht Teil des Test-Case-Anlage-Formulars im Frontend
  (`NewTestCaseForm`), nur im Edit-Formular — bewusst minimal gehalten (Abschnitt
  33); ein Test Case bekommt seine Automation-Referenz typischerweise erst, wenn die
  Automatisierung selbst existiert, meist nach der manuellen Erstanlage.

## Nächster sinnvoller Schritt

1. Live-Jira-Verifikation nachholen, sobald Zugangsdaten verfügbar sind (siehe oben)
   — reine Verifikation, keine Implementierung nötig.
2. BACKLOG.md → Next priorisieren: API-/Service-Authentication ist der logische
   nächste Schritt, jetzt wo der CI-Workflow productionsnah funktioniert, aber noch
   offen im Netzwerk steht.
