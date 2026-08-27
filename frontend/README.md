# Frontend

Dieser Ordner ist für die React-/TypeScript-Oberfläche des CSC X Tool vorgesehen.

## Ziele

- moderne, konsistente Dark-UI
- sehr gute Bedienbarkeit in Vivaldi
- desktoporientiertes Layout
- schnelle Inline-Aktionen
- robuste Höransichten mit YouTube-Fallback
- komfortables Drag-and-drop für Kandidaten und Ranglisten

## Geplante Struktur

```text
src/
├── app/                         Routing, Layout und globale Provider
├── api/                         REST-Client und Fehlerbehandlung
├── components/                  wiederverwendbare UI-Bausteine
├── features/
│   ├── shows/
│   ├── candidates/
│   ├── participants/
│   ├── entries/
│   ├── ballot/
│   ├── results/
│   └── data-management/
└── styles/                      Theme und globale Gestaltung
```

## Geplante Routen

```text
/                              Übersicht
/participants                  Teilnehmer
/data                          Sicherungen und Exporte
/shows/:showId/candidates      Kandidaten
/shows/:showId/voting          Abstimmung
/shows/:showId/result          Ergebnis
```

## Drag-and-drop

Die endgültige Bibliothek wird nach einem kleinen UI-Spike ausgewählt. Entscheidend sind:

- mehrere Listen und Wechsel zwischen Listen
- Auto-Scroll
- klarer Einfügeindikator
- stabile Darstellung bei ungefähr 30 Einträgen
- sichtbare Grenze zwischen Platz 15 und 16
- optimistische Darstellung mit sauberem Rollback bei Serverfehlern

Ein Reorder wird nach dem Drop atomar an das Backend übertragen; nicht während jeder überfahrenen Zwischenposition.

## YouTube

- eingebetteter Player, sofern möglich
- externer Link immer sichtbar
- Einbettungsfehler nur lokal im Playerbereich
- keine YouTube-API und kein API-Schlüssel erforderlich

## Noch nicht enthalten

Der Dokumentations-Bootstrap legt noch kein `package.json`, keine Komponentenbibliothek und keine konkrete Drag-and-drop-Abhängigkeit fest. Diese Entscheidungen werden im Frontend-Bootstrap anhand eines kleinen, bedienbaren Prototyps getroffen.
