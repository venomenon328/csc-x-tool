# Externe KI-Analyse für Teilnehmerprofile, Tippspiel und Kandidaten-Fit

**Protokollversion:** 1.0  
**Stand:** 2026-09-01  
**Zugehöriges Issue:** #136

## 1. Zweck

Dieses Dokument definiert das reproduzierbare Verfahren, mit dem der versionierte Analyseexport des CSC X Tools außerhalb der Anwendung durch eine KI ausgewertet wird.

Es unterstützt zwei voneinander getrennte Anwendungsfälle:

1. **Tippspiel:** Für die anonymen Beiträge einer laufenden Mottoshow werden plausible Song-/Teilnehmer-Zuordnungen ermittelt.
2. **Ausführliche Kandidatenbewertung nach Nutzerfreigabe:** Bereits vom Nutzer ausgewählte eigene Einreichungskandidaten werden ergänzend gegen das konkrete aktuelle Teilnehmerfeld bewertet.

Die allgemeine Kandidatenrecherche, erste Vorschlagslisten und iterative Erweiterungssuchen sind ausdrücklich nicht Teil der teilnehmerbezogenen Analyse. Dort bleibt der Nutzer das Gate: Er prüft die profilfrei recherchierten Vorschläge und entscheidet selbst, welche Songs in seine persönliche Kandidatenliste aufgenommen werden.

Das CSC X Tool bleibt Datenquelle und Arbeitsoberfläche. Es führt selbst keine KI-Prognosen, automatische Genreklassifikation oder Teilnehmerprofilierung aus.

## 2. Verbindliche Grundlagen und Rangfolge

Für historische Einreichungen, Teilnahmen, Stimmzettel und deren Semantik ist [`historical-contests-ballots-analysis.md`](historical-contests-ballots-analysis.md) maßgeblich.

Ergänzend gelten:

1. der Exportvertrag `csc-x-tool-analysis` in seiner jeweiligen `formatVersion`;
2. dieses Dokument für die externe Analyse und deren Versionierung;
3. die externe CSC-Ausschlussliste ausschließlich für die Zulässigkeitsprüfung eigener Kandidaten;
4. das Dokument `Mottoshows` für die fachlichen Showregeln;
5. das Dokument `Workflow und Strategie` für Recherche, persönlichen Repräsentations-Fit und allgemeine strategische Bewertung;
6. das Google-Sheet `Teilnehmer` für versionierte Teilnehmerprofile, Songmerkmale, Belege und Analyseläufe.

Harte Motto- und Ausschlussregeln werden durch keine statistische oder KI-basierte Einschätzung relativiert.

## 3. Grundsätze

### 3.1 Stabile Identitäten

Teilnehmer werden ausschließlich über `participant_id` verbunden. Anzeigenamen und Aliase dienen der Darstellung und Suche, niemals als Primärschlüssel.

Contestbezogene Eigenschaften wie Teilnahme-ID und vertretenes Land werden nicht dauerhaft dem Teilnehmer zugeschrieben.

Doppelte oder widersprüchliche Teilnehmeridentitäten sind vor einer offiziellen Profilbildung zu bereinigen. Daten verschiedener `participant_id`-Werte dürfen nicht allein aufgrund gleicher Anzeigenamen stillschweigend zusammengeführt werden.

### 3.2 Drei klar getrennte Erkenntnisarten

Jede gespeicherte oder ausgegebene Aussage gehört zu genau einer der folgenden Klassen:

- **Fakt:** direkt aus dem Analyseexport oder einer belastbaren externen Quelle ableitbar;
- **abgeleitetes Signal:** nach einer dokumentierten Regel aus Fakten berechnet;
- **Hypothese/Einschätzung:** interpretative Aussage mit sichtbarer Konfidenz, Belegen und möglichen Gegenbelegen.

Eine Hypothese darf nie nachträglich wie ein gesicherter Fakt behandelt werden.

### 3.3 Zwei getrennte Teilnehmermodelle

Für jeden Teilnehmer werden zwei Profile getrennt geführt:

- **Geschmacksmodell:** Was bewertet der Teilnehmer in veröffentlichten Stimmzetteln hoch oder niedrig?
- **Auswahlmodell:** Welche Art von Song reicht der Teilnehmer selbst ein?

Eigene Einreichungen sind ein starkes Signal für Auswahlverhalten, aber kein ungefilterter Beweis des allgemeinen Musikgeschmacks. Mottovorgaben, strategische Erwägungen, bewusste Stilwechsel und Spaßbeiträge können die Auswahl stark prägen.

### 3.4 Keine erfundenen Negativbewertungen

Die fachlichen Zustände bleiben erhalten:

- Rang 1 bis 15 ist eine bekannte positive Präferenz mit unterschiedlicher Stärke.
- `OUTSIDE_TOP_15` bedeutet nur, dass ein wählbarer Song nicht in der Top 15 stand.
- `OWN_ENTRY` ist nicht wählbar und liefert kein negatives Geschmackssignal.
- `NO_BALLOT` und `UNKNOWN` liefern keine Präferenzinformation.

Für Songs außerhalb der Top 15 wird kein Rang ab 16 erfunden.

### 3.5 Unsicherheit ist ein Ergebnis

Fehlende oder widersprüchliche Daten werden nicht durch selbstbewusste Prosa kaschiert. Ein Teilnehmer kann ausdrücklich den Profilstatus `UNANALYSIERT`, `VORLÄUFIG` oder eine geringe Datenabdeckung besitzen.

