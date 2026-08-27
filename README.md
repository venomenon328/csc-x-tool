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

P1 aus [Issue #5](https://github.com/venomenon328/csc-x-tool/issues/5) ist umgesetzt: Die Anwendung speichert zwölf Mottoshows lokal in SQLite, migriert ihr Schema mit Liquibase und zeigt die persistente Übersicht. Kandidaten, Teilnehmer, Beiträge, Ranking und Ergebnisse folgen bewusst erst in den vorgesehenen Entwicklungspaketen.

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

Voraussetzungen für lokale Entwicklung sind Java 21 sowie Node.js 24.20.0 mit npm 12.0.2. Der maßgebliche Gesamtbuild ist:

```text
./mvnw clean verify
```

Unter PowerShell lautet derselbe Befehl:

```powershell
.\mvnw.cmd clean verify
```

Er führt die Backendtests sowie im Frontend `npm ci`, Tests, Build, Linting und TypeScript-Check aus. Die Frontend-Prüfungen können separat nachvollzogen werden:

```text
cd frontend
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

## Entwicklungsbetrieb

Terminal 1 startet das Backend auf `127.0.0.1:8080`:

```text
./mvnw -pl backend -Pdev spring-boot:run
```

Das Maven-Profil `dev` lässt dabei bewusst die paketierte Frontend-JAR-Abhängigkeit weg, weil die Oberfläche im Entwicklungsbetrieb vom Vite-Server kommt. Dadurch funktioniert der Backend-Start auch auf einem frischen Checkout ohne vorheriges Installieren des Frontend-Artefakts in das lokale Maven-Repository.

Terminal 2 startet den Vite-Entwicklungsserver mit Hot Reload auf `http://127.0.0.1:5173`:

```text
cd frontend
npm ci
npm run dev
```

Vite leitet `/api` dabei an das lokale Backend weiter. Im Produktionsbetrieb gibt es keinen separaten Node-Prozess und keine CORS-Freigabe: Nach `./mvnw clean package` liefert das ausführbare JAR die SPA und `GET /api/system/health` unter derselben Origin aus.

## Lokale Datenablage

Im produktiven Betrieb verwendet das Tool ausschließlich `%LOCALAPPDATA%/CSC-X-Tool/` mit den Unterverzeichnissen `data`, `backups/automatic`, `backups/manual`, `exports`, `logs` und `runtime`. Die Datenbank liegt unter `data/csc-x-tool.db`; es werden keine fachlichen Daten im Checkout oder Installationsverzeichnis abgelegt.

Für Entwicklung, Tests oder einen isolierten Start wird der Root explizit gesetzt, zum Beispiel:

```powershell
.\mvnw.cmd "-Dspring-boot.run.arguments=--csc-x-tool.storage.root=C:\temp\csc-x-tool-data" -pl backend -Pdev spring-boot:run
```

Zusätzlich wird die dokumentierte Umgebungsvariable `CSC_X_TOOL_STORAGE_ROOT` explizit als Alias für denselben Override unterstützt. Ist weder dieser Override noch `LOCALAPPDATA` verfügbar, bricht der Start mit einer pfadbezogenen Fehlermeldung ab.

`GET /api/shows` liefert die zwölf Shows; `PATCH /api/shows/{showId}` mit `{ "name": "…" }` benennt eine Show um.

Vorgesehene Browserrouten sind `/`, `/participants`, `/data` sowie `/shows/:showId/candidates`, `/shows/:showId/voting` und `/shows/:showId/result`. Direkte Aufrufe dieser Routen landen in der SPA; `/api/**` wird davon ausdrücklich ausgenommen.
