# CSC X Tool

Lokale Einzelbenutzer-Anwendung zur Unterstützung der Teilnahme am CyBoard Song Contest (CSC).

Das Tool begleitet den praktischen Ablauf einer Mottoshow:

1. Kandidaten sammeln, anhören, kommentieren und priorisieren
2. die eigene Einreichung festlegen
3. die anonymen Beiträge der anderen Teilnehmer direkt aus dem formatierten CSC-Beitragsblock per Zwischenablage importieren und anhören
4. eine eindeutige persönliche Top 15 per Drag-and-drop erstellen
5. nach Abschluss der Abstimmung Beiträge den Teilnehmern zuordnen
6. die für die eigene Einreichung erhaltenen Punkte erfassen
7. Gesamtpunktzahl und Endplatzierung dokumentieren

## Projektstatus

Produktspezifikation, technische Architektur und phasenweiser Implementierungsplan sind festgelegt. Die Entwicklung beginnt mit dem technischen Bootstrap aus [Issue #4](https://github.com/venomenon328/csc-x-tool/issues/4); Anwendungscode existiert derzeit noch nicht.

## Festgelegte Grundrichtung

- lokal laufende Webanwendung
- Bedienung im Standardbrowser, mit Vivaldi als primärem Kompatibilitätsziel
- dunkle, moderne und desktoporientierte Oberfläche
- Spring-Boot-Backend und React-/TypeScript-Frontend
- SQLite als lokale Datenbank
- keine Anmeldung und keine Benutzerverwaltung
- komfortabler Windows-Launcher, der die Anwendung startet und den Browser öffnet
- genau eine CSC-Ausgabe mit zwölf Mottoshows
- Beitragsimport per normalem Browser-Paste-Event mit bevorzugter Auswertung formatierter Linkdaten (`text/html`)

## Dokumentation

- [Produktspezifikation](docs/specification.md)
- [Technische Architektur](docs/architecture.md)
- [Implementierungsplan](docs/implementation-plan.md)
- [Entscheidungsprotokoll](docs/decisions.md)
- [Abgrenzung der fachlichen Quellen](docs/reference/README.md)
- [GitHub-Roadmap bis 0.1.0](https://github.com/venomenon328/csc-x-tool/issues/3)

## Geplante Repository-Struktur

```text
.
├── backend/       Spring Boot, REST-API, SQLite und Datensicherung
├── frontend/      React, TypeScript und Benutzeroberfläche
├── launcher/      Windows-Start, Browseröffnung und Paketierung
└── docs/          Spezifikation, Architektur, Entscheidungen und Implementierungsplan
```

## Bewusste Abgrenzung

Das Tool recherchiert keine Kandidaten, prüft keine Mottoregeln und gleicht Songs nicht gegen die CSC-Ausschlussliste ab. Diese Arbeit findet vor dem Eintragen außerhalb der Anwendung statt. Ebenfalls nicht verwaltet werden die vollständigen Abstimmungen oder Ergebnisse anderer Teilnehmer.

## Nächster sinnvoller Schritt

Der nächste Arbeitsauftrag ist [P0: Technischen Bootstrap und gemeinsamen Build aufsetzen](https://github.com/venomenon328/csc-x-tool/issues/4). Danach folgen SQLite, Liquibase und die persistente Mottoshow-Übersicht in [P1](https://github.com/venomenon328/csc-x-tool/issues/5).
