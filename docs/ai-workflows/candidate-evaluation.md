# Arbeitsablauf: ausgewählte Kandidaten umfassend bewerten

**Workflow-Version:** 1.0  
**Stand:** 2026-09-01

## 1. Zweck und Aktivierung

Dieser Arbeitsablauf gilt ausschließlich für eine **ausdrücklich angeforderte ausführliche Bewertung von Kandidaten, die der Nutzer bereits selbst ausgewählt hat**.

Er ist nicht Teil der allgemeinen Kandidatenrecherche.

Verboten sind deshalb:

- weitere Kandidaten erzeugen;
- Alternativsongs recherchieren;
- die Kandidatenliste erweitern;
- frühere profilfreie Vorschlagslisten anhand von Teilnehmerprofilen nachträglich umsortieren;
- Teilnehmerprofile verwenden, um den Suchraum vor dem Nutzer-Gate einzuschränken.

## 2. Verbindliche Quellen und Rangfolge

Vor Beginn werden die aktuellen vollständigen Fassungen folgender Quellen gelesen:

1. [`../external-ai-analysis.md`](../external-ai-analysis.md)
2. [`../historical-contests-ballots-analysis.md`](../historical-contests-ballots-analysis.md)
3. [`README.md`](README.md), insbesondere Übergabe- und Feldregel
4. Google Doc `Workflow und Strategie`
5. Google Doc `Mottoshows`
6. `Ausschlussliste.md`
7. Google Doc `Punkteregeln`
8. Google-Sheet `Teilnehmer`
9. neuester freigegebener Analyseexport, mit `analysis.json` als kanonischer Quelle
10. belastbare aktuelle Webquellen für überprüfbare Song-, Versions- und Mottodaten

Motto und Ausschlussliste sind harte Bedingungen. Der persönliche Geschmacks- und Repräsentations-Fit bleibt etwas wichtiger als die reine strategische Gewinnchance. Teilnehmerprofile ergänzen nur die strategische Bewertung.

## 3. Benötigte Eingaben

Der Auftrag muss enthalten:

- aktiver Wettbewerb;
- Zielshow mit Nummer und Titel;
- bereits ausgewählte Kandidaten mit Interpret, Titel und möglichst genauer URL beziehungsweise Version;
- optional Nutzerfeedback, bisherige Reihenfolge und besondere Bedenken;
- aktuellsten Analyseexport oder einen eindeutig bezeichneten freigegebenen persistenten Exportstand.

Fehlende optionale Angaben werden nicht durch erfundene Nutzerpräferenzen ersetzt.

## 4. Eingangs- und Qualitätsprüfung

Vor der Kandidatenbewertung werden mindestens geprüft:

- Exportformat, `formatVersion`, `generatedAt` und Scope;
- vollständige Lesbarkeit von `analysis.json` oder dokumentierter CSV-Fallback;
- eindeutige Teilnehmeridentitäten;
- aktiver Wettbewerb;
- aktives Teilnehmerfeld;
- eigene aktive Teilnahme;
- vollständig verwendbare historische Shows;
- Aktualität des Profilstands im Sheet gegenüber dem Export;
- fehlende, vorläufige, belastbare und veraltete Profile;
- widersprüchliche Zuordnungen oder Ranglisten.

Harte Blocker werden nach dem allgemeinen Analyseprotokoll behandelt. Ein blockierter Teilbereich darf nicht durch plausible klingende Annahmen ersetzt werden.

## 5. Verbindlicher Feldfilter

### 5.1 Ermittlung des aktiven Feldes

Das relevante Feld besteht ausschließlich aus Teilnehmern, die im **aktiven Wettbewerb** eine **aktive Contest-Teilnahme** besitzen.

Für die Bewertung der eigenen Kandidaten gilt:

```text
eligible_voters = active_participations(active_contest) - own_participation
```

### 5.2 Vollständiger Ausschluss historischer Nichtteilnehmer

Teilnehmer, die nur in früheren Wettbewerben aktiv waren, werden vollständig aus der aktuellen feldbezogenen Bewertung ausgeschlossen. Sie dürfen nicht einfließen in:

- Teilnehmerfeld-Fit;
- erwartete Unterstützerzahl;
- positive oder negative Cluster;
- Feldmittelwerte;
- Polarisierungsbewertung;
- Abdeckungsquote;
- kombinierte strategische Orientierung;
- erzählerische Aussagen über den aktuellen Feldgeschmack.

Historische Daten einer heute aktiven Person bleiben als Trainingsdaten **dieser Person** zulässig.

### 5.3 Fehlender Aktivstatus