Unbekannte Teilnehmer werden über einen neutralen Feld-Prior berücksichtigt. Sie werden weder ignoriert noch mit dem Durchschnittsprofil als vermeintlich bekannte Person verwechselt.

### 3.6 Begrenzung auf den Anwendungszweck

Profile enthalten nur musik- und contestrelevante Eigenschaften. Alter, Geschlecht, Beruf, politische Haltung, Persönlichkeit oder sonstige private Eigenschaften werden nicht aus Songwahlen geraten.

### 3.7 Verbindliches Nutzer-Gate für Kandidaten

Teilnehmerprofile dürfen vor der persönlichen Vorauswahl des Nutzers nicht verwendet werden.

Damit gilt verbindlich:

- Allgemeine Kandidatenrecherche und erste Vorschlagslisten werden ohne Teilnehmerprofile erstellt.
- Auch iterative Suchen nach weiteren Vorschlägen verwenden keine Teilnehmerprofile.
- Der Nutzer hört und filtert die Vorschläge selbst und übernimmt interessante Songs in seine persönliche Kandidatenliste.
- Erst wenn der Nutzer für einen oder mehrere bereits ausgewählte Kandidaten ausdrücklich eine ausführliche Bewertung anfordert, werden Teilnehmerprofile ergänzend herangezogen.
- Vor diesem Gate dürfen Profile weder Suchraum, Sortierung, Aufnahme noch Ablehnung eines Vorschlags beeinflussen.

Das Nutzer-Gate verhindert, dass ein statistisch vermuteter Feldgeschmack die musikalische Breite der Recherche vorzeitig verengt oder interessante Nischenkandidaten unsichtbar macht.

## 4. Eingabepaket und effizienteste Übergabe

### 4.1 Bevorzugtes Format

Die bevorzugte Übergabe ist das **vollständige ZIP des Analyseexports** aus dem CSC X Tool.

Innerhalb des Pakets gilt:

- `analysis.json` ist die kanonische maschinenlesbare Quelle;
- `manifest.json` beschreibt Format, Version, Erzeugungszeitpunkt, Scope und enthaltene Dateien;
- CSV-Dateien dienen der Kontrolle, Filterung und punktuellen Tabellenarbeit;
- `analysis.md` und `README.md` dienen der menschlichen Plausibilitätsprüfung.

Der vollständige JSON-Export des Tools ist davon zu unterscheiden. Er ist ein schema- und restoreorientiertes Datenaustauschformat mit internen Rohdaten. Er kann im Notfall als zusätzliche Prüfquelle dienen, ist aber nicht der reguläre semantische Vertrag für externe KI-Analysen. Insbesondere müssen dort Bewertungszustände und weitere Beziehungen erneut aus internen Tabellen hergeleitet werden.

Ein Datenbankdump, Screenshots oder manuell zusammenkopierte Listen sind weder nötig noch vorzuziehen.

### 4.2 Empfohlener Scope

Für die erste Profilbildung wird ein **vollständiger Archivexport** erzeugt. Er soll alle hinreichend gepflegten historischen Contests und Shows sowie die aktuelle Ausgabe enthalten.

Für spätere Aktualisierungen ist erneut ein vollständiger Export die bevorzugte Variante. Dadurch bleiben Löschungen, Korrekturen, Aliasänderungen und nachträglich ergänzte Stimmzettel erkennbar. Ein Deltaexport wird nur verwendet, wenn das vollständige Paket praktisch zu groß wird; dann müssen letzter vollständiger Basisexport und Delta gemeinsam vorliegen.

Die optionale Kandidatenliste wird für einen Profil-Basislauf nicht benötigt. Sie wird nur mitexportiert, wenn bereits ausgewählte Kandidaten nach ausdrücklicher Nutzeranforderung ausführlich bewertet werden sollen.

### 4.3 Mindestinhalt

Für eine belastbare Analyse werden mindestens benötigt:

- `format`, `formatVersion`, `generatedAt` und Export-Scope;
- Teilnehmer mit stabiler `participant_id` und Aliasen;
- contestbezogene Teilnahmen und Länder;
- Contests und Shows in fachlicher Reihenfolge;
- vollständige Einreichungen mit Interpret, Titel, Version beziehungsweise URL und zugeordneter Teilnahme, soweit die Zuordnung bereits aufgelöst sein darf;
- veröffentlichte Stimmzettel mit Rang 1 bis 15;
- die abgeleiteten Zustände für eigene Einreichung und außerhalb der Top 15;
- das aktuelle Teilnehmerfeld;
- für ein Tippspiel die anonymen Beiträge der aktuellen Zielshow.

Die ausführlichen Mottoregeln und musikalischen Songmerkmale sind bewusst keine Bestandteile des Analyseexports. Sie werden aus den verbindlichen externen Dokumenten beziehungsweise durch dokumentierte Recherche und Einschätzung ergänzt.

Für die ausführliche Kandidatenbewertung kann optional die persönliche Kandidatenliste mitexportiert werden. Sie ersetzt weder das Nutzer-Gate noch die separate Motto- und Ausschlussprüfung.

### 4.4 Fallback bei Uploadproblemen

Falls das ZIP nicht verarbeitet werden kann, reicht zunächst `analysis.json`. Nur wenn auch dies technisch scheitert, werden folgende CSV-Dateien gemeinsam verwendet:

- `participants.csv`
- `participations.csv`
- `entries.csv`
- `ballots.csv`
- `assessment-matrix.csv`
- optional `candidates.csv`

