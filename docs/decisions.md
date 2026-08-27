# Entscheidungsprotokoll

**Stand:** 27.08.2026

Dieses Dokument hält die bisher verbindlich getroffenen Produkt- und Architekturentscheidungen fest. Es ersetzt keine ausführliche Anforderung aus der [Produktspezifikation](specification.md), sondern erklärt die maßgeblichen Grenzziehungen.

## Verbindliche Produktentscheidungen

### D-001 – Genau eine CSC-Ausgabe

Das Tool wird für die aktuelle Ausgabe mit zwölf Mottoshows gebaut.

Es gibt kein Modell für mehrere Ausgaben, Saisons oder wiederkehrende Contests. Die Namen der Shows bleiben editierbar, insbesondere wegen der zunächst offenen neunten Mottoshow.

### D-002 – Das Tool beginnt nach der Recherche

Ein Kandidat wird erst eingetragen, nachdem der Benutzer außerhalb des Tools seine grundsätzliche Motto-Eignung und Zulässigkeit geprüft hat.

Das Tool speichert daher keine Nachweise, Quellen, Veröffentlichungsjahre, Versionen, Veranstaltungen oder Regelprüfstatus.

### D-003 – Keine Ausschlusslistenprüfung

Die CSC-Ausschlussliste wird weder importiert noch durchsucht. Eine automatische Warnung gegen frühere Einreichungen ist nicht Bestandteil des Produkts.

### D-004 – Kandidaten sind showbezogen

Jede Mottoshow besitzt eine eigene Kandidatenliste.

Ein Kandidat kann in eine andere Show kopiert werden. Dabei entsteht ein unabhängiger Datensatz; spätere Änderungen werden nicht synchronisiert.

### D-005 – Einfaches Kandidatenmodell

Ein Kandidat besitzt nur Interpret, Titel, YouTube-Link, optionalen Kommentar, Status und manuelle Position.

Strategische Bewertungen, Genre, Bekanntheitsgrad und Profile bleiben außerhalb des Tools.

### D-006 – Statusmodell

Manuell pflegbare Kandidatenstatus sind:

- Offen
- Im Rennen
- Engere Auswahl
- Finalist
- Verworfen

„Eingereicht“ wird aus der ausgewählten eigenen Einreichung abgeleitet und nicht als normaler Status gepflegt.

Ein Archivstatus existiert nicht.

### D-007 – Getrennte Showansichten

Kandidaten und Wettbewerbsbeiträge werden immer im Kontext einer konkreten Mottoshow bearbeitet.

Eine globale Suche darf Treffer aus mehreren Shows liefern, ersetzt aber keine getrennten Arbeitsansichten.

### D-008 – Beitragsblock per Zwischenablage statt CSV

Die Beiträge anderer Teilnehmer werden primär direkt aus dem formatierten CSC-Beitrag übernommen:

1. Beitragsblock im Forum kopieren
2. Importfläche im Tool öffnen
3. `Strg+V`
4. Importvorschau prüfen und bestätigen

Die Forensoftware stellt die Beiträge als anklickbare Linktexte im Format `Interpret - Titel` dar. Beim Kopieren bleiben die Linkziele in einer formatierten Zwischenablage-Repräsentation erhalten. Der Importer wertet daher Rich-Text-/HTML-Daten des Paste-Vorgangs aus und übernimmt sichtbaren Linktext sowie URL.

Ein gewöhnliches Plaintext-Textfeld ist nicht der Primärweg. CSV bleibt ein mögliches Exportformat, ist aber kein Importweg.

### D-009 – Robuste Import-Fallbacks

Der Beitragsimport unterstützt zusätzlich:

- markdownartige Links
- Plaintext mit expliziter URL
- Plaintext ohne URL als unvollständige Vorschau

Rich HTML mit anklickbaren Links besitzt Priorität. Fallback-Darstellungen dürfen keine zusätzlichen Dubletten erzeugen, wenn dieselben Beiträge bereits aus HTML erkannt wurden.

### D-010 – Import immer mit Vorschau

Zwischenablageinhalte werden nicht unmittelbar als fertige Wettbewerbsbeiträge gespeichert.

Vor dem Import werden Interpret, Titel, Link und Erkennungsstatus angezeigt. Zweifelhafte `Interpret - Titel`-Trennungen, fehlende URLs und ungewöhnliche Linkziele können manuell korrigiert werden.

### D-011 – Anonyme Beiträge

Wettbewerbsbeiträge werden zunächst ohne Teilnehmer erfasst.

Die Teilnehmerzuordnung wird erst nach Abschluss der eigenen Abstimmung freigeschaltet.

### D-012 – Schlanker Hörzustand

Für einen Wettbewerbsbeitrag genügen:

- gehört
- erneut anhören
- Kommentar
- optionale Rangposition

Ein kombinierter, feingranularer Workflowstatus wird nicht eingeführt.

### D-013 – Lange Rangliste, verbindliche Top 15

