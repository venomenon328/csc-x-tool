# Arbeitsablauf: anonyme Songliste möglichen Einreichenden zuordnen

**Workflow-Version:** 1.1  
**Stand:** 2026-09-01

## 1. Zweck

Dieser Arbeitsablauf erzeugt eine evidenzbasierte Tippspielanalyse für die anonymen Songs einer laufenden Mottoshow.

Eine vollständige Zuordnung um jeden Preis ist ausdrücklich nicht das Ziel.

Zulässig ist eine vorsichtige Spekulation mit geringer Konfidenz, wenn mindestens ein konkretes historisches oder manuelles Indiz existiert. Nicht zulässig sind:

- freies Raten ohne Datenbasis;
- Zuordnung nur aufgrund eines Anzeigenamens, Landes oder vermuteter Demografie;
- Restelimination als scheinbar musikalisch begründeter Tipp;
- erzwungenes vollständiges Matching trotz fehlender Evidenz.

## 2. Verbindliche Quellen

Vor Beginn werden gelesen:

1. [`../external-ai-analysis.md`](../external-ai-analysis.md)
2. [`../historical-contests-ballots-analysis.md`](../historical-contests-ballots-analysis.md)
3. [`README.md`](README.md), insbesondere Übergabe- und Feldregel
4. Google-Sheet `Teilnehmer`
5. Google Doc `Mottoshows`
6. bei Bedarf Google Doc `Punkteregeln`
7. neuester freigegebener Analyseexport mit `analysis.json` als kanonischer Quelle
8. belastbare aktuelle Webquellen für die Merkmale der anonymen Songs

## 3. Benötigte Eingaben

Der Auftrag muss enthalten:

- aktiver Wettbewerb;
- Zielshow mit Nummer und Titel;
- vollständige anonyme Songliste mit Interpret, Titel und möglichst URL;
- optional ausdrücklich als sicher vorgegebene Zuordnungen;
- optional manuelle Hinweise, jeweils als Fakt, Beobachtung oder Vermutung gekennzeichnet;
- aktuellsten Analyseexport oder einen eindeutig bezeichneten freigegebenen persistenten Exportstand.

Eine im Analyseexport bereits enthaltene tatsächliche Zuordnung der Zielshow gilt niemals allein dadurch als sichere Zuordnung. Dies gilt ausdrücklich auch für die eigene Einreichung des Nutzers.

## 4. Eingangs- und Qualitätsprüfung

Vor jeder Zuordnung werden geprüft:

- Exportformat, `formatVersion`, `generatedAt` und Scope;
- vollständige Lesbarkeit von `analysis.json` oder dokumentierter CSV-Fallback;
- aktiver Wettbewerb;
- aktive Contest-Teilnahmen des aktiven Wettbewerbs;
- Vollständigkeit der anonymen Songliste;
- Verhältnis von Song- und Teilnehmerzahl;
- bekannte Nicht-Einreichende oder zusätzliche Songs;
- ausdrücklich vorgegebene sichere Zuordnungen;
- stabile Teilnehmeridentitäten;
- vollständige historische Shows;
- Profile und deren Aktualität;
- Teilnehmer ohne jegliche verwertbare Datenbasis.

Ein unvollständiges Feld wird nicht durch Annahmen künstlich vervollständigt.

## 5. Zulässige mögliche Einreichende

Die Menge möglicher Einreichender besteht ausschließlich aus aktiven Contest-Teilnahmen des aktiven Wettbewerbs:

```text
eligible_submitters = active_participations(active_contest)
```

Dies schließt den Nutzer selbst vollständig und gleichberechtigt ein. Eine technisch bereits bekannte eigene Einreichung entfernt ihn nicht aus der Kandidatenmenge.

Nur Zuordnungen, die im Auftrag ausdrücklich als sicher vorgegeben wurden, werden fixiert und anschließend aus Song- und Teilnehmermenge entfernt. Aus der Zielshow selbst gelesene Ist-Zuordnungen dürfen dafür nicht verwendet werden.

Historische Nichtteilnehmer sind keine Matching-Kandidaten. Ihre Profile dürfen nicht als Ersatzkandidaten, Cluster oder Feld-Prior in die aktuelle Zuordnung eingehen.

Historische Daten einer heute aktiven Person bleiben als Trainingsdaten ihres Auswahl- und Geschmacksmodells verwendbar.

## 6. Stichtag und Leakage-Schutz

Verwendet werden ausschließlich Informationen, die zum realen Tippzeitpunkt verfügbar sein durften.

Vor jeder Analyse werden sämtliche tatsächlichen Einreichendenzuordnungen der Zielshow logisch maskiert. Das gilt ohne Ausnahme für alle aktuellen Teilnehmer und ausdrücklich auch für die eigene Einreichung des Nutzers. Entsprechende Felder und daraus ableitbare aktuelle Zuordnungsinformationen dürfen weder für Scoring noch Matching, Profilbildung, Alternativen oder Plausibilitätsprüfung verwendet werden.

