# Produktspezifikation – CSC X Tool

**Version:** 0.2  
**Stand:** 27.08.2026  
**Status:** gemeinsam erarbeitete fachliche Baseline für die weitere Entwicklung

## 1. Zweck des Produkts

Das CSC X Tool ist eine lokal laufende Einzelbenutzer-Anwendung zur Unterstützung einer konkreten Ausgabe des CyBoard Song Contest.

Die Anwendung begleitet den praktischen Ablauf jeder Mottoshow von der bereits vorrecherchierten Kandidatenliste über das eigene Voting bis zur Dokumentation des Ergebnisses der eigenen Einreichung.

Der Kernablauf lautet:

1. Kandidaten für eine Mottoshow eintragen
2. Kandidaten anhören, kommentieren, priorisieren und aussortieren
3. die eigene Einreichung festlegen
4. die anonymen Wettbewerbsbeiträge als formatierten Beitragsblock aus der Zwischenablage importieren
5. Beiträge anhören und eine persönliche Rangliste bilden
6. die eindeutige Top 15 abschließen und als Text ausgeben
7. nach Abschluss der Abstimmung Beiträge Teilnehmern zuordnen
8. die von den Teilnehmern an die eigene Einreichung vergebenen Punkte erfassen
9. Gesamtpunktzahl und Endplatzierung dokumentieren

## 2. Produktgrenzen

### 2.1 Im initialen Produktumfang

- Verwaltung der zwölf vorgesehenen Mottoshows dieser CSC-Ausgabe
- Verwaltung eigener Einreichungskandidaten je Mottoshow
- unabhängige Kandidatenlisten je Mottoshow
- Kopieren eines Kandidaten in eine andere Mottoshow
- Status, Kommentar und manuelle Reihenfolge der Kandidaten
- Festlegen genau einer eigenen Einreichung je Mottoshow
- Teilnehmerstammdaten mit Land und Flagge
- Import der anonymen Wettbewerbsbeiträge aus einem formatierten, aus dem CSC kopierten Beitragsblock
- Übernahme der in der Zwischenablage enthaltenen Linkziele, insbesondere YouTube-URLs
- Einschätzung, Sicherheit, Kommentar und Rangposition der Beiträge
- komfortable Rangbildung per Drag-and-drop
- Abschluss und Textausgabe einer eindeutigen Top 15
- nachträgliche Zuordnung der Beiträge zu Teilnehmern
- Erfassung der für die eigene Einreichung erhaltenen Punkte je Teilnehmer
- Unterscheidung zwischen unbekanntem Abstimmungsstand, nicht abgegebener Abstimmung und einer abgegebenen Abstimmung mit null oder mehr Punkten
- berechnete und optional zusätzlich erfasste offizielle Gesamtpunktzahl
- Endplatzierung einschließlich Kennzeichnung einer geteilten Platzierung
- lokale Persistenz, automatische Sicherungen und manuelle Exporte
- lokaler Windows-Launcher ohne notwendigen Konsolen- oder PowerShell-Aufruf

### 2.2 Ausdrücklich nicht im initialen Produktumfang

- Recherche nach Kandidaten
- KI-Anbindung
- automatische Prüfung der Mottoregeln
- automatische Prüfung gegen die CSC-Ausschlussliste
- Verwaltung von Quellen, Nachweisen, Veröffentlichungsjahren, konkreten Veranstaltungen oder sonstigen Mottoshow-spezifischen Zusatzdaten
- Bewertung von Repräsentations-Fit, Gewinnchance, Genre, Bekanntheitsgrad oder strategischem Profil
- vollständige Verwaltung der Abstimmungen anderer Teilnehmer
- vollständige Ergebnistabelle aller Wettbewerbsbeiträge
- Berechnung der Sieger einer Mottoshow oder des Gesamtcontests
- Gleichstände in der eigenen ausgehenden Bewertung
- mehrere CSC-Ausgaben
- mehrere Benutzer, Anmeldung, Rollen oder Rechte
- Fristen und Erinnerungen
- CSV-Import
- native WPF-Oberfläche oder eigenständiges Desktopfenster
- umfangreiche Tastatursteuerung oder frei konfigurierbare Shortcuts
- Statistiken und Diagramme in der ersten Ausbaustufe

## 3. Fachliche Grundlagen und Abgrenzung der Quellen

Die vorhandenen CSC-Dokumente dienen nur dort als fachliche Grundlage, wo ihre Inhalte für das Tool relevant sind:

- **Mottoshows** liefert Nummer und Bezeichnung der zwölf vorgesehenen Runden. Die Anwendung bildet die Regeln der Mottos nicht ab und validiert sie nicht.
- **Punkteregeln** liefert die interne Zuordnung der Ränge 1 bis 15 zu 25, 20, 16, 13, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2 und 1 Punkt.
- **Workflow und Strategie** liefert die Rahmenannahmen, dass ungefähr 30 anonyme Beiträge je Mottoshow gehört und anschließend 15 Favoriten eindeutig gereiht werden. Die dort beschriebene Recherche- und Kandidatenbewertung bleibt außerhalb der Anwendung.
- **Ausschlussliste** wird nicht importiert und nicht automatisch geprüft. Ein eingetragener Kandidat gilt für das Tool als bereits außerhalb der Anwendung geprüfter und gültiger Kandidat.

## 4. Nutzer und Laufzeitumgebung

- genau ein Benutzer
- primär Windows
- lokale Ausführung auf demselben Rechner wie der Browser
- Vivaldi als primäres Browserziel
- keine mobile Nutzung als eigener Produktfall
- keine Synchronisation mit einem Server oder Cloudkonto
- keine Anmeldung

## 5. Begriffe

### Mottoshow

Eine der zwölf Runden dieser CSC-Ausgabe. Sie besitzt eine feste Nummer und eine editierbare Bezeichnung.

### Kandidat

Ein vom Benutzer bereits fachlich geprüfter Song, der für die eigene Einreichung zu einer bestimmten Mottoshow in Betracht kommt.

### Eigene Einreichung

Der eine Kandidat, der für eine Mottoshow tatsächlich beim CSC eingereicht wurde oder werden soll.

### Wettbewerbsbeitrag

Ein anonym eingereichter Song eines anderen Teilnehmers, der im Rahmen der eigenen Abstimmung angehört und gegebenenfalls gerankt wird.

