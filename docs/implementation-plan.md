# Implementierungsplan – CSC X Tool

**Version:** 0.1  
**Stand:** 27.08.2026  
**Status:** operative Roadmap bis zur ersten lokal nutzbaren Version 0.1.0

## 1. Zweck dieses Dokuments

Dieses Dokument übersetzt die fachliche [Produktspezifikation](specification.md) und die [technische Architektur](architecture.md) in eine konkrete Umsetzungsreihenfolge.

Es beantwortet insbesondere:

- in welchen Entwicklungspaketen die Anwendung aufgebaut wird
- welche Pakete voneinander abhängen
- welcher überprüfbare Produktstand nach jedem Paket vorliegt
- welche technischen Risiken früh durch kleine Spikes geklärt werden
- welche Qualitäts- und Freigabekriterien für alle Pakete gelten
- wie der technische Bootstrap konkret begonnen wird

Die laufende GitHub-Roadmap ist [Issue #3](https://github.com/venomenon328/csc-x-tool/issues/3). Die einzelnen Entwicklungspakete besitzen eigene Issues mit detaillierten Checklisten und Akzeptanzkriterien.

Dieses Dokument ersetzt weder Spezifikation noch Architektur. Bei einem Widerspruch gilt:

1. fachliche Produktspezifikation
2. verbindliches Entscheidungsprotokoll
3. technische Architektur
4. dieser Implementierungsplan
5. konkrete technische Detailentscheidung im jeweiligen Issue oder ADR

## 2. Umsetzungsprinzipien

### 2.1 Vertikale, benutzbare Schnitte

Die Anwendung wird nicht zuerst vollständig in Datenbank, Backend und Frontend zerlegt und anschließend irgendwann zusammengesetzt. Jedes fachliche Entwicklungspaket soll einen sichtbaren, testbaren Arbeitsablauf liefern.

Beispiele:

- Nach dem Kandidatenpaket können Kandidaten tatsächlich verwaltet und als Einreichung gewählt werden.
- Nach dem Abstimmungspaket kann eine reale Top 15 gebildet und abgeschlossen werden.
- Nach dem Ergebnispaket ist der vollständige Lebenszyklus einer Mottoshow abbildbar.

### 2.2 Ein Produkt für einen Benutzer

Die Architektur bleibt konsequent auf den tatsächlichen Einsatzzweck zugeschnitten:

- ein lokaler Benutzer
- eine CSC-Ausgabe
- zwölf Mottoshows
- keine Anmeldung
- keine Cloud-Synchronisation
- kein allgemein betriebener Server

Es werden keine abstrakten Erweiterungspunkte für hypothetische Mandanten, Rollen, Contesttypen oder verteilte Systeme gebaut.

### 2.3 Datenintegrität von Anfang an

- Alle Schemaänderungen erfolgen über Liquibase.
- Fachliche Regeln werden soweit sinnvoll sowohl in Anwendung als auch Datenbank abgesichert.
- Reorder-, Abschluss- und Restore-Vorgänge sind atomare Commands.
- Reale SQLite-Dateien werden in Integrationstests verwendet.
- Persistente Daten liegen nicht im Repository oder Installationsverzeichnis.

### 2.4 Bedienkomfort ist Kernfunktion

Drag-and-drop, Clipboard-Import, gute leere Zustände, verständliche Fehlermeldungen und ein konsistentes dunkles Layout sind keine abschließende Dekoration.

Sie werden in den Paketen umgesetzt, in denen der jeweilige Arbeitsablauf entsteht.

### 2.5 Pakete sind keine Zwangs-PRs

Ein Entwicklungspaket darf mehrere Pull Requests umfassen. Ein PR soll:

- einen nachvollziehbaren Teilnutzen liefern
- vollständig testbar sein
- keine absichtlich kaputte Zwischenstufe nach `main` bringen
- klein genug für eine sinnvolle Prüfung bleiben

Die in den Issues genannten PR-Schnitte sind Empfehlungen, keine religiösen Vorschriften.

## 3. Gesamtübersicht und Abhängigkeiten

```text
P0 #4  Technischer Bootstrap
   │
   ▼
P1 #5  Lokale Datenbasis und Mottoshow-Übersicht
   ├───────────────┬──────────────────────────┐
   │               │                          │
   ▼               ▼                          ▼
P2 #6          P3 #7                      P7 #11
Kandidaten      Teilnehmer                 Backup/Export
   │               │                          │
   ▼               │                          │
P4 #8              │                          │
Beiträge/Import    │                          │
   │               │                          │
   ▼               │                          │
P5 #9              │                          │
Ranking/Top 15     │                          │
   └───────┬───────┘                          │
           ▼                                  │
        P6 #10                                │
        Zuordnung/Ergebnis                    │
           └──────────────────┬───────────────┘
                              ▼
                           P8 #12
                           Launcher/Release 0.1.0
```

Die fachliche Hauptreihenfolge lautet:

```text
#4 → #5 → #6 → #8 → #9 → #10 → #12
```

Dabei gelten zwei wichtige Seitenpfade:

- #7 kann nach #5 parallel zur Kandidatenentwicklung umgesetzt werden und wird spätestens für #10 benötigt.
- #11 kann technisch bereits nach #5 begonnen werden. Der Backup-Kern sollte vor dauerhafter Nutzung mit echten Contestdaten verfügbar sein, auch wenn der vollständige JSON-Roundtrip erst nach #10 getestet werden kann.

## 4. Entwicklungspakete

| Paket | Issue | Ergebnis | Hauptabhängigkeit | Freigabepunkt |
|---|---:|---|---|---|
| P0 | [#4](https://github.com/venomenon328/csc-x-tool/issues/4) | ausführbares Monorepo, gemeinsamer Build, CI | Spezifikation und Architektur | technische Basis startet |
| P1 | [#5](https://github.com/venomenon328/csc-x-tool/issues/5) | SQLite, Liquibase, zwölf Shows, echte Übersicht | P0 | persistente Anwendung |
| P2 | [#6](https://github.com/venomenon328/csc-x-tool/issues/6) | vollständige Kandidatenverwaltung | P1 | erste praktisch nutzbare Stufe |
| P3 | [#7](https://github.com/venomenon328/csc-x-tool/issues/7) | Teilnehmer, Länder, Flaggen, Aliasse | P1 | Stammdaten bereit |
| P4 | [#8](https://github.com/venomenon328/csc-x-tool/issues/8) | Beiträge, Rich-Clipboard-Import, Höransicht | P1, Song-/YouTube-Basis aus P2 | reale Beitragsliste nutzbar |
| P5 | [#9](https://github.com/venomenon328/csc-x-tool/issues/9) | vollständiges Ranking, Top-15-Snapshot, Ausgabe | P4 | abstimmungsfähig |
| P6 | [#10](https://github.com/venomenon328/csc-x-tool/issues/10) | Teilnehmerzuordnung und eigenes Ergebnis | P3, P5 | fachlich vollständige Mottoshow |
| P7 | [#11](https://github.com/venomenon328/csc-x-tool/issues/11) | Backup, Restore, JSON- und CSV-Exporte | technische Basis P1; voller Test nach P6 | datenfest |
| P8 | [#12](https://github.com/venomenon328/csc-x-tool/issues/12) | Launcher, Paketierung, Härtung, Release 0.1.0 | P6, P7 | lokal releasefähig |

## 5. Paket P0 – Technischer Bootstrap

**Issue:** [#4](https://github.com/venomenon328/csc-x-tool/issues/4)

P0 soll die kleinste sinnvolle technische Basis erzeugen. Es werden noch keine fachlichen Tabellen oder Mock-Funktionen gebaut, die später wieder entfernt werden müssen.

### 5.1 Schritt 1 – Toolchain prüfen und pinnen

Vor dem Generieren der Projekte werden aktuelle stabile Versionen aus offiziellen Quellen geprüft und anschließend explizit festgelegt:

- Java 21 als Baseline
- Spring Boot
- Maven Wrapper
- Node und npm für die Entwicklung
- React, TypeScript und Vite
- Test- und Lint-Werkzeuge

Die Entscheidung wird in Builddateien und Dokumentation festgehalten. „Nimmt halt immer latest“ ist kein reproduzierbarer Buildprozess, sondern eine kleine Zeitbombe mit freundlichem Namen.

### 5.2 Schritt 2 – Root- und Backend-Build

- Maven Wrapper anlegen
- Root-`pom.xml` als Aggregator erstellen
- Backend-Modul mit Spring Boot und Java 21 initialisieren
- minimale Anwendungsklasse anlegen
- Health-Endpunkt bereitstellen
- einheitliches API-Fehlerobjekt vorbereiten
- ersten JUnit-Smoke-Test ausführen

### 5.3 Schritt 3 – Frontend-Shell

- React-/TypeScript-/Vite-Projekt unter `frontend/` initialisieren
- Routing und gemeinsames App-Layout anlegen
- dunkles Basistheme definieren
- Platzhalterseiten für Übersicht, Teilnehmer und Datenverwaltung anlegen
- Fehlergrenze und zentrale API-Fehlerdarstellung vorbereiten
- TypeScript-Check, Linting und einen Smoke-Test einrichten

Die UI-Bibliothek wird nur nach einem kleinen visuellen Spike festgelegt. Maßgeblich sind Dark-Theme-Qualität, Tabellen-/Formularbausteine und die Eignung für eine ruhige Desktopoberfläche.

### 5.4 Schritt 4 – Entwicklungsbetrieb

- Vite-Dev-Server für Hot Reload verwenden
- `/api` im Entwicklungsbetrieb an Spring Boot weiterleiten
- im Produktionsbetrieb dieselbe Origin ohne permissives CORS verwenden
- dokumentierte Befehle für Backend, Frontend und Gesamtstart bereitstellen
- lokale Entwicklungsdaten später über einen konfigurierbaren Pfad isolieren

### 5.5 Schritt 5 – Integrierter Produktionsbuild

- Frontend mit npm bauen
- erzeugte Assets in den Backend-Build übernehmen
- React-Routen beim direkten Browseraufruf korrekt auf die SPA zurückführen
- ausführbares Spring-Boot-Artefakt erzeugen
- prüfen, dass Oberfläche und API aus demselben Prozess erreichbar sind

### 5.6 Schritt 6 – Qualitätsbaseline und CI

- Root-Build führt Backendtests, Frontendtests, TypeScript-Check und Linting aus
- GitHub Actions verwendet denselben maßgeblichen Build
- generierte Dateien und lokale Daten werden ignoriert
- README dokumentiert Voraussetzungen und Kommandos
- Dependency-Versionen und relevante Entscheidungen werden festgehalten

### 5.7 Ergebnis von P0

P0 ist abgeschlossen, wenn ein frischer Checkout reproduzierbar gebaut werden kann und das erzeugte Artefakt eine minimale dunkle Oberfläche sowie einen funktionierenden Health-Endpunkt ausliefert.

Eine Datenbank ist zu diesem Zeitpunkt ausdrücklich noch nicht erforderlich.

## 6. Paket P1 – Lokale Datenbasis und Übersicht

**Issue:** [#5](https://github.com/venomenon328/csc-x-tool/issues/5)

P1 führt die endgültige lokale Datenhaltung ein:

1. Anwendungsverzeichnisse unter `%LOCALAPPDATA%/CSC-X-Tool/`
2. konfigurierbare Entwicklungs- und Testpfade
3. SQLite mit aktivierten Fremdschlüsseln
4. geprüfter WAL-Modus oder dokumentierte Entscheidung dagegen
5. Liquibase-Masterchangelog und Migrationstests
6. `motto_show`-Schema und idempotente Seed-Daten
7. Show-API und persistente Namensänderung
8. echte Startübersicht aus Datenbankwerten

Nach P1 existiert keine statische Frontend-Demonstration mehr, sondern eine persistente lokale Anwendung.

## 7. Paket P2 – Kandidatenverwaltung

**Issue:** [#6](https://github.com/venomenon328/csc-x-tool/issues/6)

P2 ist der erste vollständig benutzbare Fachschnitt:

- Kandidaten schnell anlegen
- Status und Kommentar pflegen
- suchen und filtern
- manuell per Drag-and-drop sortieren
- temporär tabellarisch sortieren, ohne die manuelle Reihenfolge zu beschädigen
- verworfene Kandidaten zurückhaltend anzeigen
- Kandidaten in andere Shows kopieren
- eigene Einreichung wählen und ersetzen
- YouTube einbetten oder extern öffnen

Die Drag-and-drop-Bibliothek wird hier erstmals an einer echten Liste geprüft. Die Kandidatenliste dient damit als risikoärmerer Vorläufer für das komplexere Zwei-Listen-Ranking in P5.

### Frühe Nutzung

Nach P2 darf das Tool bereits für echte Kandidatenlisten verwendet werden. Davor sollte jedoch mindestens ein verlässlicher manueller beziehungsweise automatischer Backup-Kern aus P7 verfügbar sein. Ein Kandidat ist ersetzbar; mehrere Wochen liebevoll kuratierte Kommentare weniger.

## 8. Paket P3 – Teilnehmerstammdaten

**Issue:** [#7](https://github.com/venomenon328/csc-x-tool/issues/7)

P3 kann nach P1 parallel zu P2 bearbeitet werden:

- Teilnehmer- und Alias-Tabellen
- lokale Länderliste
- lokal ausgelieferte Flaggenassets
- CRUD und Aktivstatus
- Suche über Namen und Aliasse
- wiederverwendbare Teilnehmerauswahl

P3 besitzt keine Teilnahmehistorie, keine Ausgabezuordnung und keinen Datensatz für den Benutzer selbst.

## 9. Paket P4 – Beiträge, Clipboard-Import und Hören

**Issue:** [#8](https://github.com/venomenon328/csc-x-tool/issues/8)

P4 beginnt mit einem kurzen Vivaldi-/Chromium-Spike anhand eines real aus dem CSC kopierten Beitragsblocks.

Der Zielablauf lautet:

```text
CSC-Beitragsblock kopieren
  → Importfläche fokussieren
  → Strg+V
  → HTML und Plaintext temporär aus ClipboardEvent lesen
  → serverseitige Importvorschau
  → Warnungen und manuelle Korrekturen
  → bestätigte Datensätze atomar importieren
```

Parserpriorität:

1. formatierte HTML-Links
2. Markdownlinks
3. Plaintext mit ausgeschriebener URL
4. reine Textzeile als sichtbar unvollständiger Datensatz

Clipboard-HTML wird niemals direkt gerendert oder dauerhaft gespeichert.

Danach folgen Beitragsliste, Einschätzung und Sicherheit, Kommentare, Player, externe Links und Filter.

## 10. Paket P5 – Ranking und Top 15

**Issue:** [#9](https://github.com/venomenon328/csc-x-tool/issues/9)

P5 ist der wichtigste Bedienungsschnitt:

- ungeordneter Pool
- beliebig lange Rangliste
- Drag-and-drop zwischen beiden Bereichen
- Auto-Scroll und sichtbare Einfügeposition
- klare Grenze zwischen Rang 15 und 16
- atomare Speicherung der lückenlosen Reihenfolge
- eindeutige Top 15 ohne Gleichstände
- Abschlussvalidierung
- unveränderliche Snapshots
- Wiederöffnung mit erhaltenen älteren Snapshots
- Textvorschau, Zwischenablage und Textdatei

Das endgültige CSC-Ausgabeformat wird über einen gekapselten Renderer ergänzt, sobald eine reale Vorlage vorliegt. Dieser Detailpunkt darf weder Datenmodell noch Ranking blockieren.

## 11. Paket P6 – Zuordnung und Ergebnis

**Issue:** [#10](https://github.com/venomenon328/csc-x-tool/issues/10)

Nach Abschluss der Abstimmung werden:

- Beiträge den Teilnehmern zugeordnet
- doppelte Zuordnungen je Show verhindert
- unbekannt, nicht abgestimmt und abgestimmt getrennt erfasst
- nur zulässige Punktwerte angeboten
- 0 Punkte eindeutig als abgegebene Abstimmung behandelt
- Gesamtpunkte berechnet
- offizielle Gesamtpunkte optional verglichen
- Endplatzierung und geteilter Platz gepflegt
- Ergebnisse bewusst abgeschlossen und wieder geöffnet

Nach P6 ist der vollständige fachliche Lebenszyklus einer Mottoshow benutzbar.

## 12. Paket P7 – Backup, Restore und Exporte

**Issue:** [#11](https://github.com/venomenon328/csc-x-tool/issues/11)

P7 besteht aus zwei zeitlich unterschiedlich wichtigen Teilen.

### Früher Sicherheitskern

Sobald echte Daten verwendet werden:

- konsistentes SQLite-Backup
- manuelles Backup
- Backup beim Start
- getrennte automatische und manuelle Ablage
- verständliche Fehlerbehandlung

### Vollständige Datenverwaltung

Vor Release 0.1.0:

- Backup vor Migration und Restore
- Aufbewahrung der 30 neuesten automatischen Backups
- versionierter JSON-Gesamtexport
- getesteter Restore-Roundtrip
- CSV-Exporte der fachlichen Tabellen
- Datenmanagement-Oberfläche

Die vollständige Restore-Prüfung erfolgt erst, wenn nach P6 sämtliche fachlichen Entitäten vorhanden sind.

## 13. Paket P8 – Launcher und Release 0.1.0

**Issue:** [#12](https://github.com/venomenon328/csc-x-tool/issues/12)

P8 verbindet Anwendung und vorgesehenen Windows-Betrieb:

- Eininstanzprüfung
- Loopback-Port und Health-Check
- Instanzdatei
- Öffnen des Standardbrowsers
- kontrolliertes Beenden
- gebündelte Java-Laufzeit
- Windows-App-Image oder Installer
- keine separate Java-/Node-/SQLite-Installation
- CSP, Same-Origin- und CSRF-Härtung
- begrenzte lokale Logs
- globale Interpret-/Titelsuche
- vollständiger E2E- und Vivaldi-Smoke-Test
- reproduzierbares Release 0.1.0

Ein kleiner Paketierungsspike darf deutlich früher stattfinden. Die endgültige Launcherarbeit bleibt trotzdem spät, damit nicht zuerst ein sehr eleganter Startknopf für eine weitgehend leere Anwendung entsteht.

## Fortschreibung – Issue #61, Paket 1

Paket 1 ersetzt die früheren Beitragsstatus durch das persistente Paar Einschätzung/Sicherheit. Es umfasst die Schema-8-zu-9-Migration, getrennte Metadaten- und Bewertungs-APIs, JSON-v3-Export mit kompatiblen v1/v2-Import-Upgrades, CSV-Felder sowie die kompakte, zugängliche Kartensteuerung mit Filtern und Sortierungen. Die Pool- und Ranking-DnD-Verträge bleiben dabei unverändert.

## Fortschreibung – Issue #61, Paket 2

Paket 2 ergänzt eine zentrale, rein testbare Vorschlags- und Warnlogik ohne Datenmodell-, Schema- oder Exportänderung. Aus bewerteten Beiträgen wird ausschließlich auf bewusste Aktion ein Ranglistenvorschlag berechnet und über den bestehenden atomaren Reorder-Vertrag gespeichert; unbewertete Beiträge bleiben im Pool. Die manuelle Drag-and-drop-Rangfolge bleibt nach jeder Anwendung vollständig maßgeblich, und Bewertungsänderungen lösen nie eine automatische Sortierung aus.

Der Abschlussdialog zeigt fachliche Hinweise zu Bewertungsstand, auffälligen Top-15-Positionen und einer knappen unsicheren 15/16-Grenze. Sie sind keine weiteren Validierungsregeln: Die bisherige Top-15-/Snapshot-Prüfung bleibt unverändert die einzige harte Abschlussblockade.

## 14. Freigabepunkte

### A – Technische Basis

Nach P0 und P1:

- Root-Build und CI sind grün.
- Frontend und Backend funktionieren integriert.
- SQLite und Liquibase funktionieren reproduzierbar.
- Die zwölf Shows werden persistent angezeigt.

### B – Kandidatenfähig

Nach P2 und frühem Backup-Kern:

- reale Kandidatenlisten können gepflegt werden
- Sortierung, Kopieren und Einreichung funktionieren
- Daten sind gegen einen einfachen lokalen Verlust abgesichert

### C – Abstimmungsfähig

Nach P4 und P5:

- realer CSC-Beitragsblock ist importierbar
- Beiträge können eingeschätzt und sortiert werden
- Top 15 kann abgeschlossen und ausgegeben werden

### D – Fachlich vollständig

Nach P3 und P6:

- Teilnehmerzuordnung funktioniert nach Abschluss
- eigene Punkte und Endplatzierung sind vollständig pflegbar
- eine Mottoshow kann abgeschlossen werden

### E – Releasefähig

Nach P7 und P8:

- Backup und Restore sind geprüft
- Launcher und Paketierung funktionieren
- der vollständige Akzeptanzpfad läuft im paketierten Produkt
- Vivaldi besteht den manuellen Smoke-Test
- Release 0.1.0 wird reproduzierbar erzeugt

## 15. Definition of Done für Implementierungs-PRs

Ein PR gilt nur dann als fertig, wenn die jeweils zutreffenden Punkte erfüllt sind:

- fachliches Verhalten entspricht Spezifikation und Paket-Issue
- Datenbankänderungen besitzen eine Liquibase-Migration
- Migrationen wurden von leerem Stand bis aktuell getestet
- Backendvalidierung und Datenbankregeln widersprechen sich nicht
- API-Fehler sind maschinenlesbar und für den Benutzer verständlich
- Frontend behandelt Lade-, Leer- und Fehlerzustände
- relevante Backend-, Frontend- und Integrationstests sind vorhanden
- bestehende Tests bleiben grün
- keine fachlichen Daten oder Clipboard-Rohinhalte werden unnötig geloggt
- Dokumentation wird bei geänderten Entscheidungen im selben PR aktualisiert
- manuelles Akzeptanzszenario des PRs wurde durchgeführt
- CI ist grün

## 16. Teststrategie entlang der Roadmap

### Backend

- Unit-Tests für reine fachliche Regeln
- Repository-Integrationstests mit temporären SQLite-Dateien
- API-Tests für Validierung und Fehlerobjekte
- Migrationstests von leer bis aktuell
- Transaktionstests für Reorder, Abschluss und Restore

### Frontend

- Komponententests für Formulare, Filter und Statusdarstellung
- Interaktionstests für Paste-Vorschau und Abschlussdialoge
- Drag-and-drop-Tests soweit technisch zuverlässig
- TypeScript-Check und Linting im Gesamtbuild

### End-to-End

E2E-Pfade wachsen paketweise:

1. App startet und zeigt zwölf Shows.
2. Kandidat wird angelegt und als Einreichung gewählt.
3. Teilnehmer wird angelegt.
4. Beitragsblock wird eingefügt und importiert.
5. Top 15 wird gebildet und abgeschlossen.
6. Teilnehmer werden zugeordnet und Ergebnisse erfasst.
7. Export, Neustart und Restore erhalten den Stand.
8. Paketierte Anwendung startet und beendet sich unter Windows.

Automatisierte Browsertests dürfen einen kompatiblen Chromium-Browser verwenden. Vivaldi erhält zusätzlich manuelle Smoke-Tests an den relevanten Freigabepunkten.

## 17. Technische Risiken und geplante Spikes

### 17.1 UI-Komponentenbibliothek

**Zeitpunkt:** P0/P2  
**Prüfung:** Dark Theme, Tabellen, Formulare, Drawer/Dialoge, lokale Bündelung, langfristige Wartbarkeit.

### 17.2 Drag-and-drop

**Zeitpunkt:** erste Auswahl in P2, endgültige Bestätigung in P5  
**Prüfung:** mehrere Listen, Auto-Scroll, sichtbarer Einfügeindikator, stabile 15/16-Grenze, Touch nicht erforderlich, gute Desktopbedienung.

### 17.3 SQLite und WAL

**Zeitpunkt:** P1  
**Prüfung:** Dateisperren, Backupverhalten, kontrolliertes Beenden und spätere Paketierung.

### 17.4 Rich Clipboard in Vivaldi

**Zeitpunkt:** P4 vor Parserimplementierung  
**Prüfung:** vorhandene MIME-Typen, Linkziele, Sonderzeichen, Verhalten ohne dauerhafte Clipboard-Berechtigung.

### 17.5 Windows-Paketierung

**Zeitpunkt:** kleiner Vorabspike nach P1 oder P2, vollständige Umsetzung in P8  
**Prüfung:** Browseröffnung, kein Konsolenfenster, gebündelte Runtime, Upgrades und Erhalt des Benutzerverzeichnisses.

Spikes enden mit einer dokumentierten Entscheidung. Wegwerfcode wird nicht aus Sentimentalität zur Produktionsarchitektur erklärt.

## 18. Branch- und PR-Konventionen

Empfohlene Branch-Namen:

```text
feat/4-technical-bootstrap
feat/5-sqlite-show-overview
feat/6-candidate-management
fix/9-ranking-autoscroll
chore/12-release-packaging
```

Empfohlene PR-Beschreibung:

- Zweck und Bezug zum Issue
- fachliche Änderungen
- technische Änderungen
- Testnachweise
- manuell geprüfter Ablauf
- bewusst vertagte Punkte

Ein Paket-Issue wird erst geschlossen, wenn alle Akzeptanzkriterien erfüllt sind. Teil-PRs verwenden `Refs #...`; der abschließende PR darf `Closes #...` verwenden.

## 19. Offene, nicht blockierende Eingaben

### Reale CSC-Formate

[Issue #15](https://github.com/venomenon328/csc-x-tool/issues/15) sammelt:

- vollständigen realen Beitragsblock für zusätzliche Parser-Sonderfälle
- endgültiges Top-15-Ausgabeformat

Beide Eingaben verfeinern P4 beziehungsweise P5, blockieren aber weder P0 noch P1.

### Statistiken

[Issue #14](https://github.com/venomenon328/csc-x-tool/issues/14) bleibt bewusst außerhalb von 0.1.0. Priorisiert wird erst nach mehreren realen Showergebnissen.

## 20. Unmittelbar nächster Arbeitsauftrag

Der nächste Implementierungsschritt ist [P0 / Issue #4](https://github.com/venomenon328/csc-x-tool/issues/4).

Empfohlene erste Arbeitsreihenfolge:

1. Branch `feat/4-technical-bootstrap` von aktuellem `main` erstellen.
2. aktuelle stabile Toolchainversionen anhand offizieller Quellen prüfen.
3. Maven Wrapper und Root-Aggregator anlegen.
4. minimales Spring-Boot-Backend mit Health-Endpunkt erzeugen.
5. React-/TypeScript-/Vite-Shell mit Dark Theme erzeugen.
6. Entwicklungsproxy und integrierten Produktionsbuild einrichten.
7. Backend- und Frontend-Smoke-Tests ergänzen.
8. GitHub Actions für den Gesamtbuild hinzufügen.
9. README und technische Entscheidungen aktualisieren.
10. P0-Akzeptanzkriterien gegen frischen Checkout prüfen.

Erst danach folgt SQLite und Liquibase in P1. Das verhindert, dass Build-, Frontend- und Datenbankprobleme gleichzeitig in einem einzigen freundlichen schwarzen Loch verschwinden.

## 21. Pflege dieses Plans

Der Plan wird angepasst, wenn:

- eine verbindliche Produktentscheidung geändert wird
- ein Spike eine Architekturannahme widerlegt
- eine Paketgrenze nachweislich unpraktisch ist
- ein neues blockierendes Risiko entdeckt wird

Reine Implementierungsdetails werden im jeweiligen Issue oder PR dokumentiert und müssen nicht jede Zeile dieses Dokuments in Bewegung versetzen.