Bei einer CSV-Übergabe müssen `manifest.json` und die Exporterzeugungszeit zusätzlich vorliegen.

Der vollständige JSON-Export ist nur ein nachrangiger technischer Fallback. Seine Verwendung muss im Analyselauf ausdrücklich protokolliert werden.

### 4.5 Laufidentität

Jede Analyse erhält eine eindeutige `run_id`, beispielsweise:

```text
RUN-20260901-001
```

Im Blatt `Analyseläufe` werden mindestens Exportdateiname, SHA-256, `generatedAt`, Scope, Protokollversion, Modell, Reasoning-Stufe und Zweck protokolliert.

## 5. Qualitätsprüfung vor jeder Analyse

Vor Profilbildung, Tippspiel oder ausführlicher Kandidatenbewertung wird ein Quality Gate durchgeführt.

### 5.1 Harte Blocker

Eine Show wird nicht für Teilnehmerprofile verwendet, wenn mindestens einer dieser Zustände vorliegt:

- die Einreichungsliste ist nicht als vollständig bestätigt;
- Einreichungen besitzen bei einer aufgelösten historischen Show unbekannte Einreichende;
- Teilnehmeridentitäten oder Teilnahmen sind doppelt, widersprüchlich oder nicht stabil verbunden;
- Ranglisten enthalten doppelte oder fehlende Ränge innerhalb 1 bis 15;
- die Zielshow des Tippspiels besitzt keine vollständige anonyme Songliste;
- das aktuelle Teilnehmerfeld ist nicht hinreichend bekannt.

Ein harter Blocker kann auf eine einzelne Show oder Teilnehmeridentität begrenzt werden. Er entwertet nicht automatisch den gesamten Export.

### 5.2 Weiche Einschränkungen

Folgende Zustände reduzieren Abdeckung oder Konfidenz, blockieren aber nicht grundsätzlich:

- einzelne Teilnehmer ohne veröffentlichten Stimmzettel;
- Teilnehmer mit nur einer oder wenigen historischen Einreichungen;
- fehlende YouTube- oder Quellenlinks bei ansonsten eindeutiger Songidentität;
- Shows mit ungewöhnlich engem oder stark stilprägendem Motto;
- nur über den Shownamen und nicht über vollständig dokumentierte historische Mottoregeln bekannter Kontext;
- widersprüchliche externe Genre- oder Veröffentlichungsangaben;
- erkennbare bewusste Spaß-, Troll- oder Extrembeiträge.

### 5.3 Abdeckungsbericht

Jeder Lauf dokumentiert mindestens:

- Anzahl einbezogener Contests und Shows;
- Anzahl Teilnehmer im aktuellen Feld;
- Anzahl Teilnehmer mit `KEINE`, `GERINGE`, `MITTLERE` und `HOHE` Datenabdeckung;
- Anzahl vollständiger Einreichungslisten;
- Anzahl veröffentlichter und fehlender Stimmzettel;
- Anteil bereits angereicherter Songmerkmale;
- ausgeschlossene Shows, Teilnehmeridentitäten und Gründe.

## 6. Wiederverwendbare Songmerkmale

Die historischen Rohdaten enthalten bewusst keine automatische Genre- oder Geschmacksanalyse. Für externe Profile werden Songs deshalb einmalig angereichert und im Blatt `Songmerkmale` wiederverwendbar gespeichert.

### 6.1 Songidentität

`entry_id` identifiziert eine konkrete Contest-Einreichung, nicht zwingend eine weltweit eindeutige Aufnahme. Für die Merkmalsablage wird zusätzlich ein stabiler `song_key` aus normalisiertem Interpret, Titel und relevanter Versionsangabe gebildet.

Live-, Akustik-, Remix-, Cover- und Studiofassungen dürfen nur dann denselben `song_key` verwenden, wenn ihre für die Analyse relevanten Eigenschaften hinreichend gleich sind. Im Zweifel werden sie getrennt geführt.

### 6.2 Faktische Merkmale

Nach Möglichkeit mit belastbaren Quellen zu erfassen sind:

- Interpret und exakter Titel;
- konkrete Version;
- Veröffentlichungsjahr;
- Sprache;
- Herkunft beziehungsweise Musikszene, sofern relevant;
- primäres und sekundäres Genre;
- Dauer;
- Cover-, Live-, Remix- oder Novelty-Status.

### 6.3 Bewertende Merkmale

Auf einer dokumentierten Skala werden unter anderem eingeschätzt:

- Energie;
- Härte;
- Anteil harschen Gesangs;
- Komplexität beziehungsweise Sperrigkeit;
- Melodik;
- Atmosphäre;
- Zugänglichkeit;
- Hook- beziehungsweise Refrain-Stärke;
- Neuheitsgrad beziehungsweise Eigenständigkeit;
- vermutete Mainstream-Bekanntheit;
- Nostalgiepotenzial;
- Humor- beziehungsweise Gimmickanteil;
- dominante Stimmungsmerkmale.

Faktische und bewertende Konfidenz werden getrennt angegeben.

### 6.4 Quellen und Modellwissen

Überprüfbare Fakten werden recherchiert und mit Quellen dokumentiert. Rein auditive oder strategische Einschätzungen werden als Modellurteil gekennzeichnet und nicht mit einer Quellenangabe vorgetäuscht.

### 6.5 Mottovorgaben als Störfaktor

