# AGENTS.md — Arbeitsanweisung für KI-Agenten

Dieses Dokument ist verbindlich für jeden Agenten (menschlich oder KI), der an Testryn
arbeitet. Es beschreibt, warum Testryn existiert, wie es aufgebaut ist und wie daran
gearbeitet werden darf. Bei Widerspruch zwischen diesem Dokument und einer Einzelanweisung
gilt: erst analysieren, dann klären, nie stillschweigend abweichen.

## 1. Produktvision

Testryn ist eine eigenständige Testmanagement-Plattform. Testfälle, Testpläne,
Executions und deren Historie leben in Testryn — nicht in Jira, nicht in losen Dateien.

Jira (und später andere Tools) ist ausschließlich Requirement-/Task-/Bug-Quelle. Testryn
referenziert Requirements über einen generischen `RequirementLink`, ist aber fachlich
niemals von Jira abhängig. Das Core-Domain-Modell kennt kein `JiraRequirement`.

Zielbild des Kern-Workflows:

```
Jira Story ──▶ Testryn: Test Case (Steps, Expected Results, Version)
                   │
                   ├─▶ Requirement Link (→ Jira Issue)
                   ├─▶ Test Plan (Sammlung wiederverwendbarer Test Cases)
                   └─▶ Execution (Snapshot einer Plan-Ausführung)
                            └─▶ Execution Result (PASS/FAIL/SKIPPED/BLOCKED/NOT_RUN)
                                     └─▶ Report Upload (Metadaten + Dateireferenz)
```

Ein KI-Agent soll später u. a. diesen Auftrag ausführen können: "Lies BIT-27 in Jira,
leite E2E-Testfälle ab, prüfe auf Duplikate, lege fehlende Test Cases in Testryn an,
verknüpfe sie mit BIT-27." Dafür muss die REST-API vorhersehbar, dokumentiert,
möglichst idempotent und stabil sein — das ist ein Designziel, kein Zusatzfeature.

## 2. Architekturprinzipien

- **API-first.** Jede Geschäftsregel muss über die REST-API erreichbar sein. Die UI ist
  ein Client wie jeder andere und enthält selbst keine Geschäftslogik.
- **Keine losen Dateien als fachlicher Primärspeicher.** Projects, Test Cases, Versionen,
  Steps, Requirement Links, Test Plans, Executions, Results und Report-*Metadaten* leben
  in PostgreSQL. Binärdateien (Reports, Anhänge) laufen über eine Storage-Abstraktion
  (`ReportStorage`), lokal filesystembasiert, später S3-kompatibel austauschbar — ohne
  das Domain-Modell anzufassen.
- **Domain first.** Controller enthalten keine Geschäftslogik, nur Mapping/Validierung/
  Delegation. Services enthalten die fachlichen Regeln. Persistenz-Entities sind nicht
  identisch mit API-DTOs — Mapping erfolgt explizit in der Web-Schicht.
- **Test-Case-Versionierung ist ab Tag 1 Pflicht**, nicht nachträglich anflanschbar.
  Ändert sich ein Test Case, entsteht eine neue `TestCaseVersion`. Bestehende
  Executions referenzieren die zum Zeitpunkt ihrer Erzeugung gültige Version und bleiben
  unveränderlich. Siehe [docs/adr/0002-test-case-versioning.md](docs/adr/0002-test-case-versioning.md).
- **Executions sind Snapshots**, keine Live-Referenzen auf den aktuellen Planzustand.
  Jede neue Ausführung eines Plans ist eine eigenständige historische Iteration. Siehe
  [docs/adr/0003-plan-execution-snapshot.md](docs/adr/0003-plan-execution-snapshot.md).
- **Manuelle und automatisierte Ergebnisse teilen sich ein Result-Modell.** Der
  Ausführungsweg unterscheidet sich, das fachliche Ergebnis (`ExecutionResult`) nicht.
- **Modularer Monolith**, fachlich geschnitten: `project`, `testcase`, `requirement`,
  `testplan`, `execution`, `report`, `integration`. Keine geteilten
  `controller/service/repository/model`-Sammel-Packages über alle Fachbereiche hinweg.
  Jedes Modul hat intern `domain` (Entities/Enums), `repository`, `service`, `web`
  (Controller + DTOs).
