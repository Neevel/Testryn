# ADR 0008: Test-Case-Suche und einheitliches Pagination-Envelope

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

`GET /api/v1/projects/{projectKey}/test-cases` lieferte bislang ausnahmslos alle Test
Cases eines Projekts als einfaches JSON-Array. Für produktive Nutzung (UI-Tabelle mit
Filtern, ein KI-Agent, der vor dem Anlegen eines Test Cases nach ähnlichen sucht,
Abschnitt 10/11) reicht das nicht: es fehlen Suchparameter (ID/Titel, Tag,
Requirement-Key, Status, Priority) und Pagination.

## Entscheidung

- **Derselbe Endpunkt** (`GET /projects/{projectKey}/test-cases`) übernimmt Suche und
  Pagination — kein separater `/search`-Pfad. Query-Parameter, alle optional:
  `query` (Volltext gegen menschenlesbare ID und aktuellen Titel, case-insensitive,
  Teilstring), `tag`, `requirementKey`, `status`, `priority`, `page` (0-basiert,
  Default 0), `size` (Default 20, Max 100).
- **Response-Shape ändert sich** von einem bloßen Array zu einem Envelope
  `{content, page, size, totalElements, totalPages}` (`com.testryn.common.web.PageResponse`).
  Das ist eine bewusste Breaking Change am bestehenden Endpoint — gerechtfertigt, weil
  ohne Envelope weder Gesamtzahl noch Pagination-Metadaten transportierbar sind und
  ein zweiter, parallel gepflegter Endpoint nur für Pagination unnötige Duplikation
  wäre. Alle Clients (Frontend, zukünftige KI-Agenten) müssen auf `content` statt der
  Root-Antwort zugreifen. `PageResponse` ist bewusst generisch benannt — künftige
  weitere paginierte Listen-Endpunkte sollen dieselbe Hülle verwenden, keine eigene
  erfinden.
- **Technisch**: `TestCaseRepository` implementiert zusätzlich
  `JpaSpecificationExecutor<TestCase>`; `TestCaseSpecifications` baut die optionalen
  Filter dynamisch zusammen (Spring-Data-Standardmechanismus für optionale
  Mehrfeld-Suchen — kein Hand-rollen von String-verketteten JPQL-Queries). Der
  Requirement-Key-Filter nutzt eine Subquery auf `requirement_links` statt einer
  bidirektionalen `TestCase.requirementLinks`-Assoziation im Domain-Modell — die
  Domain bleibt unverändert, wo eine Subquery genügt (Abschnitt 1: "Bestehende
  Domain-Modelle nur ändern, wenn fachlich notwendig").
- **Kein Collection-Fetch-Join mit Pagination**: Ein Fetch-Join auf
  `currentVersion.steps` zusammen mit `firstResult/maxResults` würde Hibernate zur
  In-Memory-Pagination über das komplette Ergebnis zwingen (bekannte JPA/Hibernate-
  Einschränkung) und die Pagination damit faktisch aushebeln. Die Such-Query fetcht
  daher nur `project`/`currentVersion` eager; `currentVersion.steps` wird pro
  Ergebnis-Seite (nicht pro Gesamtdatensatz) explizit mit `Hibernate.initialize(...)`
  nachgeladen — ein bewusst auf die Seitengröße begrenztes N+1, kein unbegrenztes.

## Konsequenzen

- Ein Frontend/Client, der den kompletten Bestand ohne Filter braucht, ruft weiterhin
  denselben Endpoint auf, ggf. mit größerem `size` (bis 100) oder mehreren Seiten.
- Für sehr große Projekte (>100 Test Cases) muss ein Client jetzt paginieren; für den
  MVP-Maßstab ist das unkritisch.
- Ein KI-Agent kann `GET /projects/{key}/test-cases?query=login` zur
  Duplicate-Prüfung nutzen, wie in Abschnitt 11 des Auftrags beispielhaft gefordert.

## Alternativen (verworfen)

- **Neuer, separater `/test-cases/search`-Endpoint neben dem bestehenden
  Listen-Endpoint**: hätte zwei parallele, teils redundante Endpunkte mit
  unterschiedlichem Antwortformat erzeugt — schlechter für "vorhersehbare DTOs"
  (Abschnitt 9) als ein einziger, konsistent paginierter Endpoint.
- **Elasticsearch/Volltextsuchindex**: für MVP-Datenmengen (Hunderte, nicht
  Millionen Test Cases pro Projekt) unnötige Infrastruktur; explizit durch Abschnitt
  10 ausgeschlossen ("keine komplexe Suchsprache im MVP").