Ein Merkmal darf aus einer eigenen Einreichung nicht als freie Auswahlpräferenz verallgemeinert werden, wenn es durch das Motto erzwungen wurde.

Beispiele:

- Eine fremdsprachige Einreichung in `No comprende!` beweist keine allgemeine Vorliebe für nicht englischsprachige Musik.
- Eine Liveaufnahme in `Live will always be life` beweist keine allgemeine Livepräferenz.
- Ein Song aus den 1950er- oder 1960er-Jahren in `Twist & Shout` beweist keine allgemeine Oldiespräferenz.

Andere, nicht erzwungene Eigenschaften derselben Einreichung dürfen weiterhin Signale liefern.

Ist der historische Mottokontext nicht ausreichend bekannt, werden nur eindeutig nicht erzwungene Eigenschaften verallgemeinert; andernfalls sinkt die Konfidenz.

## 7. Bildung der Teilnehmerprofile

### 7.1 Geschmacksmodell aus Stimmzetteln

Bekannte Rangpositionen werden als positive Signale gewichtet. Die Startgewichtung der Protokollversion 1.0 verwendet die offiziellen CSC-Punkte, normiert auf Platz 1:

```text
rank_weight(rank) = points_for_rank(rank) / 25
```

Damit erhält Platz 1 das Gewicht `1.00`, Platz 2 `0.80` und Platz 15 `0.04`.

`OUTSIDE_TOP_15` wird nur bei einem tatsächlich veröffentlichten Stimmzettel als sehr schwaches negatives Signal verwendet. Startwert:

```text
outside_top15_weight = -0.05
```

Dieses Signal darf nur aggregierte Tendenzen leicht korrigieren. Es darf niemals so behandelt werden, als hätte der Teilnehmer den Song aktiv auf einen konkreten letzten Platz gesetzt.

`OWN_ENTRY`, `NO_BALLOT` und `UNKNOWN` erhalten kein Geschmacksgewicht.

### 7.2 Auswahlmodell aus eigenen Einreichungen

Jede bekannte eigene Einreichung liefert ein positives Auswahlsignal mit Basisgewicht `1.00`.

Das Signal wird merkmalsweise gefiltert:

- vom Motto erzwungene Merkmale werden nicht generalisiert;
- extrem enge oder nur teilweise dokumentierte Mottos reduzieren die Zahl frei interpretierbarer Merkmale;
- erkennbare Spaß- oder Trollbeiträge werden gekennzeichnet und höchstens mit reduziertem Gewicht verwendet;
- wiederholte Künstler, Genres, Epochen, Sprachen oder strategische Muster erhöhen die Evidenz erst über mehrere unabhängige Shows hinweg.

Ein einzelner Beitrag begründet höchstens eine vorläufige Hypothese.

### 7.3 Zeitgewichtung

Ältere Signale bleiben relevant, werden aber moderat abgeschwächt. Startregel:

```text
recency_weight = max(0.60, 0.90 ^ contest_distance)
```

`contest_distance` ist die Zahl vollständig dazwischenliegender CSC-Ausgaben bis zum aktuellen Analysezeitpunkt. Der Mindestwert verhindert, dass ältere Daten bei kleinen Stichproben praktisch verschwinden.

Die Regel wird durch Backtests geprüft und bei Bedarf in einer neuen Protokollversion angepasst.

### 7.4 Schrumpfung zum neutralen Prior

Bei kleinen Stichproben werden extreme Profile zum neutralen Wert `0` geschrumpft. Für eine Dimension mit Signalen `s_i` und Gesamtgewichten `w_i` gilt zunächst:

```text
profile_score = sum(w_i * s_i) / (prior_strength + sum(abs(w_i)))
prior_strength = 3.0
```

Die Skala einer Profil-Dimension reicht von `-1.0` bis `+1.0`. Ein Wert nahe `0` kann Neutralität, Widerspruch oder schlicht zu wenig Evidenz bedeuten; deshalb muss immer zusätzlich die Konfidenz betrachtet werden.

### 7.5 Konfidenz

Die Konfidenz wird nicht allein aus der Zahl der Songs abgeleitet. Maßgeblich sind unabhängige Shows, Contestbreite, Widerspruchsfreiheit, Mottokontext und Datenqualität.

Startregeln:

- **GERING:** weniger als drei unabhängige Shows oder Evidenz fast ausschließlich aus einem einzelnen stark einschränkenden Motto;
- **MITTEL:** wiederkehrendes Muster über mindestens drei unabhängige Shows mit begrenzten Gegenbelegen;
- **HOCH:** stabiles Muster über mindestens acht unabhängige Shows und nach Möglichkeit mehrere Contests, mit erklärbaren oder wenigen Gegenbelegen.

Diese Grenzen sind notwendige Orientierung, keine automatische Hochstufung. Eine hohe Fallzahl aus nahezu identischen Kontexten kann weiterhin nur mittlere Konfidenz erlauben.

### 7.6 Widersprüche

Widersprechende Signale werden nicht gelöscht. Sie werden im Blatt `Belege` als Gegenbelege verknüpft und können zu folgenden Ergebnissen führen:

- geringere Konfidenz;
- Aufteilung einer zu groben Dimension;
- zeitabhängige Veränderung des Profils;
- Kennzeichnung eines vielseitigen oder bewusst wechselhaften Auswahlverhaltens.

### 7.7 Datenabdeckung pro Teilnehmer

Die Abdeckung wird separat vom Profilinhalt geführt:

- **KEINE:** keine verwertbaren Einreichungen und keine veröffentlichten Stimmzettel;
- **GERING:** nur einzelne Einreichungen oder Stimmzettel;
- **MITTEL:** mehrere unabhängige Shows, aber erkennbare Lücken oder nur ein Contest;
- **HOCH:** breite Historie aus Einreichungen und Stimmzetteln über mehrere Shows und möglichst mehrere Contests.

## 8. Tippspielanalyse

### 8.1 Zulässige Eingaben

Für die Zielshow werden verwendet:

- das aktuelle Teilnehmerfeld;
- die anonymen aktuellen Beiträge;
- ausschließlich historische beziehungsweise vor Beginn der Zielshow verfügbare Profildaten;
- gegebenenfalls öffentlich bekannte manuelle Fakten, sofern sie im Blatt `Belege` als solche gekennzeichnet sind.

Nicht verwendet werden:

- die tatsächliche Zuordnung der Zielshow;
- Stimmzettel oder Ergebnisdaten der Zielshow;
- spätere Chat- oder Forumshinweise, die beim realen Tippzeitpunkt noch nicht vorlagen;
- unbelegte persönliche Zuschreibungen.

### 8.2 Paarweises Start-Scoring

Für jedes Paar aus aktuellem Song `s` und möglichem Teilnehmer `u` wird ein relativer Score gebildet:

```text
pair_score(u, s) =
    0.55 * selection_similarity(u, s)
  + 0.25 * taste_similarity(u, s)
  + 0.10 * recurring_behavior(u, s)
  + 0.10 * motto_and_recency_context(u, s)
```

Bedeutung:

- `selection_similarity`: Ähnlichkeit zu bisherigen eigenen Einreichungen;
- `taste_similarity`: Ähnlichkeit zu wiederholt hoch bewerteten Songs;
- `recurring_behavior`: wiederkehrende Künstler-, Genre-, Epochen-, Sprach-, Bekanntheits- oder Versionsmuster;
- `motto_and_recency_context`: aktuelle Mottotauglichkeit, jüngere Tendenzen und bewusste Stilwechsel.

Die Gewichte sind Startwerte der Protokollversion 1.0 und werden nur auf Basis dokumentierter Backtests geändert.

### 8.3 Abdeckungsschrumpfung

Bei geringer Profilabdeckung wird der Paar-Score zum neutralen Feldwert geschrumpft. Ein kaum bekannter Teilnehmer darf nicht allein deshalb extreme positive oder negative Scores erhalten.

### 8.4 Globale eindeutige Zuordnung

Die Songs werden nicht unabhängig voneinander geraten. Nach dem paarweisen Scoring wird eine globale Maximum-Weight-Zuordnung berechnet, bei der grundsätzlich gilt:

- jeder Song höchstens einem Teilnehmer;
- jeder als Einreichender infrage kommende Teilnehmer höchstens einem Song.

Hierfür eignet sich beispielsweise der Hungarian Algorithm beziehungsweise ein äquivalentes Maximum-Weight-Matching.

Falls Teilnehmerzahl und Songzahl nicht übereinstimmen oder bekannte Nicht-Einreichende existieren, werden explizite Dummy-Knoten für `KEINE_ZUORDNUNG` verwendet. Die Eindeutigkeit darf nicht durch eine falsche Vollständigkeitsannahme erzwungen werden.

### 8.5 Ausgabe

Die Tippspielausgabe enthält mindestens:

- Song;
- primär zugeordneter Teilnehmer;
- Konfidenz `GERING`, `MITTEL` oder `HOCH`;
- zwei bis drei plausible Alternativen;
- wichtigste stützende Signale;
- wichtigste Gegenargumente;
- sichtbaren Hinweis bei geringer Datenabdeckung oder engem Score-Abstand.

Eine Prozentangabe wird erst verwendet, wenn Backtests eine belastbare Kalibrierung erlauben. Vorher sind die Scores nur relative Rangwerte.

## 9. Backtesting und Kalibrierung

### 9.1 Chronologisches Leave-one-show-out

Für jede geeignete abgeschlossene historische Show wird ein Testlauf simuliert:

1. Die tatsächlichen Einreichenden der Zielshow werden verborgen.
2. Sämtliche Stimmzettel und Ergebnisse der Zielshow werden aus den Eingabedaten entfernt.
3. Profile werden nur aus Daten gebildet, die vor der Zielshow verfügbar gewesen wären.
4. Das Tippspielverfahren erzeugt eine globale Zuordnung.
5. Die Vorhersage wird anschließend mit der tatsächlichen Zuordnung verglichen.

Ein nicht chronologisches Training mit späteren Shows ist nur als gesondert gekennzeichnete Analyse zulässig und zählt nicht als realistische Kalibrierung.

### 9.2 Kennzahlen

Mindestens gespeichert werden:

- exakte Trefferzahl und -quote;
- Top-3-Trefferzahl und -quote je Song;
- Mean Reciprocal Rank der tatsächlichen Person in der Paar-Rangliste;
- Ergebnis der globalen Zuordnung;
- Vergleich mit einer Zufallsbaseline;
- Vergleich mit einer einfachen Häufigkeits- beziehungsweise Wiederholungsbaseline;
- Abdeckung und Teilnehmerzahl der Testshow.

### 9.3 Anpassungsregeln

Gewichte, Schwellen oder Dimensionen werden nur geändert, wenn:

