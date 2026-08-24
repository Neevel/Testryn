# ADR 0015: Step-Level Execution Results

- Status: Angenommen
- Datum: 2026-08-24

## Kontext

Bisher konnte eine Execution nur auf Testcase-Ebene ein Ergebnis tragen (`PASSED`/
`FAILED`/...). Für einen Reviewer in Jira (oder im Testryn Runner selbst) reicht das
nicht: "dieser Test ist rot" beantwortet nicht "welcher Schritt genau, was wurde
erwartet, was kam tatsächlich heraus". Ziel dieses Blocks: Ausführung bis auf
Step-Ebene nachvollziehbar machen -- im Runner UND im Jira Forge Panel -- ohne das
bestehende Snapshot-Modell (ADR 0003) zu verlassen.

## Entscheidung 1: Kein separates `ExecutionStepSnapshot` -- direkte Referenz auf `TestStep`

Die naheliegende Lesart von "eine Execution darf durch spätere Testcase-Änderungen
nicht verfälscht werden" ist eine eigene Kopie-Tabelle (`ExecutionStepSnapshot` mit
eigenem `action`/`expectedResult`). Analyse der bestehenden Domain (Abschnitt 2)
zeigt: das ist unnötig. `TestStep`-Zeilen sind bereits unveränderlich, sobald sie
persistiert sind (siehe deren eigenes Javadoc) -- es gibt keinen Code-Pfad, der eine
bestehende `TestStep`-Zeile ändert, nur `TestCaseVersion.create()`, der bei jeder
inhaltlichen Änderung eine komplett neue Version mit komplett neuen `TestStep`-Zeilen
anlegt (ADR 0002). Eine `ExecutionTestCase` pinnt bereits genau eine
`TestCaseVersion` (ADR 0003) -- und damit implizit genau deren `TestStep`-Menge, für
immer.

Entscheidung: `ExecutionStepResult` referenziert `TestStep` direkt (`step_id`-FK),
statt dessen Inhalt zu kopieren.

```
(gewählt)                                    (verworfen)
ExecutionStepResult                          ExecutionStepResult
   |  step_id (FK)                              |  action, expectedResult (Kopie)
   v                                             v
TestStep (unveränderlich, versions-gebunden)  eigene Snapshot-Tabelle
```

Verifiziert durch die in Abschnitt 43 geforderte Pflicht-Regression
(`ExecutionSnapshotRegressionTest`): Testcase v1 mit Step A, Execution #1 erstellt,
Testcase auf v2 geändert (Step A verändert, Step B neu) -- Execution #1 zeigt
weiterhin ausschließlich das ursprüngliche Step A, mit seinem ursprünglichen
Ergebnis.

## Entscheidung 2: Dasselbe Status-Enum wie auf Testcase-Ebene

`ExecutionStepResult.status` nutzt exakt `ExecutionResultStatus`
(`NOT_RUN`/`PASSED`/`FAILED`/`BLOCKED`/`SKIPPED`) -- kein zweites, paralleles
Status-Modell (Abschnitt 4). Konsequenz: dieselbe Auflösungslogik, dieselben
UI-Farben/Icons, keine Übersetzungsschicht zwischen den beiden Ebenen nötig.

## Entscheidung 3: Aggregations-Regel wird nur durch Step-Writes ausgelöst

Zwei fachlich unabhängige Schreibpfade zum Testcase-Level-Status (siehe
`docs/execution-model.md` für die vollständige Regel):

- Direkter Write (bestehender `results`-Endpoint, CI-Publisher, JUnit-Import,
  manuelles "ganzer Testfall PASSED") -- setzt exakt das Gesendete, rührt Step
  Results nie an.
- Abgeleitet aus Steps -- ausschließlich als Seiteneffekt eines Step-Level-Writes,
  über `ExecutionResult.deriveStatus(...)`, die bewusst NUR `status`/`executedAt`
  anfasst (nicht `comment`/`durationMs`/`executor`/`actualResult`/`failureDetails`).

