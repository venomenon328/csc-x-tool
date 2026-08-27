# CSC X Tool

Lokale Einzelbenutzer-Anwendung zur Unterstützung der Teilnahme am CyBoard Song Contest (CSC).

Das Tool begleitet den praktischen Ablauf einer Mottoshow:

1. Kandidaten sammeln, anhören, kommentieren und priorisieren
2. die eigene Einreichung festlegen
3. die anonymen Beiträge der anderen Teilnehmer importieren und anhören
4. eine eindeutige persönliche Top 15 per Drag-and-drop erstellen
5. nach Abschluss der Abstimmung Beiträge den Teilnehmern zuordnen
6. die für die eigene Einreichung erhaltenen Punkte erfassen
7. Gesamtpunktzahl und Endplatzierung dokumentieren

## Projektstatus

Das Repository befindet sich in der Spezifikations- und Bootstrap-Phase. Produktumfang, Bedienmodell und technische Grundrichtung sind dokumentiert; Anwendungscode existiert noch nicht.

## Festgelegte Grundrichtung

- lokal laufende Webanwendung
- Bedienung im Standardbrowser, mit Vivaldi als primärem Kompatibilitätsziel
- dunkle, moderne und desktoporientierte Oberfläche
- Spring-Boot-Backend und React-/TypeScript-Frontend
- SQLite als lokale Datenbank
- keine Anmeldung und keine Benutzerverwaltung
- komfortabler Windows-Launcher, der die Anwendung startet und den Browser öffnet
- genau eine CSC-Ausgabe mit zwölf Mottoshows

## Dokumentation

- [Produktspezifikation](docs/specification.md)
- [Technische Architektur](docs/architecture.md)
- [Entscheidungsprotokoll](docs/decisions.md)
- [Abgrenzung der fachlichen Quellen](docs/reference/README.md)

## Geplante Repository-Struktur

```text
.
├── backend/       Spring Boot, REST-API, SQLite und Datensicherung
├── frontend/      React, TypeScript und Benutzeroberfläche
├── launcher/      Windows-Start, Browseröffnung und Paketierung
└── docs/          Spezifikation, Architektur und Entscheidungen
```

## Bewusste Abgrenzung

Das Tool recherchiert keine Kandidaten, prüft keine Mottoregeln und gleicht Songs nicht gegen die CSC-Ausschlussliste ab. Diese Arbeit findet vor dem Eintragen außerhalb der Anwendung statt. Ebenfalls nicht verwaltet werden die vollständigen Abstimmungen oder Ergebnisse anderer Teilnehmer.

## Nächster sinnvoller Schritt

Nach Freigabe der Spezifikation folgt der technische Bootstrap des Monorepos mit Datenbankschema, Backend-Grundgerüst, Frontend-Shell und erstem ausführbaren lokalen Paket.
