# Erweiterungsspezifikation – Gesamtwertung und Conteststatistiken

**Version:** 1.1
**Stand:** 08.10.2026
**Status:** fachlich freigegeben; S1 implementiert und technisch im Paket-PR geprüft, manuelle Gesamtabnahme nach vollständiger Roadmap #14 auf `main`; S2/S3 bleiben nachgelagert

## 1. Ziel, Geltung und Abgrenzung

Der Benutzer hat am 07.10.2026 die besprochene Gesamtwertung und die nachfolgend beschriebenen Conteststatistiken als Spezifikation freigegeben. Diese Erweiterung liefert einen Auswertungsbereich mit **Gesamtwertung**, **Teilnehmerprofilen**, **Punktebeziehungen** und **Rekorden**. Sie gilt sowohl für die aktuelle als auch für historische CSC-Ausgaben, jeweils getrennt nach ausgewähltem Contest.

Die [Produktspezifikation](specification.md), das [Stimmzettel- und historische Contestmodell](historical-contests-ballots-analysis.md), die [Architektur](architecture.md) und die [Quellenabgrenzung](reference/README.md) bleiben maßgeblich. Dieses Dokument erweitert gezielt die bisherige Beschränkung auf Ergebnisableitungen für die eigene Einreichung: Showplatzierungen, Sieger einschließlich geteilter Siege, Gesamtwertungspunkte, Contestplatzierungen und die genannten Statistiken werden künftig aus vollständigen veröffentlichten Stimmzetteln berechnet. Die bisherige Vertagung von Statistiken und der Ausschluss dieser Berechnungen sind für diesen Umfang abgelöst.

Unverändert ausgeschlossen bleiben die manuelle Pflege oder der Import offiziell berechneter Gesamtwertungstabellen als zusätzliche Wahrheit, die Rückrechnung vollständiger Stimmzettel aus isolierten Ergebnissen, genaue persönliche Ränge außerhalb der veröffentlichten Top 15, KI-/Genreanalysen, Telemetrie, Cloudübertragung, neue Anmeldung oder Mehrbenutzerbetrieb und die CSC-Ausschlussprüfung. Ein Teilnehmerprofil bedeutet die auswählbare Auswertung einer vorhandenen Contest-Teilnahme in der lokalen Einzelbenutzeranwendung.

