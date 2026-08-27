# Technische Architektur – CSC X Tool

**Version:** 0.1  
**Stand:** 27.08.2026  
**Status:** technische Baseline; konkrete Dependency-Versionen werden beim Bootstrap fest gepinnt

## 1. Architekturziele

Die technische Architektur soll folgende Eigenschaften unterstützen:

- lokal und ohne Serveradministration ausführbar
- Start über einen normalen Windows-Launcher
- moderne, dunkle Browseroberfläche
- sehr komfortables Drag-and-drop
- zuverlässige lokale Persistenz
- einfache Sicherung und Wiederherstellung
- keine Anmeldung und keine Benutzerverwaltung
- ein einziger Laufzeitprozess
- möglichst geringe Betriebs- und Wartungskomplexität
- klare fachliche Struktur für spätere Statistiken

## 2. Grundentscheidung

Das CSC X Tool wird als **lokale Webanwendung im Monorepo** aufgebaut.

### Laufzeit

- ein Spring-Boot-Prozess startet lokal
- das gebaute Frontend wird vom Backend als statische Anwendung ausgeliefert
- der Browser kommuniziert über dieselbe Origin mit einer JSON-REST-API
- SQLite liegt als lokale Datei im Benutzerprofil
- der Server bindet ausschließlich an die Loopback-Schnittstelle
- ein Windows-Launcher startet die Anwendung und öffnet den Standardbrowser

Es gibt im installierten Produkt keinen separat zu startenden Node-Prozess, keinen Datenbankdienst und keinen Docker-Container.

## 3. Technologiestack

### Backend

- Java 21 als konservative Baseline
- Spring Boot
- Spring Web
- Spring JDBC mit `NamedParameterJdbcTemplate` oder kleinen expliziten Repository-Klassen
- SQLite JDBC
- Liquibase für versionierte Datenbankmigrationen
- Jackson für JSON
- Bean Validation für API-Eingaben

Eine schwere ORM-Schicht ist für das kleine, klar relationale Datenmodell nicht erforderlich. Explizites SQL erleichtert insbesondere die Kontrolle von Sortierpositionen, Snapshots und SQLite-spezifischem Verhalten.

### Frontend

- React
- TypeScript
- Vite
- eine lokal gebündelte Komponentenbibliothek mit guter Dark-Theme-Unterstützung
- eine etablierte Drag-and-drop-Bibliothek mit Unterstützung für mehrere Listen, Auto-Scroll und sichtbare Drop-Indikatoren
- ein schlanker Client für REST-Aufrufe und Cache-Invalidierung

Die konkrete Komponenten- und Drag-and-drop-Bibliothek wird beim Frontend-Bootstrap nach einem kleinen Spike festgelegt. Diese Auswahl ist keine fachliche Architekturentscheidung und darf ohne Änderung der Produktspezifikation ersetzt werden.

### Persistenz

- SQLite
- aktivierte Fremdschlüssel
- WAL-Modus, sofern er sich im Packaging-Test als stabil erweist
- alle Schemaänderungen ausschließlich über Liquibase
- kontrollierte Sicherung über SQLite-kompatible Backupmechanismen, nicht durch blindes Kopieren einer geöffneten Datei

### Build und Paketierung

- Maven für Backend und Gesamtbuild
- npm für das Frontend
- Frontend-Build wird in den Spring-Boot-Build integriert
- `jpackage` oder eine gleichwertige Java-Paketierung für einen Windows-Launcher mit gebündelter Java-Laufzeit

Das installierte Produkt darf keine separat installierte Java-, Node- oder SQLite-Umgebung voraussetzen.

## 4. Systemkontext