- **Requirement-Provider-Abstraktion.** `RequirementProvider` ist das Interface,
  `JiraRequirementProvider` eine austauschbare Implementierung. Keine Jira-spezifische
  Logik außerhalb des `integration.jira`-Pakets.

## 3. Arbeitsweise

1. Architektur/Auswirkung zuerst analysieren, dann implementieren.
2. Kein Overengineering: nur bauen, was der aktuelle Meilenstein braucht. Kein
   Event-Sourcing, keine Microservices, kein Kafka/Kubernetes/CQRS ohne konkreten Bedarf.
3. Bestehende Architektur respektieren. Abweichungen erfordern eine kurze Begründung
   (Commit-Message oder ADR), keine stillschweigenden Umbauten "nebenbei".
4. Keine Architekturentscheidungen versteckt in Refactoring-Commits.
5. Ein Task = ein logisch zusammenhängender Commit. Kein Sammel-Commit über mehrere
   unabhängige Änderungen.
6. Tests sind Pflicht für Produktionscode (Domain-Unit-Tests, Service-Tests,
   Repository-Integrationstests mit Testcontainers, REST-API-Integrationstests).
7. `PROJECT_STATUS.md` bei jedem abgeschlossenen, relevanten Task aktualisieren.
   `BACKLOG.md` pflegen statt halbfertigen Code liegen zu lassen.
8. Keine neuen Abhängigkeiten ohne fachlichen/technischen Grund.
9. Keine Remote-Pushes ohne explizite Freigabe durch den Menschen.
10. Rückfragen nur bei: grundlegender Änderung der öffentlichen Architektur,
    widersprüchlichen Anforderungen, benötigten Credentials/externen Zugängen,
    irreversiblen externen Aktionen. Alles andere selbstständig entscheiden und
    dokumentieren.

## 4. Domain-Grenzen (Module)

| Modul | Verantwortung |
|---|---|
| `project` | Projects als logische Testmanagement-Bereiche |
| `testcase` | Test Cases, Test Case Versionen, Test Steps |
| `requirement` | RequirementLink, RequirementProvider-Abstraktion |
| `testplan` | Test Plans, Zuordnung von Test Cases |
| `execution` | Executions (Snapshots), Execution Results |
| `report` | Report-Metadaten, Storage-Abstraktion (`ReportStorage`) |
| `integration.jira` | Konkrete Jira-Anbindung (Implementierung von `RequirementProvider`) |
| `common` | Querschnitt: Fehlerbehandlung, IDs, Konfiguration, API-Basisklassen |

Ein Modul darf ein anderes Modul nur über dessen `service`- oder öffentliche
`domain`-Schnittstellen ansprechen, nie über dessen Repository oder interne DTOs.

## 5. Technologie

Siehe [docs/adr/0001-persistence-strategy.md](docs/adr/0001-persistence-strategy.md) und
`README.md` für die vollständige Begründung. Kurzfassung:

- Backend: Java 21+, Spring Boot, Maven, Spring Web, Spring Data JPA, Bean Validation,
  PostgreSQL, Liquibase, springdoc-openapi, JUnit 5, AssertJ, Testcontainers.
- Frontend: React, TypeScript, Vite.
- Infrastruktur: Docker Compose (PostgreSQL, Backend, Frontend) für lokale Entwicklung.

## 6. Sicherheit (MVP-Niveau)

- Keine Secrets im Repository — Environment Variables / `.env` (gitignored).
- Upload: Content-Type- und Größenvalidierung, serverseitig generierte Storage-Keys
  (kein Client-kontrollierter Dateipfad).
- Alle IDs serverseitig validieren, keine ungefilterten Stacktraces/Fehlerdetails an
  Clients.
- Authentifizierung bewusst einfach im MVP, aber architektonisch erweiterbar (kein
  Enterprise-Rollenmodell erzwingen).

## 7. Out of Scope (MVP)

Siehe `BACKLOG.md` → Later. Nicht bauen: Atlassian Marketplace App, eigener MCP Server,
komplexe AI Engine, automatische Testgenerierung im Backend, LDAP/SAML/Enterprise-SSO,
Multi-Tenancy-SaaS, Billing, Kubernetes, komplexe Dashboards, Excel-Designer,
PDF-Reporting-Engine, umfangreiche Plugin-Plattform, native Mobile App, eigene Test
Execution Engine / Selenium Runner.
