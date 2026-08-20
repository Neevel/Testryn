# ADR 0006: Execution-Result-PATCH-Semantik (JSON Merge Patch)

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

`PATCH /api/v1/executions/{executionId}/results/{resultId}` verhielt sich fachlich
wie ein Voll-Replace: der Request-DTO enthielt alle veränderlichen Felder
(`status`, `comment`, `durationMs`, `executor`, `actualResult`, `failureDetails`),
und jedes Feld, das der Aufrufer wegließ, wurde von Jackson als `null` gebunden und
so auch persistiert. Das führte dazu, dass z. B. eine UI-Aktion, die nur `comment`
ändern wollte, unbeabsichtigt zuvor per CI/CD-Pipeline gesetzte Werte wie
`durationMs` oder `executor` löschte — ein reproduzierbarer Datenverlust, keine nur
kosmetische Unschönheit.

Ein manueller Tester im Runner und eine externe Automatisierungs-Pipeline schreiben
i. d. R. unterschiedliche Teilmengen desselben Ergebnisses (Tester: Status, Kommentar,
Actual Result, Failure Details; Pipeline: Status, Duration, Executor). Beide Wege
müssen sich gegenseitig ergänzen können, ohne sich gegenseitig Daten zu überschreiben.

## Entscheidung

`PATCH .../results/{resultId}` implementiert **JSON Merge Patch (RFC 7396)**-
Semantik:

- Ein im Request-Body **fehlendes** Feld bleibt unverändert.
- Ein Feld mit explizitem JSON-Wert `null` **löscht** den bestehenden Wert (dort,
  wo fachlich sinnvoll — siehe unten).
- Ein Feld mit einem konkreten Wert überschreibt genau dieses Feld.

Technisch: Der Controller nimmt den Request-Body als `com.fasterxml.jackson.databind.JsonNode`
entgegen (kein direkt gebundenes DTO) und übergibt ihn unverändert an
`ExecutionService`. Der Service lädt den aktuellen Zustand des `ExecutionResult` in
ein kleines, rein technisches Zwischenobjekt (`ExecutionResultPatchState`,
Getter/Setter, ein Feld pro veränderlichem Attribut) und mergt den JSON-Patch mittels
`ObjectMapper#readerForUpdating(...)` darüber — Jackson ruft dabei ausschließlich für
im JSON tatsächlich vorhandene Properties den Setter auf, alle anderen Felder
behalten den zuvor aus der Entity geladenen Wert. Das gemergte Ergebnis wird
anschließend als Ganzes an `ExecutionResult#apply(...)` übergeben; die Domain-Methode
selbst bleibt ein einfacher, vollständiger Zustandssetter — die Partial-Update-Logik
lebt bewusst nur im Service, nicht in der Domain.

- `status` darf durch den Merge **nicht** auf `null` gesetzt werden (ein Ergebnis hat
  immer einen Status); ein expliziter `"status": null` im Patch wird als
  `BadRequestException` (400) abgelehnt.
- `comment`, `durationMs`, `executor`, `actualResult`, `failureDetails` sind
  optionale, löschbare Felder — `null` ist ein gültiger, expliziter Zielwert.
- Ungültige JSON-Werte (z. B. unbekannter Status-Enum-Wert, falscher Zahlentyp) oder
  unbekannte Properties werden als `BadRequestException` (400) beantwortet, nicht als
  500.

Für OpenAPI/Dokumentation (Abschnitt „API für KI-Agenten") wird das Request-Schema
weiterhin über eine dokumentierende DTO-Klasse (`ExecutionResultPatchRequest`, alle
Felder optional/nullable, ohne Bean-Validation-Bindung) an springdoc angehängt, damit
Clients ein vollständiges, korrektes Schema sehen, obwohl die tatsächliche Bindung
über `JsonNode` läuft.

## Konsequenzen

- Wiederholte Teil-Updates (Tester setzt Kommentar nach, Pipeline liefert später
  Duration nach) sind jetzt verlustfrei möglich.
- Die Domain-Methode `ExecutionResult#apply(...)` bleibt unverändert einfach; nur der
  Service bekommt zusätzliche (aber klar lokalisierte) Merge-Logik.
- Clients, die explizit einen Wert löschen wollen, müssen das Feld mit `null` im JSON
  mitschicken statt es wegzulassen — dieses Verhalten muss in der API-Doku klar
  beschrieben sein (siehe OpenAPI-Beschreibung am Endpoint).

## Alternativen (verworfen)

- **Separate DTO-Felder als `Optional<T>` binden lassen**: Jackson kann bei
  Standard-Deserialisierung nicht zwischen "Feld fehlt" und "Feld ist explizit
  `null`" unterscheiden (beides ergibt ein leeres `Optional`) — löst das Problem
  nicht ohne zusätzliche Custom-Deserializer, die de facto auf dasselbe
  `JsonNode`-Merge-Prinzip hinauslaufen, nur unübersichtlicher verteilt.
- **Zusätzliche Library für JSON-Patch/JSON-Merge-Patch** (z. B.
  `zjsonpatch`, `json-patch`): Für einen einzigen Endpoint mit sechs Feldern ist der
  eingebaute `readerForUpdating`-Mechanismus von Jackson (bereits Kernabhängigkeit)
  ausreichend — eine zusätzliche Bibliothek wäre unnötige Abstraktion für diesen
  Anwendungsfall.
- **Getrennte Endpoints pro Feld** (z. B. `PATCH .../comment`): unnötig granular für
  sechs zusammengehörige Felder eines einzelnen Ergebnisses, erschwert atomare
  Updates mehrerer Felder in einem Request.
