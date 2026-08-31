# Erweiterungsspezifikation – Historische CSC-Daten, veröffentlichte Einzelwertungen und Analyseexport

**Version:** 1.0  
**Stand:** 30.08.2026  
**Status:** verbindliche fachliche Grundlage für die nachgelagerte Erweiterungsroadmap

## 1. Einordnung und Geltungsbereich

Dieses Dokument ergänzt die bestehende Produktspezifikation des CSC X Tool um mehrere CSC-Ausgaben, vollständige historische Einreichungen, veröffentlichte Einzelwertungen, Analyseexporte und ein späteres Tippspiel.

Für diesen Erweiterungsbereich ersetzt es insbesondere die bisherigen Annahmen:

- genau eine CSC-Ausgabe,
- Land direkt am dauerhaften Teilnehmerstammsatz,
- Erfassung ausschließlich der Punkte für die eigene Einreichung,
- keine vollständigen Bewertungen anderer Teilnehmer.

Die bestehenden Regeln für Kandidatenverwaltung, eigene Abstimmung, lokale Einzelbenutzernutzung, Sicherungen und Windows-Betrieb bleiben bestehen, soweit dieses Dokument sie nicht ausdrücklich erweitert.

Die CSC-Ausschlussliste bleibt vollständig außerhalb dieses Datenarchivs. Historische Einreichungen dürfen weder automatisch noch manuell als Ersatzquelle für die Ausschlussprüfung verwendet werden.

## 2. Zweck

Die Erweiterung soll zwei belastbare historische Datenarten verwalten:

1. die vollständige Zuordnung aller Wettbewerbsbeiträge einer Mottoshow zu ihren Einreichenden,
2. die veröffentlichte persönliche Top 15 jedes abstimmenden Teilnehmers.

Daraus sollen ohne zusätzliche offizielle Ergebnisdaten ableitbar sein:

- welcher Teilnehmer welchen Song eingereicht hat,
- welche 15 Songs ein Teilnehmer in welcher Reihenfolge bevorzugt hat,
- welche wählbaren Songs außerhalb seiner Top 15 lagen,
- welcher Song für ihn die eigene und deshalb nicht wählbare Einreichung war,
- welche fest zugeordneten Punkte sich aus einem Rang ergeben,
- welche Punkte die eigene Einreichung von einem bestimmten Teilnehmer erhalten hat.

Die Daten sollen in einem für externe KI-Analysen gut lesbaren Format exportiert werden können. Die KI-Analyse selbst bleibt außerhalb des Tools.

Als spätere Nebenfunktion soll die Einreichungshistorie beim Tippspiel helfen, in dem anonyme aktuelle Beiträge den vermuteten Einreichenden zugeordnet werden.

## 3. Produktziele

### 3.1 Vollständige historische Einreichungen

Für jede erfasste Mottoshow ist die vollständige Songliste einschließlich Teilnehmerzuordnung bekannt. Das gilt ausdrücklich auch für Beiträge, die in keiner einzigen veröffentlichten Top 15 vorkommen.

### 3.2 Vollständige veröffentlichte Einzelwertungen

Eine abgegebene Bewertung wird als eindeutige Rangfolge von Platz 1 bis 15 gespeichert. Gleichstände innerhalb eines einzelnen Stimmzettels sind nicht zulässig.

### 3.3 Saubere Ableitungen statt redundanter Speicherung

Die Datenbank speichert Rangpositionen. Punktwerte, Nullpunkte und weitere Bewertungszustände werden daraus und aus der vollständigen Songliste abgeleitet.

### 3.4 Mehrere CSC-Ausgaben

Teilnehmeridentitäten und Aliasse bleiben dauerhaft erhalten. Länderzuordnungen und Teilnehmerfelder werden pro CSC-Ausgabe verwaltet.

### 3.5 Externe Analysefähigkeit

Ein versioniertes Exportpaket stellt historische Einreichungen, Einzelwertungen und auf Wunsch aktuelle Kandidaten in einer maschinen- und menschenlesbaren Form bereit.

### 3.6 Tippspiel-Unterstützung

Die historische Einreichungsliste kann später in einer eigenen Arbeitsfläche als Recherchehilfe für die Zuordnung aktueller anonymer Beiträge verwendet werden.

## 4. Ausdrücklich nicht Bestandteil

Nicht Bestandteil dieser Erweiterung sind:

