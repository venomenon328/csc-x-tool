# Ausführungsweg und Modellauswahl

## 1. Wann diese Regeln greifen

Diese Datei vollständig lesen, wenn ein neues Paket zur Umsetzung vorbereitet wird oder konkrete technische Review-Nacharbeiten noch umgesetzt werden müssen. Sie ergänzt den [Workflow](WORKFLOW.md), ersetzt aber weder Startprüfung noch Freigabe.

Keine Empfehlung bei bloßer Beratung, reiner Rückschau, einem Review ohne neuen Implementierungsbedarf, ausschließlich offener manueller Abnahme oder nach bereits erledigter Implementierung/Nacharbeit. Wird eine zunächst empfohlene Eigenumsetzung im selben Auftrag abgeschlossen, die Empfehlung am Ende nicht wiederholen. Noch unklare beziehungsweise blockierte Arbeit nicht als startbereiten Codex-Auftrag ausgeben.

## 2. Reihenfolge

1. Anforderungen, Qualitätsanspruch und blockierende Entscheidungen klären.
2. Paket auf sinnvolle Teilbarkeit und eigenständig prüfbaren Endzustand prüfen.
3. Tatsächlich verfügbare Ausführungs- und Verifikationswege ermitteln.
4. Bevorzugten Ausführungsweg unter Berücksichtigung der erwarteten Ergebnisqualität bestimmen: ChatGPT oder Codex.
5. Erst dann passende Modellklasse und Reasoning wählen, soweit eine Delegation ansteht.

Ein stärkeres Modell ersetzt keinen fehlenden Repositoryzugriff, keinen verbotenen Testlauf und keine offene Produktentscheidung. Eine künstliche Zerlegung darf weder notwendige Kontextzusammenhänge noch die Qualität des Gesamtergebnisses beeinträchtigen. Ein kleineres Paket muss nicht mit einem kleineren Modell bearbeitet werden.

## 3. Eigenumsetzung einschätzen

Immer eine konkrete, umgebungsbezogene Aussage für noch anstehende Umsetzung treffen:

- `Selbst umsetzbar: Ja`: ChatGPT kann den beauftragten Implementierungsumfang einschließlich notwendiger automatisierter Prüfung, Commit und Push zuverlässig erledigen. Remote-CI darf die Prüfung liefern, wenn ihre Ergebnisse zugänglich sind.
- `Selbst umsetzbar: Teilweise`: Nur ein benennbarer Teil dieses Umfangs ist mit den vorhandenen Werkzeugen zuverlässig möglich. Fehlenden Schritt und zuständige Umgebung konkret nennen.
- `Selbst umsetzbar: Nein`: ChatGPT kann die beauftragte Ausführung in dieser Umgebung nicht zuverlässig durchführen; ein passender Codex-Ausführungsweg ist beispielsweise aufgrund benötigter Laufzeit, Kontextführung oder Werkzeug-/Testzyklen geeigneter.

Code schreiben können allein genügt nicht. Umgekehrt machen wenige berührte Dateien die Eigenumsetzung nicht automatisch sicher. Kleine isolierte Fixes und zusammengehörige Nacharbeiten bevorzugt selbst erledigen, wenn der gesamte Implementierungsweg belastbar verfügbar ist und kein relevanter Qualitätsvorteil einer Delegation zu erwarten ist. `Selbst umsetzbar: Ja` beschreibt die Fähigkeit, nicht zwangsläufig den qualitativ besten Ausführungsweg.

**Manuelle Abnahme separat behandeln:** Eine außerhalb des Implementierungsumfangs vereinbarte Nutzerprüfung macht eine sonst vollständige Eigenumsetzung nicht automatisch „Teilweise“. Sie muss separat als offenes Merge- oder Releasegate genannt werden. Gehört sie ausdrücklich zum beauftragten Endzustand, ist die verbleibende Einschränkung entsprechend einzurechnen. Codex kann dieselbe Abnahme ebenfalls nicht automatisch ersetzen.

Die Befugnis zur tatsächlichen Eigenumsetzung folgt aus dem Auftrag, nicht aus dieser Einschätzung.

## 4. Modell und Reasoning wählen

Modellnamen und derzeit dokumentierte Bezeichnungen stehen im [Modellkatalog](MODEL_CATALOG.md). Die folgende Zuordnung ist eine lokale Arbeitsheuristik, keine empirisch bewiesene Qualitäts-, Erfolgs- oder Kostenrangliste. Die Klassen sind Ausgangspunkte, keine Obergrenzen.