Falls der Analyseexport den Aktivstatus der Contest-Teilnahme nicht enthält, wird das aktuelle Feld aus dem Sheet `Teilnehmer` und der im Export bekannten aktuellen Teilnahme gebildet. Die Einschränkung wird im Abdeckungsbericht genannt. Ein Stammdatensatz mit `participant.active = true` allein beweist keine aktive Teilnahme am aktuellen Wettbewerb.

## 6. Gezielte Profilaktualisierung

Nur Profile der aktuell möglichen Punktegeber müssen für diesen Lauf vollständig ausgewertet werden. Profile historischer Nichtteilnehmer dürfen vorhanden bleiben, werden aber nicht neu angereichert, sofern sie nicht aus einem anderen Grund benötigt werden.

Bei fehlendem oder veraltetem Profilstand:

1. historische Einreichungen und veröffentlichte Stimmzettel der betroffenen aktuell aktiven Teilnehmer bestimmen;
2. nur benötigte fehlende Songmerkmale ergänzen;
3. Geschmacks- und Auswahlmodell getrennt aktualisieren;
4. Belege und Gegenbelege verknüpfen;
5. ersetzte Profilzeilen als `ÜBERHOLT` markieren;
6. neue aktive Profilzeilen anlegen;
7. neuen Lauf in `Analyseläufe` protokollieren.

Für die Kandidatenbewertung ist das **Geschmacksmodell** deutlich wichtiger als das Auswahlmodell.

Verbindliche Semantik:

- Rang 1 bis 15 nach den offiziellen CSC-Punkten gewichten;
- `OUTSIDE_TOP_15` nur sehr schwach negativ verwenden;
- `OWN_ENTRY`, `NO_BALLOT` und `UNKNOWN` sind neutral;
- keine fiktiven Ränge ab 16;
- vom historischen Motto erzwungene Merkmale nicht als freie Präferenz behandeln;
- BOTB-Interpretwahlen nur als begrenzte Auswahlmodell-Ereignisse nach `external-ai-analysis.md` behandeln; sie ändern weder das aktuelle Feld noch werden sie als Geschmacks- oder Songsignal überinterpretiert;
- kleine Stichproben zum neutralen Prior schrumpfen;
- keine demografischen, politischen, psychologischen oder privaten Zuschreibungen.

## 7. Kandidatenprüfung

Jeder Kandidat wird zunächst unabhängig von den Teilnehmerprofilen geprüft auf:

- exakte Motto-Eignung;
- Interpret und Titel;
- konkrete Aufnahme beziehungsweise Version;
- Ausschlusslistenstatus einschließlich Schreib- und Titelvarianten;
- relevante Live-, Cover-, Remix-, Edit- oder Originalkonstellationen;
- belastbare Veröffentlichungs-, Sprach-, ESC- oder Genreangaben;
- mögliche Klärungsfälle.

Ein ausgeschlossener oder das Motto nicht erfüllender Song wird nicht durch guten Feld-Fit rehabilitiert.

## 8. Allgemeine Bewertung ohne Teilnehmerprofile

Jeder zulässige Kandidat wird zunächst nach `Workflow und Strategie` unabhängig vom konkreten Feld bewertet. Mindestens zu betrachten sind:

- persönlicher Geschmacks- und Repräsentations-Fit;
- unmittelbare Wirkung;
- Eingängigkeit;
- Hook, Refrain oder zentrales Motiv;
- emotionale beziehungsweise atmosphärische Wirkung;
- Eigenständigkeit und Wiedererkennungswert;
- Wirkung innerhalb eines Feldes von ungefähr 30 Songs;
- Zugänglichkeit außerhalb der Kernzielgruppe;
- Dramaturgie und Dynamik;
- Produktion und Länge;
- Nostalgiepotenzial;
- Abschreckungs- und Polarisierungsrisiken.

Mainstreamnähe und Bekanntheit sind keine automatischen Vorteile. Ein charaktervoller Discovery Pick kann strategisch stärker sein als ein bekannter Durchschnittssong.

## 9. Teilnehmerfeld-Fit

### 9.1 Kandidatenmerkmale

Jeder Kandidat wird mit derselben Merkmalslogik wie historische Songs angereichert. Überprüfbare Fakten erhalten Quellen; auditive und strategische Bewertungen werden als Modellurteil gekennzeichnet.

### 9.2 Vergleich mit möglichen Punktegebern

Der Kandidat wird ausschließlich mit den belastbaren Geschmacksprofilen aus `eligible_voters` verglichen.

Qualitativ zu bestimmen sind:

- relative Top-15-Anschlussfähigkeit;
- Potenzial für sehr hohe Einzelwertungen;
- Zahl plausibler Unterstützer;
- breite, konzentrierte oder polarisierte Unterstützerstruktur;
- nachvollziehbare positive Cluster;
- nachvollziehbare negative Cluster;
- Risiken durch Härte, harschen Gesang, Sperrigkeit, Länge, Genre, Stimme oder andere Merkmale;
- tatsächliche Datenabdeckung des aktiven Feldes.

Einzelne Teilnehmer werden nur bei konkreter, hinreichender Evidenz genannt. Schwächere Signale werden zu fachlich nachvollziehbaren Clustern zusammengefasst.

Teilnehmer ohne verwertbares Profil erhalten einen neutralen Feld-Prior. Für sie wird weder Zustimmung noch Ablehnung erfunden.

### 9.3 Feldabdeckung

Der Nenner der Feldabdeckung ist ausschließlich die Zahl der aktuell möglichen Punktegeber:

```text
field_coverage = profiled_eligible_voters / eligible_voters
```

Historische Nichtteilnehmer und die eigene Teilnahme gehören nicht in den Nenner.

### 9.4 Begrenzter Einfluss

Als optionale kombinierte Orientierung gilt:

```text
profile_influence = 0.40 * field_coverage
combined_strategy =
    (1 - profile_influence) * generic_strategy
  + profile_influence * field_fit
```

Immer getrennt auszugeben sind:

- allgemeine strategische Gewinnchance;
- Teilnehmerfeld-Fit;
- Feldabdeckung.

Die kombinierte Orientierung ist keine Sieg- oder Punktwahrscheinlichkeit.

## 10. Pflichtausgabe

### 10.1 Abdeckungsbericht

Zu Beginn:

- verwendeter Export und Profilstand;
- aktiver Wettbewerb und Zielshow;
- Zahl aktiver Teilnehmer;
- Zahl möglicher Punktegeber nach Ausschluss der eigenen Teilnahme;
- Zahl mit belastbarem, vorläufigem und fehlendem Geschmacksprofil;
- Feldabdeckung;
- ausgeschlossene historische Nichtteilnehmer;
- relevante Daten- und Quality-Gate-Einschränkungen.

### 10.2 Je Kandidat

- Interpret – Titel – konkrete Version;
- Motto-Eignung: `EINDEUTIG`, `GRENZFALL` oder `NICHT ERFÜLLT`;
- Ausschlussprüfung: `ZULÄSSIG`, `KLÄRUNGSFALL` oder `AUSGESCHLOSSEN`;
- Genre und relevante musikalische Merkmale;
- persönlicher Geschmacks-/Repräsentations-Fit: 1–10;
- allgemeine Gewinnchance ohne Teilnehmerprofile: 1–10;
- Teilnehmerfeld-Fit: 1–10 oder `NICHT BELASTBAR`;
- Feldabdeckung;
- Unterstützerstruktur: `BREIT`, `KONZENTRIERT`, `POLARISIERT` oder `NICHT BELASTBAR`;
- stärkste positive Signale;
- stärkste Risiken und Gegenargumente;
- Unsicherheit: `GERING`, `MITTEL` oder `HOCH`;
- ausführliches Gesamturteil.

### 10.3 Vergleich und Empfehlung

Abschließend:

1. direkte Rangliste aller zulässigen Kandidaten;
2. wichtigste Trade-offs;
3. eindeutige Gesamtempfehlung;
4. Kandidat mit höchstem Upside;
5. Kandidat mit größter strategischer Sicherheit;
6. Kandidat mit bestem persönlichen Repräsentations-Fit;
7. konkrete Angabe, ob und wie die Profile das generische Urteil verändert haben;
8. verwendete `run_id` und im Sheet vorgenommene Aktualisierungen.

Gesicherte Fakten, Modellschlüsse, subjektiver Fit und strategische Einschätzung bleiben getrennt.

## 11. Persistierung

Bei einem offiziellen Lauf werden ergänzt beziehungsweise aktualisiert:

- `Songmerkmale` nur für tatsächlich benötigte neue oder geänderte Songs;
- `Profile` für betroffene aktive Teilnehmer;
- `Belege` einschließlich Gegenbelegen;
- `Analyseläufe` mit Exportstand, Protokoll- und Workflow-Version;
- `Teilnehmer` mit aktuellem Profilstatus und Feldzugehörigkeit.

Die Kandidatenbewertung selbst wird nicht automatisch in das CSC X Tool zurückgeschrieben.