### Beitragsblock

Der aus einem CSC-Beitrag kopierte Block der anonymen Wettbewerbsbeiträge. Im Forum werden die Beiträge als anklickbare Linktexte im Format `Interpret - Titel` dargestellt. Beim Kopieren kann die Zwischenablage neben Plaintext auch eine Rich-Text-/HTML-Repräsentation mit den tatsächlichen Linkzielen enthalten.

### Rangliste

Die vom Benutzer per Drag-and-drop gebildete eindeutige Reihenfolge von Wettbewerbsbeiträgen. Sie darf mehr als 15 Beiträge enthalten.

### Top 15

Die ersten 15 Plätze der Rangliste. Nur diese werden als eigene Bewertung beim CSC eingereicht.

### Abstimmungsabschluss

Der bewusste Abschluss einer Top 15. Dabei wird ein unveränderlicher Snapshot der eingereichten Reihenfolge gespeichert.

### Ergebniserfassung

Die Erfassung der Punkte, die andere Teilnehmer der eigenen Einreichung gegeben haben.

## 6. Informationsarchitektur

Die Hauptnavigation besteht aus:

1. **Übersicht**
2. **Teilnehmer**
3. **Daten und Sicherungen**

Jede Mottoshow besitzt drei Arbeitsbereiche:

1. **Kandidaten**
2. **Abstimmung**
3. **Ergebnis**

Ein eigener Bereich für Regeln, Recherche oder Ausschlussprüfung existiert nicht.

## 7. Mottoshows und Fortschritt

### SHOW-001 – Initiale Mottoshows

Die Anwendung legt beim ersten Start zwölf Mottoshows mit Nummer und initialer Bezeichnung an.

### SHOW-002 – Editierbare Bezeichnung

Die Bezeichnung einer Mottoshow kann geändert werden. Das ist insbesondere für die zunächst als `TBA` geführte neunte Show erforderlich.

### SHOW-003 – Keine konfigurierbaren Regeln

Eine Mottoshow besitzt keine Regelfelder, Nachweisfelder oder Mottoshow-spezifischen Zusatzattribute.

### SHOW-004 – Abgeleiteter Fortschritt

Die Übersicht zeigt den Fortschritt einer Mottoshow aus den vorhandenen Daten, ohne einen zusätzlichen manuell gepflegten Gesamtstatus zu verlangen.

Angezeigt werden mindestens:

- Anzahl der Kandidaten
- ausgewählte eigene Einreichung oder Hinweis, dass noch keine gewählt wurde
- Anzahl der Wettbewerbsbeiträge
- Anzahl eingeschätzter Beiträge
- Anzahl einsortierter Beiträge
- Status der abgeschlossenen Top 15
- Status der Teilnehmerzuordnung
- Status der Ergebniserfassung
- berechnete Gesamtpunktzahl
- Endplatzierung, sofern gepflegt

### SHOW-005 – Keine Fristen

Einreichungs- und Abstimmungsfristen werden nicht gepflegt.

## 8. Kandidatenverwaltung

### 8.1 Kandidatendaten

Ein Kandidat besitzt:

- Interpret, Pflichtfeld
- Titel, Pflichtfeld
- YouTube-Link, Pflichtfeld
- Kommentar, optional
- Status
- manuelle Position innerhalb der Mottoshow
- Erstellungs- und Änderungszeitpunkt

Weitere fachliche Zusatzdaten sind nicht vorgesehen.

### CAND-001 – Trennung nach Mottoshow

Die Kandidatenansicht einer Mottoshow enthält ausschließlich Kandidaten dieser Show.

Es gibt keine gemeinsame Tabelle, die Kandidaten mehrerer Shows vermischt.

### CAND-002 – Schnellerfassung

Ein Kandidat kann mit Interpret, Titel und YouTube-Link schnell hinzugefügt werden. Der Kommentar ist bereits bei der Erfassung verfügbar, aber nicht erforderlich und typischerweise zunächst leer.

### CAND-003 – Statusmodell

Ein Kandidat besitzt genau einen manuell pflegbaren Status aus:

- `OFFEN`
- `IM_RENNEN`
- `ENGERE_AUSWAHL`
- `FINALIST`
- `VERWORFEN`

`EINGEREICHT` ist kein manuell pflegbarer Status. Die Anwendung zeigt stattdessen einen abgeleiteten Einreichungsindikator, wenn der Kandidat als eigene Einreichung der Mottoshow ausgewählt wurde.

### CAND-004 – Standardstatus

Ein neu angelegter oder in eine andere Show kopierter Kandidat erhält den Status `OFFEN`.

### CAND-005 – Manuelle Reihenfolge

Kandidaten können innerhalb einer Mottoshow per Drag-and-drop frei sortiert werden.

Die manuelle Reihenfolge ist die gespeicherte Standardreihenfolge.

### CAND-006 – Temporäre Tabellensortierung

Die Ansicht kann temporär nach mindestens Interpret, Titel, Status und Erfassungszeitpunkt sortiert werden.

Eine temporäre Sortierung verändert die gespeicherte manuelle Reihenfolge nicht. Drag-and-drop ist nur in der Ansicht „Manuelle Reihenfolge“ aktiv.

### CAND-007 – Verworfene Kandidaten

Verworfene Kandidaten bleiben gespeichert und auffindbar. Sie werden standardmäßig in einem eingeklappten oder separat filterbaren Bereich dargestellt, damit sie die aktive Auswahl nicht dominieren.

Ein Archivstatus ist nicht vorgesehen.

### CAND-008 – Bearbeiten und Löschen

Alle Kandidatenfelder können nachträglich bearbeitet werden.

Kandidaten können gelöscht werden. Das Löschen erfordert eine Bestätigung oder bietet unmittelbar danach eine Rückgängig-Aktion.

Ein als eigene Einreichung ausgewählter Kandidat kann nicht unbemerkt gelöscht werden. Vor dem Löschen muss die Einreichung aufgehoben oder ersetzt werden.

### CAND-009 – Dubletten

Ein exakter oder sehr ähnlicher Kandidat innerhalb derselben Mottoshow darf gespeichert werden. Die Anwendung darf unverbindlich warnen, blockiert die Speicherung aber nicht.

Gleiche Kombinationen aus Interpret und Titel mit unterschiedlichen YouTube-Links sind ausdrücklich zulässig.

