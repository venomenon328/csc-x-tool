# Arbeitsabläufe für externe KI-Analysen

**Stand:** 2026-09-01  
**Status:** verbindliche operative Ergänzung zu [`../external-ai-analysis.md`](../external-ai-analysis.md)

Dieses Verzeichnis enthält die ausführbaren Arbeitsabläufe für externe KI-Analysen. Das allgemeine Analyseprotokoll bleibt für Datenmodell, Profilbildung, Gewichtung, Unsicherheit und Versionierung maßgeblich. Die Dateien in diesem Verzeichnis konkretisieren die Eingabeübergabe und die beiden wiederkehrenden Anwendungsfälle.

## Enthaltene Arbeitsabläufe

- [`candidate-evaluation.md`](candidate-evaluation.md): ausführliche Bewertung bereits vom Nutzer ausgewählter Kandidaten unter Einbeziehung des aktiven Teilnehmerfelds.
- [`tips-game-analysis.md`](tips-game-analysis.md): evidenzbasierte Zuordnung einer anonymen Songliste zu möglichen Einreichenden des aktiven Wettbewerbs.

## Verlässliche Übergabe des Analyseexports

### Bevorzugter Chat-Upload

Für die tatsächliche KI-Auswertung werden aus dem Analyse-ZIP mindestens diese Dateien entpackt und **als einzelne Textdateien** übergeben:

1. `analysis.json` als kanonische Datenquelle;
2. `manifest.json` für Formatversion, Erzeugungszeitpunkt und Scope.

Die CSV-Dateien können zusätzlich angehängt werden, sind aber kein Ersatz für `analysis.json`, solange dieses lesbar vorliegt.

Der ZIP-Upload bleibt als Archiv sinnvoll, ist als alleinige Übergabe jedoch nicht verlässlich genug. Binärdateien können je nach Laufzeit zwar registriert, aber weder in das aktive Dateisystem eingebunden noch inhaltlich indexiert werden. Vor Beginn einer Analyse muss deshalb ausdrücklich bestätigt werden, dass `analysis.json` tatsächlich gelesen wurde; der bloße sichtbare Uploadname genügt nicht.

### Persistente Ablage in Google Drive

Für wiederkehrende Analysen ist eine private Drive-Ablage die bevorzugte dauerhafte Lösung. Empfohlene Struktur:

```text
CSC/
  Analyseexport/
    current/
      analysis.json
      analysis.txt
      manifest.json
      manifest.txt
      participants.csv
      participations.csv
      botb-selections.csv
      entries.csv
      ballots.csv
      assessment-matrix.csv
      upload-meta.json
    archive/
      <generatedAt>/
        ...
```

`current` enthält immer den zuletzt freigegebenen vollständigen Export. `archive` bewahrt nur die tatsächlich veröffentlichten vorherigen `current`-Stände auf. `upload-meta.json` ergänzt insbesondere Dateiname und SHA-256 des ursprünglichen ZIPs sowie SHA-256 der kanonischen JSON-Kerndateien. Ein neuer Analyselauf protokolliert `generatedAt`, Exportformat, Formatversion, Dateiname und soweit verfügbar SHA-256 des verwendeten Stands.

#### Connectorfreundliche Textspiegel

Google Drive kann rohe JSON- oder CSV-Dateien korrekt synchronisieren, ohne dass ein angebundener Analyse-Connector diese Dateitypen zuverlässig findet oder als Text ausliefert. Deshalb erzeugt der lokale Publisher zusätzlich:

- `analysis.txt` als byteidentischen Spiegel von `analysis.json`;
- `manifest.txt` als byteidentischen Spiegel von `manifest.json`.

Diese Dateien sind **keine zweite fachliche Datenquelle**. `analysis.json` und `manifest.json` bleiben kanonisch. Der Publisher kopiert die Bytes unverändert und vergleicht anschließend jeweils SHA-256 von Original und Spiegel. Nur bei Gleichheit gilt der Spiegel als veröffentlicht.

