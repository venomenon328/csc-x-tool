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

### D-012 – Kompakte Beitragseinschätzung

Für einen Wettbewerbsbeitrag genügen:

- Einschätzung und Sicherheit als gemeinsames fünfstufiges Paar oder unbewertet
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

### A-014 – `@hello-pangea/dnd` für manuelle Listen

Der P2-Spike verwendet **`@hello-pangea/dnd` 18.0.1** in der echten Kandidatenliste mit React 19.2.8, TypeScript 6.0.3 und Material UI 9.3.1. Er deckt eine vertikale, scrollbar begrenzte Kandidatenliste mit eigenem Drag-Handle, sichtbarem Drop-Hinweis und den integrierten Auto-Scroll-Mechanismen der Bibliothek ab. Buttons, Statusauswahl und externe Links bleiben innerhalb einer Kandidatenzeile normal bedienbar, weil nur der explizite Handle ziehbar ist.

Der Spike zeigte keinen Kompatibilitätsblocker. Die Bibliothek bleibt daher die einzige DnD-Abhängigkeit; ihre Droppable-/Draggable-Struktur unterstützt auch den späteren Zwei-Listen-Fall in P5, ohne Ranking-Fachlogik vorwegzunehmen. Ein Drop sendet genau einen vollständigen Reorder-Command; bei Fehler stellt die Oberfläche den letzten serverbestätigten Stand wieder her.

### A-015 – Lokaler ISO-Länderkatalog und Flaggenassets

P3 führt einen versionierten, serverseitig validierten Katalog aller regulären ISO-3166-1-Alpha-2-Codes mit deutschen Ländernamen unter `backend/src/main/resources/countries/countries-de.json` ein. Die Teilnehmer-API liefert ausschließlich diesen Katalog, nach deutschem Anzeigenamen sortiert; das Frontend pflegt keine zweite Länderliste. Gespeichert werden nur aus diesem Katalog stammende, kanonisch großgeschriebene Codes.

Für Flaggen verwendet das Frontend `country-flag-icons` **1.6.20** als lokal gebündelte MIT-Abhängigkeit in ihrem 3:2-React-Format. Weder Bilder noch Daten werden von einem CDN oder einer Flaggen-API geladen. `CountryFlag` kapselt die lokale Darstellung inklusive neutralem Fallback. Der erforderliche MIT-Lizenzhinweis ist in `frontend/THIRD-PARTY-NOTICES.md` enthalten und wird zusammen mit dem Produkt beibehalten.

### A-016 – Serverseitige P4-Importpipeline mit jsoup

P4 liest `text/html` und `text/plain` ausschließlich aus demselben normalen Browser-`paste`-Event und sendet beide Repräsentationen an den showbezogenen Preview-Endpunkt. Die serverseitige Pipeline nutzt **jsoup 1.23.2** ausschließlich zum Parsen des HTML-Fragments; sie lädt keine Linkziele oder sonstige externe Ressourcen nach. Sie gibt nur extrahierte Linktexte, Linkziele und relevante unvollständige Zeilen als Text zurück. Rohe Clipboard-Blöcke werden weder gerendert, persistiert noch normal geloggt.

Die gemeinsame YouTube-Normalisierung und die datensparsame Player-Fläche wurden aus P2 als Song-Basis extrahiert und werden für Kandidaten und Wettbewerbsbeiträge wiederverwendet. Der bestätigte Import validiert alle ausgewählten Zeilen erneut und speichert sie atomar.

Der verpflichtende manuelle Vivaldi-Smoke mit einem real aus dem CSC-Editor kopierten Block wurde bei der Implementierung **nicht** durchgeführt. A-009 und A-011 sind für den automatisiert getesteten Implementierungsweg umgesetzt, die Browserannahme bleibt bis zu dieser manuellen Abnahme ausdrücklich offen.

### A-017 – P5-Rangfolge, Snapshot und neutrale Ausgabe

P5 verwendet ausschließlich die seit P4 vorhandene Spalte `contest_entry.ranking_position`. Ein einzelner Drag-and-drop-Abschluss sendet die vollständigen ID-Listen beider Arbeitsbereiche. Das Backend akzeptiert sie nur, wenn sie zusammen alle aktuellen Beiträge der Show genau einmal enthalten, und ersetzt die Rangpositionen innerhalb einer SQLite-Transaktion lückenlos durch `1..n`; alle ungeordneten Beiträge erhalten `NULL`. Die serverbestätigten zwei Listen sind die fachliche Quelle für die Oberfläche; ein Speicherfehler setzt sie auf den letzten bestätigten Stand zurück.