- Berechnung oder Speicherung offizieller Gesamtpunktzahlen,
- Berechnung oder Speicherung offizieller Platzierungen,
- Verwaltung von Siegen oder geteilten Gesamtplatzierungen,
- Berechnung eines Mottoshow-Siegers oder Gesamtsiegers,
- Erfindung einer Reihenfolge für Songs außerhalb der veröffentlichten Top 15,
- Speicherung einer vermeintlich genauen Rangposition ab Platz 16,
- Speicherung einer fachlich relevanten „genauen Aufnahme oder Version“,
- automatische Genre-, Stimmungs- oder Geschmacksanalyse,
- eine integrierte KI-Prognose,
- automatische Google-Drive-Synchronisation,
- Verwendung historischer Einreichungen für die CSC-Ausschlussprüfung,
- Sprachprüfung des jeweils verwendeten Worts für „Punkte“,
- Abgleich des Punkteworts mit dem vom Teilnehmer vertretenen Land,
- Parsing oder fachliche Interpretation offizieller Gesamtwertungstabellen.

## 5. Verbindliche Quellenannahmen

### 5.1 Teilnehmerfeld

Vor dem Import einer historischen Mottoshow wird das Teilnehmerfeld der betreffenden CSC-Ausgabe vollständig gepflegt.

Teilnehmer werden als dauerhafte Identitäten geführt. Bereits vorhandene Aliasnamen dienen auch zur Auflösung historischer Schreibweisen.

### 5.2 Länderzuordnung

Ein Teilnehmer kann in verschiedenen CSC-Ausgaben unterschiedliche Länder vertreten. Das Land gehört deshalb zur Teilnahme an einer konkreten Ausgabe und nicht dauerhaft zur Teilnehmeridentität.

### 5.3 Vollständige Songliste

Für jede historische Abstimmungsrunde kann eine vollständige Songliste einschließlich Zuordnung jedes Songs zu einem Teilnehmer bereitgestellt werden.

Die vollständige Songliste wird vor den veröffentlichten Einzelwertungen erfasst. Ein Stimmzettelimport erzeugt niemals nebenbei einen bislang unbekannten Wettbewerbsbeitrag.

### 5.4 Einzelwertungen

Die Quelle enthält die veröffentlichten Einzelwertungen. Es ist nicht erforderlich, aus einer offiziellen Gesamtpunktzahl auf einzelne Bewertungen zurückzurechnen.

Eine veröffentlichte Bewertung besteht aus genau 15 Songzeilen. Die Zeilen stehen aufsteigend nach vergebenen Punkten:

- erste erkannte Songzeile: Rang 15,
- zweite erkannte Songzeile: Rang 14,
- …
- letzte erkannte Songzeile: Rang 1.

Die dargestellten Punktzahlen und das lokalisierte Punktewort sind für das Datenmodell nicht maßgeblich.

## 6. Begriffe

### Teilnehmeridentität

Der dauerhaft wiedererkennbare Forenaccount beziehungsweise die fachlich gleiche Person. Sie besitzt einen aktuellen Anzeigenamen und beliebig viele Aliasnamen.

### CSC-Ausgabe

Eine konkrete Ausgabe des CyBoard Song Contest, beispielsweise `CSC IX` oder `CSC X`.

### Contest-Teilnahme

Die Teilnahme einer Teilnehmeridentität an einer bestimmten CSC-Ausgabe einschließlich des in dieser Ausgabe vertretenen Landes.

### Mottoshow

Eine nummerierte Runde innerhalb einer CSC-Ausgabe. Historische Ausgaben müssen nicht exakt zwölf Shows besitzen.

### Wettbewerbsbeitrag

Ein Song, der in einer konkreten Mottoshow von genau einem Teilnehmer eingereicht wurde.

### Veröffentlichter Stimmzettel

Die veröffentlichte persönliche Top 15 eines Teilnehmers für eine konkrete Mottoshow.

### Stimmzettelposition

Die Zuordnung genau eines Wettbewerbsbeitrags zu einem Rang von 1 bis 15 innerhalb eines veröffentlichten Stimmzettels.

### Außerhalb der Top 15

Ein Song, der für den betreffenden Teilnehmer wählbar war, aber nicht in dessen veröffentlichter Top 15 vorkommt. Seine genaue Position ist unbekannt.

### Eigene Einreichung

Der Wettbewerbsbeitrag des abstimmenden Teilnehmers. Dieser Beitrag war für ihn nicht wählbar und darf nicht als negative Präferenz interpretiert werden.

## 7. Fachliches Zielmodell

Das folgende Modell ist fachlich verbindlich. Konkrete Tabellen- oder API-Namen dürfen technisch sinnvoll abweichen, solange die Semantik erhalten bleibt.

### 7.1 Teilnehmer

Ein Teilnehmer besitzt dauerhaft:

- stabile interne ID,
- aktuellen Anzeigenamen,
- Aliasnamen,
- Erstellungs- und Änderungszeitpunkt,
- optional einen globalen Verwaltungsstatus für die Stammdatenoberfläche.

Ein globaler Aktivstatus entscheidet nicht darüber, ob der Teilnehmer in einer bestimmten Ausgabe teilgenommen hat.

