# Testryn

Testryn ist eine eigenständige Testmanagement-Plattform. Test Cases, Test Case
Versionen, Test Plans, Executions und Ergebnisse leben in Testryn — unabhängig vom
Issue-Tracker. Jira (und später weitere Tools) ist ausschließlich Requirement-/Task-/
Bug-Quelle und wird über generische `RequirementLink`s referenziert.

Details zu Produktvision, Architekturprinzipien und Arbeitsweise: [AGENTS.md](AGENTS.md).
Aktueller technischer Stand: [PROJECT_STATUS.md](PROJECT_STATUS.md). Roadmap:
[BACKLOG.md](BACKLOG.md). Architekturentscheidungen: [docs/adr/](docs/adr/).

## Kern-Workflow

```
Jira Story ──▶ Testryn: Test Case (Steps, Expected Results, Version)
                   │
                   ├─▶ Requirement Link (→ Jira Issue)
                   ├─▶ Test Plan
                   └─▶ Execution (Snapshot)
                            └─▶ Execution Result (PASS/FAIL/SKIPPED/BLOCKED/NOT_RUN)
                                     └─▶ Report Upload
```

## Quick Start (Docker Compose)

Voraussetzung: Docker Desktop.

```bash
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend-API: http://localhost:8080/api/v1
- OpenAPI/Swagger UI: http://localhost:8080/swagger-ui.html
- PostgreSQL: `localhost:5432` (siehe `docker-compose.yml` für Zugangsdaten)

Zum Beenden: `Strg+C`, dann `docker compose down` (Daten bleiben im Volume erhalten;
`docker compose down -v` löscht auch die Volumes).

## Lokalen Testryn-Stand auf einen anderen Rechner übertragen

Ein Git-Clone enthält nur den Quellcode. Projekte, Test Cases, Requirement Links,
Executions und Service-Token-Hashes liegen in PostgreSQL; hochgeladene Reports liegen
in einem separaten Docker-Volume. Für einen vollständigen Rechnerwechsel müssen beide
Bestände übertragen werden.

Auf dem bisherigen Rechner bei laufendem Docker Desktop:

```powershell
.\scripts\backup-local.ps1
```

Das Skript legt unter `backups/testryn-<Zeitstempel>/` einen PostgreSQL-Dump, die
Report-Dateien und SHA-256-Prüfsummen ab. `backups/` ist von Git ausgeschlossen.
Den erzeugten Ordner separat und sicher auf den neuen Rechner übertragen.

Auf dem neuen Rechner das Repository klonen, Docker Desktop starten und das Backup
wiederherstellen:

```powershell
git clone https://github.com/Neevel/Testryn.git
cd Testryn
.\scripts\restore-local.ps1 -BackupDirectory "C:\Pfad\zum\testryn-Backup" -Force
```

`restore-local.ps1` prüft zuerst die Prüfsummen. `-Force` ist absichtlich Pflicht,
weil der Restore die lokale Testryn-Datenbank und lokale Report-Dateien ersetzt. Die
Service-Token-Hashes werden mit übertragen; vorhandene rohe Token-Werte funktionieren
danach weiter. Vor dem Umzug deshalb mindestens einen funktionierenden Admin-Token
sicher aufbewahren. Ein `TESTRYN_BOOTSTRAP_TOKEN` greift nach dem Restore nicht, weil
bereits Service Tokens in der Datenbank existieren.

Rechnergebundene Secrets werden nicht im Backup oder in Git gespeichert. Vor dem
Start auf dem neuen Rechner daher erneut setzen:

```powershell
$env:TESTRYN_JIRA_BASE_URL="https://<tenant>.atlassian.net"
$env:TESTRYN_JIRA_EMAIL="<atlassian-email>"
$env:TESTRYN_JIRA_API_TOKEN="<klassisches-api-token>"
docker compose up -d
```

### Jira-Panel über einen lokalen Quick Tunnel erreichbar machen

Forge Cloud kann `localhost:8080` nicht direkt erreichen. Nach jedem Rechner- oder
Tunnel-Neustart einen neuen Tunnel öffnen und das Terminal geöffnet lassen:

```powershell
cloudflared tunnel --url http://localhost:8080
```

Die ausgegebene `https://<zufällig>.trycloudflare.com`-Adresse muss sowohl als
`TESTRYN_API_BASE_URL` gesetzt als auch in
`integrations/jira-forge/manifest.yml` unter
`permissions.external.fetch.backend` eingetragen werden. Danach:

```powershell
cd integrations/jira-forge
npm install
npx forge login
npx forge variables set -e development TESTRYN_API_BASE_URL "https://<zufällig>.trycloudflare.com"
npx forge deploy -e development --approve MAJOR_VERSION_RULE
npx forge install --upgrade -e development --site <tenant>.atlassian.net --product jira
```

Die echte Forge-App-ID und die verschlüsselten Forge-Variablen gehören zur bereits
registrierten Atlassian-App und bleiben bei Verwendung desselben Atlassian-Kontos
erhalten. Insbesondere muss `TESTRYN_API_TOKEN` nach einem vollständigen
Datenbank-Restore nicht neu gesetzt werden. Die neue Tunnel-Adresse ist dagegen bei
jedem Quick Tunnel zu aktualisieren.

## Architekturüberblick

Modularer Monolith (kein Microservices-Overengineering). Backend: Java 21+, Spring
Boot, Maven, Spring Data JPA, PostgreSQL, Liquibase. Frontend: React, TypeScript,
Vite. Fachliche Module: `project`, `testcase`, `requirement`, `testplan`,
`execution`, `report`, `integration.jira`. Begründung in [docs/adr/](docs/adr/).

Fachliche Daten (Projects, Test Cases, Versionen, Steps, Requirement Links, Test
Plans, Executions, Results, Report-Metadaten) liegen ausschließlich in PostgreSQL.
Binärdateien (Reports) laufen über eine austauschbare Storage-Abstraktion, lokal
dateisystembasiert auf einem persistenten Docker-Volume.

## Lokale Entwicklung ohne Docker Compose

### Backend

Voraussetzung: Java 21+, Maven, eine laufende PostgreSQL-Instanz.

```bash
cd backend
mvn spring-boot:run
```

Konfiguration über Environment Variables (siehe
`backend/src/main/resources/application.yml` für Defaults), u. a.:

```bash
TESTRYN_DB_URL=jdbc:postgresql://localhost:5432/testryn
TESTRYN_DB_USERNAME=testryn
TESTRYN_DB_PASSWORD=testryn
TESTRYN_STORAGE_BASE_PATH=./data/reports
# optional, für Jira-Anreicherung von Requirement Links (API-Token-Modus, Default):
TESTRYN_JIRA_BASE_URL=https://<tenant>.atlassian.net
TESTRYN_JIRA_EMAIL=<email>
TESTRYN_JIRA_API_TOKEN=<klassisches-api-token>
# optional, alternativer Auth-Modus: Jira Cloud OAuth 2.0 (3LO), siehe unten:
TESTRYN_JIRA_OAUTH_CLIENT_ID=<client id aus der Atlassian Developer Console>
TESTRYN_JIRA_OAUTH_CLIENT_SECRET=<client secret>
TESTRYN_JIRA_OAUTH_REDIRECT_URI=http://localhost:8080/integrations/jira/oauth/callback
TESTRYN_JIRA_OAUTH_ENCRYPTION_KEY=<Base64 von 32 Zufallsbytes>
# einmalig beim allerersten Start ohne bestehende Service Tokens -- siehe docs/security.md:
TESTRYN_BOOTSTRAP_TOKEN=<selbst gewählter Wert>
```

`TESTRYN_JIRA_BASE_URL`, `TESTRYN_JIRA_EMAIL`, Verbindungsname, Auth-Art und
Aktivstatus sind Startwerte. Sie können anschließend unter **Settings → Jira Cloud
Integration** ohne Neustart geändert werden. API-Token, OAuth-Client-Secret und
Verschlüsselungsschlüssel bleiben dagegen ausschließlich Server-Environment-
Variablen und werden nie an das Frontend ausgegeben.

#### Jira Cloud OAuth 2.0 (3LO) einrichten (ADR 0018)

Alternative zu `TESTRYN_JIRA_API_TOKEN`. Der API-Token-Modus bleibt vollständig als
Rückfall erhalten.

