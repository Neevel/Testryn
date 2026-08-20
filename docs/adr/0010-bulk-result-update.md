# ADR 0010: Atomarer Bulk-Endpoint für Execution Results

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

Ein CI-Publisher, der z. B. 40 Testergebnisse einer Pipeline nach Testryn meldet,
soll das nicht als 40 einzelne `PATCH .../results/{resultId}`-Aufrufe tun müssen
(Netzwerk-Overhead, kein Alles-oder-nichts-Verhalten bei einem Teilfehler,
Abschnitt 4-7).

## Entscheidung

- **Ein Endpoint**: `PATCH /api/v1/executions/{executionId}/results` (Plural, kein
  `resultId` im Pfad — das Ziel jedes Eintrags steht im Body). Kein separates
  `/bulk`-Suffix; `PATCH` auf die Kollektion ist konsistent mit dem bestehenden
  `PATCH` auf ein einzelnes Result.
- **Dieselbe Partial-Patch-Semantik pro Eintrag** wie beim Einzel-Endpoint (JSON
  Merge Patch, RFC 7396, ADR 0006): ein im Eintrag fehlendes Feld bleibt unverändert,
  ein explizites JSON `null` löscht es (außer `status`), ein gesetzter Wert
  überschreibt. Technisch: derselbe `mergePatch`-Mechanismus wird wiederverwendet,
  nicht dupliziert.
- **Zielauflösung**: jeder Eintrag trägt optional `resultId` und/oder
  `automationReference` (ADR 0009). Mindestens eines ist Pflicht. Sind beide gesetzt,
  müssen sie auf dasselbe Result auflösen — sonst wird der gesamte Request abgelehnt
  (keine stille Priorisierung, Abschnitt 12). `automationReference` wird
  ausschließlich unter den Test Cases aufgelöst, die tatsächlich Teil dieser
  Execution sind — nie projektweit, nie mit automatischer Test-Case-Anlage
  (Abschnitt 11).
- **Atomarität ohne verstecktes Transaktions-Timing-Risiko**: die Methode läuft in
  drei Phasen — (1) alle Einträge parsen, (2) alle Referenzen auflösen, (3) alle
  Merges durchführen und validieren — **bevor** irgendeine Entität mutiert wird.
  Erst wenn Phase 1-3 für ausnahmslos jeden Eintrag ohne Verstoß durchläuft, werden
  die `apply(...)`-Aufrufe ausgeführt. Das ist bewusst redundant zur
  Spring-`@Transactional`-Rollback-Garantie (die allein durch eine geworfene
  RuntimeException ohnehin schon jede DB-Änderung dieser Methode zurückrollen würde):
  die explizite Validierung-vor-Mutation macht die Alles-oder-nichts-Garantie im Code
  sichtbar und ermöglicht außerdem, **alle** Verstöße in einer Antwort zu sammeln,
  statt beim ersten Fehler abzubrechen.
- **Fehlerformat**: keine neue, parallele Fehlerstruktur (Abschnitt 7). Wiederverwendet
  `ApiError` unverändert; `fieldErrors` (existierendes Feld) trägt einen Eintrag pro
  Verstoß, `field` = `automationReference` bzw. `resultId` des betroffenen Eintrags
  (Fallback `results[n]`, falls keines von beiden auswertbar war) — das erlaubt dem
  CI-Publisher, eine Fehlerantwort direkt auf seine eigene lokale Testliste zurückzuführen.
- **Duplicate-Erkennung** verallgemeinert auf beide Referenzarten: zwei Einträge, die
  (über `resultId`, über `automationReference`, oder gemischt) auf dasselbe Result
  auflösen, gelten als Duplikat und lassen den gesamten Request scheitern.
- **`durationMs` darf nicht negativ sein** — diese Validierung wurde bei dieser
  Gelegenheit auch in den bestehenden Einzel-PATCH-Endpoint gezogen (dieselbe
  Merge-Logik), damit beide Endpunkte konsistent validieren, statt zwei leicht
  unterschiedliche Regelsätze zu pflegen.
- **Response**: eigenes `BulkResultUpdateResponse` (nicht `ExecutionResultResponse`
  wiederverwendet), das zusätzlich `testCaseHumanId`/`automationReference` pro
  Eintrag mitliefert — der Publisher muss dafür nicht extra die volle Execution
  laden, um seine Log-Ausgabe pro Test Case sinnvoll zu beschriften.

## Konsequenzen

- Ein CI-Publisher kann Ergebnisse rein über `automationReference` senden, ohne
  vorher `resultId`s aus einem separaten API-Aufruf holen zu müssen.
- Ein fehlerhafter Bulk-Request (z. B. eine falsch geschriebene
  `automationReference`) ändert garantiert nichts — auch nicht die 39 anderen,
  korrekten Einträge desselben Requests.
- Etwas höhere Code-Komplexität in `ExecutionService` (drei Phasen statt einer
  einzigen Schleife) gegenüber einer naiven "pro Eintrag sofort anwenden"-Variante —
  bewusst in Kauf genommen für Atomarität und vollständige Fehlerauflistung.

## Alternativen (verworfen)

- **Naive Schleife mit sofortigem `apply()` pro Eintrag + Rollback nur über
  `@Transactional` bei der ersten Exception**: hätte zwar dieselbe DB-Atomarität
  (Rollback), aber nur den *ersten* Fehler gemeldet — ein Client müsste sich Fehler
  für Fehler durcharbeiten, statt alle auf einmal zu sehen. Schlechter für einen
  CI-/KI-Agenten, der ohne Rückfrage arbeiten soll (Abschnitt 39).
- **Best-effort statt atomar** (jeder Eintrag wird unabhängig versucht, Response
  listet Erfolge und Fehler gemischt): explizit durch Abschnitt 6 ausgeschlossen
  ("kein partielles Commit").
- **`automationReference` global statt execution-scoped auflösen**: hätte erlaubt,
  versehentlich das Result eines völlig anderen (evtl. bereits abgeschlossenen)
  Execution-Laufs zu treffen — widerspricht Abschnitt 11 explizit.