```text
┌──────────────────────┐
│ Windows-Launcher     │
│ Start / Reopen       │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────────────────────┐
│ Spring-Boot-Prozess auf 127.0.0.1    │
│                                      │
│  ┌──────────────┐  ┌──────────────┐  │
│  │ REST-API     │  │ Frontend     │  │
│  │              │  │ static files│  │
│  └──────┬───────┘  └──────────────┘  │
│         │                             │
│  ┌──────▼───────┐  ┌──────────────┐  │
│  │ Domain/SQL   │  │ Backup/Export│  │
│  └──────┬───────┘  └──────────────┘  │
└─────────┼────────────────────────────┘
          │
          ▼
┌──────────────────────┐
│ SQLite im Benutzer-  │
│ profil               │
└──────────────────────┘

Browser ───────────────► YouTube nur beim Abspielen/Öffnen
```

## 5. Repository-Struktur

```text
.
├── README.md
├── .editorconfig
├── .gitignore
├── pom.xml                         # späterer Root-/Aggregator-Build
├── backend/
│   ├── README.md
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/de/venomenon/cscxtool/
│       │   │   ├── show/
│       │   │   ├── candidate/
│       │   │   ├── participant/
│       │   │   ├── entry/
│       │   │   ├── ballot/
│       │   │   ├── result/
│       │   │   ├── backup/
│       │   │   ├── system/
│       │   │   └── shared/
│       │   └── resources/
│       │       ├── db/changelog/
│       │       ├── application.yml
│       │       └── static/         # gebautes Frontend
│       └── test/
├── frontend/
│   ├── README.md
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── app/
│       ├── features/
│       │   ├── shows/
│       │   ├── candidates/
│       │   ├── participants/
│       │   ├── entries/
│       │   ├── ballot/
│       │   ├── results/
│       │   └── data-management/
│       ├── components/
│       ├── api/
│       └── styles/
├── launcher/
│   ├── README.md
│   ├── packaging/
│   └── assets/
└── docs/
    ├── specification.md
    ├── architecture.md
    ├── decisions.md
    └── reference/
        └── README.md
```

Die fachlichen Backend-Pakete sind vertikal nach Funktion gegliedert. Ein globales Sammelbecken aus `controller`, `service`, `repository` und `model` wird vermieden.

## 6. Backend-Schnitt

Jedes fachliche Modul enthält nur die Schichten, die es tatsächlich benötigt. Ein typisches Modul kann enthalten:

```text
candidate/
├── CandidateController.java
├── CandidateApplicationService.java
├── CandidateRepository.java
├── Candidate.java
├── CandidateStatus.java
├── CandidateRequest.java
└── CandidateResponse.java
```

### Module

#### `show`

- Initialisierung und Pflege der zwölf Mottoshows
- abgeleitete Fortschrittsinformationen
- ausgewählte eigene Einreichung
- Abschlusszustände

#### `candidate`

- Kandidaten-CRUD
- Status
- manuelle Reihenfolge
- Kopieren in andere Shows
- Auswahl als Einreichung

#### `participant`

- Teilnehmerstammdaten
- Aliasse
- Land und Ländercode
- Aktivstatus

#### `entry`

- Wettbewerbsbeiträge
- Textblockimport und Vorschau
- Hörstatus und Kommentar
- Teilnehmerzuordnung nach Abschluss

#### `ballot`

- Rangliste
- atomare Neuordnung
- Abschlussvalidierung
- Top-15-Snapshots
- Textausgabe
- interne Punktzuordnung

#### `result`

- Ergebniseinträge je Teilnehmer und Show
- Status `UNBEKANNT`, `NICHT_ABGESTIMMT`, `ABGESTIMMT`
- Punktvalidierung und Summen
- offizielle Summe
- Endplatzierung und Gleichstandskennzeichnung

#### `backup`

- kontrollierte SQLite-Sicherungen
- JSON-Gesamtexport und Wiederherstellung
- CSV-Exporte
- Aufbewahrungsregeln

#### `system`

- Health-Endpoint
- Instanzinformationen
- kontrolliertes Herunterfahren
- Anwendungsverzeichnisse
- Start- und Migrationsablauf

## 7. Frontend-Schnitt

### Routen