Der Abschluss setzt `motto_show.ballot_closed_at` und erzeugt eine unveränderliche Top-15-Kopie in `ballot_snapshot` und `ballot_snapshot_item`. Die Originalreferenz ist absichtlich nullable und verwendet `ON DELETE SET NULL`, damit eine spätere erlaubte Löschung den historischen Text nicht zerstört. Wiederöffnen macht ausschließlich den aktuellen Snapshot historisch; ein späterer Abschluss erzeugt die monoton nächste Fassung. Rangpunkte werden zentral aus dem Rang berechnet und nicht gespeichert.

Bis zur realen Vorlage aus #15 ist der Renderer bewusst neutral und verbindlich: exakt 15 Zeilen `1. Interpret - Titel` bis `15. Interpret - Titel`, ohne Überschrift oder Punktwerte. Vorschau, Clipboard und UTF-8-Textdatei verwenden denselben serverseitigen Renderer und ausschließlich den aktuellen Snapshot. Der P5-Vivaldi-Smoke mit ungefähr 30 Beiträgen ist noch nicht durchgeführt; die manuelle Abnahme bleibt ausdrücklich offen, solange er nicht in Vivaldi dokumentiert ist.

### A-018 – P7-Backupcontainer, Staging-Restore und zentraler Datenlock

P7 verwendet für jede laufende SQLite-Datenbank ausschließlich die Xerial-Erweiterungen `BACKUP TO` und `RESTORE FROM`, die auf der SQLite Online Backup API basieren. Die geöffnete Hauptdatei und ihre WAL-Nebenfiles werden niemals kopiert. Ein erfolgreiches Backup ist ein atomar veröffentlichtes `.cscbackup`-ZIP mit `manifest.json` und `database.sqlite`; das Manifest v1 enthält UTC-Zeitpunkt, Build-Version, Schema-Generation, Anlass und SHA-256 des Snapshots. Vor Veröffentlichung und vor Restore prüfen SHA-256, `PRAGMA quick_check` und `PRAGMA foreign_key_check` die Datei.

`STARTUP` und `PRE_MIGRATION` liegen gemeinsam in `backups/automatic` und werden erst nach erfolgreicher neuer Sicherung auf die jüngsten 30 Artefakte gekürzt. `MANUAL` und `PRE_RESTORE` liegen in `backups/manual` und werden nie automatisch gelöscht. Ein Start erzeugt nach erfolgreicher Migration zwingend ein `STARTUP`-Backup; bei tatsächlich ausstehenden Changesets einer vorhandenen Datenbank entsteht zuvor ein `PRE_MIGRATION`-Backup. Schlagen diese Pflichtsicherungen fehl, gilt der Start nicht als erfolgreich.

Restore-Dateien gelangen nur als Uploadbytes oder per serverseitig aufgelöster Backup-ID in das Backend. Native Backups werden entpackt, geprüft und bei Bedarf in einer temporären echten SQLite-Stagingdatei vorwärts migriert. JSON-v1 wird ausschließlich als expliziter vollständiger Fachvertrag in eine frisch migrierte Stagingdatei eingespielt; Liquibase- und Laufzeitdaten, Pfade, Logs und der Länderkatalog sind nicht Bestandteil. Erst eine erfolgreiche Vorschau darf separat bestätigt werden.

Ein fairer zentraler Read/Write-Lock um jede normale JDBC-Verbindung und den finalen Restore-Switch verhindert parallelen Repositoryzugriff. Unter dem exklusiven Lock entsteht unmittelbar vor dem Umschalten ein geprüftes `PRE_RESTORE`-Backup. Die Stagingdatei wird anschließend per Online-Restore eingespielt und die Live-Datenbank erneut geprüft. Bei einem Fehler nach dem Umschalten wird diese Sicherheitskopie automatisch zurückgespielt; technische I/O-, SQLite- und Liquibase-Ursachen bleiben technische Fehler und werden nicht als fachliche Inkompatibilität etikettiert.

Erg\u00e4nzend ist der JSON-Vollauszug ein einziger SQLite-Lesesnapshot: Alle Tabellen werden in derselben Read-Transaktion gelesen, so dass ein paralleler WAL-Schreibvorgang keine gemischten St\u00e4nde erzeugt und der exklusive Restore-Switch bis zum Exportende wartet. Ein JSON-v1-Import pr\u00fcft vor dem Staging neben Format und Version auch den UTC-Exportzeitpunkt, genau zw\u00f6lf Mottoshows, alle Referenzen, Abschluss-/Snapshot-Invarianten sowie Codes des lokalen L\u00e4nderkatalogs.