Alle Beiträge dürfen sortiert werden. Für den Abschluss sind nur mindestens 15 gerankte Beiträge erforderlich.

Die ersten 15 bilden die ausgehende Bewertung. Sie sind eindeutig geordnet und enthalten keine Gleichstände.

### D-014 – Ausgehende Bewertung ohne Punktangaben

Die kopierte oder exportierte Bewertung enthält standardmäßig nur die Rangliste 1 bis 15.

Die Punktzuordnung wird intern für spätere Auswertungen vorgehalten, aber nicht zwingend ausgegeben.

### D-015 – Abschluss mit Snapshot

Der Abschluss einer Abstimmung speichert einen Top-15-Snapshot.

Eine Abstimmung kann bewusst wieder geöffnet werden; ein erneuter Abschluss erzeugt einen neuen Snapshot.

### D-016 – Teilnehmerstammdaten bleiben klein

Teilnehmer besitzen:

- Anzeigenamen
- Land mit Flagge
- frühere Namen oder Aliasse
- Aktivstatus

Eine Länderhistorie oder Teilnahmeverwaltung pro Show existiert nicht. Aktive Teilnehmer gelten grundsätzlich für alle zwölf Shows.

### D-017 – Der Benutzer ist kein Teilnehmerdatensatz

Der Benutzer selbst wird nicht in den Teilnehmerstammdaten geführt.

### D-018 – Ergebniszustände werden unterschieden

Für die erhaltenen Punkte werden folgende Fälle getrennt:

- noch unbekannt
- nicht abgestimmt
- abgestimmt mit 0 bis 25 zulässigen Punkten

Damit kann ein vollständiger Ergebnisstand zuverlässig von einer nur teilweise gepflegten Tabelle unterschieden werden.

### D-019 – Berechnete und offizielle Gesamtpunktzahl

Die Summe der Einzelwertungen wird berechnet.

Eine offizielle Gesamtpunktzahl kann zusätzlich gespeichert werden. Eine Abweichung wird angezeigt, aber nicht automatisch aufgelöst.

### D-020 – Endplatzierung mit Gleichstandskennzeichen

Die Endplatzierung wird als Zahl gespeichert und kann zusätzlich als geteilt gekennzeichnet werden.

Die allgemeinen Gleichstandsregeln des CSC werden darüber hinaus nicht implementiert.

### D-021 – Keine Fristen

Einreichungs- und Abstimmungsfristen werden nicht gepflegt.

### D-022 – Dark Mode als einziger Modus

Die Anwendung erhält eine hochwertige dunkle Oberfläche. Ein heller Modus ist nicht erforderlich.

### D-023 – Drag-and-drop ist Kernfunktion

Manuelle Kandidatensortierung und Beitragsranking erfolgen komfortabel per Drag-and-drop.

Eine besondere Tastatursteuerung ist nicht erforderlich.

### D-024 – YouTube eingebettet und extern

Ein eingebetteter Player ist vorgesehen, sofern das jeweilige Video dies zulässt.

Der externe YouTube-Link bleibt immer als zuverlässiger Fallback verfügbar.

## Verbindliche Architekturentscheidungen

### A-001 – Lokale Webanwendung im Browser

Die Anwendung läuft lokal und wird im Standardbrowser geöffnet. Ein eigenständiges Desktopfenster und WPF werden nicht verwendet.

Vivaldi ist das primäre Browserziel.

### A-002 – Ein lokaler Prozess

Das Backend liefert API und gebautes Frontend aus einem Prozess aus.

Im installierten Betrieb laufen kein separater Node-Server und kein Datenbankdienst.

### A-003 – Spring Boot und React/TypeScript

Das Backend wird mit Spring Boot umgesetzt. Das Frontend wird mit React und TypeScript gebaut.

Konkrete Frameworkversionen werden beim Bootstrap festgelegt.

### A-004 – SQLite

SQLite wird als lokale Datenbank verwendet.

PostgreSQL wäre für einen einzelnen lokalen Benutzer unnötige Betriebsinfrastruktur.

### A-005 – Explizites SQL

Der Backendzugriff erfolgt vorzugsweise über Spring JDBC und explizite Repository-Klassen statt einer schweren ORM-Schicht.

### A-006 – Versionierte Migrationen

Das Datenbankschema wird über Liquibase versioniert.

### A-007 – Windows-Launcher

Ein komfortabler Windows-Launcher startet die Anwendung ohne manuellen Konsolenbefehl und öffnet den Standardbrowser.

Ein erneuter Start öffnet die bereits laufende Instanz statt einen zweiten Server zu erzeugen.

### A-008 – Lokale Datenhoheit

Die Anwendung bindet nur an Loopback, besitzt keine Telemetrie und benötigt keine Anmeldung.

Externe Verbindungen entstehen nur durch den Browser beim Einbetten oder Öffnen von YouTube.

### A-009 – Normaler Paste-Event statt dauerhafter Clipboard-Berechtigung

