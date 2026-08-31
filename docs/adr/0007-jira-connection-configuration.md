# ADR 0007: Jira-Connection-Konfiguration

- Status: Angenommen
- Datum: 2026-08-20

> **Nachtrag 24.08.2026:** Die Aussagen „ausschließlich Environment Variables“ und
> „kein Schreib-Endpoint“ wurden für nicht geheime Metadaten durch ADR 0017 ersetzt.
> Das API-Token bleibt weiterhin ausschließlich externe Serverkonfiguration.

## Kontext

Testryn soll eine Jira-Verbindung konfigurierbar machen (Name, Base URL, Auth-Art,
Identität, Secret-Referenz, Aktiv/Inaktiv, Connection Test), ohne dabei Secrets über
GET-Endpunkte zurückzugeben, im Klartext zu loggen, dauerhaft im Frontend zu
speichern oder ins Repository zu legen. Die Architektur soll späteres OAuth 2.0
ermöglichen, ohne dass in diesem Arbeitsblock eine Atlassian-Marketplace-/Forge-App
oder ein vollständiger OAuth-Flow gebaut wird.

## Entscheidung

- **Eine** Jira-Verbindung pro Testryn-Instanz für dieses MVP (kein
  Multi-Connection-Verwaltungssystem). `JiraProperties`
  (`@ConfigurationProperties(prefix = "testryn.jira")`) trägt `name`, `baseUrl`,
  `email`, `apiToken`, `authType` (Enum, aktuell nur `API_TOKEN` implementiert,
  `OAUTH2` als Platzhalter für später), `active`. Ausschließlich über Environment
  Variables befüllt (`TESTRYN_JIRA_*`), wie zuvor — kein Secret-Store-Produkt in
  diesem Arbeitsblock, aber die Konfigurationsquelle ist durch
  `@ConfigurationProperties` bereits so isoliert, dass ein späterer Wechsel auf einen
  echten Secret-Manager (Vault, AWS Secrets Manager, …) nur die Bean-Konstruktion
  betrifft, nicht die Verwendung im Rest der Anwendung.
- **Kein Schreib-Endpoint** für Connection-Settings über die REST-API in diesem
  MVP — Konfiguration bleibt Environment-Variable-basiert. Das vermeidet jede
  Notwendigkeit, ein Secret jemals über HTTP entgegenzunehmen, zu validieren oder
  clientseitig zwischenzuspeichern, und erfüllt damit die Vorgabe "Secrets nie
  dauerhaft im Frontend" ohne zusätzlichen Aufwand.
- **Lesbare Statusauskunft**: `GET /api/v1/integrations/jira/connection` liefert
  `name`, `baseUrl`, `authType`, `email`, `active`, `tokenConfigured` (bool) und
  `usable` (bool) — **niemals** `apiToken` selbst. Ein Frontend kann damit den
  Verbindungsstatus anzeigen, ohne je das Secret zu sehen.
- **Connection Test**: `POST /api/v1/integrations/jira/connection/test` ruft
  `GET /rest/api/3/myself` mit der konfigurierten Identität auf und meldet nur
  `{success, message}` zurück — Fehlermeldungen sind bewusst generisch
  ("Authentication rejected by Jira", "Jira is not reachable") statt Jira-
  Rohantworten oder Exception-Details durchzureichen, um kein Secret- oder
  Instanz-internes Detail zu leaken.
- **Auth-Art-Abstraktion für späteres OAuth 2.0**: `JiraAuthType { API_TOKEN,
  OAUTH2 }` lebt bereits im Provider-Modul. `JiraIssueClient`/
  `JiraRequirementProvider` kennen nur "usable ja/nein" und die generische
  `RequirementProvider`-Schnittstelle (ADR 0005) — ein künftiger OAuth2-Codepfad
  (Token-Refresh, Authorization-Code-Flow) betrifft ausschließlich, wie
  `JiraIssueClient` seinen Authorization-Header aufbaut, nicht die
  Domain/Requirement-Schicht.
- **Kein Secret-Logging**: `JiraIssueClient` loggt bei Fehlern nur Statuscode/Key,
  nie Header oder Response-Body; `basicAuthHeader()` baut den Wert nur für den
  einzelnen Request auf, ohne ihn zu loggen oder zurückzugeben.

## Konsequenzen

- Ein Betreiber kann die Jira-Anbindung per Environment Variables (lokale
  Entwicklung: `.env`/Docker-Compose-Override, gitignored) konfigurieren und testen,
  ohne den Code zu ändern.
- Deaktivieren einer Verbindung (`TESTRYN_JIRA_ACTIVE=false`) ist möglich, ohne
  Zugangsdaten zu entfernen — nützlich, um Jira-Calls temporär abzuschalten.
- Ein UI-Formular zum Bearbeiten der Verbindung ist in diesem Arbeitsblock bewusst
  **nicht** vorgesehen (nur Statusanzeige + Test-Button) — vermieden wird damit jedes
  Secret-Input-Handling im Frontend. Als BACKLOG-Punkt vermerkt, falls später
  mehrere Verbindungen oder ein Secret-Store-UI benötigt werden.

## Alternativen (verworfen)

- **DB-persistierte Connection-Entity mit verschlüsseltem Token-Feld**: für ein
  MVP mit genau einer Verbindung unnötiger Aufwand (Verschlüsselung,
  Schlüsselverwaltung) gegenüber Environment-Variable-Konfiguration; keine
  fachliche Notwendigkeit, mehrere Jira-Instanzen gleichzeitig zu verwalten.
- **Direkter OAuth-2.0-Flow bereits jetzt implementieren**: explizit außerhalb des
  Scopes dieses Arbeitsblocks (Abschnitt 44 des Auftrags); die
  `JiraAuthType`-Abstraktion stellt sicher, dass das später ergänzt werden kann,
  ohne den bereits gebauten Code umzubauen.