### CAND-010 – Kopieren in eine andere Mottoshow

Ein Kandidat kann über eine Aktion in eine oder mehrere andere Mottoshows kopiert werden.

Dabei werden Interpret, Titel, YouTube-Link und Kommentar übernommen. Der kopierte Kandidat:

- ist ein technisch unabhängiger Datensatz
- erhält den Status `OFFEN`
- wird am Ende der manuellen Reihenfolge der Zielshow eingefügt
- ist nicht automatisch als Einreichung ausgewählt

Spätere Änderungen werden nicht zwischen den Kopien synchronisiert.

### CAND-011 – Eigene Einreichung

Pro Mottoshow kann höchstens ein Kandidat als eigene Einreichung festgelegt werden.

Beim Ersetzen einer bereits ausgewählten Einreichung verlangt die Anwendung eine bewusste Bestätigung.

Die Einreichung zeigt mindestens Interpret, Titel und YouTube-Link deutlich im Kopfbereich der Show.

### CAND-012 – Kandidaten anhören

Ein Kandidat kann möglichst innerhalb der Anwendung über einen eingebetteten YouTube-Player angehört werden.

Zusätzlich steht immer eine Aktion zum Öffnen des Links in einem externen Browser-Tab zur Verfügung.

Ist eine Einbettung für ein Video nicht erlaubt oder technisch nicht möglich, bleibt der externe Link vollständig nutzbar.

## 9. Teilnehmerverwaltung

### 9.1 Teilnehmerdaten

Ein Teilnehmer besitzt:

- aktuellen Anzeigenamen, Pflichtfeld
- Land, Pflichtfeld
- Ländercode für die Flaggenanzeige
- null bis viele frühere Namen oder Aliasse
- Aktivstatus

Der Benutzer selbst wird nicht als Teilnehmerdatensatz angelegt.

### PART-001 – Land und Flagge

Das Land wird in Teilnehmerlisten und Zuordnungsansichten mit lesbarem Namen und Flagge dargestellt.

Als technische Grundlage soll vorzugsweise ein ISO-3166-1-Alpha-2-Code gespeichert werden. Die konkrete Darstellung kann als Flaggen-Icon oder Flaggen-Emoji erfolgen, muss aber konsistent und gut lesbar sein.

### PART-002 – Aliasse

Frühere Namen oder Aliasse können hinzugefügt, bearbeitet und entfernt werden.

Teilnehmersuche und Zuordnungsdialog berücksichtigen sowohl den aktuellen Namen als auch Aliasse.

### PART-003 – Aktivstatus

Aktive Teilnehmer werden standardmäßig für alle Mottoshows dieser einen CSC-Ausgabe berücksichtigt.

Eine gesonderte Teilnahmeverwaltung pro Mottoshow oder Ausgabe existiert nicht.

Inaktive Teilnehmer bleiben für bestehende historische Zuordnungen und Ergebnisse erhalten, erscheinen aber nicht mehr standardmäßig in neuen Auswahl- und Erfassungslisten.

### PART-004 – Keine Selbstzuordnung

Da der Benutzer selbst nicht als Teilnehmer geführt wird, kann ein anonymer Wettbewerbsbeitrag nicht versehentlich dem Benutzer zugeordnet werden.

## 10. Import und Verwaltung der Wettbewerbsbeiträge

### 10.1 Daten eines Wettbewerbsbeitrags

Ein Wettbewerbsbeitrag besitzt:

- Interpret, Pflichtfeld
- Titel, Pflichtfeld
- YouTube-Link, Pflichtfeld
- Kommentar oder Hörnotiz, optional
- Einschätzung, optional 1 bis 5
- Sicherheit, gemeinsam mit der Einschätzung optional 1 bis 5
- optionale Rangposition
- optionale Teilnehmerzuordnung, erst nach Abstimmungsabschluss
- Erstellungs- und Änderungszeitpunkt

### ENTRY-001 – Beitragsblock-Import aus der Zwischenablage

Die Wettbewerbsbeiträge werden primär durch Kopieren des formatierten Beitragsblocks aus dem CSC-Forum und anschließendes Einfügen per `Strg+V` in eine dafür vorgesehene Importfläche übernommen.

Die Importfläche ist kein gewöhnliches Plaintext-Feld. Sie verarbeitet das Browser-`paste`-Event und wertet die vom Browser bereitgestellten Zwischenablageformate aus.

Ein CSV-Import ist nicht vorgesehen.

### ENTRY-002 – Rich-Text-/HTML-Import als Primärweg

Wenn die Zwischenablage eine HTML-Repräsentation enthält, wird diese bevorzugt verarbeitet.

Die Anwendung extrahiert aus anklickbaren Links mindestens:

- den sichtbaren Linktext
- das `href`-Linkziel

Der erwartete CSC-Normalfall ist ein anklickbarer Link mit sichtbarem Text im Format:

```text
Interpret - Titel
```

und einem YouTube-Link als Ziel.

Beispiel:

```text
Imminence - Paralyzed -> https://www.youtube.com/watch?v=2Dqu1Gh45qU
The Killers - Read My Mind -> https://www.youtube.com/watch?v=5VWZU2SDFcY
Alice In Chains - Would? -> https://www.youtube.com/watch?v=mOJEcEkR1a8
```

Die Anwendung soll die Linkinformationen direkt aus dem vom Benutzer ausgelösten Paste-Vorgang lesen. Eine dauerhafte oder im Hintergrund arbeitende Zwischenablageberechtigung ist dafür nicht vorgesehen.

### ENTRY-003 – Unterstützte Fallback-Formate

Der Importer wertet Eingabeformate in folgender Priorität aus:

1. **HTML/Rich Text mit anklickbaren Links** – bevorzugter CSC-Normalfall; Linktext und URL werden direkt übernommen.
2. **Markdownartige Links**, beispielsweise `[Interpret - Titel](https://youtube.com/...)`.
3. **Plaintext mit expliziter URL**, sofern Linktext und URL zuverlässig zugeordnet werden können.
4. **Nur `Interpret - Titel` ohne URL** – wird als unvollständiger Datensatz in der Vorschau angezeigt und nicht stillschweigend als vollständiger Beitrag übernommen.

