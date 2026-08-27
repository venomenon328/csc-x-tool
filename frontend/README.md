# Frontend

Dieses Modul enthält die React-/TypeScript-/Vite-Oberfläche des CSC X Tool.

## Ziele

- moderne, konsistente Dark-UI
- sehr gute Bedienbarkeit in Chromium-basierten Desktopbrowsern
- desktoporientiertes Layout
- schnelle Inline-Aktionen
- Routing- und Fehlerbehandlungsbasis für spätere Fachschnitte

## Struktur

```text
src/
├── app/                         Routing, Layout und globale Provider
├── api/                         REST-Client und Fehlerbehandlung
├── components/                  wiederverwendbare UI-Bausteine
└── styles/                      Theme und globale Gestaltung
```

## P0-Routen

```text
/                              Übersicht
/participants                  Teilnehmer
/data                          Sicherungen und Exporte
/shows/:showId/candidates      Kandidaten
/shows/:showId/voting          Abstimmung
/shows/:showId/result          Ergebnis
```

## Entwicklung

```text
npm ci
npm run dev
```

Der Dev-Server läuft auf Port 5173 und leitet `/api` an `http://127.0.0.1:8080` weiter. Für die einzelnen Prüfungen stehen `npm run lint`, `npm run typecheck`, `npm run test` und `npm run build` zur Verfügung.

## UI-Komponentenbibliothek

Die P0-Prüfoberfläche verwendet Material UI mit lokal gebündeltem Dark Theme, Drawer, Formularfeld, Tabelle und Dialog. Sie ist eine technische Darstellung ohne fachliche Mock-Daten. Die Begründung und Grenzen der Entscheidung stehen im [Entscheidungsprotokoll](../docs/decisions.md).

## Noch nicht enthalten

Fachliche Daten, Drag-and-drop, Clipboard-/Vivaldi-Spike, Parser, YouTube-Integration und Launcher bleiben ausdrücklich spätere Pakete.
