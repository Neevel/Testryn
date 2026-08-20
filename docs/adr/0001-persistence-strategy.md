# ADR 0001: Persistenzstrategie

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

Testryn verwaltet Testmanagement-Daten (Projects, Test Cases, Versionen, Steps,
Requirement Links, Test Plans, Executions, Results, Report-Metadaten), die
nachvollziehbar, konsistent und dauerhaft historisch abfragbar sein müssen. Die
Vorgabe schließt lose Dateien (JSON/YAML/CSV/Report-Verzeichnisse) als primären
fachlichen Speicher ausdrücklich aus.

## Entscheidung

- Relationales Schema in **PostgreSQL** als alleinige fachliche Datenhaltung für alle
  in Abschnitt 3 des Produktauftrags genannten Entitäten.
- **Spring Data JPA / Hibernate** als ORM, da es zum gewählten Java/Spring-Stack passt
  und Team-bekannt ist. Domain-Invarianten (z. B. Unveränderlichkeit historischer
  Executions) werden auf Service-Ebene erzwungen, nicht allein über JPA-Mapping.
- **Liquibase** für versionierte, nachvollziehbare Schema-Migrationen (Changelogs unter
  `backend/src/main/resources/db/changelog`). Migrationen sind additiv; Breaking
  Changes am Schema erfordern eine bewusste Analyse (siehe AGENTS.md).
- Binärdaten (Reports, Anhänge) werden **nicht** in der Datenbank abgelegt. Es wird nur
  die Storage-Referenz (Key, Content-Type, Größe, Checksumme) persistiert; das Byte-
  Material läuft über die `ReportStorage`-Abstraktion (siehe ADR 0004).
- Jede Tabelle erhält eine technische UUID als Primärschlüssel. Menschenlesbare IDs
  (z. B. `BITLESS-TC-42`) sind ein zusätzliches, stabiles Attribut, kein Primärschlüssel
  — technische Referenzen (Foreign Keys, API-Pfade) nutzen die UUID.

## Konsequenzen

- Lokale Entwicklung und CI benötigen eine laufende PostgreSQL-Instanz (Docker Compose
  bzw. Testcontainers in Tests).
- Migrationen müssen bei jeder Schemaänderung mitgeliefert werden; es gibt keinen
  `ddl-auto: update`-Automatismus in Produktion/CI.
- Ein Wechsel des Objektspeichers (z. B. auf S3) berührt das relationale Schema nicht,
  da nur Referenzen gespeichert werden.

## Alternativen (verworfen)

- **Dateibasierte Ablage (JSON/YAML) als Primärspeicher**: explizit durch den
  Produktauftrag ausgeschlossen; keine transaktionale Konsistenz, keine sinnvollen
  Abfragen über Historie/Verknüpfungen.
- **Dokumentenorientierte Datenbank (z. B. MongoDB)**: Testryn-Domäne ist stark
  relational (Projekt → Test Case → Version → Step, Execution → Result, Plan ↔ Test
  Case). Ein relationales Modell bildet das nativ und mit referenzieller Integrität ab.
