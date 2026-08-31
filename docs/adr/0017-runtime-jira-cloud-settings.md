# ADR 0017: Laufzeitkonfiguration der Jira-Cloud-Verbindung

- Status: Angenommen
- Datum: 2026-08-24
- Präzisiert und ersetzt Teile von: ADR 0007

## Kontext

ADR 0007 hielt sämtliche Jira-Verbindungsdaten bewusst ausschließlich in
Environment Variables. Für eine nutzbare Testmanagement-Oberfläche muss ein
Administrator die Jira-Cloud-Site jedoch ohne Image-Neubau und Neustart wechseln
können. Gleichzeitig darf kein dauerhaft im Browser gespeichertes oder über einen
GET-Endpunkt auslesbares Secret entstehen.

## Entscheidung

- Testryn verwaltet im MVP weiterhin genau **eine** Jira-Verbindung.
- Nicht geheime Metadaten (`name`, Jira-Cloud-`baseUrl`, `email`, `active`) werden in
  PostgreSQL gespeichert und über `PUT /api/v1/integrations/jira/connection`
  bearbeitet.
- Das API-Token bleibt ausschließlich in externer Serverkonfiguration
  (`TESTRYN_JIRA_API_TOKEN`). REST-Antworten liefern nur `tokenConfigured`.
- Zulässig sind ausschließlich HTTPS-Basis-URLs auf `*.atlassian.net`, ohne Pfad,
  Port, Query, Fragment oder Userinfo. Das verhindert, dass das Jira-Feature als
  frei konfigurierbarer SSRF-Proxy auf interne Ziele verwendet wird.
- `JiraIssueClient` liest vor jedem fachlichen Aufruf einen unveränderlichen Snapshot
  der aktuellen Einstellungen. Änderungen wirken ohne Backend-Neustart.
- Die Jira-Forge-App liest die Jira-Basis-URL beim Erstellen eines verknüpften
  Testfalls aus Testryn und besitzt keine kundenspezifische `JIRA_BASE_URL` mehr.

## Konsequenzen

- Eine Testryn-Installation kann auf eine beliebige Jira-Cloud-Site umgestellt
  werden, ohne Code, Docker-Environment oder Forge-Deployment anzupassen.
- Ein Wechsel der Jira-Site erfordert weiterhin ein zu dieser Site passendes
  serverseitiges API-Token. OAuth 2.0 und Jira Server/Data Center bleiben spätere,
  eigenständige Erweiterungen.
- Bereits gespeicherte Requirement Links behalten ihre ursprüngliche URL als
  historischer Verweis; neu aufgelöste und neu erstellte Links verwenden die
  aktuelle Konfiguration.
