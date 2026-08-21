# ADR 0013: JUnit XML `automationReference` Convention (`classname#name`)

- Status: Angenommen
- Datum: 2026-08-21

## Kontext

`testryn-publisher` liest jetzt zusätzlich zum bestehenden JSON-Format JUnit-
kompatibles XML (Maven Surefire/Failsafe, identisches Schema, siehe
`JUnitXmlResultBatchReader`) und muss aus jedem `<testcase>` deterministisch genau
die `automationReference` ableiten, die ADR 0009 bereits als stabilen Schlüssel für
die Bulk-Result-Auflösung etabliert hat. Ohne eine feste, dokumentierte Konvention
würde jedes CI-Setup seine eigene, leicht abweichende Ableitung erfinden -- genau die
Uneinheitlichkeit, die ADR 0009 durch ein einziges, maschinenfreundliches Feld
vermeiden wollte.

## Entscheidung 1: `classname + "#" + name`, wörtlich aus dem XML

`com.example.LoginTest#successfulLogin` -- `classname` und `name` werden unverändert
aus den gleichnamigen `<testcase>`-Attributen übernommen, keine Normalisierung, keine
Kürzung.

- **Nicht nur der bloße Methodenname**: `successfulLogin` allein würde projektweit
  kollidieren, sobald zwei Testklassen eine gleichnamige Methode haben --
  `classname` ist notwendig, um die Referenz eindeutig zu halten.
- **`#` als Trenner**, nicht `.`: ein Punkt ist im `classname`-Teil selbst bereits
  das Paketsegment-Trennzeichen (`com.example.LoginTest`) -- ein weiterer Punkt vor
  dem Methodennamen wäre strukturell nicht vom Klassenpfad unterscheidbar
  (`com.example.LoginTest.successfulLogin` liest sich wie ein noch tieferes Package).
  `#` ist zudem keine Neuerfindung: es ist bereits die Konvention, die JUnit 5 selbst
  für `MethodSelector`-URIs und die meisten IDEs für eine
  Klasse-plus-Methode-Referenz verwenden ("gehe zu Testklasse#Methode") --
  Entwickler, die den Wert in der Testryn-UI sehen, erkennen das Format sofort
  wieder.
- **Fehlender `classname`** (selten; reale Surefire-Reports haben ihn praktisch
  immer): Fallback auf `name` allein, statt den Import mit einem harten Fehler
  abzubrechen -- ein einzelnes Attribut, das nicht jedes denkbare JUnit-XML-Dokument
  zwingend hat, soll nicht die gesamte Datei unbrauchbar machen.

## Entscheidung 2: ADR 0009s Zeichensatz um `#` erweitert

ADR 0009 legte `[A-Za-z0-9_.-]` fest -- bewusst ohne `#`, weil zum damaligen
Zeitpunkt keine Konvention existierte, die es gebraucht hätte. Diese Entscheidung
wird hier **bewusst geändert** (nicht umgangen): `TestCaseService`s
`AUTOMATION_REFERENCE_PATTERN` erlaubt jetzt zusätzlich `#`. Ohne diese Änderung
wäre die in Entscheidung 1 getroffene Konvention serverseitig gar nicht speicherbar
gewesen -- am echten, laufenden Stack entdeckt: der erste Versuch, eine reale
`classname#name`-Referenz über die API anzulegen, schlug mit `400 Bad Request` fehl,
bevor diese Änderung vorgenommen wurde. `#` ist in keinem anderen Kontext des
bestehenden Zeichensatzes bedeutungstragend, es entsteht also kein
Mehrdeutigkeits-Risiko mit bereits gültigen Referenzen wie `auth.login.valid`.

## Entscheidung 3: Parametrisierte/dynamische Tests -- keine Rückübersetzung