Die Importfläche verarbeitet den vom Benutzer ausgelösten Browser-`paste`-Event und liest daraus `text/html` sowie `text/plain`.

Für den normalen Import wird kein dauerhafter oder hintergründiger Zugriff auf die Systemzwischenablage benötigt. `navigator.clipboard.read()` ist keine Voraussetzung für die Kernfunktion.

### A-010 – Clipboard-HTML ist untrusted input

Formatierter Zwischenablageinhalt wird niemals als HTML in der Oberfläche gerendert.

Der Parser extrahiert nur relevante Linktexte und URLs. Der rohe HTML-/Textblock wird initial nicht dauerhaft gespeichert oder vollständig protokolliert.

### A-011 – Parserlogik zentral testbar

Die Baseline sieht vor, `text/html` und `text/plain` vom Frontend an einen Import-Preview-Endpunkt zu senden und die eigentlichen Parserregeln serverseitig deterministisch zu testen.

Falls ein technischer Spike eine kleine Vorverarbeitung im Browser erforderlich macht, darf die reine Linkextraktion verschoben werden; Vorschau, Validierung und fachliche Importregeln bleiben zentral reproduzierbar.

### A-012 – Material UI für die Komponentenbasis

Der P0-Spike legt **Material UI 9.3.1** als Komponentenbibliothek fest; die benötigten Emotion-Pakete werden lokal mit dem Frontend gebündelt. Der Spike prüft das dunkle Theme sowie Drawer, Formularfeld, Tabelle und Dialog in einer kleinen rein technischen App-Shell. Die Bibliothek liefert dafür eine konsistente, gut wartbare React-Basis ohne eigenen Runtime-Service und ohne in späteren Paketen Fachlogik vorwegzunehmen.

Diese Entscheidung umfasst ausdrücklich **keine** Drag-and-drop-Bibliothek. Deren Wahl bleibt für die echte Kandidatenliste in P2 offen.

### A-013 – SQLite-WAL und verbindungslokale Regeln

P1 verwendet den WAL-Modus als SQLite-Baseline. Der Spike läuft gegen echte temporäre Datenbankdateien: WAL bleibt nach erneutem Öffnen aktiv, und ein schreibender Zugriff gelingt, während eine zweite Verbindung einen konsistenten Lesesnapshot hält.

`foreign_keys=ON` und ein `busy_timeout` von fünf Sekunden werden beim Öffnen **jeder** Verbindung gesetzt, weil beide SQLite-Pragmas verbindungslokal sind. Liquibase und die fachlichen Repositories verwenden dieselbe konfigurierte Datasource. Migrationsfehler brechen den Start mit einer verständlichen Meldung ab, werden nicht in Storage-Fehler übersetzt und behalten ihre SQLite-Ursache in der Fehlerkette.

## Bewusst vertagte Entscheidungen

### O-001 – Vollständiger Import-Testblock

Das Kernformat ist nicht mehr offen: formatierte Links mit sichtbarem `Interpret - Titel` und erhaltenem Linkziel.

Vor der finalen Parserimplementierung soll noch ein vollständiger realer Beitragsblock einer Mottoshow als Testfixture bereitgestellt werden, um Sonderfälle und ungewöhnliche Titel abzudecken.

### O-002 – Ausgabeformat der Top 15

Offen bis ein reales oder gewünschtes Einreichungsformat vorliegt.

### O-003 – Statistiken und Diagramme

Werden anhand realer Daten separat priorisiert. Das initiale Datenmodell soll die spätere Auswertung ermöglichen, ohne bereits eine Berichtssammlung vorzutäuschen.

### O-004 – Drag-and-drop-Bibliothek

Die konkrete Bibliothek wird erst mit der echten Kandidatenliste in P2 gewählt. Maßgeblich sind mehrere Listen, Auto-Scroll, Einfügeindikator und die Desktopbedienung; P0 zieht keine Drag-and-drop-Implementierung vor.

### O-005 – HTML-Parser und genaue Parseraufteilung

Ob die HTML-Linkextraktion vollständig im Backend beispielsweise mit jsoup oder teilweise im Browser erfolgt, wird beim Import-Spike entschieden. Das ändert weder Bedienung noch fachliches Verhalten.

### O-006 – Installer- und Launcher-Details

Das Produktverhalten ist festgelegt; konkrete Paketierungswerkzeuge, Icon und Installerform werden im technischen Bootstrap entschieden.

## Kein weiterer fachlicher Klärungsbedarf vor dem Bootstrap

Die vertagten Punkte blockieren die Grundstruktur, das Datenmodell und die ersten Entwicklungsinkremente nicht.

Insbesondere ist der bisherige offene Punkt zum grundsätzlichen Importformat ausreichend geklärt: Der technische Bootstrap kann die Importfläche und den Preview-Contract bereits berücksichtigen; der vollständige reale Block wird später für Parserhärtung und Tests benötigt.