Die Fallbacks dienen der Robustheit gegenüber unterschiedlichen Zwischenablage-Repräsentationen durch Browser oder Zwischenprogramme. Sie ändern nicht den primären Bedienweg `CSC-Beitrag kopieren -> Strg+V im Tool`.

### ENTRY-004 – Interpret und Titel parsen

Der sichtbare Linktext wird in Interpret und Titel zerlegt.

Der Normalfall verwendet die Trennung `Interpret - Titel`. Da Interpret oder Titel selbst Bindestriche enthalten können, darf eine nicht eindeutig interpretierbare Zeile nicht stillschweigend falsch zerlegt werden.

Der Parser darf für den Normalfall eine definierte Heuristik verwenden. Zweifelhafte Fälle werden in der Importvorschau markiert und können dort manuell korrigiert werden.

### ENTRY-005 – Importvorschau

Der Import besteht aus:

1. Beitragsblock im CSC-Forum kopieren
2. Importfläche öffnen und `Strg+V` ausführen
3. verfügbare Zwischenablageformate auswerten
4. Links und sichtbare Beschriftungen extrahieren
5. Interpret und Titel parsen
6. erkannte Datensätze in einer Vorschau anzeigen
7. nicht oder nur teilweise erkannte Datensätze deutlich markieren
8. einzelne Werte vor dem Import korrigieren
9. Import bestätigen

Die Vorschau zeigt mindestens Interpret, Titel, Link und Erkennungsstatus. Bei problematischen Fällen soll außerdem die ursprüngliche Beschriftung beziehungsweise der relevante Rohinhalt nachvollziehbar bleiben.

### ENTRY-006 – YouTube-Linkvalidierung

YouTube-Links werden als erwarteter Normalfall erkannt und entsprechend gekennzeichnet.

Andere Linkziele dürfen nicht unbemerkt als gültige YouTube-Links behandelt werden. Sie werden in der Vorschau als ungewöhnlich oder unvollständig markiert und können manuell korrigiert werden.

Die Anwendung benötigt für den Import keine YouTube-API.

### ENTRY-007 – Wiederholter Import

Bei einem Import in eine bereits befüllte Mottoshow warnt die Anwendung vor möglichen Dubletten. Der Benutzer kann den Import abbrechen, problematische Einträge auslassen oder bewusst übernehmen.

Eine automatische Zusammenführung anhand von Interpret und Titel findet nicht statt.

### ENTRY-008 – Manuelle Pflege

Wettbewerbsbeiträge können auch einzeln hinzugefügt, bearbeitet und gelöscht werden.

### ENTRY-009 – Einschätzung und Sicherheit

Ein Wettbewerbsbeitrag ist entweder vollständig unbewertet oder besitzt gemeinsam eine fünfstufige **Einschätzung** und **Sicherheit**. Die Einschätzung bedeutet 1 `Raus`, 2 `Eher raus`, 3 `Wackelkandidat`, 4 `Klarer Punkte-Kandidat` und 5 `Favorit`. Die Sicherheit beschreibt von 1 `Erster Eindruck` bis 5 `sehr gut bekannt oder abschließend bewertet`, wie belastbar dieses Urteil ist.

Das erstmalige Setzen einer Einschätzung setzt die Sicherheit auf 1. Jede spätere Änderung der Einschätzung behält die Sicherheit. Der Benutzer kann beide Werte gemeinsam zurücksetzen. Eine vorhandene Einschätzung bedeutet, dass der Beitrag bearbeitet wurde.

Die Oberfläche bietet auf jeder Poolkarte fünf Sterne für die Einschätzung und fünf klar unterscheidbare Punkte für die Sicherheit. Jede Stufe ist per Tastatur erreichbar, mit ihrem Klartext beschriftet und besitzt eine Pressed-Semantik. Die Sicherheit wird erst nach einer Einschätzung bedienbar. Farbe ergänzt nur Füllstand und Symbolform.

Die Filter `Ohne Einschätzung` und `Unsicher` (Sicherheit 1–2) sowie die Sortierungen `Einschätzung` und `Sicherheit` ersetzen die früheren Statusfunktionen. Einschätzungssortierung zeigt bewertete Beiträge zuerst, dann Einschätzung und Sicherheit jeweils absteigend; Sicherheitssortierung zeigt unbewertete Beiträge zuerst, danach niedrigste Sicherheit. Alle Gleichstände verwenden die manuelle Poolposition.

Bewertungen verändern weder Pool- noch Rangposition und lösen niemals eine automatische Umordnung aus. Ein Ranglistenvorschlag wird nur durch eine bewusste Aktion angewendet; Abschlusswarnungen erscheinen ausschließlich im Abschlussdialog.

### ENTRY-010 – Hören

Die Höransicht bietet:

- eingebetteten YouTube-Player, sofern möglich
- externen YouTube-Link
- Interpret und Titel
- Kommentar/Hörnotiz
- kompakte Anzeige und Bearbeitung von Einschätzung und Sicherheit
- aktuelle Rangposition, sofern vorhanden
- Navigation zum vorherigen und nächsten Beitrag

Eine Tastatursteuerung über besondere Shortcuts ist nicht erforderlich.

## 11. Rangliste und eigene Bewertung

### BALLOT-001 – Zwei Arbeitsbereiche

Die Ranking-Oberfläche besteht aus:

- **Noch nicht eingeordnet**
- **Rangliste**

Beiträge können per Drag-and-drop zwischen beiden Bereichen und innerhalb der Rangliste verschoben werden.

### BALLOT-002 – Beliebig lange Rangliste

Die Rangliste darf zwischen 0 und allen vorhandenen Wettbewerbsbeiträgen enthalten.

Es ist zulässig, alle ungefähr 30 Beiträge vollständig zu sortieren. Erforderlich sind nur die ersten 15 Plätze.

### BALLOT-003 – Hervorgehobene Top 15

Die ersten 15 Positionen der Rangliste werden deutlich als abzugebende Wertung hervorgehoben.

Ab Position 16 beginnt ein visuell getrennter Bereich „Außerhalb der Top 15“.

### BALLOT-004 – Eindeutige Reihenfolge

Die eigene Bewertung enthält keine Gleichstände.

Jeder Beitrag kann höchstens einmal in der Rangliste vorkommen. Die Positionen sind eindeutig und lückenlos.

### BALLOT-005 – Komfortables Drag-and-drop