- mehrere Backtests ein wiederkehrendes Problem zeigen;
- die Änderung fachlich erklärbar ist;
- die neue Protokollversion dokumentiert wird;
- alte Läufe mit ihrer ursprünglichen Version interpretierbar bleiben.

Gewichte werden nicht nach einer einzelnen spektakulär falschen Zuordnung hektisch umgebaut. Ein Tippspiel ist keine Kernschmelze, auch wenn die Tabelle gelegentlich so aussehen mag.

## 10. Teilnehmerfeldbezogene Bewertung bereits ausgewählter Kandidaten

### 10.1 Voraussetzung: ausdrückliche Nutzeranforderung

Die in diesem Kapitel beschriebene Profilanalyse beginnt erst hinter dem Nutzer-Gate aus Abschnitt 3.7.

Sie darf nur ausgeführt werden, wenn:

- der Nutzer den oder die Kandidaten bereits selbst aus profilfrei recherchierten Vorschlägen ausgewählt oder anderweitig benannt hat;
- der Nutzer ausdrücklich eine ausführliche Bewertung unter Einbeziehung der Teilnehmerprofile verlangt.

Sie dient nicht dazu, allgemeine Kandidaten zu finden, Vorschlagslisten vorzusortieren oder vermeintlich feldschwache Songs vor der persönlichen Sichtung auszuscheiden.

### 10.2 Unveränderte harte und persönliche Kriterien

Die bestehende Kandidatenbewertung bleibt maßgeblich:

1. Motto-Eignung;
2. Zulässigkeit nach Ausschlussliste;
3. persönlicher Geschmacks- und Repräsentations-Fit;
4. allgemeine strategische Gewinnchance.

Der persönliche Repräsentations-Fit bleibt etwas wichtiger als die reine Gewinnchance.

Die Teilnehmerprofile ergänzen erst bei der ausführlichen Bewertung Punkt 4. Sie ersetzen weder die harte Prüfung noch den persönlichen Fit.

### 10.3 Kandidatenmerkmale

Jeder ausdrücklich zur ausführlichen Bewertung benannte Kandidat wird mit derselben Merkmalslogik wie historische Songs angereichert. Dadurch kann er gegen die Geschmacksmodelle des aktuellen Feldes verglichen werden.

Nicht zur Bewertung benannte Vorschläge werden nicht profilbezogen angereichert oder vorsortiert.

### 10.4 Voter-Fit

Für jeden aktuellen Teilnehmer außer dem Einreichenden wird ein relativer Kandidaten-Fit berechnet. Das Geschmacksmodell hat hierbei deutlich höheren Stellenwert als das Auswahlmodell.

Zu betrachten sind insbesondere:

- Wahrscheinlichkeit einer Top-15-Platzierung als relative Tendenz;
- mögliche hohe Platzierungen;
- Zahl plausibler Unterstützer;
- Konzentration auf wenige starke Unterstützer gegenüber breiter mittlerer Zustimmung;
- Polarisierungs- und Abschreckungsrisiken;
- bekannte Teilnehmercluster mit ähnlichen oder gegensätzlichen Präferenzen;
- Datenabdeckung des jeweils bewerteten Teilnehmers.

Die eigene Teilnahme wird aus der potenziellen Punktevergabe ausgeschlossen, da die eigene Einreichung nicht bewertet werden darf.

### 10.5 Feldabdeckung

Für unbekannte Teilnehmer wird ein neutraler Feld-Prior verwendet. Der Einfluss des Profilmodells auf die strategische Gesamtbewertung steigt nur mit der tatsächlichen Feldabdeckung.

Als optionale Startformel für eine kombinierte strategische Orientierung gilt:

```text
profile_influence = 0.40 * field_coverage
combined_strategy =
    (1 - profile_influence) * generic_strategy
  + profile_influence * field_fit
```

`field_coverage` liegt zwischen `0` und `1`. Selbst bei vollständiger Abdeckung ersetzt der teilnehmerbezogene Fit damit höchstens 40 Prozent der allgemeinen strategischen Einschätzung.

Die drei Werte `generic_strategy`, `field_fit` und `field_coverage` werden zusätzlich immer getrennt ausgegeben. Die kombinierte Zahl ist keine Gewinnwahrscheinlichkeit.

### 10.6 Pflichtausgabe pro Kandidat

Zusätzlich zu den Angaben aus `Workflow und Strategie` werden ausgegeben:

- **Teilnehmerfeld-Fit:** 1 bis 10;
- **Datenabdeckung des aktuellen Feldes:** Prozentwert oder qualitative Stufe;
- **erwartete Unterstützerstruktur:** breit, konzentriert oder polarisiert;
- **auffällige positive Teilnehmercluster:** nur bei hinreichender Evidenz;
- **auffällige Risiken und Gegencluster:** nur bei hinreichender Evidenz;
- **Unsicherheit:** GERING, MITTEL oder HOCH;
- **kurze Begründung**, welche Profile das Urteil tatsächlich verändert haben.

Ein Kandidat darf durch schwache oder spekulative Profile nicht künstlich aus einer ansonsten starken Empfehlung verdrängt werden.

## 11. Struktur des Google-Sheets `Teilnehmer`

Ein Tabellenblatt pro Teilnehmer wird bewusst nicht verwendet. Normalisierte Tabellen sind für Vergleiche, Filter, Aktualisierungen und maschinelle Verarbeitung wesentlich robuster.

### 11.1 `Hinweise`

Enthält Zweck, Erkenntnisklassen, Konfidenzregeln, das Nutzer-Gate, Verweise auf das Analyseprotokoll und kurze Pflegehinweise.

