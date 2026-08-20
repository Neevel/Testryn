# ADR 0002: Test-Case-Versionierung

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

Wird ein Test Case nach seiner Verwendung in einer Execution verändert, darf die
historische Execution dadurch nicht rückwirkend einen anderen Inhalt bekommen. Ohne
Versionierung würde "was wurde eigentlich getestet?" bei jeder späteren Änderung des
Test Cases unbeantwortbar.

## Entscheidung

- Ein `TestCase` ist die stabile Identität (technische ID + menschenlesbare
  Projekt-ID, z. B. `BITLESS-TC-42`, Status, Priority, Tags, Referenz auf die aktuelle
  Version).
- Der inhaltliche Zustand (Titel, Beschreibung, Preconditions, Steps mit Expected
  Results) lebt in `TestCaseVersion` — einer unveränderlichen, fortlaufend
  nummerierten Momentaufnahme (`versionNumber`, beginnend bei 1).
- Jede inhaltliche Änderung über `PUT /test-cases/{id}` erzeugt eine **neue**
  `TestCaseVersion` (`versionNumber + 1`) statt die bestehende Version zu mutieren.
  Bereits persistierte Versionen werden nie verändert oder gelöscht.
- Änderungen an nicht-versionierten Attributen (Status, Priority, Tags) erzeugen
  **keine** neue Version — sie sind Metadaten des `TestCase`, nicht Teil des
  inhaltlichen Snapshots.
- Eine Execution referenziert beim Anlegen (Snapshot, siehe ADR 0003) die zu diesem
  Zeitpunkt aktuelle `TestCaseVersion` per Fremdschlüssel. Da Versionen unveränderlich
  sind, bleibt der historische Inhalt einer Execution automatisch stabil, unabhängig
  davon, wie oft der Test Case danach weiterentwickelt wird.

## Konsequenzen

- Jede `PUT`-Änderung eines Test Cases ist potenziell "teuer" (neue Zeilen für Version
  + Steps), aber fachlich korrekt und einfach nachvollziehbar (`GET
  /test-cases/{id}/versions` zeigt die volle Historie).
- Keine komplizierte Diff-/Merge-Logik nötig — jede Version ist ein vollständiger,
  in sich geschlossener Snapshot inkl. ihrer Steps.
- UI und API müssen bei Bedarf explizit zwischen "aktuellem Stand des Test Cases" und
  "Version, die eine bestimmte Execution verwendet hat" unterscheiden.

## Alternativen (verworfen)

- **In-Place-Update ohne Versionierung**: verletzt die Kernanforderung, dass
  historische Executions unveränderlich bleiben müssen.
- **Feldweise Änderungshistorie (Audit-Log/Event-Sourcing)**: für den MVP unnötig
  komplex; ein einfaches Snapshot-Modell erfüllt die fachliche Anforderung ohne
  Event-Store.