Vorgesehene Routen:

```text
/                              Übersicht
/participants                  Teilnehmer
/data                          Sicherungen und Exporte
/shows/:showId/candidates      Kandidaten
/shows/:showId/voting          Abstimmung
/shows/:showId/result          Ergebnis
```

Die Show-Detailansicht darf diese Routen optisch als Tabs innerhalb eines gemeinsamen Layouts darstellen.

### Zustandsmodell

- Serverdaten bleiben fachliche Quelle der Wahrheit.
- Formulare dürfen lokalen Entwurfszustand halten.
- Änderungen der manuellen Reihenfolge werden als atomare Reorder-Operation gesendet.
- Ein Drag-Vorgang erzeugt nicht für jede überfahrene Position einen Serverrequest.
- Nach erfolgreichem Reorder wird die vollständige neue Reihenfolge bestätigt.
- Optimistische Aktualisierung ist zulässig, sofern bei einem Fehler zuverlässig auf den letzten bestätigten Stand zurückgerollt wird.

### Zentrale UI-Komponenten

- `ShowOverviewCard`
- `SongRow`
- `CandidateList`
- `EntryPool`
- `RankedEntryList`
- `Top15Boundary`
- `YoutubePlayerPanel`
- `ParticipantSelect`
- `ReceivedScoreGrid`
- `ImportPreview`
- `CompletionDialog`
- `BackupManager`

Komponentenbezeichnungen sind orientierend und kein API-Vertrag.

## 8. Vorgesehenes Datenmodell

Das folgende Modell beschreibt die fachlichen Beziehungen. Spaltennamen und technische Detailtypen werden im ersten Datenbank-Issue endgültig festgelegt.

### `motto_show`

- `id`
- `show_number`, eindeutig 1 bis 12
- `name`
- `selected_candidate_id`, optional
- `ballot_closed_at`, optional
- `results_closed_at`, optional
- `final_place`, optional
- `final_place_tied`
- `official_total_points`, optional
- `created_at`
- `updated_at`

### `candidate`

- `id`
- `motto_show_id`
- `artist`
- `title`
- `youtube_url`
- `comment`
- `status`
- `manual_position`
- `created_at`
- `updated_at`

### `participant`

- `id`
- `display_name`
- `country_code`
- `active`
- `created_at`
- `updated_at`

### `participant_alias`

- `id`
- `participant_id`
- `alias`

### `contest_entry`

- `id`
- `motto_show_id`
- `artist`
- `title`
- `youtube_url`
- `comment`
- `listened`
- `relisten`
- `ranking_position`, optional
- `participant_id`, optional
- `created_at`
- `updated_at`

### `ballot_snapshot`

- `id`
- `motto_show_id`
- `snapshot_number`
- `created_at`
- `is_current`

### `ballot_snapshot_item`

- `id`
- `ballot_snapshot_id`
- `rank`, eindeutig 1 bis 15 je Snapshot
- `contest_entry_id`
- `artist_snapshot`
- `title_snapshot`
- `youtube_url_snapshot`

### `received_score`

- `id`
- `motto_show_id`
- `participant_id`
- `status`
- `points`, optional
- `created_at`
- `updated_at`

Eindeutiger Schlüssel: `motto_show_id + participant_id`.

### Mögliche technische Ergänzungen

- `app_metadata` für Schema- und Exportinformationen
- `import_session` und `import_row` nur, falls Importentwürfe dauerhaft gespeichert werden sollen
- `audit_event` ist initial nicht vorgesehen

## 9. Datenbankregeln

Soweit SQLite dies sinnvoll unterstützt, werden folgende Regeln zusätzlich in der Datenbank abgesichert:

- Show-Nummer zwischen 1 und 12 und eindeutig
- Kandidatenstatus aus der definierten Wertemenge
- positive manuelle Positionen
- höchstens eine Teilnehmerzuordnung je Teilnehmer und Show
- höchstens ein Ergebniseintrag je Teilnehmer und Show
- Ergebnisstatus aus der definierten Wertemenge
- zulässige Punktwerte
- Endplatzierung positiv, sofern vorhanden
- Snapshot-Ränge 1 bis 15 und innerhalb des Snapshots eindeutig
- aktivierte Foreign-Key-Prüfung für jede Verbindung

Komplexe Abschlussregeln werden zusätzlich in der Anwendung validiert und innerhalb einer Transaktion gespeichert.

## 10. REST-API-Grundsätze

- Ressourcenorientierte JSON-Endpunkte unter `/api`
- keine separate öffentliche API
- keine Versionierung im URL-Pfad für die erste lokale Version
- konsistente Fehlerobjekte mit maschinenlesbarem Code und verständlicher deutscher Meldung
- serverseitige Validierung aller schreibenden Requests
- Reorder-Operationen senden geordnete ID-Listen oder eine äquivalente atomare Repräsentation
- Abschlussoperationen sind explizite Commands und keine impliziten Nebenwirkungen normaler Updates

Beispielhafte Endpunktgruppen:

```text
/api/shows
/api/shows/{showId}/candidates
/api/shows/{showId}/entries
/api/shows/{showId}/ballot
/api/shows/{showId}/results
/api/participants
/api/import-preview
/api/backups
/api/exports
/api/system
```

Die konkrete Endpunktliste wird aus den Use-Cases der Produktspezifikation abgeleitet.

## 11. Textblockimport

Der Parser wird als klar getrennte Komponente aufgebaut:

```text
Raw text
  -> line segmentation
  -> format-specific parsing
  -> normalized preview rows
  -> user corrections
  -> validated import command
```

### Anforderungen an den Parser

- keine stillen Verluste unbekannter Zeilen
- Zeilennummer und Originaltext bleiben in der Vorschau sichtbar
- erkannte Felder werden transparent dargestellt
- Warnung statt automatische Zusammenführung bei möglichen Dubletten
- Parserregeln sind durch Tests mit realen Beispielblöcken abgesichert
- Formatdetails sind austauschbar, ohne das Beitragsmodell zu verändern

Der reale Beispieltext wird nicht im Quellcode als geheimnisvoller String in einem Test versteckt, sondern als anonymisierte Testressource dokumentiert.

## 12. Rangfolge und Reorder-Transaktionen

Manuelle Kandidatenreihenfolge und Beitragsrangliste werden serverseitig atomar gespeichert.

Bei einer Neuordnung:

1. prüft das Backend, dass alle übergebenen IDs zur richtigen Show gehören
2. prüft es auf fehlende und doppelte IDs im betroffenen Bereich
3. berechnet es lückenlose Positionen
4. schreibt es alle Änderungen in einer Transaktion
5. liefert es die bestätigte Reihenfolge zurück

Für die Beitragsansicht können `ranking_position = null` und positive Positionen kombiniert werden:

- `null` bedeutet „noch nicht eingeordnet“
- positive Werte bilden die lückenlose Rangliste

Die ersten 15 Positionen werden nicht als eigener veränderlicher Datensatz geführt, sondern aus der Rangliste abgeleitet. Erst beim Abschluss entsteht ein Snapshot.

## 13. YouTube-Integration

### URL-Normalisierung

Die Anwendung akzeptiert übliche YouTube-URL-Formen und extrahiert soweit möglich eine Video-ID. Original-URL und normalisierte Video-ID dürfen getrennt gespeichert beziehungsweise berechnet werden.

### Einbettung

- bevorzugt datensparsame Einbettung über die Privacy-Enhanced-Domain von YouTube
- externe Schaltfläche bleibt immer sichtbar
- Einbettungsfehler werden lokal im Playerbereich angezeigt
- kein automatischer Wechsel des gespeicherten Links
- keine YouTube-API und kein API-Schlüssel in der ersten Version

### Content Security Policy