JUnit 5 `@ParameterizedTest`/`@TestFactory` erzeugen `name`-Attribute wie
`successfulLogin(String)[1]` oder frei wählbare `displayName`-Strings, die vom
eigentlichen Methodennamen abweichen können. Der Reader unternimmt **keinen**
Versuch, daraus algorithmisch den "wahren" Methodennamen zurückzugewinnen --
was im XML steht, wird eins zu eins übernommen. Das bedeutet:

- Jede Parameter-Instanz eines parametrisierten Tests bekommt ihre eigene,
  vollständige `automationReference` (inklusive `(String)[1]`-Suffix o. Ä.) und
  braucht daher ihren eigenen Testryn-Test-Case, falls sie einzeln nachverfolgt
  werden soll.
- Ein generierter Display-Name, der sich zwischen zwei Testläufen ändert (z. B. weil
  er den Parameterwert selbst enthält und der Wert nicht stabil ist), erzeugt eine
  neue, andere `automationReference` -- der alte Testryn-Test-Case verliert dadurch
  seine Zuordnung. Dies ist eine bekannte, akzeptierte Grenze dieser Konvention, kein
  Bug im Reader.

**Warum bewusst keine Magie hier**: jeder Versuch, Parameter-Suffixe zu erkennen und
wegzuschneiden, wäre notwendigerweise heuristisch (unterschiedliche Runner/Frameworks
formatieren das unterschiedlich) und würde in genau den Fällen, in denen es falsch
rät, eine falsche Testfall-Zuordnung erzeugen -- stiller Datenverlust wäre schlimmer
als eine explizite, dokumentierte Grenze. Wer parametrisierte Tests stabil einzeln
verfolgen will, sollte ihnen einen stabilen, expliziten `displayName` ohne
laufzeitabhängige Werte geben.

## Konsequenzen

- Die Konvention ist vollständig deterministisch und ohne Zusatzkonfiguration nutzbar
  -- der Normalfall (unparametrisierte JUnit-Tests) funktioniert ohne jede weitere
  Overhead.
- Kein separates Mapping-File/-Subsystem nötig für den Normalfall.
- Bestehende, über die JSON-`publish`-Route gesetzte `automationReference`-Werte
  (z. B. `auth.login.valid`, ohne `#`) bleiben unverändert gültig -- diese Änderung
  erweitert nur den erlaubten Zeichensatz, sie schränkt ihn nicht ein.
- Live gegen den laufenden Stack verifiziert: vier reale Testryn-Test-Cases mit
  `com.example.PublisherDemoTest#{passedTest,failedTest,skippedTest,
  anotherPassedTest}`-Referenzen, angelegt und über `publish-junit` mit echten,
  durch `mvn test` erzeugten Surefire-XML-Dateien befüllt.

## Alternativen (verworfen)

- **Nur der bloße Methodenname** (`successfulLogin`): kollidiert projektweit, siehe
  Entscheidung 1.
- **`.` statt `#` als Trenner** (`com.example.LoginTest.successfulLogin`): strukturell
  nicht vom Package-Pfad unterscheidbar, siehe Entscheidung 1.
- **Ein optionales, explizites YAML-Mapping-File** (JUnit-Name -> gewünschte
  `automationReference`): für den in Abschnitt 12 der ursprünglichen Anforderung
  vorgesehenen Fall einer expliziten Override-Möglichkeit erwogen, aber nicht gebaut
  -- die `classname#name`-Konvention deckt den weit überwiegenden Normalfall bereits
  vollständig und deterministisch ab; ein weiteres Subsystem nur für den seltenen
  Sonderfall (Umbenennung ohne Test-Case-Neuanlage) wäre Komplexität ohne
  ausreichenden Gegenwert für diesen Block. Bleibt ein mögliches späteres
  Backlog-Item, falls echter Bedarf entsteht.
- **Automatisches Zurückschneiden von Parameter-Suffixen**: siehe Entscheidung 3 --
  explizit verworfen wegen des Risikos stiller Fehlzuordnung.