Diese Trennung löst Abschnitt 9 ("dürfen nicht widersprüchlich auseinanderlaufen")
und Abschnitt 20/36 (JUnit bleibt Testcase-Level-only) gleichzeitig, ohne eine
dritte Konzept-Schicht einzuführen: der bestehende Testcase-Level-Pfad bleibt
komplett unverändert, die neue Step-Ebene fügt nur einen zusätzlichen,
klar abgegrenzten Ableitungs-Trigger hinzu.

## Entscheidung 4: Eager Initialisierung, keine Lazy-Auto-Creation

Beim Erzeugen einer Execution wird für jeden Step jeder Testcase-Version sofort ein
`ExecutionStepResult` mit `NOT_RUN` angelegt (Abschnitt 8). Alternative (Lazy: erst
beim ersten Klick anlegen) wurde verworfen -- sie hätte "wie viele Steps hat diese
Execution, wie viele liefen bereits" zu einer bedingten Frage gemacht (abhängig
davon, welche Steps ein Tester überhaupt schon berührt hat), statt einer einfachen
Zählung. Die Mehrkosten (N zusätzliche Inserts beim Execution-Anlegen, N klein pro
Testcase) sind vernachlässigbar.

## Entscheidung 5: Bulk-Step-Endpoint adressiert nur über `stepResultId`

Der bestehende Testcase-Level-Bulk-Endpoint identifiziert Einträge über `resultId`
**oder** `automationReference` (zwei Wege, weil automatisierte Aufrufer die interne
UUID oft nicht kennen). Für Step Results gibt es dieses Bedürfnis nicht -- jeder
Aufrufer hat die `stepResultId` bereits aus einem vorherigen `GET` der Execution.
Der neue Bulk-Endpoint (`PATCH .../step-results`) adressiert deshalb bewusst nur
über `stepResultId`, nicht über eine zusätzliche `executionTestCaseId` +
`stepReference`-Komposition (Abschnitt 19s Beispiel) -- ein Weg für eine Sache,
keine unnötige Deep-REST-Struktur (Abschnitt 17).

## Entscheidung 6: COMPLETED/ABORTED-Guard jetzt mitgelöst (Abschnitt 50)

Der seit Block 4 offene Backlog-Punkt ("Result-Writes in abgeschlossene Executions
noch nicht verhindert") wird in diesem Block gelöst, weil die Regel eindeutig war
und die Änderung klein blieb: ein einziger `requireWritable(execution)`-Guard in
`ExecutionService`, angewendet auf beide Testcase-Level-Pfade UND beide neuen
Step-Level-Pfade. `CREATED`/`RUNNING` erlauben Writes, `COMPLETED`/`ABORTED` lehnen
mit `409 Conflict` ab. Der CI-Publisher braucht keine eigene Anpassung -- er ruft
denselben Bulk-Testcase-Endpoint auf, der jetzt bereits geschützt ist.

## Konsequenzen

- Jira-Panel und Runner können echte Step-für-Step-Transparenz zeigen, ohne
  Testdaten zu duplizieren.
- Migration `0006-execution-step-results.sql` ist rein additiv -- bestehende
  Executions bleiben lesbar, ohne dass historische Step-Daten erfunden werden
  müssten (es gibt keine).
- `docs/execution-model.md` dokumentiert das vollständige Modell inkl. der
  Aggregationsregel als eigenständige Referenz.

## Alternativen (verworfen)

- **Separates `ExecutionStepSnapshot`** mit eigener Content-Kopie: siehe
  Entscheidung 1 -- unnötige Duplikation bereits unveränderlicher Daten.
- **Ein einziges, größeres Status-Enum mit Zusatzwerten für Steps**: verworfen
  zugunsten der Wiederverwendung von `ExecutionResultStatus` (Abschnitt 4).
  Kein Unknown/Skipped-Sonderstatus für JUnit-Steps nötig -- `NOT_RUN` ist bereits
  fachlich korrekt ("dieser Step wurde nicht berichtet").
- **Automatisches Step-Mapping aus JUnit-Testmethoden**: explizit ausgeschlossen
  (Abschnitt 20/54) -- JUnit-Testmethoden haben keine inhärente Beziehung zu
  Testryn-Steps, ein künstliches Mapping würde falsche Präzision vortäuschen.
- **`executionTestCaseId` + `stepReference` als Bulk-Adressierung**: siehe
  Entscheidung 5.