Für einen über Google Drive angebundenen KI-Lauf gilt deshalb die Lesereihenfolge:

1. `current/analysis.json` und `current/manifest.json`, wenn der Connector beide tatsächlich lesen kann;
2. andernfalls `current/analysis.txt` und `current/manifest.txt` als geprüfte Transportspiegel derselben Inhalte;
3. erst wenn auch diese nicht vollständig lesbar sind, der dokumentierte CSV-Fallback.

Bei Verwendung der `.txt`-Spiegel wird im Analyselauf ausdrücklich vermerkt, dass transporttechnisch die Spiegel gelesen wurden, fachlich aber weiterhin der Inhalt des kanonischen JSON-Vertrags ausgewertet wird.

### Automatisierte Veröffentlichung über Google Drive for Desktop

Unter Windows übernimmt [`../../scripts/publish-analysis-export.ps1`](../../scripts/publish-analysis-export.ps1) die lokale Veröffentlichung in einen von Google Drive for Desktop synchronisierten Ordner.

Einmalig werden Download- und Drive-Verzeichnis in einer lokalen, nicht versionierten Konfiguration unter `%APPDATA%\CSC X Tool\analysis-export-publisher.json` gespeichert:

```powershell
.\scripts\publish-analysis-export.ps1 `
  -Configure `
  -DownloadDirectory '<Download-Verzeichnis>' `
  -DriveDirectory '<lokaler Drive-Pfad zu CSC\Analyseexport>'
```

Der Konfigurationsaufruf veröffentlicht zugleich den neuesten passenden Analyseexport. Danach genügt aus dem Repository-Root:

```powershell
.\scripts\publish-analysis-export.ps1
```

Optional kann eine konkrete Datei gewählt werden:

```powershell
.\scripts\publish-analysis-export.ps1 -ZipPath '<Pfad zu analysis-....zip>'
```

Das Script:

1. wählt standardmäßig das neueste `analysis-*.zip` aus dem konfigurierten Download-Verzeichnis;
2. entpackt in ein temporäres lokales Verzeichnis;
3. prüft `manifest.json`, `analysis.json`, Format, Formatversion, `generatedAt` und alle im Manifest gelisteten Dateien;
4. berechnet SHA-256 des ursprünglichen ZIPs;
5. kopiert den neuen Stand zunächst nach `_incoming` im Drive-Verzeichnis und prüft die Kern-Dateien erneut;
6. erzeugt `analysis.txt` und `manifest.txt` als byteidentische Connector-Spiegel und prüft die Identität per SHA-256;
7. verschiebt einen vorhandenen `current`-Stand anhand seines `generatedAt` nach `archive`;
8. aktiviert den neuen Stand als `current` und schreibt abschließend `upload-meta.json`;
9. versucht bei einem Fehler während des Umschaltens den vorherigen `current`-Stand wiederherzustellen.

Wird dasselbe ZIP erneut veröffentlicht, erfolgt keine unnötige Archivrotation. Fehlende oder veraltete Connector-Spiegel und `upload-meta.json` werden trotzdem nachgezogen. Dadurch kann nach einem Publisher-Update der bereits aktuelle Export einfach erneut verarbeitet werden.

Lokale Maschinenpfade werden nicht im öffentlichen Repository gespeichert. Die Quell-ZIP wird nicht gelöscht.

Google Drive for Desktop synchronisiert die fertige lokale Struktur anschließend asynchron in die Cloud. Vor einer externen KI-Analyse muss deshalb abgewartet werden, bis Drive den Status `Aktuell` beziehungsweise `Up to date` meldet. Die Analyse selbst prüft zusätzlich den tatsächlich auf Drive lesbaren `current`-Stand; eine erfolgreiche lokale Script-Ausgabe allein beweist noch keinen abgeschlossenen Cloud-Sync.

### GitHub nur als private Textablage

Der öffentliche Produkt-Repository `venomenon328/csc-x-tool` enthält keine Analyseexporte. Teilnehmer-, Stimmzettel- und Zuordnungsdaten werden dort weder als ZIP noch entpackt committed.

Falls GitHub als persistente Quelle verwendet werden soll, ist ein **separates privates Daten-Repository** erforderlich. Dort werden die entpackten UTF-8-Dateien gespeichert, nicht das ZIP als alleinige Quelle. Empfohlene Struktur:

```text
current/
  analysis.json
  manifest.json
  *.csv
