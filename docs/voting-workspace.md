# Abstimmungsarbeitsplatz: unabhängige Scrollbereiche

**Stand:** 29.09.2026

**Status:** vom Nutzer freigegebene Spezifikation; kein Nachweis einer Implementierung

**Lieferumfang und Abnahmestand:** [Issue #174](https://github.com/venomenon328/csc-x-tool/issues/174)

## 1. Geltung und Ablösung

Diese Ergänzung zur [Produktspezifikation](specification.md) legt das Desktop-Scrollverhalten der bestehenden Route `/shows/:showId/voting` einschließlich des vorhandenen Zugangs `?mode=assignments` fest. Sie konkretisiert Hören, Ranking, UI und Browserabnahme; sie ändert keine fachliche Daten- oder Speicherseman­tik.

Das bereits eingeführte Grundmodell aus [#58](https://github.com/venomenon328/csc-x-tool/issues/58) bleibt bestehen: links ein vollständiger Arbeitsbestand aller Beiträge, rechts eine kompakte persönliche Rangfolge. Die zwei sichtbaren Bereiche sind keine disjunkten Besitzlisten. Die ältere Darstellung in BALLOT-001 der Baseline beschreibt insoweit nicht mehr das sichtbare Layout. Der atomare Rankingvertrag bleibt davon unberührt.

Ausdrücklich ersetzt werden:

- #58, Abschnitt **Zielbild / Scrollverhalten**: normales Seitenscrolling links, lediglich sticky rechte Spalte und zusätzlich begrenzt scrollbar gerenderte Rangliste;
- das ungefähre Höhenziel aus [PR #65](https://github.com/venomenon328/csc-x-tool/pull/65): ein pauschaler Desktop-Höhenwert, in den „typischerweise“ 15 Einträge passen.

Alle anderen geltenden Hauptlisten-, Bewertungs-, DnD-, Eigeneinreichungs-, Abschluss-, Snapshot- und Zuordnungsverträge bleiben erhalten. Diese Ablösung rechtfertigt keine erneute Implementierung der historischen Pakete. Die ergänzenden Entscheidungen stehen unter D-026/A-022 in [decisions.md](decisions.md); Entwicklungsbefugnisse und Prüfwege bestimmt das [Projektprofil](PROJECT_PROFILE.md) mit dem eingebundenen Workflow.

## 2. Desktopreferenz und messbares Ziel

Das Tool bleibt eine Einzelbenutzeranwendung. Für diesen Arbeitsplatz ist die vom einzigen Nutzer benannte Referenz verbindlich:

- Windows mit **2560 × 1440 Bildschirmauflösung**;
- **Vivaldi maximiert**, nicht im Vollbildmodus;
- **100 % Browserzoom**, entsprechend den bereitgestellten Screenshots.

Die Bildschirmauflösung ist nicht mit `window.innerWidth`/`window.innerHeight` gleichzusetzen. Die tatsächliche Windows-Anzeigeskalierung, Browserleisten und der verbleibende CSS-Inhaltsviewport sind beim Browsernachweis zu erfassen; insbesondere wird keine unbekannte Windows-Skalierung als 100 % behauptet. Das Layout verwendet die verfügbare Browserhöhe und keine fest codierte Bildschirmhöhe von 1440 CSS-Pixeln.

**Verbindliches Sichtbarkeitskriterium:** Sobald die Rangliste innerhalb der rechten Spalte nach oben gescrollt wurde, sind die Rangplätze 1 bis 15 gleichzeitig vollständig sichtbar. Dies gilt für die offene und die gesperrte Rangliste. Kein Teil der 15. Zeile, ihres Rahmens oder ihrer Bedienelemente wird abgeschnitten oder vom angehefteten Kopf verdeckt. Die Hauptspalte muss dafür nicht bewegt werden und darf an einer beliebigen Position stehen.

Player und 15 Rangplätze müssen ausdrücklich **nicht gleichzeitig** sichtbar sein. Platz für die sichtbare Grenze 15/16 darf genutzt werden, ist aber keine zusätzliche gleichzeitige Sichtbarkeitsgarantie. Schrift und Bedienflächen werden nicht verkleinert, um eine weiterhin ungeeignete Scrollstruktur zu kaschieren.

Kleinere Fenster und stärkerer Zoom müssen weiterhin vollständig bedienbar sein; für sie gilt keine Garantie von 15 gleichzeitig sichtbaren Zeilen. Der bestehende Wechsel zu untereinander angeordneten Bereichen bleibt erhalten. Ein eigenes mobiles Produktlayout entsteht nicht.

## 3. Zwei unabhängige Arbeitsbereiche

Im Desktop-Zweispaltenmodus ist die Voting-Arbeitsfläche auf den verfügbaren Inhaltsviewport begrenzt. Sie enthält genau einen vertikalen Arbeitsscrollbereich je Spalte. Gemeinsames Seitenscrolling ist keine Voraussetzung mehr, um rechts zur Rangliste oder zu ihren weiteren Inhalten zu gelangen.

### Linke Spalte

Der linke Scrollbereich enthält den Showkopf mit Rücknavigation, den vorhandenen Import beziehungsweise Zuordnungsimport samt Vorschau, die Beitragsbedienung und die vollständige Hauptliste. Ein hoher gemeinsamer Show-/Importkopf oberhalb beider Spalten darf nicht erneut die nutzbare rechte Höhe oder ihre unabhängige Erreichbarkeit bestimmen.

Die große Hauptlistenbreite und die vorhandenen Such-, Filter-, Sortier- und CRUD-Funktionen bleiben erhalten. Das Layout ändert weder die manuelle Arbeitsreihenfolge noch die fachliche Zuordnung eines Beitrags.

### Rechte Spalte

Player, Eigeneinreichungsbereich, Vorheriger/Nächster, gegebenenfalls Teilnehmerzuordnung, Rangliste und nachgelagerte Snapshot-/Ausgabefunktionen werden über denselben rechten Spaltenscrollbereich erreicht.

Die innere Rangliste erhält im Desktop-Zweispaltenmodus weder einen zusätzlichen eigenen vertikalen Scrollbalken noch eine pauschale Höhenbegrenzung wie den bisherigen Wert 900. Sie wächst mit ihren Einträgen. Damit kann der Benutzer den Player nach oben aus dem Ausschnitt scrollen und den verfügbaren Platz für die Rangliste nutzen.

Der Ranglistenkopf mit den bestehenden Vorschlags-/Abschluss-/Wiederöffnungsaktionen bleibt beim Erreichen der oberen Spaltenkante während der Arbeit in der Rangliste angeheftet. Er darf weder Rangzeilen noch Einfügeindikatoren oder Dropziele verdecken. Es entsteht keine neue Aktionsleiste und kein dauerhaft angehefteter Player.

### Scroll- und Zustandsverhalten

Normales Scrollen wirkt nur auf die angesprochene Spalte. An deren oberem oder unterem Ende wird es nicht an die andere Spalte oder eine gemeinsame Arbeitsseite weitergereicht. Beide Bereiche bleiben auch mit Tastaturfokus erreichbar und scrollbar; bestehende Formulareingaben und Dialogbedienung dürfen dadurch nicht beeinträchtigt werden.

Auswahl, Bewertungsänderung, Reorder und normale Re-Renders setzen die Scrollpositionen nicht ungefragt zurück. Filter oder temporäre Sortierung dürfen nicht zusätzlich die andere Spalte bewegen. Notwendiges Begrenzen einer Scrollposition nach Inhaltsverkürzung/Fensteränderung und gezieltes Fokus- oder DnD-Scrolling sind keine solchen unbeabsichtigten Sprünge. Eine neue Speicherung von Scrollpositionen über Reload/Neustart wird nicht eingeführt.

## 4. Player und Drag-and-drop

Reines Scrollen bewegt den vorhandenen Player nur aus dem sichtbaren Ausschnitt: Die Instanz wird nicht entfernt oder neu erzeugt, die Quelle nicht neu gesetzt und die Wiedergabe nicht neu gestartet. Die weiterhin gemeinsame aktive Beitrags-ID steuert Hauptliste, Rangliste und Player. Ein bewusster Songwechsel behält seine bisherige Bedeutung. Externer Link und Einbettungsfallback bleiben vorhanden und durch Zurückscrollen erreichbar.

Die vorhandene DnD-Bibliothek `@hello-pangea/dnd` und der gemeinsame DnD-Kontext werden beibehalten. Jeder Dropbereich besitzt im Desktopmodus nur eine vertikale Scroll-Ebene: die jeweilige Spalte. Die Lösung darf nicht lediglich um die bisher scrollbar begrenzte Rangliste einen weiteren Scrollcontainer legen. Auch unbeabsichtigte Scrollcontainer durch übergeordnete Overflow-Regeln sind zu prüfen.

Unverändert erhalten bleiben:

- Hauptliste → Hauptliste: nur im bisher erlaubten manuellen, ungefilterten Modus; verändert ausschließlich die Arbeitsreihenfolge;
- Hauptliste → Rangliste: auch aus Such-, Filter- und temporären Sortieransichten; Aufnahme oder Verschieben ohne Duplikat;
- Rangliste → Rangliste: nur Rangfolge ändern;
- Rangliste → Hauptliste beziehungsweise Entfernen per Icon: nur den Rang entfernen, nicht die Hauptlistenposition verändern;
- Auto-Scroll an den relevanten Spaltenrändern, eindeutige Einfügeindikatoren, stabile Grenze 15/16 und die großzügige bestehende End-Dropzone;
- vollständige atomare Reorder-Requests, getrennte bestätigte Ordnungen und gezieltes Rollback bei Speicherfehlern.

Die Fachverträge aus [architecture.md](architecture.md), insbesondere Reorder und Snapshot, werden nicht durch CSS- oder DOM-Umbauten verändert. Scrolländerungen, sichtbare Rangnummern und Fokus dürfen keine zusätzlichen fachlichen Schreibvorgänge auslösen.

## 5. Abgrenzung

Das ist eine begrenzte Frontend-Layoutänderung, kein Voting-Redesign. Backend, API, Datenbank, Migrationen, Importparser, Rankingberechnung, Bewertungen, Exportformate und persistierte Daten bleiben unverändert. Eigene Einreichung, abgeschlossene Abstimmung, Vorschlag, Abschlusswarnungen, Zuordnungen, Snapshots, Kopieren, Textausgabe und Historie bleiben erreichbar und fachlich unverändert.

Notwendige Anpassungen des gemeinsamen Seitenrahmens sind auf den Voting-Layoutvertrag zu begrenzen. Kandidaten-, Übersichts-, Teilnehmer- und Datenansicht behalten ihr bisheriges Verhalten. Keine neue Bibliothek, keine verschiebbare Trennleiste, kein Fokusmodus, keine vorsorgliche Virtualisierung und kein zweiter Player.

Tooltips, Menüs, Dialoge, Fokusmarkierungen und Drag-Feedback dürfen nicht abgeschnitten werden. Die vorhandenen Theme- und Komponentenregeln bleiben maßgeblich.

## 6. Abnahmevertrag

Layout, DnD-Integration, Regressionstests und Nachweise bilden ein zusammenhängendes Implementierungspaket. Ein zeitweise defektes DnD-Verhalten wird nicht als unabhängig lieferbarer Layout-Zwischenschritt akzeptiert.

Vor dem späteren Produktmerge sind der aktuelle Remote-Prüfpfad **Build / Root build** und die manuelle Windows-/Vivaldi-Abnahme erforderlich. Verantwortlich für die reale Browserabnahme ist der Nutzer; der Implementierungs-PR liefert die Prüfanleitung. Die dokumentierten Szenarien prüfen unabhängiges Scrollen, vollständig sichtbare Top 15 offen/gesperrt, Playerkontinuität, alle DnD-Richtungen mit Auto-Scroll und Endzone, Auswahl-/Fehlerfälle sowie Erreichbarkeit der übrigen Funktionen und kleinerer Layouts.

Der Browsernachweis nennt den getesteten Commit, Vivaldi-Version, Bildschirmauflösung, maximierten Zustand, Browserzoom, Windows-Skalierung und tatsächlichen CSS-Inhaltsviewport. Eine Darstellung mit 15 vorhandenen DOM-Elementen ist kein geometrischer Sichtbarkeitsnachweis. Automatisierte Chromium-Nachweise können ergänzen, ersetzen aber keine tatsächlich ausgeführte manuelle Vivaldi-Abnahme.

Prüfdaten bleiben synthetisch und isoliert: ungefähr 30 Beiträge als Normalfall, 100 Beiträge gemäß bestehendem Größenvertrag sowie leeres, kurzes, genau 15 und länger gefülltes Ranking. Nutzerscreenshots, reale Teilnehmer-/Bewertungsdaten und private Exporte werden nicht als öffentliche Fixtures veröffentlicht. Eine neue große Browser-Testinfrastruktur wird für dieses Paket nicht vorausgesetzt.

Das neue UI-Abnahmegate gilt nicht für einen reinen Dokumentations-PR ohne Produktänderung. Der bestehende verpflichtende Buildpfad und die Mergefreigabe bleiben auch dort maßgeblich. Die Spezifikationsfreigabe allein ist keine Implementierungs- oder Mergefreigabe.