Das Drag-and-drop-Verhalten muss insbesondere bei ungefähr 30 Beiträgen komfortabel bleiben und mindestens bieten:

- klar erkennbare Drag-Handles
- sichtbare Einfügeposition
- automatische Verschiebung der übrigen Einträge
- Auto-Scroll an den Listenrändern
- große und eindeutige Drop-Zonen
- stabile Darstellung der Grenze zwischen Platz 15 und 16
- Möglichkeit, einen Beitrag wieder in „Noch nicht eingeordnet“ zurückzulegen

### BALLOT-005a – Bewusster Ranglistenvorschlag

In einer offenen Rangliste kann der Benutzer `Ranglistenvorschlag anwenden` wählen. Die Aktion ist ohne bewertete Beiträge deaktiviert und bei bereits vorhandener Rangliste ausdrücklich zu bestätigen. Der Vorschlag enthält nur bewertete Beiträge; unbewertete Beiträge bleiben ungeordnet im Pool. Seine Sortierung verwendet den ganzzahligen Wert `300 + (Einschätzung - 3) × Faktor`, mit den Faktoren 35, 55, 70, 85 und 100 für Sicherheit 1 bis 5. Damit schwächt eine niedrige Sicherheit zur neutralen Mitte ab, statt einen Bonus zu geben.

Bei Gleichstand erhält eine vorhandene Rangposition Vorrang, danach entscheidet die persistente Poolposition. Die Aktion sendet ausschließlich die vollständigen Listen an den bestehenden atomaren Reorder-Vertrag. Nach der bewussten Anwendung bleibt Drag-and-drop vollständig maßgeblich; spätere Bewertungsänderungen wenden keinen Vorschlag automatisch erneut an.

### BALLOT-006 – Interne Punktzuordnung

Die Anwendung ordnet den Rängen intern folgende Punkte zu:

| Rang | Punkte |
|---:|---:|
| 1 | 25 |
| 2 | 20 |
| 3 | 16 |
| 4 | 13 |
| 5 | 11 |
| 6 | 10 |
| 7 | 9 |
| 8 | 8 |
| 9 | 7 |
| 10 | 6 |
| 11 | 5 |
| 12 | 4 |
| 13 | 3 |
| 14 | 2 |
| 15 | 1 |

Diese Punkte werden für spätere Auswertungen gespeichert oder reproduzierbar berechnet. Sie müssen im Text der ausgehenden Bewertung nicht enthalten sein.

### BALLOT-007 – Abstimmung abschließen

Die Aktion `Abstimmung abschließen` ist nur möglich, wenn mindestens 15 Beiträge gerankt sind.

Beim Abschluss werden die ersten 15 Plätze als eindeutiger Snapshot gespeichert.

Noch nicht eingeordnete Beiträge oder weitere Rangpositionen ab Platz 16 verhindern den Abschluss nicht.

### BALLOT-008 – Abschlussprüfung

Vor dem Abschluss prüft die Anwendung mindestens:

- mindestens 15 gerankte Beiträge
- genau 15 Beiträge im auszugebenden Top-15-Snapshot
- eindeutige, lückenlose Reihenfolge
- vollständige Pflichtdaten der Top-15-Beiträge
- keine mehrfach verwendete Beitrags-ID

Gleichstände werden weder angeboten noch akzeptiert.

### BALLOT-008a – Abschlusswarnungen

Der Abschlussdialog weist kompakt auf unbewertete Beiträge, unsichere Top-15-Beiträge (Sicherheit 1–2), hoch eingeschätzte Beiträge außerhalb der Top 15 oder ungeordnet, niedrig eingeschätzte Top-15-Beiträge sowie eine rechnerisch knappe und unsichere Grenze zwischen Rang 15 und 16 hin. Die Grenze gilt bei einem Abstand der Sortierwerte von höchstens 15, wenn mindestens einer der beiden Beiträge Sicherheit 1 oder 2 hat.

Diese Hinweise sind nie zusätzliche Abschlussblockaden. Die bestehende Prüfung auf mindestens 15 lückenlose Ränge und den gültigen Snapshot bleibt allein maßgeblich.

### BALLOT-009 – Snapshot

Ein abgeschlossener Top-15-Stand wird als Snapshot mit Zeitpunkt gespeichert.

Der Snapshot enthält mindestens Rang, Interpret, Titel und Referenz auf den ursprünglichen Wettbewerbsbeitrag. Interpret und Titel werden zusätzlich als Text im Snapshot gehalten, damit spätere Korrekturen am Beitrag nicht unbemerkt den tatsächlich abgeschlossenen Stand verändern.

### BALLOT-010 – Wieder öffnen

Eine abgeschlossene Abstimmung kann nach bewusster Bestätigung wieder geöffnet werden.

Ein vorhandener Snapshot bleibt historisch erhalten. Ein erneuter Abschluss erzeugt einen neuen aktuellen Snapshot.

### BALLOT-011 – Ausgabe

Der aktuelle abgeschlossene Top-15-Snapshot kann:

- als Textvorschau angezeigt
- in die Zwischenablage kopiert
- als Textdatei exportiert

werden.

Die Ausgabe enthält eine eindeutige Rangliste von 1 bis 15. Punktwerte sind standardmäßig nicht Bestandteil der Ausgabe.

Das exakte Textformat wird anhand eines später bereitgestellten realen Beispiels festgelegt.

## 12. Zuordnung der Beiträge zu Teilnehmern

### MAP-001 – Zeitpunkt

Die Teilnehmerzuordnung wird erst nach Abschluss der eigenen Abstimmung freigeschaltet.

Vorher sind Teilnehmerfelder ausgeblendet oder nicht bearbeitbar.

### MAP-002 – Zuordnung

Ein Wettbewerbsbeitrag kann genau einem Teilnehmer zugeordnet werden.

Ein aktiver Teilnehmer kann innerhalb derselben Mottoshow höchstens einem Wettbewerbsbeitrag zugeordnet werden.

Die Anwendung verhindert oder bestätigt bewusst eine Zuordnung, die diese Eindeutigkeit verletzen würde.

### MAP-003 – Unvollständige Zuordnung

Nicht alle Beiträge müssen sofort zugeordnet werden. Die Oberfläche zeigt fehlende Zuordnungen deutlich an und bietet einen Filter `ohne Teilnehmer`.