Der ursprüngliche Spezifikationsauftrag umfasste Repositorydokumentation und Entwicklungspakete. Der gesonderte Implementierungsauftrag vom 08.10.2026 liefert S1 (#177). Liefer-, Prüf- und Abnahmestände führen die Paket-Issues und PRs; die nachfolgenden S2-/S3-Funktionen sind weiterhin spezifiziert, nicht geliefert.

## 2. Gemeinsame Daten- und Berechnungsgrundlage

### 2.1 Kanonische Quellen und Identität

Kanonisch sind die vollständige Songliste, deren Zuordnung zu stabilen Contest-Teilnahmen, die veröffentlichten Stimmzettelstatus und die eindeutigen Stimmzettelränge 1 bis 15. Länder und Anzeigenamen dienen der Darstellung; Zuordnungen und Gruppierung erfolgen über stabile IDs. Teilnehmer mit geändertem Namen, Alias, Land oder Verwaltungsaktivstatus behalten ihre Ergebnisidentität. Das Teilnehmerfeld umfasst alle Contest-Teilnahmen; ein Darstellungsfilter oder `active`-Flag entfernt keine historischen Einreichungen oder Wertungen.

Der eigene veröffentlichte Stimmzettel zählt unter denselben Regeln wie jeder andere. Die persönliche Arbeitsrangliste, ihre Einschätzungen und Top-15-Snapshots sind keine zusätzliche Stimme. Die optionale Markierung der eigenen Contest-Teilnahme erzeugt weder eine Teilnahme noch einen veröffentlichten Stimmzettel. Legacy-Ergebniswerte und BOTB-Auswahlen gehen in keine der hier definierten Berechnungen ein.

Ein gültiger veröffentlichter Stimmzettel enthält genau 15 verschiedene Beiträge derselben Show, jeden Rang 1 bis 15 genau einmal und keine eigene Einreichung. Innerhalb eines Stimmzettels bleiben Gleichstände unzulässig.

### 2.2 Zwei Punktearten

Die Oberfläche und alle fachlichen Antworten unterscheiden ausdrücklich:

| Begriff | Herkunft | Zweck |
| --- | --- | --- |
| **Stimmzettelpunkte** | Rang eines Beitrags auf einem veröffentlichten Stimmzettel | Showergebnis und alle Punktebeziehungen bzw. Präferenzstatistiken |
| **Gesamtwertungspunkte** | Showplatzierung eines Beitrags | Contest-Gesamtwertung und ihre Entwicklung |

Für beide Ebenen gilt dieselbe zentral gepflegte Funktion `P(r)`:

| Rang `r` | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `P(r)` | 25 | 20 | 16 | 13 | 11 | 10 | 9 | 8 | 7 | 6 | 5 | 4 | 3 | 2 | 1 |

Für einen Showrang größer als 15 gilt `P(r) = 0`. Ein vollständiger Stimmzettel verteilt genau 140 Stimmzettelpunkte. Die pro Show ausgeschütteten Gesamtwertungspunkte sind bei Gleichständen dagegen nicht auf 140 begrenzt.

### 2.3 Null, fehlende Bewertung und nicht wählbar

| Ausgangslage für abstimmende Person und Beitrag | Statistische Aussage | Numerische Bewertung/Opportunity |
| --- | --- | --- |
| Gültiger Stimmzettel, fremder Beitrag auf Rang 1 bis 15 | Beitrag bepunktet | `P(r)`; zählt in den Nenner |
| Gültiger Stimmzettel, wählbarer fremder Beitrag fehlt in Top 15 | Außerhalb der Top 15 | echte `0`; zählt in den Nenner |
| `NICHT_ABGESTIMMT` | Keine abgegebene Bewertung | kein Wert; zählt nicht in den Nenner |
| `UNERFASST` | Unbekannt | kein Wert; zählt nicht in den Nenner und verhindert Showabschluss |
| Eigene Einreichung | Nicht wählbar | kein Wert; zählt nicht in den Nenner |
| Keine Einreichung der betrachteten empfangenden Person in der Show | Kein bewertbarer Beitrag | kein Wert; zählt nicht in den Nenner |

Das Fehlen eines veröffentlichten Stimmzettels darf nie automatisch als Nichtabgabe oder Nullwertung gelten. Nullpunkte bedeuten ausschließlich „außerhalb dieser Top 15“, keine Ablehnung des Songs und keinen bekannten persönlichen Rang ab 16.

### 2.4 Gemeinsamer Auswertungsstand

Alle Ansichten verwenden standardmäßig ausschließlich die **explizit abgeschlossenen Shows des ausgewählten Contests**. Sie nennen Anzahl und Identität der einbezogenen Shows. Aktuelle oder historische Ausgabe ist keine zusätzliche Wertungsbedingung. Ein Contestwechsel darf keine Daten der vorherigen Auswahl im neuen Ergebnis belassen.

Offene, lediglich abschlussbereite oder wieder geöffnete Shows fließen nicht ein. Eine vorläufige Gesamtwertung unter Einbeziehung laufender Shows ist eine mögliche spätere Erweiterung und kein Bestandteil der Pakete dieses Dokuments. Die bestehende showbezogene Zwischenstandsanzeige aus veröffentlichten Stimmzetteln darf weiterhin existieren und ist als Zwischenstand von der gewerteten Contestansicht unterscheidbar.

Serverantworten leiten ihren Auswertungsstand aus einem konsistenten SQLite-Lesesnapshot ab. Ein gleichzeitiges Abschließen, Wiederöffnen, Ersetzen eines Stimmzettels oder Restore darf keine Antwort mit teilweise altem Status und teilweise neuen Rangdaten erzeugen. Zusammen angezeigte Daten dürfen nicht unbemerkt unterschiedliche einbezogene Shows darstellen.

## 3. Expliziter Showabschluss

### 3.1 Zustände und Abschlussbereitschaft

| Angezeigter Zustand | Bedingung | In Contestauswertungen enthalten? |
| --- | --- | --- |
| **Erfassung läuft** | Offen; mindestens eine Abschlussvoraussetzung fehlt | Nein |
| **Abschlussbereit** | Offen; alle Abschlussvoraussetzungen erfüllt | Nein |
| **Abgeschlossen** | Bewusster erfolgreicher Abschluss | Ja |

„Abschlussbereit“ ist abgeleitet, kein zusätzlich manuell zu pflegender Status. Ein persistenter, optionaler Abschlusszeitpunkt, `result_closed_at`, unterscheidet offen und abgeschlossen. Er ist fachlich und technisch unabhängig vom bestehenden `ballot_closed_at` und von historischen Legacy-Ergebnisabschlüssen.

Der Server erlaubt den Showabschluss nur, wenn gleichzeitig:

1. die vollständige Songliste nach dem bestehenden, für aktuelle bzw. historische Shows passenden Vollständigkeitsvertrag bestätigt ist;
2. alle vorhandenen Beiträge genau einer gültigen Teilnahme derselben Ausgabe zugeordnet sind und je Teilnahme höchstens ein Beitrag existiert;
3. für jede Contest-Teilnahme der Status dieser Show geklärt ist: `ABGESTIMMT` oder ausdrücklich `NICHT_ABGESTIMMT`, niemals `UNERFASST`;
4. jeder abgegebene Stimmzettel sämtliche bestehenden Rang-, Referenz-, Eindeutigkeits- und Selbstwertungsregeln erfüllt;
5. mindestens ein gültiger veröffentlichter Stimmzettel vorliegt.

Die vorhandene unterschiedliche Vollständigkeitsprüfung aktueller und historischer Songlisten bleibt erhalten. Der neue Abschluss erfindet keine fehlenden Beiträge und verlangt für historische Shows keinen persönlichen Kandidaten- oder Hörworkflow. Die automatische Abschlussbereitschaft wird vor dem tatsächlichen Schreiben erneut geprüft; eine veraltete Clientanzeige reicht nicht als Nachweis.

Alle Teilnehmer dürfen ausdrücklich als nicht abgestimmt erfasst sein, die Show ist dann jedoch nicht wertbar. Es entsteht insbesondere keine gemeinsame Siegergruppe aus einem Feld ohne einen einzigen gültigen Stimmzettel.

### 3.2 Wiederöffnen und Korrekturen

Die bewusste Aktion **Show wieder öffnen** entfernt die Show unmittelbar aus Gesamtwertung, Verlauf, Beziehungen und Rekorden. Die Wirkung auf die Anzahl gewerteter Shows wird in der Oberfläche erklärt und nach Erfolg sichtbar. Der vorhandene eigene Top-15-Snapshot wird dadurch weder geöffnet noch ersetzt. Nach erlaubten Korrekturen muss die Show erneut explizit abgeschlossen werden.

Ein Wiederöffnen des Showergebnisses hebt keine sonstigen Integritätsregeln auf. Insbesondere bleibt eine historische Songlisten-Wiederöffnung durch bereits veröffentlichte Stimmzettel nach dem bestehenden Vertrag gesperrt. Korrekturen nutzen die bestehenden zulässigen Ersetzungs-, Lösch- und Zuordnungspfade und deren Bestätigungen; dieser Abschluss schafft keinen privilegierten Korrekturweg.

### 3.3 Schutz aller wertungsrelevanten Schreibpfade

Der Server schützt abgeschlossene Ergebnisse auch dann, wenn ein anderer Dialog oder API-Pfad deren Grundlagen ändern würde. Eine bloße UI-Sperre genügt nicht.

| Änderung | Vertrag bei betroffenen abgeschlossenen Shows |
| --- | --- |
| Veröffentlichter Stimmzettelstatus, Rangfolge, Empfänger oder Ersetzen/Löschen eines Stimmzettels | Erst Ergebnis der betroffenen Show ausdrücklich wieder öffnen; danach bestehende Validierungen und atomare Schreibregeln anwenden |
| Beitrag hinzufügen, entfernen, in eine andere Show verschieben oder Einreichenden ändern; bestätigte Vollständigkeit zurücknehmen | Betroffene Ergebnisse müssen offen sein; sonst verständlicher Konflikt ohne Teiländerung; zusätzliche bestehende Songlisten-/Stimmzettelsperren bleiben bestehen |
| Contest-Teilnahme hinzufügen/entfernen oder referenzielle Zuordnung ändern | Alle abgeschlossenen Shows berücksichtigen, deren erwartetes Teilnehmerfeld, Zuordnungen oder Stimmzettel dadurch geändert würden; keine stille Erweiterung, Entwertung oder automatische Wiederöffnung |
| Eigene Contest-Teilnahme wechseln | Reiner Wechsel der Anzeigeidentität darf Ergebnisse nicht ändern. Ein bestehender Pfad, der dabei Beitragszuordnungen entfernt oder verändert, muss zuvor sämtliche betroffenen Showabschlüsse respektieren |
| Wechsel der aktuellen CSC-Ausgabe (`contest.is_current`) ohne Änderung der Showzugehörigkeit | Bereits gültig abgeschlossene Ergebnisse bleiben gültig und eingeschlossen. Der Vollständigkeitsbeleg darf nicht allein durch den Wechsel von aktueller zu historischer Readiness verloren gehen. Er ist beim Übergang zu erhalten und bei späteren zulässigen relevanten Korrekturen neu zu prüfen; ein dauerhaft wahrer Marker darf keine fehlende Zuordnung verdecken. S1 überträgt beim Wechsel aktuell → historisch für abgeschlossene Shows den Vollständigkeitsbeleg in `entry_list_complete`; beim Wechsel zur aktuellen Ausgabe wird dieser historische Marker zurückgesetzt. Abschlussprüfungen validieren unabhängig davon immer die kanonischen Zuordnungen; bestehende Regeln für offene aktuelle/historische Listen bleiben erhalten. |
| Fachliche Showreihenfolge oder Contestzugehörigkeit ändern, soweit ein vorhandener Pfad dies erlaubt | Betroffene abgeschlossene Auswertungsgrundlagen und Verläufe dürfen nicht stillschweigend verändert werden; erforderliche Wiederöffnung ausdrücklich verlangen |
| Anzeigename, Alias, Land, rein administrativer Aktivstatus, Showbezeichnung oder Songmetadaten korrigieren | Keine zusätzliche Sperre allein wegen dieses Abschlusses, sofern IDs, Wählbarkeit, Zuordnung, Teilnehmerfeld und Ergebnis unverändert bleiben; bestehende unabhängige Regeln gelten weiter |
| Kommentar, persönliche Einschätzung, Hörzustand oder eigene Arbeitsrangliste ändern | Keine neue Ergebnissperre, soweit die Änderung keine kanonische veröffentlichte Wertung oder Zuordnung verändert; bestehender persönlicher Ballotvertrag bleibt maßgeblich |

Eine Änderung wird auf ihre tatsächliche Wirkung geprüft, nicht allein nach dem Namen des Endpunkts. Insbesondere dürfen kombinierte Updates keine Zuordnungsänderung als vermeintliche Metadatenkorrektur mitschreiben. Unveränderte IDs mit korrigiertem Anzeigetext bleiben derselbe statistische Beitrag bzw. Teilnehmer.

Prüfung des Abschlusszustands, erneute Prüfung aller betroffenen Referenzen und die eigentliche Mutation erfolgen unter derselben wirksamen Transaktions-/Konkurrenzkontrolle. Das gilt auch für Abschluss und Wiederöffnung selbst. Veraltete Erwartungszustände und konkurrierende Requests führen zu einem nachvollziehbaren Konflikt oder einem vollständig konsistenten Endzustand, niemals zu Teiländerungen oder einem abgeschlossenen ungültigen Ergebnis. Mehrfachbestätigung eines bereits erfolgreich ausgeführten Abschlusses darf keine doppelte Punktgutschrift erzeugen.

## 4. Showergebnis und Gesamtwertung

### 4.1 Showplatzierungen

Für jeden Beitrag `e` wird die Summe `S(e)` seiner Stimmzettelpunkte aus allen gültigen veröffentlichten Stimmzetteln der Show berechnet. Die vollständige Songliste bestimmt die Beitragsmenge, einschließlich nie bepunkteter Beiträge. Nichtabgaben tragen keinen Punktwert bei; sie werden dadurch nicht zu Nullurteilen.

Der Showrang lautet:

`showRank(e) = 1 + Anzahl der Beiträge mit strikt mehr Stimmzettelpunkten als S(e)`

Dies sind geteilte Wettbewerbsränge mit übersprungenen Folgeplätzen. Ein alphabetisches Sortierkriterium, eine Beitrags-ID oder die Anzahl von Höchstwertungen darf keinen sportlichen Gleichstand auflösen.

| Situation | Showränge | Gesamtwertungspunkte |
| --- | --- | --- |
| Zwei gemeinsame Erstplatzierte | 1, 1, 3 | 25, 25, 16 |
| Zwei Beiträge teilen Platz 15 | 15, 15, 17 | 1, 1, 0 |
| Drei Beiträge teilen Platz 14 | 14, 14, 14, 17 | 2, 2, 2, 0 |

Jeder Beitrag mit `showRank <= 15` erhält `P(showRank)` Gesamtwertungspunkte. Maßgeblich ist der Rang, nicht die erste bis fünfzehnte Tabellenzeile. Mehr als 15 Teilnehmer können in derselben Show Gesamtwertungspunkte erhalten; Punktwerte werden weder geteilt noch gemittelt. Die einreichende Contest-Teilnahme erhält die Punkte ihres Beitrags. Eine Nichtabgabe des eigenen Stimmzettels erzeugt keine hier neu eingeführte Strafe oder Disqualifikation der Einreichung.

### 4.2 Contestplatzierungen und Verlauf

Die Gesamtwertungspunkte eines Teilnehmers sind die Summe über seine Beiträge in allen aktuell abgeschlossenen Shows des ausgewählten Contests. Auch die Contestplatzierung verwendet ausschließlich diese Punktesumme und dieselbe Rangformel mit strikt größeren Werten. Showgewinne, Podiumsplätze, höhere Einzelwertungen, Stimmzettelpunktsummen, Namen und Länder sind keine Tie-Breaker.

Bei wenigstens einer abgeschlossenen Show enthält die Gesamtwertung das Contest-Teilnehmerfeld einschließlich Teilnehmern mit null Gesamtwertungspunkten. Fehlt einem Teilnehmer in einer abgeschlossenen Show eine Einreichung, entsteht dadurch keine Showplatzierung; die Showzelle weist „keine Einreichung“ aus und trägt keine Punkte zur Summe bei. Ohne eine einzige abgeschlossene Show zeigt der Bereich „Noch keine gewertete Show“ statt einer erfundenen gemeinsamen Siegerliste.

Die Tabelle enthält Platz, Teilnehmer, Land, Gesamtwertungspunkte und die Punkte jeder Show. Ein Showdetail nennt Beitrag, Stimmzettelpunkte, Showplatz und Gesamtwertungspunkte. Eine abgeschlossene Show ohne erreichte Gesamtwertungspunkte zeigt `0`; eine noch nicht gewertete Show zeigt `–` samt Status. Die Anzeige dieser Zustände darf nicht von einem pauschalen Auffüllen fehlender Zahlen abhängen.

Der Verlauf verwendet die fachliche Shownummernreihenfolge, nicht Abschlusszeitpunkt, Importreihenfolge oder Datenbank-ID. Für jeden gewerteten Schritt werden kumulierte Gesamtwertungspunkte und geteilte Contestplätze aus den bis dahin einbezogenen abgeschlossenen Shows berechnet. Eine Platzveränderung ist `vorheriger Platz - aktueller Platz`; positive Zahlen bedeuten Aufstieg. Für den ersten gewerteten Schritt gibt es keinen Vergleichswert. Offene oder fehlende Shows bleiben als Lücken bzw. nicht gewertet erkennbar; ein Wiederöffnen berechnet alle betroffenen folgenden Stände neu.

Das Diagramm erlaubt die Auswahl einzelner Teilnehmer, damit eine vollständige Contestbesetzung nicht zwangsläufig gleichzeitig als Linien dargestellt wird. Tabelle und Diagramm verwenden denselben Datenstand. Anzahl der Showsiege und Podiumsplätze kann ergänzend angezeigt werden; ein Showrang 1 zählt als Sieg, ein Showrang höchstens 3 als Podiumsplatz, jeweils auch bei Gleichstand.

## 5. Punktebeziehungen und Teilnehmerprofile

### 5.1 Gerichtete Beziehungen und Nenner

Eine **Opportunity von A an B** ist eine abgeschlossene Show, in der A einen gültigen veröffentlichten Stimmzettel abgegeben hat und B einen für A wählbaren Beitrag besitzt. A und B müssen verschiedene stabile Teilnehmeridentitäten sein. Ob B selbst abgestimmt hat, beeinflusst diese einseitige Opportunity nicht.

Für jede gerichtete Beziehung `A → B` werden berechnet:

- **Punktesumme:** Summe der Stimmzettelpunkte von A an Bs Beiträge;
- **Shows mit Punkten:** Anzahl dieser Opportunities mit Wert größer als null;
- **bewertbare Shows:** Anzahl aller Opportunities, einschließlich echter Nullwerte;
- **Durchschnitt:** Punktesumme geteilt durch bewertbare Shows;
- **25er:** Anzahl der Opportunities, in denen A Bs Beitrag auf Rang 1 gesetzt hat.

Beispiel: „78 Punkte · in 5 von 6 Shows bepunktet · Ø 13 Punkte“. Ein Nenner von null ergibt „nicht berechenbar/keine gemeinsame Bewertungsbasis“, keinen Durchschnitt von null. Der Detailaufruf zeigt die einbezogenen Shows, Beiträge, Punktwerte und bei ausgeschlossenen Shows den Grund.

### 5.2 Zwei persönliche Toplisten und globale Höchstwerte

Jedes Teilnehmerprofil zeigt mindestens die Top 5 seiner Punktegeber und die Top 5 seiner Punkteempfänger. Die Rangfolge richtet sich nach der Punktesumme. Alle Beziehungen mit demselben Wert wie die Grenzposition sind enthalten; die Topliste kann deshalb länger als fünf Zeilen sein. Bei weniger als fünf tatsächlich vorhandenen positiven Punktebeziehungen werden alle vorhandenen gezeigt, keine fiktiven Unterstützer ergänzt. Eine erweiterbare vollständige Liste enthält auch bekannte Beziehungen ohne Punkte und deren Bewertungsbasis.

Contestweite Höchstwerte werden als gerichtete Beziehungen angezeigt: `A → B: X Stimmzettelpunkte`. Die Liste zeigt mindestens die Top 5 positiver Beziehungen einschließlich aller Gleichstände an der Grenze, soweit so viele vorliegen; die vollständige Liste bleibt erreichbar. Zwei doppelte Rekordlisten für „meiste gegeben“ und „meiste erhalten“ sind nicht nötig. Die Gegenrichtung und beide Nenner sind im Beziehungsdetail sichtbar. Eine Heatmap verwendet Geber als Zeilen, Empfänger als Spalten; die Diagonale ist „nicht wählbar“, fehlende Daten sind von bekannten Nullen getrennt. Eine Zelle öffnet die Beziehung mit Showaufschlüsselung.

### 5.3 Punktepartnerschaft und unerwiderte Punkteliebe

Diese beiden Vergleiche verwenden ausschließlich die **gemeinsame beidseitige Basis**: abgeschlossene Shows, in denen sowohl `A → B` als auch `B → A` Opportunities sind. Abweichende Nichtabgaben dürfen keinen scheinbaren einseitigen Geschmack erzeugen. Die beiden auf dieser gemeinsamen Basis berechneten Richtungssummen heißen `X` und `Y`.

| Statistik | Kennzahl und Rangfolge | Erforderliche Anzeige |
| --- | --- | --- |
| **Punktepartnerschaft** | `min(X, Y)`, absteigend | Beide Summen, gemeinsame Shows; ein Paar nur einmal |
| **Unerwiderte Punkteliebe** | Größere Richtung minus kleinere Richtung, absteigend; Richtung der Mehrunterstützung sichtbar | `A → B`, `B → A`, Differenz und gemeinsame Shows |

Bei `X = Y` besteht keine unerwiderte Differenz. Ohne gemeinsame beidseitige Basis gibt es keinen dieser Paarrekorde. Eine Punktepartnerschaft mit Wert null wird nicht als gegenseitige Unterstützung ausgezeichnet. Die vollständige gerichtete Summe aus §5.1 kann wegen einer anderen Showbasis abweichen; die Oberfläche bezeichnet diesen Unterschied ausdrücklich.

## 6. Weitere Rekorde und Kennzahlen

### 6.1 Geschmackszwillinge und musikalische Paralleluniversen

Die Präferenzähnlichkeit vergleicht zwei abgegebene Stimmzettel derselben abgeschlossenen Show anhand derselben für beide wählbaren **Drittbeiträge**. Die eigenen Einreichungen von A und B werden aus beiden Vergleichsvektoren entfernt. Für jeden übrigen Beitrag stehen seine Stimmzettelpunkte bzw. eine echte Null fest; aus einer Nichtabgabe wird kein Vektor gebildet.

Je Show wird die gewichtete Jaccard-Überschneidung berechnet:

`similarity(A, B, Show) = Summe min(pA(e), pB(e)) / Summe max(pA(e), pB(e))`

Die Summe läuft über dieselbe Menge von Drittbeiträgen. Ein leerer Vergleich oder Nenner null ist nicht berechenbar und wird nicht als 100 % gewertet. Der Contestwert ist das arithmetische Mittel der berechenbaren Showwerte: Jede gemeinsame Show zählt gleich viel, nicht proportional zu ihrem Teilnehmerfeld. Es wird erst für die Anzeige gerundet.

Identische Punktverteilungen ergeben 100 %, vollständig getrennte Favoriten 0 %. Für die beiden Vektoren `(25, 20)` und `(20, 25)` beträgt die Überschneidung `40/50 = 80 %`. Gemeinsame letzte Top-15-Plätze wiegen damit weniger als gemeinsame Höchstwertungen. Gemeinsame Nullwerte erhöhen den Wert nicht.

**Geschmackszwillinge** sind die Paare mit den höchsten, **musikalische Paralleluniversen** die mit den niedrigsten berechenbaren Werten. Beide zeigen Anzahl und Ergebnisse der verglichenen Shows; ein Paar wird jeweils nur einmal geführt. Bereits eine gemeinsame Show darf dargestellt werden, erhält aber die sichtbare Einordnung „Basis: 1 Show“. Es gibt keinen unbegründeten Mindestumfang von beispielsweise drei Shows. Die Aussage bleibt auf beobachtete Top-15-Präferenzen begrenzt.

### 6.2 Publikumsliebling

Für jeden Beitrag wird die Anzahl der gültigen, für ihn wählbaren Stimmzettel als Nenner gezählt. Der Zähler enthält die Stimmzettel, auf denen der Beitrag Punkte erhielt. **Publikumsliebling** sortiert nach `positive Wertungen / mögliche bekannte Wertungen`, absteigend. Die Darstellung zeigt Quote, beide absoluten Zahlen und Show. Eigene Einreichung, Nichtabgaben und unerfasste Stimmzettel vergrößern den Nenner nicht.

Ohne eine einzige wählbare gültige Wertung ist diese Quote nicht berechenbar. Die Kennzahl misst die Breite der Top-15-Unterstützung, unabhängig von der Gesamthöhe der Punkte. Eine hohe Quote bei kleiner Datenbasis bleibt als solche erkennbar.

### 6.3 König der 25er

Je Beitrag und je einreichendem Teilnehmer werden die erhaltenen Rang-1-Wertungen gezählt. Die Rangfolge des Rekords richtet sich nach dieser Anzahl. Der Wert je Teilnehmer ist die Summe über seine Einreichungen der abgeschlossenen Shows.

Ergänzend wird die Quote `erhaltene 25er / gültige wählbare Bewertungsmöglichkeiten` gezeigt. Beim Teilnehmer ist der Nenner die Summe der beitragsbezogenen Bewertungsmöglichkeiten über seine einbezogenen Shows. Zahl der Einreichungen bzw. Shows und Nenner bleiben sichtbar. Es werden keine Höchstwertungen aus bloß geteilten Show- oder Contestplätzen abgeleitet.

### 6.4 Kult oder Skip

Für jeden Beitrag besteht die Population aus allen bekannten wählbaren Bewertungen: tatsächliche Stimmzettelpunkte und echte Nullen. Mit `n` Bewertungen und deren Mittelwert `m` wird die Populationsvarianz berechnet:

`variance = Summe (p - m)^2 / n`

Die Rekordfolge richtet sich nach der Varianz, absteigend; die Standardabweichung `sqrt(variance)` darf als verständliche Anzeigegröße dienen. Kein `n - 1`-Nenner, keine künstlichen Nullen für fehlende oder eigene Bewertungen. `n = 0` ist nicht berechenbar; bei `n = 1` ist die Varianz mathematisch null und die Einzelwertungsbasis wird ausdrücklich gezeigt. Für den Polarisierungsrekord sind mindestens zwei bekannte wählbare Bewertungen erforderlich: Eine Einzelwertung beschreibt keinen Vergleich. Ein solches Beitragsdetail darf weiterhin seine eine Wertung im Histogramm zeigen.

Daneben steht ein Histogramm mit exakten Häufigkeiten für `0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 16, 20, 25`. Seine Häufigkeiten summieren sich zu `n`. Die Kombination aus Verteilung und Streuung zeigt, ob hohe Einzelwertungen vielen Nichtnennungen gegenüberstehen. Die Beschreibung erklärt die Null als „außerhalb Top 15“ und unterstellt niemandem Ablehnung.

### 6.5 Allein auf weiter Flur

Ein Beitrag ist **exklusiv bepunktet**, wenn genau ein gültiger wählbarer Stimmzettel ihm mehr als null Punkte gibt. Für jeden Abstimmenden werden seine Punkte für solche Beiträge über abgeschlossene Shows summiert; dies ist die primäre Rekordkennzahl. Ergänzend werden die Anzahl exklusiv bepunkteter Beiträge und die Liste seiner **exklusiven 25er** gezeigt.

Der Detailnachweis enthält Beitrag, Show, eigene Punkte und Zahl aller bekannten wählbaren Bewertungen dieses Beitrags. Nichtabgaben sind keine Gegenmeinungen. Bei nur einer bekannten wählbaren Bewertung ist die formale Exklusivität sichtbar als auf dieser einen Bewertung beruhend einzuordnen. Werden bei einer Korrektur weitere Stimmzettel erfasst und die Show erneut abgeschlossen, wird die Exklusivität vollständig neu abgeleitet.

### 6.6 Konsensbeauftragter

Für jede abgeschlossene Show mit gültigem Stimmzettel von A wird As Übereinstimmung mit dem **übrigen** Teilnehmerfeld berechnet. As eigener Stimmzettel wird vor jeder Bildung der Vergleichswertung vollständig entfernt. As eigene Einreichung ist aus der Vergleichsmenge entfernt, weil A sie nicht bewerten konnte. Für die verbleibenden Beiträge werden die Stimmzettelpunkte aller anderen gültigen Stimmzettel summiert; die jeweils eigene nicht wählbare Einreichung eines anderen Abstimmenden erzeugt dabei keine Nullpräferenz.

Die eigene Punkteverteilung und die Summenverteilung des übrigen Felds werden jeweils durch ihre Summe geteilt, sodass beide Vektoren auf derselben Vergleichsmenge die Summe 1 besitzen. Anschließend gilt dieselbe gewichtete Jaccard-Formel aus §6.1, nun für diese normierten Vektoren. So wird eine einzelne Wertung nicht unmittelbar mit einer viel größeren Punktsumme des Felds verglichen.

Eine Show ohne mindestens einen anderen gültigen Stimmzettel bzw. ohne positive Vergleichssumme ist nicht berechenbar. Der Teilnehmerwert ist das arithmetische Mittel seiner berechenbaren Showwerte mit gleichen Showgewichten. Der Rekord sortiert nach diesem Wert absteigend und zeigt Zahl und Einzelwerte der verglichenen Shows. Das Herausrechnen der eigenen Stimme verhindert, dass sie ihre eigene Konsensnähe erhöht.

### 6.7 Dauerbrenner

Für jeden Teilnehmer werden ermittelt:

- längste Serie von Shows mit mindestens einem Gesamtwertungspunkt;
- längste Serie von Podiumsplatzierungen mit Showrang höchstens 3;
- Anzahl der Top-15-Platzierungen und ergänzend Showsiege/Podiumsplätze einschließlich geteilter Ränge.

Serien verwenden tatsächliche aufeinanderfolgende Shownummern derselben Ausgabe. Eine dazwischenliegende offene, wieder geöffnete oder fehlende Show unterbricht die **nachweisbare** Serie; sie ist dadurch kein belegter Misserfolg und erhält keine Nullwertung. Eine abgeschlossene Show ohne eigene Einreichung oder ohne die verlangte Leistung beendet ebenfalls die jeweilige Serie. Noch bevorstehende Shows werden nicht als Niederlagen an eine bestehende Serie angehängt.

Eine Serie nennt Länge sowie erste und letzte Show. Mehrere gleich lange Serien bleiben nachvollziehbar. Gleichstände am Showrang 15 zählen als Top-15-Platzierungen, auch wenn dadurch mehr als 15 Beiträge Gesamtwertungspunkte erhalten. Gleichstände am Rang 3 zählen als Podiumsplätze. Zeigt eine getrennte Häufigkeitsliste über alle gewerteten Shows einen anderen Wert als eine lückenlose Serie, wird beides nicht vermischt.

### 6.8 Knapp daneben ist auch vorbei

Pro abgeschlossener Show wird die **erste nicht punktberechtigte Ranggruppe** betrachtet: die Beiträge mit dem kleinsten Showrang größer als 15. Diese Gruppe kann wegen Gleichständen auf Rang 17 oder noch weiter hinten beginnen. Alle Beiträge dieser Gruppe zählen gleichberechtigt als knappe Nichtqualifikation.

Der Abstand ist ihre Stimmzettelpunktsumme zur Summe der letzten punktberechtigten Ranggruppe. Er wird positiv als `Punkte der letzten punktberechtigten Gruppe - Punkte des betrachteten Beitrags` angezeigt. Dies sind Stimmzettelpunkte, keine Gesamtwertungspunkte. Gleiche Punktsummen an der Grenze wären derselbe Rang und dürften nicht künstlich in qualifiziert und nicht qualifiziert getrennt werden.

Der Teilnehmerrekord zählt, wie oft jemand zu dieser ersten nicht punktberechtigten Gruppe gehörte; daneben sind Show, Rang und jeweiliger Abstand erreichbar. Ein weiterer Contestrekord zeigt den kleinsten positiven Abstand innerhalb dieser Nichtqualifikationsfälle mit allen punktgleichen Fällen. Eine Show ohne nicht punktberechtigte Gruppe liefert keinen solchen Rekord. Null Fälle bzw. fehlende Shows erzeugen keinen vermeintlichen Rekordgewinner.

### 6.9 Zünglein an der Waage

Die Ansicht rechnet für eine abgeschlossene Show genau **einen ausgewählten gültigen veröffentlichten Stimmzettel hypothetisch heraus**. Die übrigen Rangpositionen, Zuordnungen und Beiträge bleiben unverändert. Alle Showpunktsummen, geteilten Showränge und daraus folgenden Gesamtwertungspunkte werden unter denselben Regeln neu berechnet.

Der Vergleich zeigt je betroffenem Beitrag bzw. Teilnehmer ursprüngliche und hypothetische Stimmzettelpunkte, Showplätze und Gesamtwertungspunkte. Er nennt außerdem die Wirkung auf die aktuelle Contestsumme: ursprüngliche Contestsumme minus ursprüngliche Gesamtwertungspunkte dieser Show plus hypothetische Gesamtwertungspunkte dieser Show; die übrigen abgeschlossenen Shows bleiben rechnerisch unverändert. Sichtbar sind insbesondere eine geänderte Siegergruppe, neu entstandene oder aufgehobene Gleichstände sowie der Eintritt in oder Austritt aus den Gesamtwertungspunkten. Nur rechnerisch begründete Änderungen werden behauptet; die Richtung und Höhe einer Punktedifferenz müssen zum Tabellenvergleich passen.

Bliebe kein einziger gültiger Stimmzettel übrig, heißt das Ergebnis „Keine wertbare Gegenrechnung ohne diesen Stimmzettel“. Es werden dann weder eine gemeinsame Siegergruppe noch fiktive Gesamtwertungspunkte erzeugt. Nichtabgaben sind nicht als herauszurechnende Stimmen auswählbar.

Die Simulation ist eindeutig als hypothetisch beschriftet und rein lesend. Sie verändert keine Stimmzettel, Abschlüsse, persönlichen Snapshots, echte Contestpunkte oder Rekorde. Die Gegenrechnung umfasst die ausgewählte Show und ihre Punktevergabe; sie benötigt keinen neuen Speicherstand und keine Freigabe zum Wiederöffnen.

## 7. Gemeinsame Regeln für Rekorde und Darstellung

Die humorvolle Überschrift wird jeweils von einer kurzen sachlichen Berechnungserklärung begleitet. Datenbasis, Punkteart und zugehörige Show-/Beitrags-/Beziehungsdetails sind erreichbar. Berechnungen erfolgen mit ausreichender Präzision; Vergleiche und Gleichstände beziehen sich auf **ungerundete** Kennzahlen. Insbesondere Quotienten und mittlere Ähnlichkeitswerte dürfen nicht allein wegen gleicher gerundeter Anzeige zusammengeführt werden. Bei mathematischer Gleichheit dürfen Rundungsfehler keinen Rangunterschied erzeugen.

Alle fachlichen Rekordgleichstände werden gleichberechtigt dargestellt. Innerhalb einer Gleichstandsgruppe darf eine stabile technische Anzeigereihenfolge verwendet werden, ohne Rang, Rekordstatus oder Top-5-Grenze zu verändern. Bei begrenzten Listen werden alle Gleichstände an der Grenze einbezogen. Symmetrische Paarrekorde führen ein Paar nur einmal; gerichtete Beziehungen bleiben gerichtet.

Keine Datenbasis ergibt einen Leerzustand, keine Nullrekordgewinner. Eine echte gemessene Null bleibt dagegen ein gültiger Messwert, beispielsweise 0 % Favoritenüberschneidung bei vorhandenen gemeinsamen Shows. Bei auf positive Ereignisse gerichteten Rekorden wie Punktepartnerschaft, exklusiven Punkten, 25ern oder knappen Nichtqualifikationen wird das Fehlen solcher Ereignisse als solches angezeigt. Die kleinste Datenbasis wird nicht versteckt; insbesondere eine einzige Show oder Bewertung wird sichtbar bezeichnet, ohne willkürliche Mindestshowzahl als neue Zugangshürde.

Die vier Ansichten sind wie folgt abgegrenzt:

| Ansicht | Mindestinhalt |
| --- | --- |
| **Gesamtwertung** | Teilnehmer/Land, geteilte Plätze, Gesamtwertungspunkte, Showspalten, Platzveränderung, Showdetails und auswählbarer Verlauf |
| **Teilnehmer** | Auswählbare Contest-Teilnahme, eigene Beiträge und Showergebnisse, Gesamtplatz/Verlauf, Top-Geber und Top-Empfänger, persönliche Rekorde und Beziehungen |
| **Punktebeziehungen** | Gerichtete globale Höchstwerte, Geber-Empfänger-Heatmap, beide Richtungen und Showaufschlüsselung; gemeinsame Basis bei Gegenseitigkeitskennzahlen |
| **Rekorde** | Gruppen für Beiträge, Teilnehmer, Beziehungen und Verlauf; Erläuterungen und Detailnachweise für die jeweils gelieferten Kennzahlen |

Der bestehende dunkle Desktopstil und die Navigation werden konsistent erweitert. Tabellen bleiben auch bei langen Namen, geteilten Grenzplätzen und dem vorgesehenen Teilnehmerumfang lesbar. Farben unterstützen Zahlen und Zustandsbeschriftungen, ersetzen sie aber nicht. Die konkrete Routengestaltung und Auswahl einer lokal gebündelten Diagrammdarstellung sind Implementierungsdetails; dieser Auftrag verlangt keine neue Laufzeitplattform oder externe Statistikdienste.

## 8. Persistenz, Migration, Backup und Export

Es entsteht keine manuell gepflegte Summen-, Platzierungs- oder Rekordtabelle. Gespeichert werden die bestehenden Ausgangsdaten und die notwendige neue Abschlussinformation; sämtliche Zahlen, Ränge und Kennzahlen bleiben reproduzierbare Ableitungen. Eine technische Zwischenspeicherung darf niemals zur unabhängigen Wahrheit werden und muss bei Abschluss, Wiederöffnung, zulässigen Datenänderungen und Restore vollständig korrekt invalidiert werden.

Neue Schemafelder werden über Liquibase eingeführt. **Vorhandene Shows starten ohne neuen expliziten Ergebnisabschluss**, auch wenn Songliste und Stimmzettel vollständig sind, ein alter Legacy-Ergebnisabschluss existiert oder die Ausgabe historisch ist. Die Migration darf vorhandene Daten weder als Zustimmung zum Abschluss interpretieren noch eine eigene Stimme erzeugen. Die Oberfläche kann solche Shows unmittelbar als abschlussbereit zeigen.

Der vollständige JSON-Export/Restore führt den neuen Abschlusszustand verlustfrei mit. S1 verwendet dafür JSON-Version 11 und Schema-Generation 17. Der neue nullable Showwert `resultClosedAt` ist im vollständigen v11-Datensatz ausdrücklich erforderlich. Die alten Showformen der Versionen 7–10 werden separat gelesen und mit offenem Ergebniszustand hochgestuft; Versionen 1–6 durchlaufen weiterhin ihre bestehenden Upgrades. Alle bisher unterstützten älteren Formate bleiben importierbar und ergänzen fehlende Ergebnisabschlussinformationen deterministisch als offen. Ein neuer Export mit abgeschlossener Show muss deren vollständige Eingaben enthalten und vor Übernahme alle Abschlussinvarianten erfüllen. Ungültige oder widersprüchliche Abschluss-/Referenzdaten blockieren die Übernahme vor einer Teiländerung.

Native Backups bleiben über Vorwärtsmigrationen kompatibel. Alte native Backups erhalten beim Upgrade offene Ergebniszustände; neue Backups bewahren ihre gültigen Abschlüsse. Der bestehende geprüfte Staging-Restore, Sicherheitsbackup, zentrale Datenlock und konsistente JSON-Lesesnapshot bleiben verbindlich. Restore und Reimport müssen anschließend dieselben Zahlen und Gleichstände liefern wie der exportierte gültige Datenstand.

Der separate Analyseexport bleibt ein Analysevertrag ohne Restorepfad. Seine bestehenden Auswahlmöglichkeiten und Quellen bleiben unverändert; insbesondere wird er nicht auf abgeschlossene Shows eingeschränkt. Diese Erweiterung macht externe Analysedateien oder importierte Gesamtwertungstabellen nicht zu Ergebnisquellen und verlangt keine zusätzlichen Statistik- oder grafischen Berichtsexporte. Private reale Teilnehmer-, Stimmzettel- und Analysedaten gehören weiterhin nicht als Testfixture in das öffentliche Repository.

## 9. Entwicklungspakete und Abhängigkeiten

Die drei Ausbaustufen werden in vier eigenständig prüfbare Pakete geschnitten. Stufe 3 wird geteilt, damit deskriptive Kennzahlen und hypothetische Neuberechnung getrennt integriert und abgenommen werden können. Jedes Paket enthält seine notwendigen Tests und Dokumentationsaktualisierungen.

| Stufe/Paket | Issue | Nutzbares Ergebnis | Abhängigkeit |
| --- | --- | --- | --- |
| **1 / S1** | [#177](https://github.com/venomenon328/csc-x-tool/issues/177) | Expliziter Showabschluss/Wiederöffnung, Schutz der Eingaben, Migration/Kompatibilität, Show-/Gesamtwertung und Verlauf | vorhandenes Contest-, Songlisten- und Published-Ballot-Modell |
| **2 / S2** | [#178](https://github.com/venomenon328/csc-x-tool/issues/178) | Teilnehmerprofile, Top-Geber/Empfänger, gerichtete Höchstwerte, Heatmap, Punktepartnerschaft, unerwiderte Punkteliebe, 25er und Serien | S1 |
| **3 / S3a** | [#179](https://github.com/venomenon328/csc-x-tool/issues/179) | Geschmackszwillinge, Paralleluniversen, Publikumsliebling, Polarisierung mit Histogramm, exklusive Punkte, Konsensnähe | S1 und S2 |
| **3 / S3b** | [#180](https://github.com/venomenon328/csc-x-tool/issues/180) | Knappe Nichtqualifikationen und lesende Stimmzettel-Gegenrechnung | S1 und S2; keine fachliche Abhängigkeit von S3a |

S1 liefert die verwendbare Gesamtwertung; S2 liefert die beiden ausdrücklich gewünschten persönlichen Toplisten und Contesthöchstwerte. S3a und S3b vervollständigen die freigegebenen Zusatzstatistiken. Ein Paket-PR schließt nur sein eigenes Issue; das übergeordnete Statistikissue bleibt bis zum vollständigen freigegebenen Umfang offen. Der Dokumentationsauftrag ersetzt keine spätere Startprüfung gemäß [Workflow](dev-rules/WORKFLOW.md).

## 10. Verifikation und manuelle Abnahme der Implementierung

Diese Szenarien beschreiben erforderliche spätere Nachweise, keine in diesem Spezifikationsauftrag ausgeführten Tests. Verbindlicher automatisierter Mergepfad bleibt remote **Build / Root build** gemäß [Projektprofil](PROJECT_PROFILE.md). Ergänzende Tests sichern konkret betroffene Berechnungs-, SQLite-, Migrations-, Transaktions- und Restoreverträge mit isolierten synthetischen Daten ab. Auf der lokalen Windows-Workstation werden keine Agenten-Builds, Tests, Installationen oder automatisierten Browserläufe gestartet.

| Paket | Zentrale automatisierte Akzeptanzfälle | Manuelles Abnahmeszenario nach Gesamtintegration auf `main` |
| --- | --- | --- |
| **S1** | Gleichstände `1,1,3`, an Rang 15 und über die Punktegrenze hinaus; geteilte Contestplätze ohne Tie-Breaker; Datenbestand ohne abgeschlossene Shows; vollständige Show ohne gültige Stimme; eigene Stimme nur einmal; alle Contest-Teilnahmen unabhängig von `active`; historische/aktuelle Vollständigkeitsregeln einschließlich Wechsel der aktuellen Ausgabe ohne Entwertung abgeschlossener Shows; Abschlusskonkurrenz und sämtliche betroffenen Schreibpfade; Wiederöffnung und folgende Verlaufsschritte; alte/neue JSON- und native Backupzustände | Benutzer in Windows/Vivaldi: aktuelle und historische Show bewusst abschließen, Null und offen unterscheiden, Gleichstandsgruppe vollständig sehen, Verlauf prüfen, wieder öffnen/korrigieren/erneut abschließen, verständlichen Konflikt eines geschützten Schreibpfads und weiterhin zulässige reine Metadatenkorrektur prüfen |
| **S2** | Einseitige vs. gemeinsame Opportunities; echte Null vs. fehlende Bewertung/Selbstbeziehung; alle Top-5-Grenzgleichstände; Richtungs- und Identitätswechsel; Gegenseitigkeit/Differenz; 25er-Nenner; Serien mit offener/fehlender/wieder geöffneter Showlücke und geteiltem Rang | Benutzer in Windows/Vivaldi: Teilnehmer wechseln, beide Toplisten und vollständige Listen öffnen, Heatmaprichtung/Null/keine Daten erkennen, Gegenrichtung und Shows nachvollziehen, Rekorde und Seriendetails ansehen |
| **S3a** | Gleiche/disjunkte/teilweise überlappende Punktevektoren; beide eigenen Beiträge ausgeschlossen; gleiche Showgewichte; exakte Rekordgleichstände trotz Rundung; Nullnenner und kleine Basis; Histogrammsumme/Populationsvarianz; Exklusivität; Konsensberechnung ohne eigene Stimme | Benutzer in Windows/Vivaldi: je Kennzahl Erklärung, Basis und Drilldown nachvollziehen, Beitragshistogramm prüfen, kleine Basis und unberechenbare Werte eindeutig erkennen |
| **S3b** | Erste nicht punktberechtigte Gruppe auch jenseits von Rang 16; keine Nichtqualifikanten; geteilte Grenze; einzelne Stimme erzeugt/entfernt Siege und Punkteränge; letzte gültige Stimme nicht als wertbare Gegenrechnung; keinerlei Schreibwirkung | Benutzer in Windows/Vivaldi: knappe Ergebnisse samt Abstand sehen, eine Stimme hypothetisch entfernen, ursprüngliche und hypothetische Ergebnisse vergleichen, Hypothese klar erkennen und nach Verlassen unveränderte echte Ergebnisse bestätigen |

**Übergreifende Abnahmeentscheidung vom 08.10.2026:** Sämtliche manuellen Windows-/Vivaldi-Szenarien aus S1, S2, S3a und S3b werden erst **nach vollständiger Umsetzung der Statistik-Roadmap #14** gemeinsam auf dem dann integrierten `main` ausgeführt. Sie sind **kein blockierendes Mergegate eines einzelnen Teil-PRs**; ihr tatsächliches Ergebnis muss vor dem Abschluss der übergeordneten Roadmap dokumentiert werden. Diese Festlegung ersetzt die frühere Teil-PR-Mergepflicht, nicht die Szenarien selbst. Je Teil-PR bleiben geprüfter Head, relevanter CI-/Test-Merge-Bezug, automatisierte Nachweise und technisches Review vor Merge erforderlich. Für die spätere manuelle Gesamtabnahme sind Szenario, erwartetes Ergebnis, durchführende Person und geprüfter `main`-Stand festzuhalten. Automatisierte Chromium-Prüfungen ersetzen keine behauptete menschliche Vivaldi-Abnahme. Paketierungs-/Releaseprüfungen bleiben separate Gates des bestehenden Workflows.
