# Fachliche Quellen und ihre Verwendung

Die Projektquellen zum CyBoard Song Contest enthalten mehr Informationen, als das CSC X Tool fachlich abbilden soll. Dieses Verzeichnis dokumentiert deshalb ausdrücklich, welche Inhalte in die Anwendung einfließen und welche außerhalb bleiben.

Die Originaldokumente werden zunächst nicht in dieses öffentliche Repository kopiert.

## Mottoshows

### Wird verwendet für

- die initialen Nummern 1 bis 12
- die initialen Bezeichnungen der Mottoshows
- die editierbare Bezeichnung der zunächst offenen neunten Show

### Wird nicht verwendet für

- automatische Prüfung der Motto-Eignung
- Mottoshow-spezifische Formularfelder
- Veröffentlichungsjahr, Sprache, ESC-Land, Veranstaltung oder andere Nachweise
- Warn- oder Freigabestatus

Ein Kandidat gilt beim Eintragen als bereits außerhalb des Tools geprüft.

## Punkteregeln

### Wird verwendet für

Die interne Punktzuordnung der eindeutigen Top 15:

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

Diese Zuordnung unterstützt die eigene Top 15, abgeleitete Stimmzettelpunkte und gemäß [contest-statistics.md](../contest-statistics.md) dieselbe zweite Punktevergabe aus der Showplatzierung für die Contest-Gesamtwertung.

Für die am 07.10.2026 freigegebene Erweiterung wird zusätzlich die Gleichstandsregel aus den hinterlegten Punkteregeln (am 07.10.2026 geprüft) verwendet: Zwei gemeinsame erste Plätze erhalten je 25 Punkte; danach folgt Platz 3 mit 16 Punkten. Daraus folgen geteilte Wettbewerbsränge mit übersprungenen Folgeplätzen und volle Rangpunkte für alle Mitglieder einer punktberechtigten Platzgruppe. Die Nutzerfreigabe übernimmt geteilte Ränge auch für die Contest-Gesamtsumme.

### Wird nicht verwendet für

Zusätzliche, nicht belegte Tiebreaks oder manuell importierte offizielle Endtabellen. Persönliche ausgehende Bewertungen und veröffentlichte Einzelstimmzettel bleiben eindeutig und enthalten keine Gleichstände. Die berechneten Show-/Contestergebnisse aus #14 ändern diesen Stimmzettelvertrag nicht.

## Workflow und Strategie

### Wird verwendet für

- ungefähr 30 anonyme Beiträge je Mottoshow als erwartete Größenordnung
- eine eindeutige persönliche Top 15
- die nachträgliche Teilnehmerzuordnung nach Abschluss der eigenen Abstimmung
- Teilnehmerländer als Teil des ESC-inspirierten Contest-Rahmens

### Wird nicht verwendet für

- Kandidatenrecherche
- Geschmacks- oder Repräsentationsbewertung
- strategische Gewinnchancen
- Profile wie Familiar Hit, Discovery Pick, Balanced Pick oder Wildcard
- Genre- und Bekanntheitsbewertung
- Quellenrecherche
- Finalistenprüfung
- Philippinen-Bonuskandidaten
- einen eigenen Teilnehmer- oder Länderstammdatensatz für den Benutzer

Diese Arbeit geschieht vor dem Eintragen und bleibt bewusst außerhalb des Tools.

## Ausschlussliste

Die Ausschlussliste wird nicht in die Anwendung übernommen.

Insbesondere gibt es:

- keinen Import der Liste
- keinen exakten oder unscharfen Abgleich
- keine automatische Sperre
- keinen Klärungsstatus für Varianten, Cover oder Liveaufnahmen

Die Zulässigkeit ist Verantwortung des vorgelagerten Rechercheprozesses.

## Abgeleitete Seed-Daten

Die Anwendung darf folgende kleine, stabile Datenmengen direkt als Migration oder Anwendungskonfiguration enthalten:

- zwölf Nummern und Mottoshow-Bezeichnungen
- Rang-zu-Punkte-Zuordnung für Plätze 1 bis 15
- lokal gebündelte Ländercodes, Ländernamen und Flaggenzuordnungen

Die vollständigen Regel- und Ausschlussdokumente sind keine Laufzeitabhängigkeit der Anwendung.

