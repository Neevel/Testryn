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
# optional, für Jira-Anreicherung von Requirement Links:
TESTRYN_JIRA_BASE_URL=https://<tenant>.atlassian.net
TESTRYN_JIRA_EMAIL=<email>
TESTRYN_JIRA_API_TOKEN=<token>
# einmalig beim allerersten Start ohne bestehende Service Tokens -- siehe docs/security.md:
TESTRYN_BOOTSTRAP_TOKEN=<selbst gewählter Wert>
```

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