### 11.2 `Teilnehmer`

Eine aktuelle Übersichtszeile pro stabiler `participant_id`.

Spalten:

```text
participant_id
anzeigename
aliase
aktuell_im_feld
aktuelle_teilnahme_id
aktuelles_land
contests_n
erste_teilnahme
letzte_teilnahme
einreichungen_n
stimmzettel_n
bewertete_songentscheidungen_n
datenabdeckung
profil_status
profil_version
letzter_run_id
zuletzt_aktualisiert
notiz
```

### 11.3 `Profile`

Long-Format: eine Zeile pro Teilnehmer, Modell und Dimension.

Spalten:

```text
profile_id
participant_id
modell
dimensionsgruppe
dimension_key
auspraegung
score
konfidenz
datenpunkte_n
unabhaengige_shows_n
kurzbegruendung
status
run_id
zuletzt_aktualisiert
```

### 11.4 `Belege`

Konkrete stützende oder widersprechende Evidenz für Profilzeilen. Die historischen Rohdaten werden nicht vollständig dupliziert; gespeichert werden nur für die Profilinterpretation relevante Verweise und Beobachtungen.

Spalten:

```text
evidence_id
participant_id
profile_id
evidence_type
richtung
contest_id
show_id
entry_id
artist
title
rang
dimension_key
signalwert
gewicht
beobachtung
quellenreferenz
run_id
```

### 11.5 `Songmerkmale`

Wiederverwendbare Anreicherung eindeutiger Songs oder Versionen.

Spalten:

```text
song_key
artist
title
version
release_year
language
origin_scene
primary_genre
secondary_genres
duration_seconds
mainstream_bekanntheit
energy
hardness
harsh_vocals
complexity
melodicness
atmosphere
accessibility
hook_strength
novelty
mood_tags
faktenquellen
fakten_konfidenz
einschaetzungs_konfidenz
letzter_run_id
zuletzt_aktualisiert
```

### 11.6 `Analyseläufe`

Protokolliert jeden Profil-, Tippspiel-, Kandidaten- oder Backtestlauf.

Spalten:

```text
run_id
run_date
zweck
protocol_version
export_format
export_version
export_generated_at
export_scope
included_contests
included_shows
current_contest_id
current_show_id
export_filename
export_sha256
model
reasoning
prompt_version
result_summary
notizen
```

### 11.7 `Backtests`

Spalten:

```text
backtest_id
run_id
target_contest_id
target_show_id
training_cutoff
participants_n
entries_n
exact_matches
exact_accuracy
top3_hits
top3_accuracy
mean_reciprocal_rank
random_baseline
frequency_baseline
notizen
```

### 11.8 `Dimensionen`

Versionierbares Wörterbuch der verwendeten Profil- und Songdimensionen.

Spalten:

```text
dimension_key
modell
dimensionsgruppe
label
definition
skala_min
skala_max
negativer_pol
positiver_pol
evidence_rules
forced_by_motto_caveat
aktiv
```

## 12. Aktualisierungsverfahren

### 12.1 Erster Aufbau

1. Vollständigen Analyseexport erzeugen und übergeben.
2. Quality Gate einschließlich Prüfung stabiler Teilnehmeridentitäten durchführen.
3. `Analyseläufe` um einen Profil-Basislauf ergänzen.
4. Teilnehmerstamm und Abdeckungswerte aus stabilen IDs aufbauen.
5. Noch fehlende Songs recherchieren und in `Songmerkmale` ergänzen.
6. Geschmacks- und Auswahlprofile getrennt berechnen.
7. Profilbelege und Gegenbelege verknüpfen.
8. Geeignete historische Shows chronologisch backtesten.
9. Profilstatus und Konfidenzen nach den realen Ergebnissen setzen.

### 12.2 Spätere Aktualisierung

Bei neuen historischen Daten oder einer abgeschlossenen aktuellen Show:

1. neuen vollständigen Analyseexport erzeugen;
2. SHA-256 und `generatedAt` gegen den letzten Lauf prüfen;
3. geänderte Contests, Shows, Teilnahmen, Einreichungen und Stimmzettel bestimmen;
4. nur neue oder geänderte Songs anreichern;
5. neuen `run_id` anlegen;
6. betroffene Profile neu berechnen;
7. bisher aktive, ersetzte Profilzeilen auf `ÜBERHOLT` setzen statt sie spurlos zu löschen;
8. neue aktive Profilzeilen und Belege anlegen;
9. relevante Backtests erneut ausführen;
10. Änderungen, Konfidenzverschiebungen und verbleibende Lücken zusammenfassen.

### 12.3 Manuelle Fakten und Nutzerfeedback

Vom Nutzer mitgeteilte Informationen dürfen als `MANUELLE_FAKT` oder `USER_FEEDBACK` in `Belege` aufgenommen werden. Sie müssen:

- auf eine konkrete Aussage begrenzt sein;
- eine Quellenreferenz oder verständliche Notiz besitzen;
- von KI-Hypothesen unterscheidbar bleiben;
- bei späterem Widerspruch korrigierbar oder als überholt markierbar sein.

## 13. Ausgabeformate

### 13.1 Profilaktualisierung

Eine Aktualisierung meldet kompakt:

- verwendeten Export und `run_id`;
- neue oder geänderte Teilnehmerprofile;
- hoch- oder heruntergestufte Konfidenzen;
- wichtige neue Belege und Gegenbelege;
- ausgeschlossene oder unvollständige Daten;
- aktuelle Feldabdeckung.