Eine unvollständige Zuordnung blockiert die separate Erfassung der für die eigene Einreichung erhaltenen Punkte nicht.

## 13. Ergebnis der eigenen Einreichung

### 13.1 Ergebniseintrag je Teilnehmer

Für jeden aktiven Teilnehmer wird pro Mottoshow ein Ergebniseintrag geführt.

Der Eintrag besitzt einen Status aus:

- `UNBEKANNT`
- `NICHT_ABGESTIMMT`
- `ABGESTIMMT`

Bei `ABGESTIMMT` wird zusätzlich die Punktzahl erfasst.

### RESULT-001 – Bedeutung der Status

- `UNBEKANNT`: Der Ergebniseintrag wurde noch nicht abschließend geprüft oder erfasst.
- `NICHT_ABGESTIMMT`: Der Teilnehmer hat keine gültige Abstimmung abgegeben.
- `ABGESTIMMT`: Der Teilnehmer hat abgestimmt; die eigene Einreichung erhielt dabei null oder mehr Punkte.

`NICHT_ABGESTIMMT` und `UNBEKANNT` sind fachlich nicht dasselbe und werden getrennt dargestellt.

### RESULT-002 – Zulässige Punktwerte

Bei `ABGESTIMMT` ist genau einer der folgenden Punktwerte zulässig:

`0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 16, 20, 25`

Ein leerer Punktwert ist bei `ABGESTIMMT` nicht zulässig.

Bei `UNBEKANNT` und `NICHT_ABGESTIMMT` ist kein Punktwert gespeichert.

### RESULT-003 – Berechnete Gesamtpunktzahl

Die berechnete Gesamtpunktzahl ist die Summe aller Punktwerte aus Einträgen mit Status `ABGESTIMMT`.

`UNBEKANNT` und `NICHT_ABGESTIMMT` tragen null zur Summe bei, werden in der Oberfläche aber weiterhin unterschiedlich gekennzeichnet.

### RESULT-004 – Offizielle Gesamtpunktzahl

Zusätzlich kann optional die offiziell veröffentlichte Gesamtpunktzahl eingetragen werden.

Weicht sie von der berechneten Gesamtpunktzahl ab, zeigt die Anwendung eine deutliche Warnung und beide Werte nebeneinander an.

Die Abweichung wird nicht automatisch korrigiert.

### RESULT-005 – Endplatzierung

Pro Mottoshow kann die offizielle Endplatzierung der eigenen Einreichung als positive ganze Zahl gespeichert werden.

Zusätzlich kann die Platzierung als `geteilt` gekennzeichnet werden.

### RESULT-006 – Ergebniserfassung abschließen

Die Aktion `Ergebniserfassung abschließen` ist möglich, wenn:

- für keinen aktiven Teilnehmer mehr der Status `UNBEKANNT` vorliegt
- eine Endplatzierung gepflegt ist

Die offizielle Gesamtpunktzahl bleibt optional.

### RESULT-007 – Wieder öffnen

Eine abgeschlossene Ergebniserfassung kann nach bewusster Bestätigung wieder geöffnet und korrigiert werden.

### RESULT-008 – Eigene Einreichung als Bezug

Die Ergebnisansicht zeigt die ausgewählte eigene Einreichung prominent an.

Falls noch keine Einreichung gewählt ist, kann die Ergebniserfassung vorbereitet, aber nicht abgeschlossen werden.

## 14. Suche und Filter

### SEARCH-001 – Kandidaten

Die Kandidatenansicht unterstützt mindestens:

- Suche nach Interpret oder Titel
- Filter nach Status
- Ein-/Ausblenden verworfener Kandidaten

### SEARCH-002 – Wettbewerbsbeiträge

Die Abstimmungsansicht unterstützt mindestens:

- Suche nach Interpret oder Titel
- Filter `ohne Einschätzung`
- Filter `unsicher` für Sicherheit 1 bis 2
- Filter `noch nicht eingeordnet`
- Filter `ohne Teilnehmer`, sobald Zuordnungen freigeschaltet sind

### SEARCH-003 – Globale Suche

Eine einfache globale Suche über Interpret und Titel aller Kandidaten und Wettbewerbsbeiträge ist vorgesehen.

Kommentare müssen in der ersten Ausbaustufe nicht global durchsucht werden.

## 15. Datensicherung und Export

### DATA-001 – SQLite

Die persistenten Anwendungsdaten werden in einer lokalen SQLite-Datenbank gespeichert.

### DATA-002 – Automatische Sicherungen

Die Anwendung erzeugt mindestens:

- beim erfolgreichen Start eine kontrollierte Sicherung
- vor einer Datenbankmigration eine Sicherung
- beim manuellen Auslösen eine Sicherung

Automatische Sicherungen werden versioniert und nach einer definierten Aufbewahrungsregel bereinigt. Als initialer Standard gelten die 30 neuesten automatischen Sicherungen; manuell erstellte Sicherungen werden nicht automatisch gelöscht.

### DATA-003 – Speicherort

Datenbank, Sicherungen, Exporte und Logs liegen unter einem klar benannten Anwendungsverzeichnis im lokalen Benutzerprofil, vorzugsweise unter `%LOCALAPPDATA%/CSC-X-Tool/`.

Sie werden nicht im Installationsverzeichnis gespeichert.

### DATA-004 – Vollständiger Export

Die Anwendung kann alle fachlichen Daten in ein versioniertes JSON-Format exportieren und aus einem kompatiblen vollständigen Export wiederherstellen.

Der Export verwendet Version 3 mit Einschätzung und Sicherheit. Die Importstrecke unterstützt weiterhin Version 1 und Version 2 und bildet deren frühere Statusdaten konservativ auf das Bewertungspaar ab.

Vor einer Wiederherstellung wird automatisch eine Sicherung des aktuellen Stands angelegt.

### DATA-005 – Tabellenexporte

Sinnvolle CSV-Exporte sind mindestens vorgesehen für:

- Kandidaten
- Wettbewerbsbeiträge mit Einschätzung, Sicherheit und Rangpositionen
- Teilnehmer
- erhaltene Punkte und Endergebnisse

Ein CSV-Import wird daraus ausdrücklich nicht abgeleitet.

### DATA-006 – Top-15-Export

