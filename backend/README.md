# Backend

Dieser Ordner ist für das lokale Spring-Boot-Backend vorgesehen.

## Verantwortlichkeiten

- REST-API und Auslieferung des gebauten Frontends
- SQLite-Persistenz
- Liquibase-Migrationen
- fachliche Validierungen
- Beitragsblockimport aus Rich-Text-/HTML- und Plaintext-Zwischenablagedaten
- Importvorschau und Parserwarnungen
- Kandidaten- und Ranking-Reihenfolgen
- Top-15-Snapshots
- Teilnehmerzuordnung
- Ergebniserfassung und Summen
- Backup, Restore und Exporte
- Health-Check und kontrolliertes Herunterfahren

## Geplante fachliche Module

```text
src/main/java/de/venomenon/cscxtool/
├── show/
├── candidate/
├── participant/
├── entry/
├── ballot/
├── result/
├── backup/
├── system/
└── shared/
```

Die Struktur ist vertikal nach Fachfunktion gegliedert. Gemeinsame Infrastruktur gehört nur dann nach `shared`, wenn sie tatsächlich von mehreren Modulen benötigt wird; `shared` ist kein höflicher Name für eine Gerümpelschublade.

## Beitragsblockparser

Das `entry`-Modul erhält eine klar getrennte Import-Preview-Komponente. Das Frontend liefert aus einem vom Benutzer ausgelösten Paste-Event nach Möglichkeit sowohl `text/html` als auch `text/plain`.

Der Parser priorisiert:

1. HTML mit anklickbaren Links
2. markdownartige Links
3. Plaintext mit expliziter URL
4. Plaintext ohne URL als unvollständige Preview

Im HTML-Normalfall werden Linktext und `href` extrahiert. Der sichtbare CSC-Linktext wird anschließend als `Interpret - Titel` interpretiert. Unsichere Trennungen oder ungewöhnliche URLs erzeugen Warnungen und bleiben vor dem Import korrigierbar.

Clipboard-HTML gilt vollständig als nicht vertrauenswürdig. Es wird weder gerendert noch dauerhaft gespeichert. Für serverseitige HTML-Verarbeitung soll beim Bootstrap ein kleiner Parser wie jsoup geprüft werden; Regex auf beliebiges HTML ist nicht die Zielarchitektur.

## Geplante Ressourcen

```text
src/main/resources/
├── application.yml
├── db/changelog/
└── static/
```

`static` enthält im paketierten Build das erzeugte Frontend. Im Entwicklungsbetrieb darf Vite einen eigenen Dev-Server verwenden; im installierten Produkt existiert nur der Spring-Boot-Prozess.

## Testschwerpunkte

- reale temporäre SQLite-Datenbanken
- Migrationen von leer bis aktuell
- Reorder-Transaktionen
- Abschlussvalidierungen
- Snapshot-Integrität
- Punktwerte und Ergebniszustände
- Backup und Restore
- Parser mit realistischen HTML-Clipboard-Fixtures
- Markdown- und Plaintext-Fallbacks
- Bindestriche und Unicode-Trenner in Linktexten
- fehlende und ungewöhnliche Linkziele
- Importvorschau ohne stillen Datenverlust

## Noch nicht enthalten

In diesem Dokumentations-Bootstrap werden bewusst noch keine Builddateien, Anwendungsklassen oder Migrationen angelegt. Sie folgen in einem eigenen technischen Bootstrap-Inkrement.