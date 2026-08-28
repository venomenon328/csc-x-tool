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

Die Entwicklungsinhalte bis 0.1.0 sind umgesetzt: Die Anwendung verwaltet die zwölf Mottoshows, Kandidaten, Beiträge, Top 15, Teilnehmer, Ergebnisse sowie Sicherungen lokal in SQLite. Der Windows-Releasepfad erzeugt ein App-Image und einen per-user MSI; die manuelle Windows-/Vivaldi-Abnahme bleibt vor einer Freigabe verpflichtend.

## Sicherungen und Exporte

Die Seite `/data` erzeugt manuelle Sicherungen, listet automatische und manuelle Artefakte, lädt sie herunter und führt Restore-Vorschau sowie eine separate Bestätigung aus. Backups sind einzelne `.cscbackup`-Container mit einem SQLite-Snapshot und prüfbarem Manifest; sie dürfen nicht manuell verändert werden. Bei jedem Restore entsteht unmittelbar davor eine zusätzliche, nicht rotierte Sicherheitskopie.

Der vollständige JSON-Download verwendet den versionierten Contract `csc-x-tool-full-export` v1 und enthält alle fachlichen Daten einschließlich historischer Top-15-Snapshots und Ergebniszustände. CSV-Downloads sind UTF-8 mit BOM, Semikolon und CRLF für Kandidaten, Wettbewerbsbeiträge, Teilnehmer und Ergebnisse. Der bestehende Top-15-Textdownload bleibt getrennt.

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

## Repository-Struktur

```text
.
├── backend/       Spring Boot, REST-API und ausgelieferte SPA
├── frontend/      React, TypeScript, Vite und UI-Shell
├── launcher/      Windows-Start, Browseröffnung und Paketierung
└── docs/          Spezifikation, Architektur, Entscheidungen und Implementierungsplan
```

## Bewusste Abgrenzung

Das Tool recherchiert keine Kandidaten, prüft keine Mottoregeln und gleicht Songs nicht gegen die CSC-Ausschlussliste ab. Diese Arbeit findet vor dem Eintragen außerhalb der Anwendung statt. Ebenfalls nicht verwaltet werden die vollständigen Abstimmungen oder Ergebnisse anderer Teilnehmer.

## Toolchain

| Bestandteil | Festgelegte Version |
| --- | --- |
| Java | 21 |
| Maven Wrapper | 3.9.16 |
| Spring Boot | 4.1.1 |
| Node.js | 24.20.0 LTS |
| npm | 12.0.2 |
| React | 19.2.8 |
| TypeScript | 6.0.3 |
| Vite | 8.2.0 |
| Material UI | 9.3.1 |

Alle direkten Frontend-Abhängigkeiten sind exakt versioniert; `frontend/package-lock.json` ist verbindlich. Maven-Abhängigkeiten werden über das fest gepinnte Spring-Boot-Parent verwaltet.

## Build und Tests

Voraussetzungen für lokale Entwicklung sind Java 21 sowie Node.js 24.20.0 mit npm 12.0.2. Die lokale Windows-Workstation soll während Builds und Tests benutzbar bleiben; die dafür festgelegten Heap-, Worker-, CPU- und Prioritätsgrenzen sind deshalb Bestandteil der Entwicklungsinfrastruktur und nicht optionales Tuning.

Unter Windows ist die maßgebliche vollständige Prüfung:

```powershell
.\scripts\mvn-safe.cmd clean verify
```

Der `.cmd`-Starter ruft die eigentlichen PowerShell-Schutzregeln nur für diesen Prozess mit einer passenden Execution Policy auf; eine globale Änderung der PowerShell-Ausführungsrichtlinie ist dafür nicht erforderlich.

Unter CI beziehungsweise auf Nicht-Windows-Systemen bleibt der entsprechende Gesamtbuild:

```text
./mvnw clean verify
```

Der Root-Build führt bereits die Backendtests sowie im Frontend `npm ci`, Tests, Build, Linting und TypeScript-Check aus. Die vollständige Frontend-Prüfsequenz soll deshalb nicht unmittelbar davor oder danach noch einmal separat ausgeführt werden. Für gezielte Frontendprüfungen wird unter Windows der ressourcenbegrenzte npm-Wrapper verwendet, zum Beispiel:

```powershell
.\scripts\npm-safe.cmd test
.\scripts\npm-safe.cmd run lint
.\scripts\npm-safe.cmd run typecheck
```

