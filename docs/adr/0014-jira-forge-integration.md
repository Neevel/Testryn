# ADR 0014: Jira Forge Integration (Read-Only Issue Panel)

- Status: Angenommen
- Datum: 2026-08-24

## Kontext

Testryn ist bislang ausschließlich über die eigene UI und REST-API nutzbar. Fachlich
lebt der Kontext für "welche Tests decken diese Story ab?" aber in Jira, auf der
Story-Seite selbst -- ein Entwickler oder PO muss aktuell Testryn separat öffnen und
selbst die richtige Requirement-Verknüpfung finden. Ziel dieses Blocks: dieselbe
Information direkt in Jira sichtbar machen, ähnlich Testiny/Xray, ohne die
grundlegende Architektur ("Testryn ist Source of Truth für Testdaten", ADR 0005) zu
verlassen.

## Entscheidung 1: Forge, nicht Jira Custom Fields als Testdaten-Store

Zwei grundsätzlich verschiedene Architekturen standen zur Wahl:

```
(A, gewählt)                          (B, verworfen)
Jira Issue                            Jira Issue
   |                                     |
   v                                     v
Forge Issue Panel                     Jira Custom Fields
   |  liest live                         |  Kopie der Testdaten
   v                                     v
Testryn REST API                      manuell/per Automation
   |                                     synchronisiert mit Testryn
   v
Testryn-DB (einzige Quelle)
```

(B) würde Testdaten (Status, Steps, Ergebnisse) tatsächlich nach Jira duplizieren --
zwei Wahrheiten, die auseinanderlaufen können, plus Sync-Logik, die gepflegt werden
müsste. (A) hält Testryn als einzige Quelle: das Panel zeigt bei jedem Rendern den
tatsächlich aktuellen Stand, nie einen veralteten Snapshot. Genau das war explizit
gefordert (Abschnitt 1/2) und ist auch architektonisch die einzige Option, die mit
ADR 0005 ("Testryn ist Source of Truth, nicht Jira") konsistent bleibt.

## Entscheidung 2: `jira:issuePanel`, UI Kit statt Custom UI

`jira:issuePanel` ist genau das vorgesehene Jira-Modul für "zusätzlicher Inhalt auf
der Issue-Seite" (Abschnitt 3) -- kein Nachfolgemodul mit besserer Passung gefunden.
Innerhalb von Forge zwei UI-Technologien zur Wahl: **UI Kit** (server-deklariertes
React über `@forge/react`, von Jira nativ gerendert, kein iframe) und **Custom UI**
(eigenes HTML/JS-Bundle in einem iframe, mit voller Kontrolle über Rendering).

Gewählt: **UI Kit**. Begründung:

- Abschnitt 27 schließt eine iframe-Einbettung von Testryn explizit aus ("Forge soll
  echte Integration darstellen") -- UI Kit rendert grundsätzlich ohne iframe, Custom
  UI grundsätzlich mit einem. Damit erfüllt UI Kit diese Anforderung strukturell,
  nicht nur zufällig.
- Der vollständige UI-Bedarf dieses MVPs (Abschnitt 4/11-21: eine Liste, ein-/
  ausklappbare Karten, Status-Badges mit Icon+Text+Farbe, Links, vier States) deckt
  sich vollständig mit den vorhandenen UI-Kit-Komponenten (`Box`, `Stack`, `Inline`,
  `Text`, `Lozenge`, `Icon`, `Link`, `Button`, `SectionMessage`, `EmptyState`,
  `Spinner`) -- kein Bedarf an einer freien Custom-UI-Canvas.
- Weniger Betriebsaufwand: kein eigenes Frontend-Build/-Bundling/-Hosting für das
  Panel selbst nötig (Abschnitt 25: "klein halten").

Custom UI bleibt eine spätere Option, falls ein zukünftiger Block (z. B. "Create Test
Case aus Jira", explizit außerhalb dieses Blocks, Abschnitt 47) UI braucht, die UI
Kit nicht abdeckt.

## Entscheidung 3: Testryn bleibt Source of Truth -- eine neue, provider-neutrale Read-API statt Jira-Spezifika im Core

Der Forge-Resolver braucht "welche Test Cases decken Jira-Issue X ab, mit Steps und
letztem Ergebnis" in einem Aufruf (Abschnitt 22: kein N+1). Zwei Optionen:

- Jira-spezifischen Endpoint im Core bauen (`/api/v1/requirements/providers/jira/{key}/test-cases`,
  Abschnitt 5's Beispiel) -- hätte "Jira" im Core-Pfad verankert.
- **Gewählt**: `GET /api/v1/requirement-links/coverage?provider=jira&externalKey=...`
  -- `provider` ein gewöhnlicher Query-Parameter, keine Jira-spezifische Logik im
  `requirement`-Modul selbst (Abschnitt 6). `RequirementProviderType` war bereits ein
  generisches Enum (ADR 0005/0009); dieser Endpoint nutzt es einfach als weiteren
  Such-Parameter, genau wie die bestehende automationReference-Auflösung im Bulk-API
  keine framework-spezifische Logik im Core braucht (ADR 0009).

Der neue `RequirementCoverageService`/-`Controller` bleibt bewusst provider-neutral
und lebt im bestehenden `requirement`-Modul, nicht in einem neuen, Jira-benannten
Modul -- ein zweiter Provider (z. B. GitHub Issues, BACKLOG.md) bräuchte keine neue
API, nur einen weiteren `provider`-Wert.

## Entscheidung 4: Der Forge-Resolver hält den Service Token, nie der Browser

```
(gewählt)                              (verworfen)
Forge Backend/Resolver                 Jira Browser
   |  Authorization: Bearer <token>       |  Authorization: Bearer <token>
   v                                      v
Testryn API                            Testryn API
```

Ein UI-Kit-Frontend läuft im Browser des Jira-Nutzers -- jedes Secret, das dort
läge, wäre für jeden mit Zugriff auf den Browser/die Netzwerk-Requests sichtbar,
dauerhaft. Der Forge-Resolver läuft dagegen serverseitig in Atlassians eigener
Infrastruktur; `process.env.TESTRYN_API_TOKEN` ist dort gesetzt (als verschlüsselte
Forge-Umgebungsvariable, `forge variables set --encrypt`) und wird ausschließlich für
den einen `fetch`-Aufruf an Testryn verwendet, nie an die Antwort des Resolvers
zurückgehängt. Dasselbe Muster wie der bestehende CI-Publisher
(`TESTRYN_API_TOKEN`, ADR 0011/0012) -- ein weiterer Maschinen-Client, kein neuer
Auth-Mechanismus.

## Entscheidung 5: MVP ist read-only -- `testryn:read`, keine Schreibaktionen

Ein dedizierter, ausschließlich `testryn:read`-gescopter Service Token für diese App
(nicht der CI-Publisher-Token, nicht `write`/`admin`) -- das Panel liest nur, hat
keinerlei Schreibpfad, also braucht es strukturell keine höhere Berechtigung
(Abschnitt 8/47). Genauso `permissions.scopes: []` auf der Jira-Seite: das Panel ruft
die Jira-REST-API selbst nie auf, die aktuelle Issue-Referenz kommt kostenlos aus dem
von der Plattform bereitgestellten Aufruf-Kontext (`context.extension.issue.key`).
Zukünftige Schreibaktionen (Create Test Case, Link Existing, Generate Tests, Start
Execution -- Abschnitt 31, bewusst nicht in diesem Block) wären ein separater,
`testryn:write`-gescopter Pfad mit eigener Freigabe, keine stille Scope-Erweiterung
eines bestehenden Tokens.

## Konsequenzen

- Jira zeigt Testryn-Testabdeckung live, ohne eine einzige Zeile Testdaten selbst zu
  speichern.
- Der bestehende Requirement-Link-Workflow (Testryn → Preview → Confirm → Jira Story
  verknüpft) bleibt vollständig unverändert; das Panel liest lediglich, was dort
  bereits existiert (Abschnitt 30).
- Ein zweiter Requirement-Provider bräuchte keine neue Testryn-API, nur einen
  weiteren `RequirementProviderType`-Wert und einen weiteren Aufrufer derselben
  `coverage`-Route.
- Etwas Betriebsaufwand für den Forge-Teil (eigenes Deployment, eigener Service
  Token, eigene Umgebungsvariablen) -- bewusst in Kauf genommen für eine echte,
  Jira-native Integration statt eines iframes.

## Alternativen (verworfen)

- **Jira Custom Fields als Testdaten-Kopie**: siehe Entscheidung 1 -- zwei
  Wahrheiten, Sync-Aufwand, widerspricht ADR 0005.
- **Custom UI (iframe)**: siehe Entscheidung 2 -- von Abschnitt 27 explizit
  ausgeschlossen, UI Kit deckt den MVP-Bedarf vollständig ab.
- **Jira-spezifischer REST-Pfad im Core** (`/requirements/providers/jira/...`):
  siehe Entscheidung 3 -- hätte "Jira" in den Core-Pfad eingebrannt, wo ein
  Query-Parameter genügt.
- **Ein allgemeines, Forge-eigenes Berechtigungssystem/OAuth statt Service Tokens**:
  außerhalb des Scopes (Abschnitt 47) -- Service Tokens (ADR 0012) sind bereits das
  etablierte, funktionierende Maschine-zu-Maschine-Auth-Modell; ein zweites,
  Forge-spezifisches Verfahren wäre unnötige Parallelstruktur.
