# Frontend

Dieser Ordner ist für die React-/TypeScript-Oberfläche des CSC X Tool vorgesehen.

## Ziele

- moderne, konsistente Dark-UI
- sehr gute Bedienbarkeit in Vivaldi
- desktoporientiertes Layout
- schnelle Inline-Aktionen
- robuste Höransichten mit YouTube-Fallback
- komfortables Drag-and-drop für Kandidaten und Ranglisten
- direkter Import formatierter CSC-Beitragsblöcke per Zwischenablage

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

## Beitragsimport aus der Zwischenablage

Die Abstimmungsansicht erhält eine eigene Importfläche. Der primäre Bedienweg lautet:

1. formatierten Beitragsblock im CSC-Forum markieren und kopieren
2. Importfläche im Tool öffnen oder fokussieren
3. `Strg+V`
4. Importvorschau prüfen
5. Import bestätigen

Die Importfläche verarbeitet den normalen Browser-`paste`-Event und liest daraus insbesondere:

- `text/html`
- `text/plain`

Die CSC-Linktexte haben im Normalfall das sichtbare Format `Interpret - Titel`; das eigentliche YouTube-Ziel steckt im formatierten Zwischenablageinhalt. Ein gewöhnliches Plaintext-Textfeld würde diese Information verlieren und ist deshalb nicht die primäre Importkomponente.

Für den normalen Workflow wird keine dauerhafte Clipboard-Berechtigung benötigt. Insbesondere ist ein programmatisches `navigator.clipboard.read()` keine Voraussetzung.

Das Frontend sendet den temporär gelesenen HTML-/Plaintext-Inhalt an die Importvorschau des Backends. Rohes Clipboard-HTML wird niemals direkt gerendert.

Die Vorschau zeigt mindestens:

- Interpret
- Titel
- Link
- Erkennungsstatus beziehungsweise Warnungen
- Korrekturmöglichkeit vor dem endgültigen Import

Als Fallbacks können markdownartige Links und Plaintext mit expliziten URLs verarbeitet werden. Reiner `Interpret - Titel`-Text ohne Linkziel erscheint höchstens als unvollständige Preview-Zeile.

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

Beim technischen Bootstrap soll zusätzlich ein kleiner Paste-Spike in Vivaldi beziehungsweise einem kompatiblen Chromium-Browser bestätigen, welche `text/html`-Struktur beim Kopieren aus dem CSC-Forum konkret ankommt.