Weitere Details stehen unter [Resource-safe local development](scripts/README-resource-safety.md). Agenten beachten zusätzlich die verbindlichen Regeln aus `AGENTS.md`.

## Windows-Release-Paketierung

Windows-Releases werden mit JDK 21 samt `jpackage` und WiX `3.14.1.20250415` erzeugt. Die kanonischen Einstiegspunkte erzeugen App-Image, per-user MSI und Prüfsumme und prüfen danach den paketierten Ablauf. Die Maven-Property `revision` ist die Standardquelle; für einen abweichenden Release wird dieselbe validierte `X.Y.Z`-Version beiden Schritten übergeben:

```powershell
.\launcher\packaging\build-release.ps1 -Clean
.\launcher\packaging\smoke-release.ps1
```

```powershell
.\launcher\packaging\build-release.ps1 -Clean -Version 0.1.2
.\launcher\packaging\smoke-release.ps1 -Version 0.1.2
```

Details zu Artefakten, WiX und dem automatisierten Installer-Smoke stehen unter [launcher/packaging](launcher/packaging/README.md). Der manuelle Vivaldi-Release-Smoke wird davon nicht simuliert und bleibt bis zu seiner realen Durchführung in der [Release-Checkliste](docs/release-checklist-0.1.0.md) offen.

## Entwicklungsbetrieb

Terminal 1 startet das Backend auf `127.0.0.1:8080`. Unter Windows wird auch dafür der Maven-Wrapper verwendet:

```powershell
.\scripts\mvn-safe.cmd -pl backend -Pdev spring-boot:run
```

Auf Nicht-Windows-Systemen lautet der entsprechende Befehl:

```text
./mvnw -pl backend -Pdev spring-boot:run
```

Das Maven-Profil `dev` lässt dabei bewusst die paketierte Frontend-JAR-Abhängigkeit weg, weil die Oberfläche im Entwicklungsbetrieb vom Vite-Server kommt. Dadurch funktioniert der Backend-Start auch auf einem frischen Checkout ohne vorheriges Installieren des Frontend-Artefakts in das lokale Maven-Repository.

Terminal 2 startet den Vite-Entwicklungsserver mit Hot Reload auf `http://127.0.0.1:5173`. Unter Windows:

```powershell
.\scripts\npm-safe.cmd ci
.\scripts\npm-safe.cmd run dev
```

Auf Nicht-Windows-Systemen:

```text
cd frontend
npm ci
npm run dev
```

Vite leitet `/api` dabei an das lokale Backend weiter. Im Produktionsbetrieb gibt es keinen separaten Node-Prozess und keine CORS-Freigabe: Nach einem Produktionsbuild liefert das ausführbare JAR die SPA und `GET /api/system/health` unter derselben Origin aus.

## Lokale Datenablage

Im produktiven Betrieb verwendet das Tool ausschließlich `%LOCALAPPDATA%/CSC-X-Tool/` mit den Unterverzeichnissen `data`, `backups/automatic`, `backups/manual`, `exports`, `logs` und `runtime`. Die Datenbank liegt unter `data/csc-x-tool.db`; es werden keine fachlichen Daten im Checkout oder Installationsverzeichnis abgelegt.

Für Entwicklung, Tests oder einen isolierten Start wird der Root explizit gesetzt, zum Beispiel:

```powershell
.\scripts\mvn-safe.cmd "-Dspring-boot.run.arguments=--csc-x-tool.storage.root=C:\temp\csc-x-tool-data" -pl backend -Pdev spring-boot:run
```

Zusätzlich wird die dokumentierte Umgebungsvariable `CSC_X_TOOL_STORAGE_ROOT` explizit als Alias für denselben Override unterstützt. Ist weder dieser Override noch `LOCALAPPDATA` verfügbar, bricht der Start mit einer pfadbezogenen Fehlermeldung ab.

`GET /api/shows` liefert die zwölf Shows; `PATCH /api/shows/{showId}` mit `{ "name": "…" }` benennt eine Show um.

Vorgesehene Browserrouten sind `/`, `/participants`, `/data` sowie `/shows/:showId/candidates`, `/shows/:showId/voting` und `/shows/:showId/result`. Direkte Aufrufe dieser Routen landen in der SPA; `/api/**` wird davon ausdrücklich ausgenommen.