### 7.2 CSC-Ausgabe

Eine CSC-Ausgabe besitzt mindestens:

- stabile interne ID,
- eindeutige Bezeichnung,
- sortierbare Ausgabe- beziehungsweise Anzeigereihenfolge,
- Kennzeichnung, ob sie die aktuell im Tool bearbeitete Ausgabe ist.

Höchstens eine Ausgabe darf als aktuelle Ausgabe markiert sein.

Optional kann für eine Ausgabe eine bestehende Contest-Teilnahme als eigene Identität des Benutzers ausgewählt werden. Es wird kein versteckter Systemteilnehmer erzeugt.

### 7.3 Contest-Teilnahme

Eine Contest-Teilnahme besitzt:

- CSC-Ausgabe,
- Teilnehmeridentität,
- in dieser Ausgabe vertretenes Land.

Pro Teilnehmer und CSC-Ausgabe existiert höchstens eine Contest-Teilnahme.

### 7.4 Mottoshow

Eine Mottoshow besitzt:

- CSC-Ausgabe,
- positive Shownummer,
- Bezeichnung.

Die Shownummer ist nur innerhalb derselben CSC-Ausgabe eindeutig.

### 7.5 Wettbewerbsbeitrag

Ein Wettbewerbsbeitrag besitzt mindestens:

- Mottoshow,
- einreichenden Teilnehmer,
- Interpret,
- Titel,
- optional einen Quell- oder YouTube-Link.

Der Link darf als Import- und Zuordnungshilfe dienen, definiert aber keine fachlich gesonderte Aufnahme- oder Versionsidentität.

Pro Teilnehmer und Mottoshow existiert höchstens ein Wettbewerbsbeitrag.

Ein Wettbewerbsbeitrag darf nur einem Teilnehmer zugeordnet sein, der an der zugehörigen CSC-Ausgabe teilnimmt.

Die bestehenden Felder des aktuellen Hör- und Rankingworkflows können für Beiträge der aktuellen Ausgabe fortbestehen. Sie sind keine Voraussetzung für rein historische Beiträge.

### 7.6 Veröffentlichter Stimmzettel

Für jede Kombination aus Mottoshow und teilnahmeberechtigtem Teilnehmer existiert fachlich ein Erfassungszustand:

- `UNERFASST`: Quelle noch nicht verarbeitet oder Status noch nicht geklärt,
- `NICHT_ABGESTIMMT`: Teilnehmer hat keinen Stimmzettel abgegeben,
- `ABGESTIMMT`: vollständige persönliche Top 15 liegt vor.

Bei `ABGESTIMMT` existieren genau 15 Stimmzettelpositionen.

### 7.7 Stimmzettelposition

Eine Position besitzt ausschließlich:

- Stimmzettel,
- Wettbewerbsbeitrag,
- Rang 1 bis 15.

Punkte werden nicht redundant gespeichert.

## 8. Verbindliche Integritätsregeln

### 8.1 Contest und Teilnahme

- Teilnehmeridentität und Contest-Teilnahme sind getrennte Entitäten.
- Das Land wird pro Contest-Teilnahme gespeichert.
- Ein Teilnehmer kann in mehreren Ausgaben mit unterschiedlichen Ländern vorkommen.
- Ein Alias bleibt dauerhaft an der Teilnehmeridentität und wird nicht pro Ausgabe dupliziert.

### 8.2 Einreichungen

- Eine Mottoshow gehört genau zu einer CSC-Ausgabe.
- Jeder Wettbewerbsbeitrag gehört genau zu einer Mottoshow.
- Jeder Wettbewerbsbeitrag ist spätestens vor dem Stimmzettelimport genau einem Teilnehmer zugeordnet.
- Ein Teilnehmer besitzt innerhalb einer Mottoshow höchstens eine Einreichung.
- Vor Freigabe des Stimmzettelimports muss die Songliste der Show als vollständig bestätigt sein.

### 8.3 Stimmzettel

Für einen Stimmzettel mit Status `ABGESTIMMT` gilt:

- exakt 15 Positionen,
- jeder Rang 1 bis 15 genau einmal,
- jeder Wettbewerbsbeitrag höchstens einmal,
- alle Beiträge gehören zur selben Mottoshow wie der Stimmzettel,
- die eigene Einreichung des Abstimmenden darf nicht enthalten sein,
- es gibt keine Gleichstände.

Ein Stimmzettel wird immer vollständig und atomar gespeichert. Teilstimmzettel mit beispielsweise zwölf Positionen sind kein zulässiger Persistenzzustand.

### 8.4 Abgeleitete Bewertungszustände

Für einen Teilnehmer mit vollständig erfasstem Stimmzettel ergeben sich je Song genau folgende Aussagen:

1. `RANG_1_BIS_15`, wenn der Song im Stimmzettel vorkommt,
2. `AUSSERHALB_TOP_15`, wenn der Song wählbar war, aber nicht vorkommt,
3. `EIGENE_EINREICHUNG`, wenn der Song vom Abstimmenden selbst eingereicht wurde.

Für einen Teilnehmer mit `NICHT_ABGESTIMMT` existiert keine Geschmacksbewertung dieser Show.

Für einen Teilnehmer mit `UNERFASST` darf weder eine Nullwertung noch eine andere Präferenz abgeleitet werden.

### 8.5 Rang und Punkte

Die Punktzuordnung wird weiterhin zentral aus dem Rang abgeleitet:

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

Ein wählbarer Song außerhalb der Top 15 erhält für diese Bewertung abgeleitet null Punkte. Daraus folgt keine genaue Rangposition.

## 9. Verwaltung mehrerer CSC-Ausgaben

### 9.1 Contest-Übersicht

Die Anwendung bietet eine Verwaltung der CSC-Ausgaben mit mindestens:

- Bezeichnung,
- aktueller beziehungsweise historischer Status,
- Anzahl der Teilnehmer,
- Anzahl der Mottoshows,
- Vollständigkeitsanzeige für Songlisten und Stimmzettel.

### 9.2 Aktuelle Ausgabe

Die vorhandenen zwölf Shows und der bestehende Kandidaten-, Hör- und Rankingworkflow werden der aktuellen Ausgabe `CSC X` zugeordnet.

### 9.3 Historische Ausgabe

Für eine historische Ausgabe können nacheinander gepflegt werden:

1. Ausgabe,
2. Teilnehmerfeld und Länder,
3. Mottoshows,
4. vollständige Songlisten mit Teilnehmerzuordnung,
5. veröffentlichte Stimmzettel.

Historische Ausgaben benötigen keinen Kandidatenworkflow für eigene künftige Einreichungen.

### 9.4 Teilnehmerverwaltung

Die Teilnehmeroberfläche trennt:

- dauerhafte Identität und Aliasse,
- Contest-Teilnahmen mit dem jeweiligen Land.

Das Entfernen einer Contest-Teilnahme ist blockiert, solange historische Einreichungen oder Stimmzettel darauf verweisen.

## 10. Vollständige Songlisten

### 10.1 Aktueller Contest

Im aktuellen Contest kann die anonyme Songliste wie bisher importiert werden. Nach der Enthüllung werden die Beiträge den Teilnehmern zugeordnet.

Die Show gilt für den historischen beziehungsweise vollständigen Bewertungsimport erst dann als songseitig vollständig, wenn:

- alle erwarteten Beiträge vorhanden sind,
- jeder Beitrag genau einem Teilnehmer zugeordnet ist,
- kein Teilnehmer versehentlich zwei Beiträge besitzt.

### 10.2 Historische Contests

Für historische Shows wird eine vollständige Songliste einschließlich Einreichenden bereitgestellt.

Die Anwendung bietet mindestens:

- manuelle Erfassung und Korrektur,
- Importvorschau,
- Auflösung von Namen über Anzeigenamen und Aliasse,
- Nutzung des contestbezogenen Landes als zusätzliche Prüf- und Anzeigeinformation,
- atomaren bestätigten Import.

Der Import erkennt die derzeit bekannten realen Formate block- oder zeilenweise über kleine, getrennte Formatstrategien. Keine Variante ist das einzig zulässige CSC-Format; weitere belegte Varianten können als zusätzliche Strategie folgen. Alle Varianten münden unverändert in dieselbe editierbare Vorschau und den atomaren Import:

- Format A: `Interpret - Titel (Land/Teilnehmer)` beziehungsweise die eindeutig auflösbare umgekehrte Zuordnung;
- Format B: `Interpret - Titel - Teilnehmer / Land` mit optionalem Leerraum am Slash;
- Format C: sichtbares Präfix `Land - Teilnehmer` mit verlinktem `Interpret - Titel`.

Bei Format C ist eine eindeutige Rich-HTML-Struktur die bevorzugte Informationsquelle: Das sichtbare Präfix und genau ein Anchor desselben logischen Quellblocks liefern Zuordnung, Songtext und Linkziel. Der Markdown-/Plaintext-Fallback bleibt nutzbar. Liefert ein Paste-Ereignis beide äquivalenten Repräsentationen, wird die eindeutige linkhaltige Variante nur einmal übernommen; die Rich-HTML-Variante hat dabei Vorrang. Widersprüchliche Darstellungen oder mehrere Links bleiben sichtbar zur manuellen Nacharbeit.

Interpret, Titel und Teilnehmernamen sind stets konkrete Quelldaten. Sie werden weder automatisch korrigiert oder harmonisiert noch fuzzy zusammengeführt. Teilnehmer werden ausschließlich über Anzeigename oder gepflegte Aliasse aufgelöst; das vertretene Land ist nur ein contestbezogenes Plausibilitätssignal.

