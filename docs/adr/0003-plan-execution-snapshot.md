# ADR 0003: Test-Plan/Execution-Snapshot-Modell

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

Ein Test Plan ist eine wiederverwendbare, veränderliche Definition ("welche Test Cases
gehören zur Regression Login & Account"). Wird ein Plan mehrfach ausgeführt, dürfen
ältere Ausführungen nicht überschrieben werden, und spätere Änderungen am Plan (Test
Case hinzugefügt/entfernt) dürfen bereits erzeugte Executions nicht rückwirkend
verändern.

## Entscheidung

- `TestPlan` bleibt eine reine, veränderliche Definition: Name, Beschreibung, Projekt,
  aktuell zugeordnete Test Cases (`TestPlanEntry`).
- Iterationen werden **nicht** als eigenes Entity neben Executions modelliert, sondern
  direkt über `Execution` abgebildet (fachlich gleichwertig, technisch einfacher):
  jede Execution trägt eine optionale Referenz auf ihren `TestPlan` sowie eine je Plan
  fortlaufende `iterationNumber` (1, 2, 3, …).
- Beim Erzeugen einer Execution aus einem Plan (`POST
  /test-plans/{id}/executions`) wird ein **Snapshot** erstellt: für jeden zu diesem
  Zeitpunkt im Plan enthaltenen Test Case wird eine `ExecutionTestCase`-Zeile
  angelegt, die fest auf die aktuelle `TestCaseVersion` dieses Test Cases zeigt.
- Ab diesem Moment ist die Execution vollständig unabhängig vom weiteren Schicksal des
  Plans und der referenzierten Test Cases: Löschen/Ändern von Plan-Einträgen oder
  neue Test-Case-Versionen wirken sich nicht auf bereits erzeugte Executions aus.
- Eine Execution kann alternativ auch ohne `TestPlan` (ad-hoc, mit direkt ausgewählten
  Test Cases) erzeugt werden — der Snapshot-Mechanismus ist identisch.

## Konsequenzen

- `ExecutionTestCase` ist die "Wahrheit" darüber, was in einer konkreten Execution
  getestet wurde und in welcher Version — nicht der aktuelle Plan-Zustand.
- Pro `ExecutionTestCase` existiert genau ein `ExecutionResult` (1:1), das den
  Ausführungsstatus trägt. Das hält Snapshot (was wurde getestet) und Ergebnis (wie
  ist es ausgegangen) sauber getrennt.
- Reporting über "wie oft wurde Plan X ausgeführt" ist eine einfache Aggregation über
  `Execution.testPlan` + `iterationNumber`, ohne eigene Iterations-Tabelle.

## Alternativen (verworfen)

- **Eigenes `PlanIteration`-Entity zwischen Plan und Execution**: fachlich
  gleichwertig zur gewählten Lösung, aber ein zusätzliches Konzept ohne echten
  Mehrwert im MVP (Abschnitt 4.7 des Produktauftrags erlaubt diese Vereinfachung
  explizit). Kann bei Bedarf später ergänzt werden, ohne Bestandsdaten umzumodellieren
  (eine `PlanIteration` wäre dann eine dünne Schicht über vorhandenen Executions).