Die Entscheidung f\u00fcr PRE_MIGRATION basiert auf einer vor DataSource-Erzeugung ermittelten vorhandenen Liquibase-Schemahistorie, nicht auf der blo\u00dfen Existenz einer SQLite-Datei. Schlagen nach einem Live-Restore sowohl die Wiederherstellung als auch die gepr\u00fcfte R\u00fccksicherung fehl, liefert die API den eigenen technischen Zustand `RESTORE_RECOVERY_FAILED`; sie behauptet in diesem Fall keinen erhaltenen oder bekannten Datenstand.

### A-019 – Persistente Beitragseinschätzung als Paar ohne automatische Rangfolge

Wettbewerbsbeiträge speichern ab Schema 9 nur noch die nullable Integer `assessment` und `assessment_confidence`. Sie sind entweder beide `NULL` oder jeweils im Bereich 1 bis 5. Die SQLite-Migration rebuildet die reale Schema-8-Tabelle kontrolliert und erhält IDs, Pool- und Rangpositionen, Teilnehmerbeziehungen, Kommentare, Zeitstempel, Fremdschlüssel und Indizes. Die ehemaligen Flags werden bewusst neutral abgebildet: Nicht bearbeitete Beiträge bleiben unbewertet; ein bearbeiteter Beitrag wird zu Einschätzung 3 mit Sicherheit 1 bei erneutem Anhören beziehungsweise 2 sonst.

Metadaten-PATCH und Bewertungs-PATCH sind absichtlich getrennt. Der Bewertungs-PATCH darf weder Metadaten noch Pool- oder Rangposition verändern; der Client sperrt ihn pro Beitrag bis zur Serverantwort und übernimmt daraus nur die Bewertungsfelder. Der vollständige Export verwendet v3. Die Import-Upgrades von JSON-v1 und JSON-v2 führen dieselbe konservative Flag-Abbildung aus; CSV exportiert Einschätzung und Sicherheit als leere oder numerische Felder.

Die Kartensteuerung nutzt fünf Sterne für Einschätzung und fünf Punkte für Sicherheit. Filter und Sortierungen verwenden die neue Fachsprache, die manuelle Pool- und Ranglisten-DnD-Semantik bleibt unverändert. Dieses Paket führt weder Ranglistenvorschläge noch Abschlusswarnungen ein; beide bleiben ein bewusst getrenntes Folgepaket.

## Bewusst vertagte Entscheidungen

### O-001 – Vollständiger Import-Testblock

Das Kernformat ist nicht mehr offen: formatierte Links mit sichtbarem `Interpret - Titel` und erhaltenem Linkziel.

Vor der finalen Parserimplementierung soll noch ein vollständiger realer Beitragsblock einer Mottoshow als Testfixture bereitgestellt werden, um Sonderfälle und ungewöhnliche Titel abzudecken.

### O-002 – Ausgabeformat der Top 15

Das endgültige CSC-Format bleibt bis zu einem realen oder gewünschten Einreichungsformat offen. Bis dahin gilt die in A-017 dokumentierte neutrale 15-Zeilen-Ausgabe.

### O-003 – Statistiken und Diagramme

Werden anhand realer Daten separat priorisiert. Das initiale Datenmodell soll die spätere Auswertung ermöglichen, ohne bereits eine Berichtssammlung vorzutäuschen.

### O-004 – Drag-and-drop-Bibliothek (erledigt in P2)

Die konkrete Bibliothek ist mit A-014 auf `@hello-pangea/dnd` 18.0.1 festgelegt. Ihre endgültige Bestätigung für den komplexeren Ranking-Fall bleibt P5 vorbehalten; dort entsteht jedoch keine zweite DnD-Abhängigkeit ohne einen nachgewiesenen Blocker.

### O-005 – HTML-Parser und genaue Parseraufteilung (für P4 entschieden)

Die HTML-Linkextraktion erfolgt in P4 vollständig serverseitig mit jsoup 1.23.2; das Frontend übergibt nur `text/html` und `text/plain` aus dem Paste-Event und rendert kein Clipboard-HTML. Der reale Vivaldi-/CSC-Paste-Smoke bleibt als verpflichtende manuelle Abnahme offen, weil kein echter CSC-Block in Vivaldi durchgeführt und dokumentiert wurde.

### O-006 – Installer- und Launcher-Details

Das Produktverhalten ist festgelegt; konkrete Paketierungswerkzeuge, Icon und Installerform werden im technischen Bootstrap entschieden.

## Kein weiterer fachlicher Klärungsbedarf vor dem Bootstrap

Die vertagten Punkte blockieren die Grundstruktur, das Datenmodell und die ersten Entwicklungsinkremente nicht.

Insbesondere ist der bisherige offene Punkt zum grundsätzlichen Importformat ausreichend geklärt: Der technische Bootstrap kann die Importfläche und den Preview-Contract bereits berücksichtigen; der vollständige reale Block wird später für Parserhärtung und Tests benötigt.