### 10.3 Keine Ableitung aus Stimmzetteln

Stimmzettel dürfen fehlende Songs nicht erzeugen. Dadurch bleibt garantiert, dass auch ein Beitrag ohne eine einzige Top-15-Nennung im Archiv vorhanden ist.

## 11. Import veröffentlichter Einzelwertungen

### 11.1 Unterstützter Block

Ein typischer Block besitzt eine Überschrift wie:

```text
[#3] Malta - -Frollo-
```

und anschließend 15 Songzeilen in aufsteigender Punktefolge.

### 11.2 Erkennung des Abstimmenden

Der Abstimmende wird aus der Blocküberschrift anhand folgender Informationen aufgelöst:

1. aktueller Anzeigename,
2. gepflegte Aliasnamen,
3. contestbezogenes Land als zusätzliche Plausibilitäts- und Konfliktinformation.

Ähnliche Namen werden nicht automatisch zusammengeführt. Mehrdeutige Treffer müssen in der Vorschau manuell aufgelöst werden.

### 11.3 Erkennung der 15 Positionen

Der Parser erkennt genau 15 Songzeilen und weist ausschließlich anhand ihrer Reihenfolge Ränge zu:

```text
erste Songzeile  -> Rang 15
...
letzte Songzeile -> Rang 1
```

Das jeweils verwendete Wort wie `Punkte`, `points`, `punti` oder eine andere Sprachform wird nicht fachlich interpretiert. Es bleibt auch dann opak, wenn es ohne Leerzeichen an der sichtbaren Zahl hängt oder in einem anderen Schriftsystem geschrieben ist; eine Whitelist von Punktewörtern existiert nicht.

Die sichtbaren Zahlen dürfen nur als strukturelles Präfixsignal dienen. Sie werden weder als Primärquelle noch als gespeicherte Punktwerte verwendet. Geschützte Leerzeichen sowie direkt benachbarte HTML- oder Markdown-Formatierung dürfen diese Trennung nicht aufheben.

### 11.4 Zuordnung zu bestehenden Beiträgen

Jede Songzeile wird ausschließlich einem bereits vorhandenen Wettbewerbsbeitrag derselben Show zugeordnet.

Als Zuordnungssignale dürfen dienen:

- angezeigter Teilnehmername oder Alias,
- contestbezogenes Land,
- Interpret,
- Titel,
- normalisierter vorhandener Link.

Keine einzelne Heuristik darf bei Mehrdeutigkeit stillschweigend entscheiden. Unbekannte oder widersprüchliche Zeilen blockieren die Speicherung des betroffenen Stimmzettels bis zur manuellen Korrektur.

### 11.5 Vorschau und Bestätigung

Die Importvorschau zeigt je Block mindestens:

- erkannten Abstimmenden,
- 15 erkannte Ränge,
- zugeordneten Song,
- zugeordneten Einreichenden,
- Warnungen und Konflikte,
- Status `importierbar` oder `Nacharbeit erforderlich`.

Die Vorschau verändert keine Persistenz.

### 11.6 Atomarität und Wiederholungen

- Ein einzelner Stimmzettel wird nur vollständig gespeichert.
- Ein Mehrfachimport darf mehrere vollständige Stimmzettel in einem Arbeitsschritt bestätigen.
- Ein fehlerhafter Stimmzettel erzeugt niemals teilweise Positionen.
- Ein bereits vorhandener Stimmzettel wird nicht unbemerkt überschrieben.
- Ersetzen erfordert eine ausdrückliche Bestätigung und ersetzt Headerstatus sowie alle 15 Positionen atomar.

### 11.7 Nicht abgegebene Bewertung

Ein Teilnehmer ohne abgegebenen Stimmzettel wird ausdrücklich als `NICHT_ABGESTIMMT` markiert. Die bloße Abwesenheit eines noch nicht bearbeiteten Blocks reicht dafür während einer laufenden Erfassung nicht aus.

## 12. Abgeleitete Ergebnisansichten

### 12.1 Eigene Einreichung

Für eine CSC-Ausgabe kann der Benutzer optional eine vorhandene Contest-Teilnahme als eigene Identität markieren.

Daraus ist je Show die eigene Einreichung eindeutig über die Song-Teilnehmer-Zuordnung bestimmbar.

### 12.2 Erhaltene Bewertung je Teilnehmer

Die bisherige Ansicht „Welche Punkte erhielt meine Einreichung von Teilnehmer X?“ wird aus den vollständigen Stimmzetteln abgeleitet:

- eigener Song auf Rang 1 bis 15: zugehöriger Rang und abgeleitete Punkte,
- eigener Song nicht in einer abgegebenen Top 15: außerhalb der Top 15, abgeleitet null Punkte,
- Teilnehmer hat nicht abgestimmt: keine Bewertung,
- Stimmzettel unerfasst: unbekannt,
- eigene Teilnehmeridentität: eigene Einreichung und deshalb nicht wählbar.

### 12.3 Keine offiziellen Gesamtwerte

Die Anwendung berechnet für diesen Erweiterungszweck keine offizielle Mottoshow-Gesamtwertung und speichert keine offizielle Gesamtpunktzahl oder Endplatzierung.

Eine Summe der für die eigene Einreichung abgeleiteten Punkte darf als reine Komfortanzeige existieren, ist aber kein offizielles Ergebnisobjekt und keine zusätzliche Quelle der Wahrheit.

## 13. Analyseexport für externe KI

### 13.1 Ziel

Der Export soll ohne Zugriff auf die SQLite-Datenbank als Quelle für externe Analysen verwendbar sein, insbesondere für Fragen wie:

- Welche Teilnehmer bewerten musikalisch ähnlich?
- Welche Teilnehmer geben bestimmten Arten von Songs häufig hohe Ränge?
- Welche aktuellen Kandidaten könnten bei einzelnen Teilnehmern oder im Feld gut ankommen?
- Welche Teilnehmer zeigen über mehrere Ausgaben stabile oder veränderte Präferenzen?

### 13.2 Exportpaket

Ein Analyseexport erzeugt ein gemeinsames versioniertes Paket mit mindestens:

- `analysis.json` als vollständige normalisierte Primärquelle,
- `analysis.md` als menschen- und KI-freundliche Darstellung,
- optionalen CSV-Dateien für Tabellenanalysen.

Ein direkter Upload zu Google Drive ist nicht Bestandteil. Das lokal erzeugte Paket kann anschließend manuell in Google Drive abgelegt werden.

### 13.3 JSON-Inhalte

`analysis.json` enthält mindestens:

- Format- und Schemaversion,
- Erstellungszeitpunkt,
- Teilnehmeridentitäten und Aliasse,
- CSC-Ausgaben,
- Contest-Teilnahmen und Länder,
- Mottoshows,
- vollständige Wettbewerbsbeiträge und Einreichende,
- Stimmzettelstatus,
- Rangpositionen 1 bis 15,
- optional ausgewählte aktuelle Kandidatenlisten als separaten, eindeutig gekennzeichneten Datenbereich.

Punktwerte dürfen im Export als ausdrücklich abgeleitete Komfortwerte enthalten sein. Der Rang bleibt die kanonische Information.

### 13.4 Markdown-Inhalte

`analysis.md` stellt je Contest, Show und Teilnehmer nachvollziehbar dar:

- persönliche Top 15,
- außerhalb der Top 15 liegende wählbare Songs,
- eigene nicht wählbare Einreichung,
- nicht abgegebene oder noch unerfasste Stimmzettel,
- historische Einreichungen des Teilnehmers.

Songs außerhalb der Top 15 werden als ungeordnete Menge dargestellt. Der Export darf keine scheinpräzise Reihenfolge ab Platz 16 erzeugen.

### 13.5 CSV-Inhalte

Sinnvolle CSV-Ausgaben sind mindestens:

- Teilnehmer und Contest-Teilnahmen,
- Wettbewerbsbeiträge,
- Stimmzettelpositionen im Long-Format,
- optional eine abgeleitete Bewertungsmatrix mit klaren Zuständen statt pauschaler Nullwerte.

### 13.6 Exportumfang

Der Benutzer kann mindestens wählen zwischen:

- vollständigem Archiv,
- ausgewählten CSC-Ausgaben,
- ausgewählten Mottoshows,
- historischem Archiv plus Kandidaten einer aktuellen Show.

### 13.7 Ausschlussliste

Der Analyseexport enthält die CSC-Ausschlussliste nicht. Historische Einreichungen und Ausschlussprüfung bleiben getrennte Datenbereiche und Arbeitsabläufe.

## 14. Tippspiel-Erweiterung

### 14.1 Zweck

Während der anonymen Bewertungsphase können aktuelle Wettbewerbsbeiträge den vermuteten Einreichenden zugeordnet werden.

### 14.2 Datenbasis

Die Arbeitsfläche verwendet:

- aktuelle anonyme Songliste,
- aktuelles Teilnehmerfeld mit contestbezogenen Ländern,
- historische Einreichungen der Teilnehmer.

### 14.3 Bedienung

Mindestens vorgesehen sind:

- eindeutige Zuordnung jedes aktuellen Songs zu höchstens einem Teilnehmer,
- eindeutige Verwendung jedes Teilnehmers höchstens einmal, soweit die aktuelle Runde dies verlangt,
- Drag-and-drop oder gleichwertig komfortable Auswahl,
- optionale Sicherheit und Notiz pro Tipp,
- historische Einreichungen eines ausgewählten Teilnehmers als schnell erreichbare Recherchehilfe,
- Auflösung nach Bekanntgabe der echten Zuordnungen,
- Anzahl korrekter Tipps und einfache persönliche Historie.

### 14.4 Abgrenzung

Das Tool erstellt keine automatische KI-Prognose. Eine externe Analyse darf Vorschläge liefern, die Entscheidung und Pflege im Tippspiel bleiben bewusst beim Benutzer.

## 15. Informationsarchitektur

Die Erweiterung ergänzt die bestehende Navigation um einen Contest- beziehungsweise Archivzugang.

Mindestens erforderlich sind:

1. Auswahl beziehungsweise Verwaltung der CSC-Ausgabe,
2. aktuelle Show-Übersicht mit bestehenden Kandidaten- und Abstimmungsfunktionen,
3. historische Contest-Übersicht,
4. Teilnehmeridentitäten mit Contest-Teilnahmen,
5. historische Showansicht für Songliste und Stimmzettelstatus,
6. Analyseexport,
7. später Tippspiel.

Die aktuelle Kandidaten- und persönliche Rankingoberfläche bleibt auf die aktuelle Ausgabe fokussiert. Historische Shows benötigen keinen künstlich nachgebauten Hör- oder Kandidatenworkflow.

## 16. Migration des bestehenden Datenbestands

### 16.1 Aktuelle Ausgabe

Die bestehenden zwölf Mottoshows werden einer neu angelegten Ausgabe `CSC X` zugeordnet.

### 16.2 Teilnehmerländer

Der bisher direkt am Teilnehmer gespeicherte Country-Code wird für `CSC X` in eine Contest-Teilnahme überführt.

Der dauerhafte Teilnehmer behält Anzeigename und Aliasse. Länderinformationen werden anschließend ausschließlich contestbezogen gepflegt.

### 16.3 Bestehende Beitragszuordnungen

Vorhandene Teilnehmerzuordnungen an Wettbewerbsbeiträgen bleiben erhalten und müssen auf gültige Contest-Teilnahmen von `CSC X` referenzieren.

### 16.4 Bestehende isolierte Ergebnisdaten

Die bisherige Tabelle mit ausschließlich für die eigene Einreichung erfassten Punktwerten kann nicht in vollständige Stimmzettel umgerechnet werden. Aus einem einzelnen Punktwert darf niemals eine komplette Top 15 erfunden werden.

Daher gilt:

- keine automatische Erzeugung veröffentlichter Stimmzettel aus isolierten Punktwerten,
- vorhandene Werte bis zur vollständigen Neuerfassung höchstens als klar gekennzeichnete Legacy-Daten behandeln,
- nach vollständigem Import der Stimmzettel werden Ergebnisanzeigen ausschließlich daraus abgeleitet,
- offizielle Gesamtpunktzahl, Endplatzierung und Gleichstandskennzeichnung gehören nicht zum neuen Zielmodell,
- eine spätere Entfernung alter Felder erfolgt erst mit gesicherter Backup-, Restore- und JSON-Migrationsstrategie.

### 16.5 Backup und Export

Native Backups bleiben über Liquibase-Migrationen vorwärts kompatibel.

Der vollständige JSON-Export erhält eine neue Formatversion. Ältere unterstützte Formate bleiben importierbar; fehlende Contest-Strukturen werden deterministisch als `CSC X` aufgebaut.

## 17. Datenschutz und Betrieb

- weiterhin lokale Einzelbenutzer-Anwendung,
- keine Cloudübertragung durch diese Erweiterung,
- keine automatische Veröffentlichung der Abstimmungsdaten,
- keine Protokollierung vollständiger importierter Stimmzettelblöcke,
- Importvorschauen behandeln Clipboard-Inhalte als nicht vertrauenswürdige Eingabe,
- bestehende Backup- und Restore-Sicherheitsregeln gelten auch für historische Daten.

## 18. Abnahmeszenario

Die Erweiterungsroadmap ist fachlich vollständig abgenommen, wenn folgendes Szenario funktioniert:

1. `CSC IX` als historische Ausgabe anlegen.
2. Teilnehmeridentitäten und Aliasse pflegen.
3. für jeden Teilnehmer das in `CSC IX` vertretene Land hinterlegen.
4. mehrere historische Mottoshows anlegen.
5. für eine Show die vollständige Songliste einschließlich aller Einreichenden importieren oder erfassen.
6. die Songliste als vollständig bestätigen.
7. einen oder mehrere veröffentlichte Bewertungsblöcke einfügen.
8. aus der ersten Songzeile Rang 15 und aus der letzten Rang 1 ableiten, ohne das lokalisierte Punktewort fachlich auszuwerten.
9. unbekannte Namen oder Songs in der Vorschau manuell auflösen.
10. jeden Stimmzettel vollständig und atomar speichern.
11. nicht abgegebene Bewertungen ausdrücklich markieren.
12. für jeden abgegebenen Stimmzettel Top 15, außerhalb der Top 15 und eigene Einreichung korrekt ableiten.
13. eine vorhandene eigene Ergebnisansicht aus den vollständigen Stimmzetteln ableiten.
14. ein Analysepaket mit JSON, Markdown und CSV erzeugen.
15. prüfen, dass keine Gesamtplatzierung, kein Sieger und keine Ausschlussprüfung aus dem Archiv berechnet werden.
16. die bestehende aktuelle Kandidaten- und persönliche Abstimmungsfunktion für `CSC X` weiterhin ohne Regression verwenden.

## 19. Entwicklungspakete und Abhängigkeiten

### P9 – Multi-Contest- und Teilnahmefundament

Ziel:

- CSC-Ausgaben,
- Contest-Teilnahmen,
- contestbezogene Länder,
- Zuordnung der vorhandenen zwölf Shows zu `CSC X`,
- sichere Migration des aktuellen Teilnehmer- und Beitragsbestands,
- Contest-Auswahl und angepasste Teilnehmerverwaltung.

Dieses Paket ist Voraussetzung für alle weiteren Pakete.

### P10 – Historische Contestverwaltung und vollständige Songlisten

Ziel:

- historische Ausgaben und Mottoshows verwalten,
- Teilnehmerfelder je Ausgabe pflegen,
- vollständige historische Songlisten mit Teilnehmerzuordnung importieren und korrigieren,
- Vollständigkeit einer Songliste explizit bestätigen,
- auch Beiträge ohne jede Top-15-Nennung sicher erfassen.

Benötigt P9.

### P11 – Veröffentlichte Einzelwertungen importieren und speichern

Ziel:

- Stimmzettelstatus je Teilnehmer und Show,
- Parser und Vorschau für veröffentlichte Bewertungsblöcke,
- Reihenfolge `erste Zeile = Rang 15` bis `letzte Zeile = Rang 1`,
- Alias- und Beitragsauflösung,
- atomare vollständige Stimmzettel,
- abgeleitete Zustände Top 15, außerhalb Top 15 und eigene Einreichung.

Benötigt P9 und P10.

### P12 – Ergebnisansichten aus Stimmzetteln ableiten und Legacy-Modell ablösen

Ziel:

- eigene Teilnehmeridentität je Contest optional festlegen,
- bisherige Ansicht der erhaltenen Punkte aus vollständigen Stimmzetteln ableiten,
- redundante isolierte Punktpflege beenden,
- offizielle Gesamtpunkte, Endplatzierung und Gleichstand aus dem aktiven Produktumfang entfernen,
- Legacy-Daten und alte JSON-Formate sicher migrieren.

Benötigt P11.

### P13 – Analyseexport für externe KI

Ziel:

- versioniertes Analysepaket,
- normalisiertes JSON,
- KI-freundliches Markdown,
- geeignete CSV-Ausgaben,
- Auswahl von Contests, Shows und optional aktuellen Kandidaten,
- keine direkte Drive- oder KI-Anbindung.

Benötigt P11; für vollständige eigene Ergebnisableitungen sinnvollerweise P12.

### P14 – Tippspiel-Arbeitsfläche

Ziel:

- aktuelle anonyme Songs Teilnehmern zuordnen,
- historische Einreichungen als Recherchehilfe anzeigen,
- Sicherheit und Notizen pflegen,
- nach Auflösung Treffer auswerten.

Benötigt P9 und P10. P11 und P13 sind fachlich nützlich, aber keine harte technische Voraussetzung.

## 20. Empfohlene Umsetzungsreihenfolge

```text
P9
 └─> P10
      ├─> P11
      │    ├─> P12
      │    └─> P13
      └─> P14
```

Empfohlene Hauptreihenfolge:

```text
P9 -> P10 -> P11 -> P12 -> P13
```

P14 folgt erst, wenn eine hinreichend große historische Einreichungsbasis vorhanden ist.

## 21. Noch benötigte Implementierungsfixture

Vor der konkreten Vorbereitung beziehungsweise Umsetzung von P10 wird ein realer historischer Songlistenblock mit Teilnehmerzuordnung benötigt.

Für P11 liegt bereits ein hinreichendes Beispiel eines veröffentlichten Einzelstimmzettels vor. Für robuste Mehrfachblock- und Clipboard-Tests ist später zusätzlich ein längerer zusammenhängender Quellblock sinnvoll.

Es besteht kein weiterer fachlicher Klärungsbedarf für die Paketabgrenzung.