Die abgeschlossene Top 15 besitzt zusätzlich den fachlichen Text- und Zwischenablageexport aus `BALLOT-011`.

## 16. Launcher und Anwendungsbetrieb

### RUN-001 – Start ohne Kommandozeile

Die Anwendung kann unter Windows über einen normalen Launcher oder Startmenüeintrag geöffnet werden. Ein manueller PowerShell- oder Konsolenbefehl ist nicht erforderlich.

### RUN-002 – Browseröffnung

Der Launcher startet den lokalen Server und öffnet die Anwendung automatisch im vom Betriebssystem konfigurierten Standardbrowser.

Ist Vivaldi der Standardbrowser, wird Vivaldi verwendet. Eine Vivaldi-spezifische Sonderintegration ist nicht erforderlich.

### RUN-003 – Einfache Instanz

Läuft die Anwendung bereits, startet ein erneuter Launcher-Aufruf keine zweite Serverinstanz. Stattdessen wird die bestehende Anwendung im Browser geöffnet.

### RUN-004 – Lokale Bindung

Der Server bindet ausschließlich an die Loopback-Schnittstelle, beispielsweise `127.0.0.1`, und ist nicht aus dem lokalen Netzwerk erreichbar.

### RUN-005 – Beenden

Die Anwendung besitzt eine gut auffindbare Aktion `Anwendung beenden`, die den lokalen Server kontrolliert herunterfährt.

Das bloße Schließen des Browser-Tabs beendet den Server nicht zwangsläufig.

### RUN-006 – Keine Anmeldung

Es gibt keine Loginmaske, Benutzerkonten oder Benutzerverwaltung.

## 17. Bedien- und Designanforderungen

### UI-001 – Dunkles Erscheinungsbild

Die Anwendung besitzt eine konsistente dunkle Oberfläche. Ein zusätzlicher heller Modus ist nicht erforderlich.

### UI-002 – Modern und desktoporientiert

Die Oberfläche ist für die Nutzung an einem Desktopmonitor optimiert und soll modern, ruhig und hochwertig wirken.

Sie vermeidet unnötig dichte Verwaltungsformulare und verwendet für Detailbearbeitung geeignete Dialoge, Drawer oder aufklappbare Bereiche.

### UI-003 – Übersichtlichkeit

Wichtige Zustände werden nicht allein durch Farbe vermittelt. Beschriftung, Icons und Kontrast unterstützen die Lesbarkeit.

### UI-004 – Inline-Aktionen

Häufige Aktionen wie Einschätzung, Sicherheit, Kommentarbearbeitung und Öffnen des YouTube-Links sind ohne unnötige Navigationswechsel erreichbar.

### UI-005 – Rückmeldungen

Speichern, Kopieren, Importieren, Abschließen, Wiederöffnen und Löschen erzeugen eindeutige Rückmeldungen.

Destruktive oder statusverändernde Aktionen besitzen sinnvolle Bestätigungen.

### UI-006 – Drag-and-drop als Kerninteraktion

Drag-and-drop wird als zentrale Interaktion behandelt und nicht lediglich als dekorative Ergänzung. Die Bedienung muss auch bei längeren Listen nachvollziehbar, stabil und flüssig sein.

Eine alternative Bedienung über Hoch-/Runter-Aktionen darf ergänzend vorhanden sein, ist aber nicht zwingend Bestandteil der ersten Version.

### UI-007 – YouTube-Fallback

Fehler beim Einbetten eines Videos beeinträchtigen weder den Datensatz noch andere Teile der Oberfläche. Der externe Link bleibt sichtbar.

### UI-008 – Paste-Fläche für den Beitragsimport

Der Beitragsimport besitzt eine deutlich erkennbare Einfügefläche mit einer kurzen Anweisung wie `CSC-Beitragsblock kopieren und hier Strg+V drücken`.

Die Oberfläche macht nach dem Einfügen sichtbar, wie viele Beiträge und Links erkannt wurden. Der Benutzer muss nicht zuerst ein Dokument, eine Datei oder einen externen Konverter erzeugen.

## 18. Nichtfunktionale Anforderungen

### NFR-001 – Lokale Datenhoheit

Fachliche Anwendungsdaten verlassen den Rechner nicht durch eine eigene Server- oder Telemetriefunktion.

Beim eingebetteten oder externen Abspielen eines YouTube-Videos gelten naturgemäß die Verbindungen des Browsers zu YouTube.

### NFR-002 – Keine Telemetrie

Die Anwendung sendet keine Nutzungsdaten, Diagnosedaten oder Abstimmungsdaten an den Entwickler oder Dritte.

### NFR-003 – Browserkompatibilität

Vivaldi ist das primäre Browserziel. Die Anwendung soll darüber hinaus in gängigen Chromium-basierten Desktopbrowsern funktionieren.

Der Zwischenablage-Import muss insbesondere mit dem vom Browser beim normalen `paste`-Event bereitgestellten `text/html` und `text/plain` funktionieren. Die Implementierung darf für den normalen Import keine dauerhafte Browserberechtigung zum Lesen der Zwischenablage voraussetzen.

### NFR-004 – Datenintegrität

Datenbankmigrationen sind versioniert. Fremdschlüssel, eindeutige Zuordnungen und fachliche Wertebereiche werden soweit sinnvoll in Datenbank und Anwendung abgesichert.

### NFR-005 – Fehlerbehandlung

Fehler beim Import, Speichern, Backup, Export oder Einbetten werden verständlich angezeigt. Ein Fehler eines einzelnen Videos darf nicht die gesamte Hör- oder Rankingansicht unbrauchbar machen.

### NFR-006 – Datenmenge

Die Anwendung muss mit mindestens folgenden Größen ohne merkliche Bedienprobleme umgehen:

- 12 Mottoshows
- 100 Kandidaten je Mottoshow
- 100 Wettbewerbsbeiträge je Mottoshow
- 100 Teilnehmer

Die real erwarteten Datenmengen liegen deutlich darunter.

### NFR-007 – Wiederholbarkeit

Importe, Abschlüsse, Wiederöffnungen und Exporte führen bei gleicher Eingabe zu nachvollziehbaren und reproduzierbaren Ergebnissen.

## 19. Fachliche Validierungen im Überblick