archive/<generatedAt>/
  ...
```

Ein Binär-ZIP im Repository ist für den GitHub-Connector nicht zuverlässig lesbar. Git LFS ist ebenfalls kein bevorzugter Analyseweg, weil ein Connector statt des Inhalts nur den LFS-Zeiger erhalten kann.

### Fallback bei sehr großen oder nicht indexierten JSON-Dateien

Kann `analysis.json` nicht direkt gelesen werden, wird bei Google-Drive-Ablage zuerst der byteidentische Transportspiegel `analysis.txt` zusammen mit `manifest.txt` verwendet.

Kann auch dieser trotz direkter Textübergabe beziehungsweise Drive-Ablage nicht vollständig gelesen werden, werden gemeinsam verwendet:

- `manifest.json` oder der byteidentische Spiegel `manifest.txt`;
- `participants.csv`;
- `participations.csv`;
- `entries.csv`;
- `ballots.csv`;
- `assessment-matrix.csv`;
- `botb-selections.csv`;
- optional `candidates.csv`.

Die Dateien müssen aus demselben Export stammen. Manuell zusammenkopierte Ausschnitte oder voneinander abweichende Exportstände sind nicht zulässig.

## Eingangsprüfung vor jedem Lauf

Vor der eigentlichen Analyse muss die KI knapp bestätigen:

- erkannter Exporttyp und `formatVersion`;
- `generatedAt`;
- Scope und enthaltene Contests/Shows;
- aktiver Wettbewerb;
- Zahl der aktiven Teilnahmen im aktiven Wettbewerb;
- ob kanonisches `analysis.json`, der geprüfte `analysis.txt`-Spiegel oder ein dokumentierter CSV-Fallback tatsächlich vollständig gelesen wurde;
- erkennbare Identitäts- oder Vollständigkeitsblocker.

Kann die Quelle nicht tatsächlich gelesen werden, darf kein neuer offizieller Profilstand erzeugt und kein vermeintlich datenbasierter Tipp ausgegeben werden.

## Gemeinsame Feldregel

Historische Daten dürfen Profile von Personen bilden, die heute wieder teilnehmen. Für feldbezogene Auswertungen zählt jedoch ausschließlich das **aktive Teilnehmerfeld des aktiven Wettbewerbs**.

Damit gilt:

- Historische-only-Teilnehmer sind keine Wähler, Unterstützer, Gegencluster oder Matching-Kandidaten des aktuellen Laufs.
- Sie gehen nicht in Feldabdeckung, Feldmittelwerte, erwartete Unterstützerzahlen oder Unsicherheitsnenner ein.
- Eine historische Teilnahme derselben heute aktiven Person bleibt selbstverständlich als Trainingssignal ihres Profils verwendbar.
- Inaktive Teilnahmen des aktiven Wettbewerbs werden ausgeschlossen.
- Bei der Bewertung der eigenen Einreichung wird zusätzlich die eigene aktive Teilnahme aus der Menge möglicher Punktegeber entfernt.

Dass jemand vor drei Jahren einmal teilgenommen hat, verleiht ihm also keine posthume Stimmkarte für den aktuellen Wettbewerb.

Historische BOTB-Interpretwahlen sind normalisierte Auswahlmodell-Ereignisse derselben `participant_id`, keine zusätzlichen aktuellen Teilnehmer oder Einreichungen. Sie verändern nie die Menge aktiver Teilnahmen des aktiven Wettbewerbs. Für historische Läufe gelten die in `external-ai-analysis.md` dokumentierten `knownSince`-Stichtagsregeln; ein fehlendes Datum ist kein automatisch zeitgerechtes Signal.
