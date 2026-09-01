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
CSC X Tool/
  Analyseexport/
    current/
      analysis.json
      manifest.json
      participants.csv
      participations.csv
      entries.csv
      ballots.csv
      assessment-matrix.csv
    archive/
      2026-09-01T01-26-25Z/
        ...
```

`current` enthält immer den zuletzt freigegebenen vollständigen Export. `archive` bewahrt nur die tatsächlich für protokollierte Analyseläufe verwendeten Stände auf. Ein neuer Lauf muss `generatedAt`, Exportformat, Formatversion und Dateiname des verwendeten Stands protokollieren.

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

### Fallback bei sehr großen JSON-Dateien

Kann `analysis.json` trotz direkter Textübergabe nicht vollständig gelesen werden, werden gemeinsam verwendet:

- `manifest.json`
- `participants.csv`
- `participations.csv`
- `entries.csv`
- `ballots.csv`
- `assessment-matrix.csv`
- optional `candidates.csv`

Die Dateien müssen aus demselben Export stammen. Manuell zusammenkopierte Ausschnitte oder voneinander abweichende Exportstände sind nicht zulässig.

## Eingangsprüfung vor jedem Lauf

Vor der eigentlichen Analyse muss die KI knapp bestätigen:

- erkannter Exporttyp und `formatVersion`;
- `generatedAt`;
- Scope und enthaltene Contests/Shows;
- aktiver Wettbewerb;
- Zahl der aktiven Teilnahmen im aktiven Wettbewerb;
- ob `analysis.json` vollständig lesbar war oder ein dokumentierter Fallback verwendet wird;
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