Erst nach dieser Maskierung dürfen Zuordnungen wieder als harte Constraints eingebracht werden, wenn der Nutzer sie im aktuellen Auftrag ausdrücklich als sicher vorgibt. Eine im Export vorhandene Ist-Zuordnung ist selbst dann kein zulässiger Beleg, wenn sie dem Modell technisch sichtbar ist oder anderweitig bereits bekannt erscheint.

Auszuschließen sind insbesondere:

- tatsächliche Einreichendenzuordnungen der Zielshow;
- Stimmzettel und Ergebnisdaten der Zielshow;
- spätere Forumshinweise oder Auflösungen;
- Profile, die bereits Daten der Zielshow enthalten;
- persönliche Kandidatenlisten des Nutzers als Beweis für fremde Einreichungen;
- nachträgliche Restelimination aus einer bereits bekannten Gesamtlösung.

Ist der übergebene Export zeitlich bereits nach Auflösung der Zielshow entstanden, müssen alle potenziell geleakten Felder der Zielshow logisch ausgeblendet werden. Ist dies nicht zuverlässig möglich, wird der Lauf blockiert.

Im Laufprotokoll wird ausdrücklich vermerkt, dass die Zielshow-Zuordnungen einschließlich einer gegebenenfalls vorhandenen eigenen Einreichung maskiert wurden.

## 7. Gezielte Profilaktualisierung

Falls erforderliche Profile fehlen oder der Export neuer als der letzte Profilstand ist, werden nur die aktuell möglichen Einreichenden mit verwertbarer Historie gezielt aktualisiert.

Getrennt bleiben:

1. **Auswahlmodell:** Eigenschaften der eigenen bisherigen Einreichungen;
2. **Geschmacksmodell:** Eigenschaften wiederholt hoch bewerteter Songs.

Für die Tippspielzuordnung ist das Auswahlmodell wichtiger.

Verbindliche Regeln:

- vom historischen Motto erzwungene Eigenschaften nicht frei verallgemeinern;
- wiederkehrende Künstler, Genres, Epochen, Sprachen, Versionen, Bekanntheitsgrade, Härtegrade, Gimmickmuster und strategische Auswahlweisen erst über mehrere unabhängige Shows stärker gewichten;
- einzelne atypische, taktische oder offensichtliche Spaßbeiträge nicht zum umfassenden Persönlichkeitsmodell erklären;
- `OUTSIDE_TOP_15` nur sehr schwach negativ verwenden;
- `OWN_ENTRY`, `NO_BALLOT` und `UNKNOWN` neutral behandeln;
- kleine Stichproben stark zum neutralen Prior schrumpfen;
- keine privaten, demografischen, politischen oder psychologischen Eigenschaften vermuten.

Neue Songmerkmale, Profile und Belege werden versioniert im Sheet `Teilnehmer` gespeichert. Der Lauf wird in `Analyseläufe` protokolliert.

## 8. Merkmalsprofil der Zielshow

Für jeden anonymen Song werden soweit relevant bestimmt:

- primäre und sekundäre Genres;
- Veröffentlichungsjahr und Epoche;
- Sprache;
- Bekanntheitsgrad;
- Energie und Härte;
- harscher oder klarer Gesang;
- Melodik und Atmosphäre;
- Komplexität beziehungsweise Sperrigkeit;
- Zugänglichkeit und Hook-Stärke;
- Nostalgiepotenzial;
- Humor-, Novelty- oder Gimmickcharakter;
- Live-, Cover-, Remix- oder sonstige besondere Version;
- Merkmale, die durch das aktuelle Motto bereits erzwungen sind.

Überprüfbare Fakten erhalten Quellen. Auditive oder strategische Einschätzungen werden als Modellurteil gekennzeichnet.

## 9. Paarweises Scoring

Für jedes fachlich überhaupt vertretbare Paar aus Teilnehmer `u` und Song `s` wird ein relativer Score gebildet:

```text
pair_score(u, s) =
    0.55 * selection_similarity(u, s)
  + 0.25 * taste_similarity(u, s)
  + 0.10 * recurring_behavior(u, s)
  + 0.10 * motto_and_recency_context(u, s)
```

Zusätzlich berücksichtigt werden:

- Datenabdeckung;
- Zahl unabhängiger historischer Shows;
- Konsistenz und Gegenbelege;
- Abstand zu Alternativen;
- jüngere gegenüber älteren Signalen;
- bewusste Stilwechsel;
- durch das aktuelle Motto unbrauchbar gewordene Unterscheidungsmerkmale.

Die Scores sind relative Indizien und keine kalibrierten Wahrscheinlichkeiten.

### 9.1 Teilnehmer mit geringer Datenbasis

Eine `LOW`-Zuordnung ist nur erlaubt, wenn wenigstens ein konkretes benennbares Indiz vorliegt.

### 9.2 Teilnehmer ohne Datenbasis

Ein Teilnehmer ohne verwertbare historische Einreichung, veröffentlichten Stimmzettel oder ausdrücklich bereitgestellten manuellen Fakt erhält keinen Songscore. Er bleibt unter `nicht profilierbare Teilnehmer`.

Dies gilt auch dann, wenn nach allen anderen Paarungen nur noch dieser Teilnehmer und ein Song übrig wären.

