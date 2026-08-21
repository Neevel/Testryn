# ADR 0012: Service Token Authentication (Machine-to-Machine)

- Status: Angenommen
- Datum: 2026-08-21

## Kontext

Die REST-API war bis zu diesem Block vollständig offen: jeder mit Netzwerkzugriff
konnte lesen und schreiben. Das größte verbleibende technische Risiko des Produkts
(explizit benannt im vorigen Block). Gleichzeitig soll kein Enterprise-Auth-System
entstehen — die tatsächlichen Clients dieser API sind heute und absehbar überwiegend
Maschinen: der CI-Publisher, künftig ein KI-Agent, eine Jira-Forge-App, weitere
CLI-Tools.

## Entscheidung 1: Service Tokens statt Benutzerkonten

Ein Service Token ist ein Credential, das zu Testryn gehört (nicht zu einer Person).
Kein Passwort, kein Login-Formular, keine Session. Genau für maschinelle Clients
gedacht — siehe Entscheidung 6 zur bewussten Trennung von menschlicher
Authentifizierung.

## Entscheidung 2: Token-Format `testryn_<lookupId>_<secret>`

- `lookupId`: 12 zufällige Bytes, hex-kodiert (24 Zeichen) — **nicht geheim**, dient
  ausschließlich dem indexierten O(1)-Datenbank-Lookup (Abschnitt 19: "keine
  komplette Token-Tabelle sequentiell scannen").
- `secret`: 32 zufällige Bytes (256 Bit), hex-kodiert (64 Zeichen) — das eigentliche
  Credential, nie im Klartext gespeichert.
- Feste Längen für beide Teile (statt eines variabel-langen, alphabet-abhängigen
  Trennzeichens) machen das Parsen eindeutig und robust, unabhängig vom verwendeten
  Alphabet — kein Risiko, dass ein Zeichen des Secrets zufällig wie das
  Trennzeichen aussieht.

## Entscheidung 3: Hashing-Strategie — SHA-256, keine Passwort-KDF

`bcrypt`/`scrypt`/`Argon2` existieren, um das Brute-Forcen *niedrig-entropischer*
menschlicher Passwörter gegen einen gestohlenen Hash künstlich zu verlangsamen. Ein
Service-Token-Secret ist bereits 256 Bit `SecureRandom`-Output — computational
unmöglich zu erraten, unabhängig von der Hash-Geschwindigkeit. Eine langsame KDF
hier einzusetzen würde nur jeder einzelnen authentifizierten Anfrage unnötige
Latenz hinzufügen, ohne einen realen Sicherheitsgewinn. Plain SHA-256 (mit
konstant-zeitigem Vergleich als billige zusätzliche Absicherung gegen einen
Timing-Seitenkanal) ist Industriestandard für hochentropische API-Tokens (GitHub
Personal Access Tokens, Stripe API Keys verfahren identisch). Ein serverseitiges
HMAC-Pepper wäre als zusätzliche Tiefenverteidigung gegen eine geleakte Datenbank
später ohne Schema-Änderung nachrüstbar — angesichts der Token-Entropie hier nicht
notwendig.

## Entscheidung 4: Scopes — `read` < `write` < `admin`, jeweils implizierend

Drei Scopes (`testryn:read`, `testryn:write`, `testryn:admin`), keine
allgemeine Rollenplattform (Abschnitt 1 schließt das explizit aus):

- `write` impliziert `read` — wer schreiben darf, darf auch lesen; ein Aufrufer
  braucht nur `write` anzufordern, um vollen CRUD-Zugriff auf Testdaten zu erhalten.
- `admin` impliziert `read` **und** `write`, und ist zusätzlich der **einzige**
  Scope, der die Token-Verwaltungsendpunkte (`/api/v1/service-tokens/**`) aufrufen
  darf — unabhängig von der HTTP-Methode (Abschnitt 23). Ohne diese Trennung könnte
  jeder gewöhnliche `write`-Token für die CI-Pipeline sich selbst oder andere
  Admin-Tokens erzeugen — ein `write`-Scope für Testdaten ist fachlich etwas völlig
  anderes als die Berechtigung, weitere Credentials auszustellen.
- Scope-Zuordnung zu HTTP-Methoden (Abschnitt 22): `GET`/`HEAD` → `read`,
  `POST`/`PUT`/`PATCH`/`DELETE` → `write`, mit der einzigen Ausnahme
  `/api/v1/service-tokens/**` → immer `admin`. Bewusst nicht feiner (z. B. kein
  Unterschied zwischen "Test Case anlegen" und "Test Plan löschen") — das wäre der
  erste Schritt zu einer allgemeinen Rollenplattform, die dieser Block explizit
  nicht bauen soll.

## Entscheidung 5: Bootstrap — `TESTRYN_BOOTSTRAP_TOKEN`, kein Dauer-Backdoor

Sobald Auth erzwungen wird, muss irgendetwas das allererste Token erzeugen können,
ohne dafür einen offenen "Token erstellen"-Endpoint zu benötigen (Abschnitt 12).
Gewählt: die kleinste der drei skizzierten Optionen — ein
`TESTRYN_BOOTSTRAP_TOKEN`-Environment-Variable, die genau einmal, beim ersten Start
**ohne** existierende Service Tokens, zu einem ganz gewöhnlichen, voll widerrufbaren
ADMIN-Token wird (`ServiceTokenBootstrap`). Kein Sonderpfad in der eigentlichen
Authentifizierungslogik: der so erzeugte Datensatz ist von jedem anderen Token
ununterscheidbar, wird exakt gleich verifiziert, und ist genauso über
`POST /service-tokens/{id}/revoke` widerrufbar. Warum das keine dauerhafte
Hintertür ist: sobald `ServiceTokenService#hasAnyToken()` `true` liefert (ab dem
ersten erzeugten Token, egal ob Bootstrap oder regulär), no-opt dieser Runner bei
jedem weiteren Start dauerhaft — auch wenn die Umgebungsvariable weiterhin gesetzt
bleibt. Es gibt keinen Code-Pfad, der die Variable später erneut prüft oder
honoriert.

Ein Detail, das den Bootstrap-Wert technisch von regulär erzeugten Tokens
unterscheidet: der vom Operator frei gewählte Rohwert folgt nicht zwingend dem
strukturierten `testryn_<lookupId>_<secret>`-Format. `ServiceTokenService#authenticate`
löst das, ohne einen zweiten, unindexierten Lookup-Pfad einzuführen: kann ein
präsentierter Token nicht strukturiert geparst werden, wird stattdessen ein
deterministischer Lookup-Key aus einem Hash-Präfix des gesamten Rohwerts berechnet
— weiterhin ein indexierter O(1)-Lookup, nie ein Tabellenscan, und für regulär
erzeugte (immer strukturiert gültige) Tokens niemals der tatsächlich genommene Pfad.

## Entscheidung 6: Machine-to-Machine ≠ Human User Authentication

Zwei fachlich und architektonisch unterschiedliche künftige Auth-Arten (Abschnitt
9):

```
Machine-to-Machine (JETZT implementiert)
  CI / Publisher / Agent / Forge-Backend → Service Token

Human User Authentication (NICHT in diesem Block)
  Browser-Nutzer → Login / Session / SSO
```

Ein Service Token ist absichtlich **kein** Ersatz für echte Nutzer-Authentifizierung:
er hat keine Identität außer einem Namen, kein Passwort, keine Session, kein
Konzept von "wer bin ich". Das bestehende Frontend braucht trotzdem *irgendeinen*
Token, um die jetzt geschützte API überhaupt aufzurufen (Abschnitt 8) — dafür gibt
es eine bewusst minimale, ehrliche Übergangslösung: ein Mensch fügt in den
Settings einen selbst erzeugten Token manuell ein, gehalten ausschließlich in
`sessionStorage` des Browser-Tabs (nie im gebauten Bundle, nie in `localStorage`).
Das ist **keine** Login-Funktion und wird auch nicht als eine solche dargestellt —
ein Browser-SPA kann ein dauerhaftes Service Token grundsätzlich nicht sicher
geheim halten. Echte Human User Authentication (Login, Sessions, ggf. SSO) bleibt
ein bewusst getrennter, noch nicht begonnener Produktblock — siehe BACKLOG.md.

## Entscheidung 7: Spring Security, minimal konfiguriert

- Stateless (`SessionCreationPolicy.STATELESS`) — keine Server-Session für einen
  Bearer-Token-Client.
- CSRF deaktiviert — CSRF schützt Cookie-/Session-basierte Authentifizierung vor
  durch den Browser automatisch mitgeschickten Credentials; ein Bearer-Token wird
  nie automatisch vom Browser mitgeschickt (nur explizit vom Code gesetzt), also
  gibt es hier keinen CSRF-Angriffsvektor zu verteidigen.
- Kein Form-Login, kein HTTP Basic.
- Ein einziger `OncePerRequestFilter` (`ServiceTokenAuthenticationFilter`) löst
  `Authorization: Bearer ...` in eine `Authentication` mit vorab abgeflachten
  Scope-Berechtigungen auf; `SecurityConfig`s `authorizeHttpRequests` bleiben
  dadurch einfache, deklarative `hasAuthority(...)`-Regeln.
- 401/403 werden über einen eigenen `AuthenticationEntryPoint`/`AccessDeniedHandler`
  im bestehenden `ApiError`-Format beantwortet (Abschnitt 21) — kein paralleles
  Fehlermodell, kein Spring-Security-Default-HTML.

## Konsequenzen

- Eine netzwerk-erreichbare Testryn-Instanz ist nach diesem Block nicht mehr anonym
  beschreibbar.
- Der bestehende CI-Publisher funktioniert unverändert weiter (er unterstützte
  `TESTRYN_API_TOKEN` bereits vorbereitend aus dem vorigen Block) — jetzt jedoch
  tatsächlich durchgesetzt statt nur akzeptiert.
- Etwas mehr Komplexität im Vergleich zu "gar keine Auth" — bewusst in Kauf
  genommen, da das erklärte Ziel dieses Blocks genau das ist.
- Human User Authentication bleibt offen; bis dahin ist die Frontend-Nutzung auf
  vertrauenswürdige Personen mit Zugriff auf einen gültigen Token beschränkt.

## Alternativen (verworfen)

- **OAuth 2.0 / OIDC für Testryn selbst**: explizit ausgeschlossen (Abschnitt 1) —
  massiv größerer Scope für ein Problem, das ein einfaches Bearer-Token vollständig
  löst.
- **JWT statt opakem Token + DB-Lookup**: ein signiertes JWT bräuchte einen
  Schlüsselverwaltungs-/Rotationsmechanismus und wäre nicht ohne Weiteres
  widerrufbar (Abschnitt 17 verlangt explizit Revocation) — ein klassisches
  gehashtes Referenz-Token mit DB-Lookup ist für dieses Szenario einfacher UND
  korrekter (sofortiger Widerruf, kein Token-Blacklisting nötig).
- **bcrypt/Argon2 für das Token-Hashing**: siehe Entscheidung 3.
- **Rollenbasiertes Berechtigungssystem über die drei Scopes hinaus**: explizit
  außerhalb des Scopes (Abschnitt 36: "RBAC beyond service-token scopes").