Die CSP erlaubt Frames nur für die tatsächlich benötigten YouTube-Domains. Andere externe Frames und Skripte bleiben gesperrt.

## 14. Länder und Flaggen

- fachlich gespeichert wird ein ISO-3166-1-Alpha-2-Code
- Ländername wird aus einer lokal gebündelten Länderliste dargestellt
- Flaggenassets werden lokal mit der Anwendung ausgeliefert
- keine Abhängigkeit von einem externen Flaggen-CDN
- fehlende oder unbekannte Codes erhalten einen neutralen Fallback statt eines kaputten Bildes

Sollte später ein nicht durch ISO abbildbares Gebiet benötigt werden, kann das Modell um einen benutzerdefinierten Anzeigenamen und Flaggenoverride erweitert werden. Das ist initial nicht erforderlich.

## 15. Sicherungskonzept

### Verzeichnisse

Vorgesehene Struktur:

```text
%LOCALAPPDATA%/CSC-X-Tool/
├── data/
│   └── csc-x-tool.db
├── backups/
│   ├── automatic/
│   └── manual/
├── exports/
├── logs/
└── runtime/
    └── instance.json
```

### Sicherungsablauf

- Datenbankverbindung kontrolliert synchronisieren
- SQLite-kompatiblen Snapshot erzeugen
- Sicherung auf Lesbarkeit prüfen
- Metadaten mit Zeitpunkt und Anwendungsversion speichern
- automatische Aufbewahrungsregel erst nach erfolgreicher neuer Sicherung anwenden

### Wiederherstellung

- aktuelle Anwendungsversion prüft Kompatibilität des Backups
- vor dem Restore entsteht eine manuelle Sicherheitskopie des aktuellen Stands
- Serverzugriffe auf die Datenbank werden während des Austauschs gesperrt
- nach erfolgreicher Wiederherstellung wird die Datenbank erneut geöffnet und geprüft

## 16. Launcher und Prozesslebenszyklus

### Start

1. Launcher prüft eine lokale Instanzdatei.
2. Ist dort eine erreichbare Instanz vermerkt, öffnet er deren URL und beendet sich.
3. Ist die Instanzdatei veraltet, wird sie bereinigt.
4. Der Backend-Prozess startet auf einer konfigurierten oder freien Loopback-Adresse.
5. Nach erfolgreichem Health-Check schreibt er Port und Prozessinformationen in die Instanzdatei.
6. Der Standardbrowser öffnet die Anwendung.

### Betrieb

- der Server läuft ohne sichtbares Konsolenfenster
- Schließen des Browser-Tabs beendet ihn nicht
- ein weiterer Launcher-Aufruf öffnet die vorhandene Instanz

### Beenden

- UI-Aktion ruft einen lokalen, CSRF-geschützten Shutdown-Command auf
- offene Transaktionen werden beendet
- Datenbank wird sauber geschlossen
- optional wird eine letzte kontrollierte Sicherung erzeugt
- Instanzdatei wird entfernt

### Absturzfall

Eine veraltete Instanzdatei wird beim nächsten Start durch Health-Check und Prozessprüfung erkannt. Der Benutzer muss sie nicht manuell löschen.

## 17. Sicherheit

Obwohl die Anwendung nur lokal läuft, gelten folgende Mindestmaßnahmen:

- Bindung ausschließlich an Loopback
- keine permissive CORS-Konfiguration
- schreibende Requests nur same-origin und CSRF-geschützt
- restriktive Content Security Policy
- keine extern geladenen Skripte oder UI-Assets
- keine Zugangsdaten oder Tokens erforderlich
- Export- und Restore-Pfade werden serverseitig kontrolliert
- keine freie Dateipfadübergabe aus dem Browser
- Shutdown nur aus der lokalen Anwendung heraus

Eine Loginmaske wird dadurch nicht eingeführt.

## 18. Logging