- Interpret, Titel und YouTube-Link sind bei Kandidaten und Wettbewerbsbeiträgen Pflichtfelder.
- Eine Mottoshow besitzt höchstens eine eigene Einreichung.
- Ein Kandidat gehört genau einer Mottoshow; Kopieren erzeugt einen unabhängigen Kandidaten.
- Rangpositionen sind eindeutig und lückenlos.
- Die eigene Top 15 enthält exakt 15 unterschiedliche Beitragsdatensätze in eindeutiger Reihenfolge.
- Ein Teilnehmer kann innerhalb einer Mottoshow höchstens einem Wettbewerbsbeitrag zugeordnet sein.
- Teilnehmerzuordnungen sind erst nach Abstimmungsabschluss bearbeitbar.
- Bei Ergebnisstatus `ABGESTIMMT` ist ein zulässiger Punktwert erforderlich.
- Bei `UNBEKANNT` oder `NICHT_ABGESTIMMT` ist kein Punktwert zulässig.
- Der Ergebnisabschluss ist mit unbekannten Teilnehmerwertungen nicht möglich.
- Eine geteilte Endplatzierung besitzt weiterhin eine numerische Platzangabe.
- Ein aus der Zwischenablage importierter Wettbewerbsbeitrag gilt erst dann als vollständig, wenn Interpret, Titel und YouTube-Link erkannt oder in der Vorschau manuell ergänzt wurden.

## 20. Durchgängiges Akzeptanzszenario

Eine erste fachlich vollständige Version gilt als benutzbar, wenn folgender Ablauf ohne externe Datenbank- oder Kommandozeilenarbeit möglich ist:

1. Der Benutzer startet das Tool über einen Windows-Launcher.
2. Vivaldi beziehungsweise der Standardbrowser öffnet die Übersicht mit zwölf Mottoshows.
3. In Show 1 werden mehrere Kandidaten mit Interpret, Titel und YouTube-Link erfasst.
4. Kandidaten werden angehört, kommentiert, per Drag-and-drop sortiert und mit Status versehen.
5. Ein Kandidat wird in Show 3 kopiert und dort unabhängig bearbeitet.
6. Ein Kandidat wird als Einreichung von Show 1 festgelegt.
7. Teilnehmer mit Namen, Land, Flagge und Alias werden gepflegt.
8. Ein CSC-Beitragsblock mit ungefähr 30 anklickbaren `Interpret - Titel`-Links wird im Forum kopiert und per `Strg+V` in die Importfläche eingefügt.
9. Die Anwendung übernimmt die Linktexte und YouTube-Ziele aus der Rich-Text-/HTML-Zwischenablage, zeigt eine Vorschau und markiert problematische Datensätze.
10. Erkannte oder korrigierte Beiträge werden importiert.
11. Beiträge werden angehört, eingeschätzt und teilweise oder vollständig in eine Rangliste gezogen.
12. Die ersten 15 Plätze werden eindeutig gereiht und die Abstimmung abgeschlossen.
13. Die Top 15 wird ohne Punktangaben in einem geeigneten Textformat in die Zwischenablage kopiert.
14. Nach dem Abschluss werden Beiträge den Teilnehmern zugeordnet.
15. Für jeden aktiven Teilnehmer wird `unbekannt`, `nicht abgestimmt` oder `abgestimmt` mit zulässiger Punktzahl erfasst.
16. Die Anwendung berechnet die Gesamtpunktzahl.
17. Eine offizielle Gesamtpunktzahl kann zum Abgleich eingetragen werden.
18. Endplatzierung und gegebenenfalls `geteilt` werden gespeichert.
19. Die Ergebniserfassung wird abgeschlossen.
20. Eine Sicherung und ein vollständiger JSON-Export werden erstellt.
21. Nach einem Neustart sind sämtliche Daten unverändert vorhanden.
22. Die Anwendung kann über die Oberfläche kontrolliert beendet werden.

## 21. Bewusst vertagte Eingaben

Für die Spezifikation bestehen keine blockierenden offenen Fachfragen.

Folgende konkrete Eingaben beziehungsweise Testdaten werden erst benötigt, bevor die jeweils betroffene Funktion finalisiert wird:

1. **Ein vollständiger realer Beitragsblock einer Mottoshow als Testfall**  
   Das grundsätzliche Importformat ist geklärt: anklickbare Links mit sichtbarem `Interpret - Titel` und in der Zwischenablage erhaltenem Linkziel. Ein vollständiger realer Block wird noch benötigt, um Sonderfälle, Leerzeilen und ungewöhnliche Titel zuverlässig in Parser-Tests abzudecken.

2. **Reales oder gewünschtes Ausgabeformat einer abgegebenen Top 15**  
   Grundlage für die endgültige Textvorlage und Zwischenablageausgabe.

3. **Gewünschte Statistiken und Diagramme**  
   Werden nach Vorliegen erster realer Daten separat priorisiert und spezifiziert.

## 22. Mögliche spätere Erweiterungen

Nicht Bestandteil der ersten Version, aber durch das Datenmodell sinnvoll vorbereitbar:

- Punkteentwicklung über die zwölf Shows
- Verteilung der erhaltenen Punktwerte
- Nullwertungsquote
- Top-Unterstützer je Teilnehmer
- Heatmap Teilnehmer × Mottoshow
- Vergleich eigener vergebener und erhaltener Punkte
- Vergleich der Showergebnisse untereinander
- zusätzliche Exporte oder grafische Berichte

Diese Erweiterungen dürfen den initialen Workflow nicht verkomplizieren.

## Anhang A – Initiale Mottoshows

1. Super Men
2. Original Sin – Reverse Cover
3. ESC in the CSC
4. Say My Name
5. It takes two
6. No comprende!
7. Twist & Shout
8. The Today Playlist
9. TBA
10. Country Roads
11. Live will always be life
12. Wonder Women

## Anhang B – Fachliche Kernentscheidung

Ein Datensatz im Tool bedeutet nicht „dieser Song wurde vom Tool auf Zulässigkeit geprüft“.

Er bedeutet:

> Der Benutzer hat den Kandidaten außerhalb des Tools geprüft und möchte ihn innerhalb dieser Mottoshow organisatorisch weiterverfolgen.

Diese Grenze ist bewusst und verbindlich. Das CSC X Tool ist ein Arbeits- und Abstimmungscockpit, keine Recherche- oder Regelprüfmaschine.
