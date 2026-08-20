# PROJECT_STATUS.md

> Momentaufnahme des aktuellen technischen Stands. Keine Historie — siehe Git-Log für
> Verlauf. Wird bei jedem abgeschlossenen, relevanten Task aktualisiert.

Stand: 2026-08-20

## Aktueller Meilenstein

Meilenstein 1 (siehe Produktauftrag Abschnitt 15): Der komplette Kern-Workflow
(Project → Test Case → Requirement Link → Test Plan → Execution → Result → Report →
Test-Case-Änderung → neue Execution mit stabiler Historie) muss über UI, REST-API und
automatisierte Tests funktionieren.

**Phase:** Architektur & Grundgerüst abgeschlossen, Implementierung läuft.

## Implementierte Features

- Repository initialisiert (Git), Projektstruktur (`backend/`, `frontend/`, `docs/`)
  angelegt.
- Architekturentscheidungen dokumentiert (ADR 0001–0005, siehe `docs/adr/`).
- `AGENTS.md`, `BACKLOG.md`, `README.md` angelegt.
- Backend/Frontend-Implementierung: siehe Checkliste unten (wird laufend aktualisiert).

## Architekturstand

Modularer Monolith (Spring Boot, Maven, PostgreSQL, Liquibase) + React/TS/Vite-
Frontend, Docker Compose für lokale Entwicklung. Details: `README.md`, `AGENTS.md`,
`docs/adr/`.

Module: `project`, `testcase`, `requirement`, `testplan`, `execution`, `report`,
`integration.jira`, `common`.

## Aktuelles Datenmodell

Siehe ADR 0001–0005 für die fachliche Begründung. Kern-Entities (Details im
Liquibase-Changelog `backend/src/main/resources/db/changelog`):

- `Project`
- `TestCase`, `TestCaseVersion`, `TestStep`
- `RequirementLink`
- `TestPlan`, `TestPlanEntry`
- `Execution`, `ExecutionTestCase` (Snapshot), `ExecutionResult`
- `Report`

## Teststatus

Wird nach erster Implementierungsrunde befüllt.

## Bekannte Einschränkungen / technische Schulden

- Authentifizierung ist im MVP bewusst minimal (kein Enterprise-Rollenmodell).
- Report-Interpretation (JUnit/TestNG/Allure-Parsing) ist noch nicht implementiert —
  MVP nimmt Dateien nur entgegen und ordnet sie zu.
- Jira-Integration liest Issues nur lesend zur Anreicherung von Requirement Links;
  keine Rückschreib-Synchronisation nach Jira.

## Nächster sinnvoller Schritt

Backend-Grundgerüst (Maven-Projekt, Spring-Boot-Bootstrap, Liquibase-Basis-Changelog,
Docker Compose mit PostgreSQL) aufsetzen, danach Domain-Modul für `project` und
`testcase` inkl. Versionierung implementieren.