| Schwierigste relevante Eigenschaft oder Qualitätsanforderung | Ausgangspunkt |
| --- | --- |
| Eindeutige, mechanische Änderung ohne fachlichen oder stilistischen Gestaltungsspielraum und mit einfachem Nachweis | Kleine, kostengünstige Modellklasse |
| Klar spezifizierte normale Feature-/Fixarbeit mit beherrschbaren Schnittstellen und Tests | Ausgewogene Allroundklasse |
| Hoher Verständnisbedarf, schwierige Modellierung, anspruchsvolle Fehlerursache oder differenzierte redaktionelle/kuratorische Arbeit | Starke Analyse-/Implementierungsklasse |
| Nicht sinnvoll weiter teilbarer, schwieriger Gesamtprozess mit interagierenden Zuständen, Recovery, Konkurrenz oder langen Werkzeugschleifen | Stärkste geeignete agentische Klasse |

Bewerte neben Anforderungsklarheit, Wechselwirkungen, Erkennbarkeit und Folgekosten von Fehlern auch die relevanten Qualitätsmerkmale: fachliche Richtigkeit, Vollständigkeit, Differenzierung, Quellenbewertung, Konsistenz, sprachliche Präzision beziehungsweise Wartbarkeit und Nutzbarkeit. Welche davon zählen, ergibt sich aus dem Auftrag; nicht für jede Kleinigkeit einen neuen Kriterienkatalog verlangen. Bloße Datei-/Zeilenzahl und Modellmarketing sind keine ausreichende Begründung.

**Redaktion und Kuratierung sind nicht automatisch mechanische Arbeit.** Ein exakt definierter Textaustausch unterscheidet sich von der Synthese mehrerer Quellen, einer fachlichen Einordnung oder der Formulierung individuell passender, konsistenter Texte. Bei solchen Aufgaben können stärkere Modelle oder höheres Reasoning auch für wenige Dateien oder kurze Texte sinnvoll sein. Bekannte Nutzererfahrungen mit vergleichbaren Aufgaben berücksichtigen; nicht erst einen gescheiterten Lauf verlangen, bevor ein erwarteter Qualitätsgewinn berücksichtigt wird.

**High ist das Standard-Reasoning dieser Heuristik**, auch bei normalen, klar beschriebenen Umsetzungspaketen. Es benötigt keinen Nachweis außergewöhnlicher Schwierigkeit. Medium oder Low nur empfehlen, wenn eine konkrete aufgabenbezogene Begründung dafür spricht und kein relevanter Qualitätsverlust zu erwarten ist, etwa bei rein mechanischen Änderungen mit eindeutigem Nachweis. Eine klare Spezifikation oder geringer Umfang allein rechtfertigt keine Absenkung.

Extra High oder Max sowie eine stärkere Modellklasse sind zulässig, wenn zusätzliche Qualität, Sorgfalt oder Zuverlässigkeit den Mehraufwand voraussichtlich rechtfertigt; nicht ausschließlich bei außergewöhnlich schwierigen technischen Aufgaben. Den erwarteten Mehrwert knapp benennen. Höhere Einstellungen sind keine Qualitätsgarantie und kein Selbstzweck; bei fehlendem plausiblen Mehrwert nicht automatisch eskalieren. Keine strukturell ungeeignete kleine Modellklasse mit maximalem Reasoning erzwingen.

Mehragenten-/Parallelmodi sind eine gesonderte Ausführungsentscheidung, nicht bloß „noch mehr Reasoning“. Nur bei trennbaren Aufgaben, klarer Integration und erlaubtem Ressourcen-/Kostenrahmen einsetzen; keine automatische Aktivierung.

Verfügbarkeit in der tatsächlich verwendeten Oberfläche prüfen. Ein bekannter API-Modellname beweist nicht die Auswahlmöglichkeit im Nutzerkonto. Bei unbekannter Verfügbarkeit die konkrete Empfehlung als solche kennzeichnen und den Zugriff als offene Voraussetzung nennen, statt eine verfügbare Alternative zu erfinden. Bei bestätigter Nichtverfügbarkeit eine konkrete geeignete verfügbare Alternative wählen oder den Startblocker benennen. Keine wiederholte Befragung, wenn eine aktuelle Bestätigung bereits vorliegt.

## 5. Qualität, Kosten und Eskalation