## 10. Globale Zuordnung

Nach dem Paar-Scoring wird eine globale eindeutige Zuordnung berechnet, etwa mit Maximum-Weight-Matching.

Dabei gilt:

- jeder Song höchstens einem Teilnehmer;
- jeder Teilnehmer höchstens einem Song;
- ausdrücklich vorgegebene sichere Zuordnungen bleiben fixiert;
- Dummy-Knoten beziehungsweise `UNAUFGELÖST` sind zulässig und erwünscht;
- nur Paarungen oberhalb einer fachlich vertretbaren Evidenzschwelle gelangen in die Kernzuordnung;
- die globale Optimierung darf keinen lokal schwachen Tipp als belastbare Erkenntnis tarnen;
- mehrere ähnlich plausible Gesamtlösungen werden als Alternativen ausgewiesen;
- Teilnehmer ohne Datenbasis werden nicht durch Restelimination automatisch zugeordnet.

Die globale Eindeutigkeit ist eine Konsistenzbedingung, kein Zwang zur Vollständigkeit.

## 11. Konfidenz

- **HIGH:** wiederholte, spezifische und weitgehend konsistente Signale mit deutlichem Abstand zu Alternativen;
- **MEDIUM:** mehrere konkrete Signale, aber ernsthafte Alternativen oder relevante Gegenbelege;
- **LOW:** mindestens ein konkretes Indiz, jedoch kleine, einseitige oder widersprüchliche Datenbasis;
- **keine Zuordnung:** keine fachlich vertretbare Evidenz.

Exakte Prozentwerte werden erst nach dokumentierter Kalibrierung durch Backtests verwendet.

## 12. Pflichtausgabe

### 12.1 Daten- und Abdeckungsbericht

- verwendeter Export;
- Protokoll-, Profil- und Workflow-Version;
- `run_id`;
- aktiver Wettbewerb und Zielshow;
- Zahl anonymer Songs;
- Zahl aktiver möglicher Einreichender;
- Zahl mit hoher, mittlerer, geringer und keiner verwertbaren Datenbasis;
- ausdrücklich vorgegebene sichere Zuordnungen;
- ausgeschlossene historische Nichtteilnehmer;
- relevante Quality-Gate- und Leakage-Einschränkungen einschließlich der bestätigten Maskierung aller Zielshow-Zuordnungen.

### 12.2 Evidenzbasierte Kernzuordnung

Empfohlenes Format:

```text
Song | Primärtipp | Konfidenz | plausible Alternativen | stärkste Indizien | Gegenargumente | Datenbasis
```

Für jeden Primärtipp werden genannt:

- besonders relevante historische Einreichungen;
- ergänzend passende Bewertungsmuster;
- nicht als Beweis gezählte, vom Motto erzwungene Merkmale;
- globale Konflikte mit anderen Songs und Teilnehmern.

### 12.3 Unaufgelöste Songs

Alle Songs ohne ausreichend begründeten Primärtipp werden separat aufgeführt. Pro Song wird erklärt, ob:

- mehrere Teilnehmer nahezu gleich plausibel sind;
- nur extrem schwache Indizien vorliegen;
- die relevanten möglichen Teilnehmer keine Daten besitzen;
- der Song außerhalb der vorhandenen Profile liegt;
- das Motto zentrale Unterscheidungsmerkmale neutralisiert.

### 12.4 Nicht profilierbare Teilnehmer

Aktuelle Teilnehmer ohne verwertbare Datenbasis werden separat genannt und nicht zugeordnet, sofern kein konkreter manueller Fakt oder keine ausdrücklich vorgegebene sichere Zuordnung vorliegt.

### 12.5 Globale Konflikte

Zusätzlich auszugeben sind:

- stärkste Zuordnungen;
- knappste Entscheidungen;
- Teilnehmer, die für mehrere Songs plausibel wären;
- Songs, deren Primärtipp durch die globale Eindeutigkeit verschoben wurde;
- ähnlich plausible alternative Gesamtmatchings.

### 12.6 Abschluss

Der Abschluss nennt:

- Zahl evidenzbasiert zugeordneter Songs;
- Zahl schwach spekulativer Zuordnungen;
- Zahl bewusst offengelassener Songs;
- Gesamtbelastbarkeit der Analyse;
- im Sheet vorgenommene Aktualisierungen.

Keine künstlich vollständige Tippspielliste ausgeben, wenn dafür geraten werden müsste.

## 13. Persistierung

Bei einem offiziellen Lauf werden aktualisiert:

- `Songmerkmale` für neue oder geänderte Ziel- und historische Songs;
- `Profile` für betroffene aktuell aktive Teilnehmer;
- `Belege` einschließlich Gegenbelegen;
- `Analyseläufe` mit Exportstand, Stichtag, Protokoll- und Workflow-Version sowie dokumentierter Zielshow-Maskierung;
- bei späteren Backtests zusätzlich `Backtests`.

Tipps werden nicht automatisch in das CSC X Tool zurückgeschrieben. Eine Übernahme erfolgt nur nach ausdrücklicher Nutzeranweisung.