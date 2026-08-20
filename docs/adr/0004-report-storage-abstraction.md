# ADR 0004: File-/Report-Storage-Abstraktion

- Status: Angenommen
- Datum: 2026-08-20

## Kontext

Reports (zunächst rohe Upload-Dateien, später z. B. JUnit-XML/Allure), Screenshots,
Logs und andere Anhänge sind Binärdaten. Sie gehören nicht in die relationale
Datenbank (ADR 0001), müssen aber zuverlässig einer Execution zugeordnet, referenziert
und wieder heruntergeladen werden können. Lokale Entwicklung soll ohne externe Cloud-
Abhängigkeit funktionieren; produktiv soll später S3-kompatibler Object Storage
möglich sein, ohne das Domain-Modell umzubauen.

## Entscheidung

- Modul `report` definiert das Interface `ReportStorage` (Paket
  `com.testryn.report.storage`):
  - `StoredObject store(String projectKey, String originalFilename, String
    contentType, InputStream content, long size)`
  - `Resource load(String storageKey)`
  - `void delete(String storageKey)` (für spätere Aufräum-Jobs vorgesehen, im MVP nicht
    zwingend genutzt)
- `StoredObject` kapselt `storageKey`, `size`, `checksum` — reine Werte, keine
  Infrastrukturdetails nach außen.
- MVP-Implementierung: `FilesystemReportStorage`, die Dateien unterhalb eines
  konfigurierbaren Basisverzeichnisses ablegt (`testryn.storage.base-path`, per Docker
  Compose auf ein persistentes Volume gemountet). Der `storageKey` wird serverseitig
  generiert (UUID-basiert + Originalendung) — **niemals** ein vom Client übergebener
  Pfad, um Path-Traversal auszuschließen.
- Die Datenbank speichert ausschließlich Metadaten (`Report`-Entity: `executionId`,
  `filename`, `contentType`, `size`, `storageKey`, `uploadedAt`, `checksum`). Der
  tatsächliche Dateiinhalt ist über `ReportStorage#load(storageKey)` erreichbar.
- Ein späterer `S3ReportStorage` (oder MinIO-kompatibel) implementiert dasselbe
  Interface; nur die Spring-Konfiguration (`testryn.storage.type`) und die
  Bean-Auswahl ändern sich, Domain und API bleiben unverändert.

## Konsequenzen

- Kein Vendor-Lock-in auf Dateisystem oder eine bestimmte Cloud, da der Rest des
  Systems ausschließlich gegen das `ReportStorage`-Interface programmiert.
- Upload-Endpunkte validieren Content-Type und Größe vor dem Speichern; der
  Originaldateiname wird nur als Metadatum übernommen, nie als Pfadbestandteil
  verwendet.
- Für den MVP existiert bewusst noch keine automatische Interpretation von
  Report-Inhalten (z. B. JUnit-XML-Parsing). Das Interface ist aber so geschnitten,
  dass ein späterer `ReportImporter` (JUnit/TestNG/Playwright/Cypress/Allure) auf den
  über `ReportStorage` verfügbaren Bytes aufsetzen kann, ohne die Storage-Schicht
  anzufassen.

## Alternativen (verworfen)

- **Reports direkt als BLOB in PostgreSQL**: verletzt ADR 0001 (keine Binärdaten in der
  relationalen Primärdatenhaltung) und skaliert schlecht bei großen Reports.
- **Sofortige S3-Anbindung im MVP**: zusätzliche Infrastruktur-Abhängigkeit ohne
  aktuellen Bedarf; die Abstraktion stellt sicher, dass der Wechsel später ein reiner
  Konfigurations-/Implementierungsaustausch ist.