1. **OAuth-2.0-(3LO)-App** in der [Atlassian Developer Console](https://developer.atlassian.com/console/myapps/)
   anlegen → *Authorization* → *OAuth 2.0 (3LO)*.
2. **Scopes** hinzufügen: `read:jira-work` und `offline_access` (mehr braucht Testryn
   nicht — Issue-Lookup und Enrichment).
3. **Callback-URL** eintragen — sie muss exakt mit `TESTRYN_JIRA_OAUTH_REDIRECT_URI`
   übereinstimmen:
   - lokaler Docker-Betrieb: `http://localhost:8080/integrations/jira/oauth/callback`
   - über einen Tunnel: `https://<tunnel-host>/integrations/jira/oauth/callback`
     (dann denselben Wert auch als `TESTRYN_JIRA_OAUTH_REDIRECT_URI` setzen und den
     Tunnel-Host beim Verbindungstest verwenden).
4. **Client ID / Secret** aus der Console als `TESTRYN_JIRA_OAUTH_CLIENT_ID` /
   `TESTRYN_JIRA_OAUTH_CLIENT_SECRET` setzen.
5. **Verschlüsselungsschlüssel** erzeugen (AES-256, Base64 von 32 Bytes) und als
   `TESTRYN_JIRA_OAUTH_ENCRYPTION_KEY` setzen — z. B.
   `python -c "import os,base64;print(base64.b64encode(os.urandom(32)).decode())"`.
   Ohne diesen Schlüssel startet Testryn normal, aber jede OAuth-Aktion meldet
   „encryption key is not configured".
6. Backend neu starten, in **Settings → Jira Cloud Integration** die Jira-Cloud-URL
   speichern, **Auth type** auf *OAuth 2.0 (3LO)* stellen, **Connect with Atlassian**
   klicken und im Atlassian-Dialog zustimmen. Testryn wählt automatisch die zur
   konfigurierten Site passende Cloud-ID aus `accessible-resources`.
7. **Disconnect** entfernt nur die gespeicherten OAuth-Credentials — Test Cases,
   Requirement Links und Jira-Issues bleiben unberührt.

Access- und Refresh-Token werden AES-256-GCM-verschlüsselt in PostgreSQL abgelegt
(nie im Klartext), der Refresh-Token wird bei jeder Erneuerung rotiert. Secrets
erscheinen nie in API-Antworten, Logs oder im Frontend.

Die API ist ab diesem Block durchgängig durch Service Tokens geschützt (ADR 0012) --
Details, Scopes und Bootstrap-Verfahren: [docs/security.md](docs/security.md).

### Frontend

Voraussetzung: Node.js 20+.

```bash
cd frontend
npm install
npm run dev
```

Die Vite-Dev-Instanz erwartet das Backend standardmäßig unter
`http://localhost:8080` (siehe `frontend/.env`/`vite.config.ts`).

## API-Dokumentation

Die REST-API ist API-first entworfen — jede Funktion der UI ist auch direkt über die
API nutzbar. Vollständige, generierte Referenz zur Laufzeit unter
`/swagger-ui.html` bzw. `/v3/api-docs`. Grober Überblick über die Ressourcen:

```
/api/v1/projects
/api/v1/projects/{projectKey}/test-cases
/api/v1/test-cases/{id}
/api/v1/test-cases/{id}/versions
/api/v1/test-cases/{id}/requirements
/api/v1/test-plans
/api/v1/test-plans/{id}/executions
/api/v1/executions/{id}
/api/v1/executions/{id}/results/{resultId}
/api/v1/executions/{id}/results        (bulk update, for CI pipelines -- see docs/ci-integration.md)
/api/v1/executions/{id}/reports
/api/v1/requirement-links/coverage     (provider-neutral coverage view, e.g. for the Jira Forge panel -- see docs/jira-forge-integration.md)
/api/v1/service-tokens                 (auth management, requires testryn:admin -- see docs/security.md)
```

Jeder Aufruf braucht einen gültigen Service Token (`Authorization: Bearer ...`) --
siehe [docs/security.md](docs/security.md).

## Testausführung

```bash
cd backend
mvn test
```

Integrationstests (Repository/REST) nutzen Testcontainers und starten dafür
automatisch eine temporäre PostgreSQL-Instanz — Docker muss dafür laufen.

```bash
cd frontend
npm run test
```

```bash
cd tools/testryn-publisher
mvn test
```

## CI-Integration

Wie eine externe Pipeline automatisierte Testergebnisse an Testryn meldet (Test-Case-
Mapping über `automationReference`, Bulk-Result-Update-API, das `testryn-publisher`-
CLI-Tool): [docs/ci-integration.md](docs/ci-integration.md).

## Jira Forge Issue Panel

`integrations/jira-forge` ist eine eigenständige Atlassian-Forge-App: ein read-only
Testryn-Coverage-Panel direkt in der Jira-Story/Task/Bug-Ansicht (verknüpfte Test
Cases, Steps, Expected Results, letzter Execution-Status). Testryn bleibt Source of
Truth, Jira speichert keine Kopie der Testdaten. Architektur, Setup, Deployment,
Security: [docs/jira-forge-integration.md](docs/jira-forge-integration.md),
Entscheidung: [ADR 0014](docs/adr/0014-jira-forge-integration.md).
