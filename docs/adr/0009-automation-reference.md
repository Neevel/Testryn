# ADR 0009: `automationReference` als stabile Automation-Mapping-Referenz

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

Ein externes, automatisiertes Testframework (JUnit, Playwright, Selenium, ...) muss
seine Ergebnisse einem konkreten Testryn-Test-Case zuordnen können, ohne interne
UUIDs im Testcode hart zu verdrahten (Abschnitt 8). Gleichzeitig darf das
Core-Domain-Modell laut AGENTS.md/Abschnitt 2 nicht an ein bestimmtes Framework
gekoppelt werden.

## Entscheidung

- Neues, optionales Feld `automationReference` (`String`, max. 200 Zeichen) direkt
  auf `TestCase` (nicht auf `TestCaseVersion`): es ist Identitäts-/Metadaten wie
  `status`/`priority`/`tags`, kein versionierter fachlicher Inhalt. Ändern des Werts
  erzeugt daher **keine** neue `TestCaseVersion` — konsistent mit dem bestehenden
  Verhalten von `updateMetadata`.
- **Format**: nur `[A-Za-z0-9_.-]`, 1–200 Zeichen (z. B. `auth.login.valid`). Kein
  Framework-Präfix, keine erzwungene Struktur darüber hinaus — "maschinenfreundlich",
  nicht an JUnit-Klassennamen o. Ä. gebunden.
- **Eindeutigkeit**: projektweit eindeutig, nicht global. Durchgesetzt auf zwei
  Ebenen: Service-Layer-Check vor dem Schreiben (klare 409-Fehlermeldung) **und**
  partieller Unique-Index in der DB (`WHERE automation_reference IS NOT NULL`,
  Migration `0004-test-case-automation-reference.sql`) als Absicherung gegen Races.
  Der Index ist partiell, damit beliebig viele Test Cases weiterhin `NULL` (= nicht
  automatisiert) haben dürfen, ohne mit einer klassischen Unique-Constraint zu
  kollidieren.
- **Änderbarkeit**: bewusst über denselben Pfad wie andere Metadaten (`PUT
  /test-cases/{id}`) änderbar, nicht unveränderlich. Klare Semantik: der *aktuelle*
  Wert ist maßgeblich für die Auflösung eingehender CI-Ergebnisse; es gibt keine
  Historie alter Referenzen. Wird ein Test Case umbenannt/refactored, kann die
  Referenz bewusst mitgezogen werden.
- **Suche**: `GET /projects/{projectKey}/test-cases?automationReference=...` ist ein
  exakter, case-sensitiver Match — keine unscharfe Suche (Abschnitt 10). Technisch:
  eine weitere optionale `Specification`, dieselbe Such-Infrastruktur wie in ADR 0008.
- **Auflösung innerhalb einer Execution** (Bulk-API, Abschnitt 11): sucht
  ausschließlich unter den `ExecutionTestCase`-Einträgen der jeweiligen Execution,
  nicht projektweit — ein automatisierter Test kann nur Ergebnisse für Test Cases
  melden, die tatsächlich Teil dieser Execution sind. Kein automatisches Anlegen
  fehlender Test Cases.

## Konsequenzen

- Ein CI-Publisher kann Ergebnisse rein über fachliche Referenzen senden
  (`{"automationReference": "auth.login.valid", "status": "PASSED"}`), ohne vorher
  `resultId`/`executionTestCaseId` aus einem separaten API-Aufruf holen zu müssen.
- Test Cases ohne `automationReference` (der Normalfall für rein manuell getestete
  Fälle) sind unverändert nutzbar; das Feld ist vollständig optional.
- Setzt jemand versehentlich zwei Test Cases auf dieselbe Referenz, schlägt das mit
  einer verständlichen 409-Antwort fehl, statt eine der beiden Referenzen still zu
  überschreiben oder Ergebnisse falsch zuzuordnen.

## Alternativen (verworfen)

- **`TestCaseVersion.automationReference`** (versioniert): hätte bedeutet, dass jede
  inhaltliche Testfall-Änderung potenziell die Automation-Zuordnung "verliert" (neue
  Version = neue Referenz nötig) — unpraktisch, da die Referenz eine Eigenschaft der
  *Identität* ist, nicht des jeweiligen Inhalts.
- **Framework-spezifische Felder** (`junitClassName`, `seleniumTestId`, ...): genau
  die Kopplung, die Abschnitt 2/33 ausschließt.
- **Separate `AutomationMapping`-Entität** mit eigener Tabelle: für ein 1:1-Feld ohne
  eigene Historie/Metadaten unnötige Komplexität; ein einfaches Spalten-Feld auf
  `TestCase` reicht und bleibt konsistent mit `status`/`priority`/`tags`.
