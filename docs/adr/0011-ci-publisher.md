# ADR 0011: CI-Publisher als eigenständiges Tool, kein Auth-System in diesem Block

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

Externe CI-Pipelines sollen Testergebnisse an Testryn melden können, ohne die
Testryn-REST-API selbst im Detail kennen zu müssen (Abschnitt 13-16). Gleichzeitig
ist die REST-API aktuell komplett offen (kein Auth), und Abschnitt 20 verlangt eine
bewusste Entscheidung dazu, nicht nur Stillschweigen.

## Entscheidung 1: `tools/testryn-publisher` als eigenständiges, kleines Maven-Modul

- **Kein Teil des `backend`-Moduls, kein Spring Boot.** Der Publisher ist ein winziges
  CLI-Tool (drei Flags, ein HTTP-Aufruf) — Spring/JPA/Postgres/Testcontainers
  mitzuschleppen wäre grober Overkill und würde ihn unnötig schwer machen, gerade weil
  er in beliebigen CI-Umgebungen laufen soll (Abschnitt 16: "kein Microservice", aber
  auch kein aufgeblähtes Werkzeug).
- **Java + Maven**, wie in Abschnitt 16 bevorzugt — dieselbe technische Welt wie das
  Backend, keine zweite Toolchain (Python/Node) für ein derart kleines Tool.
- **Eine einzige Produktionsabhängigkeit: Jackson** (`jackson-databind`, dieselbe
  Version wie das Backend). Kein CLI-Framework (picocli o. Ä.) für drei Flags und ein
  Unterkommando — Argument-Parsing ist von Hand geschrieben (AGENTS.md #8: keine neue
  Abhängigkeit ohne konkreten Grund).
- **Ausführbares Fat-Jar** via `maven-shade-plugin` (`java -jar testryn-publisher.jar
  publish ...`) — eine Pipeline soll kein Classpath-Management betreiben müssen.

## Entscheidung 2: `HttpURLConnection` statt `java.net.http.HttpClient`