- lokales Rolling-File-Logging
- verständliche technische Fehlerdetails für spätere Diagnose
- keine Songlisten, Kommentare oder Abstimmungsinhalte unnötig im normalen Log
- Importfehler referenzieren Zeilennummern, ohne den gesamten eingefügten Text dauerhaft zu protokollieren
- begrenzte Logaufbewahrung

## 19. Tests

### Backend

- Unit-Tests für Status- und Abschlussregeln
- Repository-Integrationstests gegen echte temporäre SQLite-Datenbanken
- Migrationstest von leerer Datenbank bis zum aktuellen Schema
- Tests der Punktwertvalidierung
- Tests für Kandidaten- und Ranking-Reorder
- Tests für Snapshot-Integrität
- Tests für Backup und Restore
- parserbezogene Tests mit realistischen Textressourcen

### Frontend

- Komponententests für Formulare, Filter und Statusdarstellung
- Drag-and-drop-Tests soweit im Testframework sinnvoll
- Tests der Importvorschau
- Tests der Top-15-Grenze
- Tests der Ergebnisstatus und Summendarstellung

### End-to-End

- vollständiges Akzeptanzszenario aus der Produktspezifikation
- Start mit leerer Datenbank
- Kandidatenfluss
- Import und Voting
- Abschluss, Wiederöffnung und erneuter Abschluss
- Teilnehmerzuordnung
- Ergebniserfassung
- Export, Neustart und Datenpersistenz

Vivaldi erhält mindestens einen manuellen Smoke-Test pro paketierter Release-Version. Automatisierte Browsertests dürfen zunächst mit einem kompatiblen Chromium-Browser laufen.

## 20. Build- und Releasefluss

Vorgesehener Ablauf:

1. Frontend linten, testen und bauen
2. Frontend-Artefakte in Backend-Ressourcen übernehmen
3. Backend testen und ausführbares Artefakt bauen
4. paketierte Anwendung mit temporärem Benutzerverzeichnis starten
5. Smoke-Test und Migrationstest ausführen
6. Windows-App-Image oder Installer erzeugen
7. Prüfsumme und Release-Artefakt bereitstellen

CI über GitHub Actions ist sinnvoll, aber nicht Teil dieses Dokumentations-Bootstraps.

## 21. Empfohlene Implementierungsreihenfolge

1. Root-Build, Backend-Shell und Frontend-Shell
2. Anwendungsverzeichnis, SQLite und Liquibase
3. Mottoshows und Übersicht
4. Kandidatenverwaltung einschließlich Reorder und Kopieren
5. Teilnehmerverwaltung mit Ländern und Aliassen
6. Wettbewerbsbeiträge und manuelle Pflege
7. realer Textblockparser und Importvorschau
8. Höransicht und YouTube-Einbettung
9. Rangliste und Top-15-Snapshots
10. Teilnehmerzuordnung
11. Ergebniserfassung
12. Backup, Restore und Exporte
13. Launcher und Windows-Paketierung
14. UI-Politur, Fehlerfälle und vollständige E2E-Tests

Der Launcher wird bewusst nicht als allererstes gebaut. Zunächst muss die Anwendung fachlich stabil lokal startbar sein; danach wird der Start komfortabel verpackt. Sonst besitzt man früh einen sehr schönen Knopf, der lediglich zuverlässig unfertige Software öffnet.

## 22. Nicht blockierende technische Entscheidungen

Folgende Details werden beim jeweiligen Bootstrap-Issue entschieden und in Code oder ADR festgehalten:

- konkrete Spring-Boot-Version
- konkrete React- und Vite-Version
- konkrete UI-Komponentenbibliothek
- konkrete Drag-and-drop-Bibliothek
- finaler Default-Port und Port-Fallback
- App-Icon und Installerformat
- genaue CSV-Spaltenfolgen
- konkrete Log- und Backup-Aufbewahrungsgrößen jenseits des fachlichen Standards

Keine dieser Entscheidungen erfordert vor dem Entwicklungsbeginn zusätzliche fachliche Klärung.
