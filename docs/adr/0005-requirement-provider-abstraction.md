# ADR 0005: Requirement-Provider-Abstraktion

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

Test Cases sollen mit externen Requirements (zunächst Jira-Issues, später ggf. GitHub
Issues oder Azure DevOps Work Items) verknüpft werden können. Das Core-Domain-Modell
darf dabei nicht auf Jira zugeschnitten sein — Testryn muss fachlich funktionieren,
auch wenn Jira nicht erreichbar oder gar nicht konfiguriert ist.

## Entscheidung

- Modul `requirement` definiert die generische Entity `RequirementLink`
  (`testCaseId`, `provider`, `externalId`, `externalKey`, `url`, optional `summary`,
  `createdAt`). `provider` ist ein erweiterbares Enum/String (`JIRA`, künftig z. B.
  `GITHUB`, `AZURE_DEVOPS`), kein eigener Tabellensatz pro Anbieter.
- Modul `requirement` definiert außerdem das Interface `RequirementProvider`
  (Paket `com.testryn.requirement.provider`):
  - `RequirementProviderType type()`
  - `Optional<ExternalRequirementInfo> fetch(String externalKey)` — liefert, falls
    konfiguriert und erreichbar, Anzeige-Informationen (Summary, URL) zu einem
    externen Key; liefert `Optional.empty()`, wenn nicht verfügbar, statt hart zu
    scheitern.
- `POST /test-cases/{id}/requirements` legt einen `RequirementLink` **immer** anhand
  der vom Aufrufer übergebenen Daten (`provider`, `externalKey`, `url`, optional
  `summary`) an. Ein Live-Abruf über `RequirementProvider#fetch` ist eine optionale
  Anreicherung (fehlende/leere Summary automatisch ergänzen), keine Voraussetzung für
  das Anlegen des Links.
- Konkrete Anbieter-Implementierungen liegen im Modul `integration.jira`
  (`JiraRequirementProvider`), das `RequirementProvider` implementiert und per Spring
  als Bean für `RequirementProviderType.JIRA` registriert wird. Zugangsdaten
  ausschließlich über Environment Variables (`TESTRYN_JIRA_BASE_URL`,
  `TESTRYN_JIRA_EMAIL`, `TESTRYN_JIRA_API_TOKEN`), niemals im Repository.
- Ist keine Jira-Konfiguration gesetzt, ist `JiraRequirementProvider` inaktiv;
  `RequirementLink`-Erstellung funktioniert davon unabhängig weiter (Kernprinzip:
  Testryn ist nicht von Jira abhängig).

## Konsequenzen

- Ein weiterer Provider (z. B. GitHub) bedeutet: neues Modul `integration.github`,
  neue Implementierung von `RequirementProvider`, neuer Enum-Wert — keine Änderung an
  `testcase`, `requirement`-Domain oder der öffentlichen API-Form.
- Die Domain kennt keine Jira-spezifischen Felder (kein `jiraIssueKey`,
  `jiraProjectId` o. Ä.) — alles läuft über die generischen `RequirementLink`-Felder.

## Alternativen (verworfen)

- **Direkte Jira-Kopplung im `testcase`- oder `requirement`-Modul** (z. B. eigenes
  `JiraRequirement`-Entity, wie in Abschnitt 4.5 des Produktauftrags explizit
  ausgeschlossen): verhindert spätere Multi-Provider-Fähigkeit und verletzt die
  Vorgabe, dass Testryn nicht fachlich von Jira abhängen darf.
