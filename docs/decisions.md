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

### D-008 – Textblock statt CSV-Import

Die Beiträge anderer Teilnehmer werden aus einem Textblock importiert. Ein reales Beispiel wird vor Implementierung des Parsers nachgereicht.

CSV bleibt ein mögliches Exportformat, ist aber kein Importweg.

### D-009 – Anonyme Beiträge

Wettbewerbsbeiträge werden zunächst ohne Teilnehmer erfasst.

Die Teilnehmerzuordnung wird erst nach Abschluss der eigenen Abstimmung freigeschaltet.

### D-010 – Schlanker Hörzustand

Für einen Wettbewerbsbeitrag genügen:

- gehört
- erneut anhören
- Kommentar
- optionale Rangposition

Ein kombinierter, feingranularer Workflowstatus wird nicht eingeführt.

### D-011 – Lange Rangliste, verbindliche Top 15

Alle Beiträge dürfen sortiert werden. Für den Abschluss sind nur mindestens 15 gerankte Beiträge erforderlich.

Die ersten 15 bilden die ausgehende Bewertung. Sie sind eindeutig geordnet und enthalten keine Gleichstände.

### D-012 – Ausgehende Bewertung ohne Punktangaben

Die kopierte oder exportierte Bewertung enthält standardmäßig nur die Rangliste 1 bis 15.

Die Punktzuordnung wird intern für spätere Auswertungen vorgehalten, aber nicht zwingend ausgegeben.

### D-013 – Abschluss mit Snapshot

Der Abschluss einer Abstimmung speichert einen Top-15-Snapshot.

Eine Abstimmung kann bewusst wieder geöffnet werden; ein erneuter Abschluss erzeugt einen neuen Snapshot.

### D-014 – Teilnehmerstammdaten bleiben klein

Teilnehmer besitzen:

- Anzeigenamen
- Land mit Flagge
- frühere Namen oder Aliasse
- Aktivstatus

Eine Länderhistorie oder Teilnahmeverwaltung pro Show existiert nicht. Aktive Teilnehmer gelten grundsätzlich für alle zwölf Shows.

### D-015 – Der Benutzer ist kein Teilnehmerdatensatz

Der Benutzer selbst wird nicht in den Teilnehmerstammdaten geführt.

### D-016 – Ergebniszustände werden unterschieden

Für die erhaltenen Punkte werden folgende Fälle getrennt:

- noch unbekannt
- nicht abgestimmt
- abgestimmt mit 0 bis 25 zulässigen Punkten

Damit kann ein vollständiger Ergebnisstand zuverlässig von einer nur teilweise gepflegten Tabelle unterschieden werden.

### D-017 – Berechnete und offizielle Gesamtpunktzahl

Die Summe der Einzelwertungen wird berechnet.

Eine offizielle Gesamtpunktzahl kann zusätzlich gespeichert werden. Eine Abweichung wird angezeigt, aber nicht automatisch aufgelöst.

### D-018 – Endplatzierung mit Gleichstandskennzeichen

Die Endplatzierung wird als Zahl gespeichert und kann zusätzlich als geteilt gekennzeichnet werden.

Die allgemeinen Gleichstandsregeln des CSC werden darüber hinaus nicht implementiert.

### D-019 – Keine Fristen

Einreichungs- und Abstimmungsfristen werden nicht gepflegt.

### D-020 – Dark Mode als einziger Modus

Die Anwendung erhält eine hochwertige dunkle Oberfläche. Ein heller Modus ist nicht erforderlich.

### D-021 – Drag-and-drop ist Kernfunktion

Manuelle Kandidatensortierung und Beitragsranking erfolgen komfortabel per Drag-and-drop.

Eine besondere Tastatursteuerung ist nicht erforderlich.

### D-022 – YouTube eingebettet und extern

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

## Bewusst vertagte Entscheidungen

### O-001 – Parserformat

Offen bis ein realer Textblock aus einer früheren CSC-Ausgabe vorliegt.

### O-002 – Ausgabeformat der Top 15

Offen bis ein reales oder gewünschtes Einreichungsformat vorliegt.

### O-003 – Statistiken und Diagramme

Werden anhand realer Daten separat priorisiert. Das initiale Datenmodell soll die spätere Auswertung ermöglichen, ohne bereits eine Berichtssammlung vorzutäuschen.

### O-004 – Konkrete UI- und Drag-and-drop-Bibliothek

Wird nach einem kleinen technischen Spike entschieden. Maßgeblich ist die Bedienqualität, nicht die Loyalität zu einem JavaScript-Paket.

### O-005 – Installer- und Launcher-Details

Das Produktverhalten ist festgelegt; konkrete Paketierungswerkzeuge, Icon und Installerform werden im technischen Bootstrap entschieden.

## Kein weiterer fachlicher Klärungsbedarf vor dem Bootstrap

Die vertagten Punkte blockieren die Grundstruktur, das Datenmodell und die ersten Entwicklungsinkremente nicht.

Insbesondere können Übersicht, Kandidaten, Teilnehmer, manuelle Beitragsverwaltung, Rangliste und Ergebnismodell bereits entwickelt werden, bevor Parser- und Ausgabeformat final bekannt sind.
