# ADR 0016: Testfalldefinitionen im Jira-Panel bearbeiten

- Status: Angenommen
- Datum: 2026-08-24

## Kontext

Das read-only Jira-Panel aus ADR 0014 zeigt verknüpfte Testfälle und historische
Ergebnisse. Für einen Xray-ähnlichen Arbeitsfluss sollen Nutzer die aktuelle
Testfalldefinition direkt im Kontext der Story bearbeiten können, ohne Jira zum
Speicher für Testryn-Daten zu machen.

## Entscheidung

- Testryn bleibt die einzige Source of Truth. Forge schreibt ausschließlich über
  die Testryn-REST-API; Jira speichert keine Kopie der Schritte.
- `PATCH /api/v1/test-cases/{id}/definition` ist ein enger, provider-neutraler
  Schreibpfad für Titel, Vorbedingungen und die geordnete Schrittliste. Status,
  Priorität, Tags, Automation Reference und Requirements sind nicht Teil dieses
  Requests und können daher nicht versehentlich überschrieben werden.
- Der Request enthält `expectedVersion`. Stimmt sie nicht mit der aktuellen Version
  überein, antwortet Testryn mit `409 Conflict`. Ein alter Jira-Tab kann neuere
  Änderungen damit nicht still überschreiben.
- Jede inhaltliche Änderung erzeugt nach ADR 0002 eine neue immutable
  `TestCaseVersion`. Bestehende Executions behalten ihre gepinnte Version und ihre
  Ergebnisse unverändert.
- Der Forge-Resolver liest den aktuellen Issue-Key ausschließlich aus dem
  vertrauenswürdigen Invocation Context. Vor dem PATCH prüft er über die bestehende
  Coverage-API, dass die vom Browser übergebene Testfall-ID tatsächlich mit diesem
  Issue verknüpft und in der angezeigten Ergebnismenge enthalten ist.
- Der Forge-Service-Token benötigt nun `testryn:write` (impliziert read). Jira-REST-
  Scopes bleiben leer, da weiterhin keine Jira-API aufgerufen oder verändert wird.

## Konsequenzen

Der Nutzer kann Schritte im Jira-Panel hinzufügen, löschen, bearbeiten und sortieren.
Die zusätzliche Berechtigung betrifft nur den serverseitigen Forge-Resolver; das
Token erreicht nie den Browser. Bis zur späteren Human-Authentication gilt weiterhin
das MVP-Vertrauensmodell aus ADR 0012: Jira-Nutzer mit Zugriff auf das Panel handeln
über die Identität des dedizierten Forge-Service-Tokens.

## Erweiterung: Erstellung aus dem Jira-Kontext

Das Panel kann Projekte auflisten, ein neues Projekt anlegen und einen Testfall direkt
mit dem aktuellen Issue verknüpfen. `POST /api/v1/requirement-links/test-cases`
erstellt Testfall und RequirementLink in einer gemeinsamen Transaktion; schlägt die
Verknüpfung fehl, bleibt kein verwaister Testfall zurück. Die Issue-ID wird weiterhin
nur aus dem Forge Invocation Context übernommen. Beschreibung und Preconditions sind
Bestandteil der versionierten Testfalldefinition und können im Panel angezeigt sowie
bearbeitet werden.

Testschritte besitzen zusätzlich ein optionales, versioniertes `inputData`-Feld
zwischen Action und Expected Result. Es ist Teil derselben immutable
`TestCaseVersion`; alte Clients dürfen es weglassen und bestehende Schritte werden
durch die additive Migration mit `null` weitergeführt.

## Erweiterung: Vorhandenen Testfall aus dem Jira-Kontext verknüpfen

Neben dem Anlegen eines neuen Testfalls kann das Panel einen **bereits
existierenden** Testryn-Testfall mit dem aktuellen Issue verknüpfen: Projekt
wählen, Testfälle nach Human-ID/Titel durchsuchen (der bestehende paginierte
`GET /api/v1/projects/{key}/test-cases`-Such-Endpoint, unverändert
wiederverwendet, auf eine Seite je Forge-Request begrenzt), einen Treffer
verknüpfen.

Bewusst **keine neue API**: der Schreibpfad ist der bestehende, provider-neutrale
`POST /api/v1/test-cases/{testCaseId}/requirements` (Duplikatschutz über `409`,
Best-Effort-Enrichment über den `RequirementProvider`). Er passt fachlich exakt --
ein zusätzlicher `/requirement-links`-Sibling wäre reine Dopplung gewesen.

Dasselbe Vertrauensmodell wie beim Erstellen: der Browser liefert ausschließlich
die Testfall-ID. `externalKey` stammt aus dem Forge Invocation Context, die
`url` (`<jira-base>/browse/<KEY>`) wird aus Testryns persistierten
Integrationseinstellungen gebaut (`GET /api/v1/integrations/jira/connection`), nie
aus einem Browser-Wert. Der Forge-Service-Token nutzt seinen schon vorhandenen
`testryn:write`-Scope; keine Scope-Änderung, keine Jira-REST-Scopes. Bereits
verknüpfte Testfälle zeigt das Panel als „Linked" und bietet sie nicht erneut an --
die eigentliche Durchsetzung bleibt der `409` aus Testryn.

## Erweiterung: Execution aus dem Jira-Panel starten

Das Panel kann für die zu einem Issue verknüpften Testfälle eine neue Testryn-
Execution starten. Bewusst **keine neue Backend-API**: der bestehende, provider-
neutrale Ad-hoc-Endpoint `POST /api/v1/projects/{projectKey}/executions` deckt den
Fall fachlich vollständig ab -- er erzeugt bereits einen unveränderlichen Snapshot,
der pro Testfall auf dessen aktuelle `TestCaseVersion` gepinnt ist (ADR 0003). Ein
Forge-Sonderweg, der Snapshot- oder Versionierungsregeln umginge, entsteht damit
nicht.

Einzige Backend-Änderung: `CoverageTestCaseResponse` trägt zusätzlich `projectKey`
(additiv, provider-neutral, kein Domain-/Migrationsschritt -- `project` wird vom
Entity-Graph der Coverage-Abfrage ohnehin geladen). Der Ad-hoc-Endpoint ist
projekt-scoped; ohne den Key wüsste der Resolver nicht, an welches Projekt er die
Anfrage richten muss, und müsste pro Testfall einen zusätzlichen Request stellen
(Verstoß gegen Abschnitt 22).

Vertrauensmodell wie bei `updateTestCaseDefinition`: der Browser übergibt nur
Testfall-IDs. Der Resolver liest den Issue-Key aus dem Invocation Context, ruft
die Coverage dieses Issues erneut ab und lehnt jede nicht enthaltene ID ab, bevor
etwas erzeugt wird. Das Projekt wird aus dieser verifizierten Coverage abgeleitet,
nie aus einem Browser-Wert; eine Auswahl über mehr als ein Projekt wird abgelehnt
(der Ad-hoc-Endpoint ist einprojektig). Jira bleibt read-only, der Service-Token
bleibt im Resolver, sein vorhandener `testryn:write`-Scope genügt. Nach Erfolg
bietet das Panel einen Link auf `<app-base>/executions/<id>` an. Mehrfachklicks
verhindert der Button über einen In-Flight-Guard.