### 13.2 Tippspiel

Empfohlenes Tabellenformat:

```text
Song | Primärtipp | Konfidenz | Alternativen | stärkste Indizien | Gegenargumente
```

Danach folgen:

- besonders eindeutige Zuordnungen;
- engste und riskanteste Entscheidungen;
- Teilnehmer mit zu geringer Datenbasis;
- verwendete Protokoll- und Profilversion.

### 13.3 Ausführliche Kandidatenbewertung nach Nutzer-Gate

Dieses Format wird nur verwendet, wenn der Nutzer ausdrücklich eine ausführliche Bewertung bereits ausgewählter Kandidaten verlangt.

Zusätzlich zur bestehenden Kandidatendarstellung:

```text
Interpret – Titel
- persönlicher Repräsentations-Fit
- allgemeine Gewinnchance
- Teilnehmerfeld-Fit
- Feldabdeckung
- Unterstützerstruktur
- relevante Profilcluster
- Unsicherheit und Risiken
- Gesamturteil
```

Das Ergebnis darf keine weiteren Kandidaten aus Teilnehmerprofilen erzeugen oder die vorausgegangene allgemeine Recherche rückwirkend profilbasiert umsortieren.

## 14. Wiederverwendbare Arbeitsaufträge

### 14.1 Profile aktualisieren

```text
Aktualisiere die Teilnehmerprofile nach docs/external-ai-analysis.md auf Basis des angehängten vollständigen Analyseexports. Verwende analysis.json als kanonische Quelle, protokolliere einen neuen Analyselauf im Google-Sheet Teilnehmer, ergänze nur fehlende Songmerkmale und ersetze aktive Profilstände versioniert. Gib anschließend nur die wichtigsten Profiländerungen, die aktuelle Feldabdeckung und verbleibende Datenlücken aus.
```

### 14.2 Tippspiel analysieren

```text
Erstelle für die aktuelle Zielshow eine Tippspielanalyse nach docs/external-ai-analysis.md. Verwende ausschließlich Informationen, die vor Auflösung der Zielshow verfügbar sind, berechne eine globale eindeutige Song-/Teilnehmer-Zuordnung und gib pro Song Primärtipp, Konfidenz, Alternativen, Indizien und Gegenargumente aus. Führe vorher das Quality Gate durch und nenne die verwendete Profil- und Protokollversion.
```

### 14.3 Bereits ausgewählte Kandidaten ausführlich bewerten

```text
Der Nutzer hat die folgenden Kandidaten bereits selbst ausgewählt und verlangt ausdrücklich eine ausführliche Bewertung. Bewerte ausschließlich diese Kandidaten nach Workflow und Strategie sowie zusätzlich nach docs/external-ai-analysis.md gegen das aktuelle Teilnehmerfeld. Verwende Teilnehmerprofile weder zur Erzeugung weiterer Vorschläge noch zur Erweiterung oder Vorsortierung der allgemeinen Kandidatenrecherche. Motto und Ausschlussliste bleiben harte Bedingungen. Weise allgemeinen strategischen Score, Teilnehmerfeld-Fit, Feldabdeckung, Unterstützerstruktur und Unsicherheit getrennt aus; niedrige Profilabdeckung darf das allgemeine Urteil nur entsprechend schwach beeinflussen.
```

## 15. Versionierung

Änderungen an folgenden Punkten erhöhen mindestens die Minor-Version des Protokolls:

- Merkmalsdimensionen;
- Signalgewichtung;
- Zeitgewichtung oder Prior-Stärke;
- Konfidenzschwellen;
- Paar-Scoring des Tippspiels;
- Matching- oder Backtestverfahren;
- kombinierte Kandidatenbewertung;
- Nutzer-Gate oder zulässiger Einsatzbereich der Teilnehmerprofile;
- Tabellenstruktur mit semantischer Auswirkung.

Inkompatible Änderungen am Bedeutungsgehalt bestehender Felder erhöhen die Major-Version.

Alte Analyseläufe behalten ihre ursprüngliche `protocol_version`. Sie werden nicht stillschweigend nach neuen Regeln umgedeutet.

## 16. Bekannte Grenzen

Auch bei vollständigen Daten bleibt die Analyse probabilistisch und kontextabhängig:

- Mottos erzwingen Auswahlentscheidungen.
- Historische Mottoregeln können unvollständig dokumentiert sein.
- Teilnehmer können bewusst atypisch oder taktisch einreichen.
- Geschmack verändert sich über Jahre.
- Stimmzettel zeigen nur eine Top 15 und keine vollständige Rangfolge.
- Die Konkurrenz einer konkreten Show beeinflusst, welche Songs in die Top 15 gelangen.
- Genre- und Wirkungseinschätzungen enthalten subjektive Modellanteile.
- Kleine Stichproben erlauben keine belastbaren individuellen Aussagen.
- Eine globale Zuordnung kann mehrere lokal plausible Paarungen zugunsten einer insgesamt konsistenten Lösung verschieben.
- Teilnehmerprofile werden bewusst erst nach der persönlichen Kandidatenauswahl eingesetzt und können daher keine allgemeine Recherche optimieren; genau das ist die gewollte Grenze.

Das Verfahren soll bessere, nachvollziehbare Indizien liefern. Es soll nicht so tun, als hätten historische Stimmzettel heimlich die Persönlichkeit der Teilnehmer als CSV exportiert.