`java.net.http.HttpClient` baut beim Erzeugen immer einen NIO-`Selector` auf, selbst
für einen einzigen synchronen `send()`-Aufruf. In manchen restriktiven/sandboxed
Ausführungsumgebungen (nachweislich auch in der Entwicklungsumgebung dieses Projekts,
siehe AGENTS.md #6a) scheitert das mit "Unable to establish loopback connection" schon
beim Konstruieren des Clients — bevor überhaupt ein Request gesendet wird.
`HttpURLConnection` nutzt klassisches blockierendes Socket-I/O ohne diese
Voraussetzung und funktioniert nachweislich überall dort, wo der moderne Client
scheitern kann.

Der Preis dafür: `HttpURLConnection.setRequestMethod("PATCH")` wirft seit jeher
`ProtocolException` (PATCH steht nicht in der intern hartkodierten Methoden-Liste,
die älter ist als RFC 5789). Der Standard-Workaround — das geschützte `method`-Feld
per Reflection direkt setzen — funktioniert weiterhin, benötigt seit dem
Java-Modulsystem aber ein geöffnetes `java.net`-Paket. Deshalb bäckt das Shade-Plugin
`Add-Opens: java.base/java.net` direkt ins Jar-Manifest — `java -jar
testryn-publisher.jar ...` funktioniert dadurch ohne zusätzliche JVM-Flags. Empirisch
gegen die echte, laufende Testryn-Instanz verifiziert (nicht nur gegen ein Fake):
`docker compose up --build`, Execution angelegt, Publisher-Jar ausgeführt, Ergebnis in
der Datenbank verifiziert (PROJECT_STATUS.md, Browser-Verifikation Workflow C).

## Entscheidung 3: Publisher-Core getrennt vom Input-Format (Abschnitt 17)

`ResultBatchReader` (Interface) trennt "wie kommen die Ergebnisse rein"
(`JsonResultBatchReader` heute) von `TestrynApiClient` (dem eigentlichen
Publisher-Core: baut den Bulk-Request, sendet ihn, interpretiert die Antwort). Ein
späterer JUnit-XML-Reader (bewusst NICHT in diesem Block gebaut, Abschnitt 33) würde
nur `ResultBatchReader` implementieren, ohne `TestrynApiClient` anzufassen. Ebenso ist
der eigentliche HTTP-Versand hinter `HttpTransport` verkapselt — die reale Variante
(`HttpUrlConnectionTransport`) ist von der Kernlogik (`TestrynApiClient`) unabhängig
testbar, ohne dass ein Test je einen echten Socket öffnet (Abschnitt 31).

## Entscheidung 4: `executor` default `"ci"`, `automationReference`/`resultId` dual-mode

Deckt sich mit ADR 0010: der Publisher setzt `executor = "ci"`, wenn ein Eintrag
keinen eigenen Wert mitbringt (Abschnitt 19), und akzeptiert beide Referenzarten pro
Eintrag unverändert durchgereicht — die eigentliche Auflösung/Validierung passiert im
Backend (ADR 0010), nicht doppelt im Publisher.

## Entscheidung 5 (Abschnitt 20): Kein Auth-System in diesem Block — dokumentierte
   Zurückstellung, kein Stillschweigen

Die REST-API ist aktuell vollständig offen. Ein einfacher statischer Service-Token
("wenn ohne großen Scope machbar") wurde geprüft und **bewusst nicht implementiert**:
selbst ein minimaler globaler Bearer-Token-Zwang hätte das bestehende, tokenlos
arbeitende Frontend gebrochen und wirft echte Scope-Fragen auf (gilt er für alle
Endpoints oder nur schreibende? Bricht er den anonymen UI-Zugriff komplett? Wie
verhält er sich zu einer späteren echten Nutzerverwaltung?) — das ist die Art von
Entscheidung, die laut Abschnitt 20 selbst *"größere Architekturarbeit"* darstellt und
damit **nicht erzwungen**, sondern dokumentiert zurückgestellt werden soll.

Der Publisher ist trotzdem bereits darauf vorbereitet: `TESTRYN_API_TOKEN` (Umgebungs-
variable, nie ein CLI-Argument, Abschnitt 15) wird, falls gesetzt, als
`Authorization: Bearer <token>`-Header mitgeschickt — das Backend ignoriert diesen
Header aktuell einfach. Sobald ein Service-Token-Mechanismus eingeführt wird, muss der
Publisher nicht angepasst werden.

## Konsequenzen

- Ein CI-Runner kann `testryn-publisher publish --base-url ... --execution-id ...
  --results results.json` ausführen, ohne Java-Framework-Kenntnisse oder ein
  Classpath-Setup.
- Die Auth-Frage bleibt ein offener, klar benannter Backlog-Punkt statt eines stillen
  Sicherheitslochs — bis dahin ist Testryn für den produktiven CI-Einsatz nur in
  vertrauenswürdigen Netzwerken (z. B. internes CI hinter VPN) geeignet, nicht öffentlich
  exponiert.
- Etwas Reflection-basierte Komplexität in `HttpUrlConnectionTransport`, dafür
  funktioniert der Publisher in genau den restriktiven Umgebungen, in denen der
  "modernere", augenscheinlich einfachere Weg (`java.net.http.HttpClient`) bereits am
  Client-Aufbau scheitert.

## Alternativen (verworfen)

- **`java.net.http.HttpClient`**: sauberer nativer PATCH-Support, aber am
  NIO-Selector-Problem gescheitert (siehe oben) — hätte den Publisher in genau den
  Umgebungen unbrauchbar gemacht, die diese Codebase bereits als real existierend
  dokumentiert hat.
- **POST + `X-HTTP-Method-Override: PATCH`-Header**: hätte eine Änderung am Backend
  erfordert (ein neuer, generischer Method-Override-Mechanismus für alle
  PATCH-Endpoints), nur um ein reines Client-seitiges Problem zu lösen — unnötige
  Vergrößerung der Backend-Angriffsfläche für ein Problem, das lokal im Publisher lösbar ist.
- **Apache HttpClient / OkHttp als Abhängigkeit**: hätte PATCH sauber unterstützt,
  aber eine zusätzliche, nicht triviale Abhängigkeit für ein absichtlich minimales
  Tool eingeführt (AGENTS.md #8).
- **Publisher als Python-/Node-Skript**: von Abschnitt 16 explizit nur erlaubt, wenn
  "erheblich einfacher und wartbarer" — für einen einzigen HTTP-Aufruf mit JSON-Body
  ist das nicht der Fall; Java/Maven hält den Publisher in derselben Toolchain wie das
  Backend.