Ziel ist ein angemessenes Verhältnis aus **Ergebnisqualität, Zuverlässigkeit und erwarteten Gesamtkosten** bis zur Abnahme, nicht das billigste gerade noch abnahmefähige Ergebnis. Den vereinbarten Qualitätsanspruch nicht zugunsten geringerer Tokenkosten unterschreiten. Ein plausibler zusätzlicher Qualitätsgewinn innerhalb des beauftragten Scopes kann Mehrkosten rechtfertigen, auch wenn dadurch keine Korrekturschleife eingespart wird. Bei voraussichtlich vergleichbarer Qualität und Zuverlässigkeit den wirtschaftlicheren Weg bevorzugen. Explizite Budget- oder Ressourcenlimits bleiben verbindlich.

Zu den Gesamtkosten gehören Modellnutzung, Wiederholungen, Fehlerkorrekturen, CI und manuelle Übergaben. Normale autonome Test-/Korrekturschleifen sind kein gescheiterter Lauf. Präzise Qualitäts-, Preis- oder Zeitvorteile nur mit passenden Belegen behaupten; erwarteten Nutzen als Einschätzung kennzeichnen und API-Preise nicht ungeprüft auf ein Codex-Abonnement übertragen.

Bei Nacharbeit zuerst den Fehlermechanismus beziehungsweise das Qualitätsdefizit prüfen: unklare Spezifikation, unpassende Umgebung, übergroßes Paket, mangelnde Denktiefe, oberflächliche oder undifferenzierte Inhalte, ausgelassene Anforderungen oder unzuverlässiger Gesamtprozess. Entsprechend Scope, Zugang, Prüfverfahren, Reasoning oder Modellklasse anpassen. Nicht wegen jedes roten Tests automatisch eskalieren; bei ernsthaftem Modellversagen aber ebenso wenig identisch wiederholen. Eine Qualitätsinvestition schon bei der Erstwahl ist ausdrücklich möglich. Sie erlaubt keine ungefragten Features oder nachträglich erfundenen Mergeblocker.

Optional im PR knapp festhalten: tatsächlich verwendetes Modell/Reasoning, beobachtete Qualitätsunterschiede, wesentliche Korrekturschleifen und Ursache. Nutzerurteil, eigene Beobachtung und messbaren Nachweis unterscheiden; nur wirklich bekannte Daten erfassen. Daraus später die lokale Heuristik verbessern; kein Pflicht-Reporting für triviale Änderungen.

## 6. Ausgabe und Prompt

Zuerst `Bevorzugter Weg: ChatGPT` oder `Bevorzugter Weg: Codex` und die Einschätzung zur Eigenumsetzung mit kurzer Begründung nennen. Bei empfohlener Delegation zusätzlich genau eine Kombination im Format `Codex: <Modell> — Reasoning: <Stufe>` samt 1–3 Sätzen zu Qualitätsanspruch, Schwierigkeit, Risiko und Kostenabwägung liefern. Abweichungen vom High-Standard knapp begründen; bei höheren Einstellungen den erwarteten Zusatznutzen nennen. Keine Auswahlspanne wie „Terra oder Sol“ beziehungsweise „High bis Max“.

Bei bevorzugter Eigenumsetzung muss nicht zusätzlich ein gleichrangiger Codex-Auftrag erzeugt werden. Eine ausdrücklich verlangte Codex-Alternative als Alternative kennzeichnen. Diese bewusste Vereinfachung ersetzt den bisherigen Zwang, für jede mögliche Eigenumsetzung dennoch eine vollständige parallele Codex-Empfehlung auszugeben.

Jeder an Codex zu delegierende **startbereite** Auftrag erhält einen direkt kopierbaren Codeblock. Er identifiziert Repository, Issue/Paket, Arbeits-/Zielbranch oder bestehenden PR, `AGENTS.md` als Einstieg und gewünschten Endzustand. Bei Nacharbeit den konkreten Reviewkommentar beziehungsweise die Revision und aktiven Befundkennungen referenzieren. Modellwahl wird in der Oberfläche getroffen; kein langer Modellmonolog im Arbeitsauftrag.

Wenige Zeilen sind das Ziel, keine starre Zeichenzahl. Akzeptanzkriterien, allgemeine Tests, Commitregeln und Abschlussformat nicht aus den verbindlichen Quellen abschreiben. Neue fachliche Einschränkungen zuerst dort persistieren. Nur echte nicht anderweitig zuverlässig abrufbare Übergabeinformationen ergänzen. Ein Pflichtdokument ohne Zugriff wird nicht durch einen vermeintlich kompakten, aber unvollständigen Prompt ersetzt.
