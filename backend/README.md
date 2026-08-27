# Backend

Dieser Ordner ist für das lokale Spring-Boot-Backend vorgesehen.

## Verantwortlichkeiten

- REST-API und Auslieferung des gebauten Frontends
- SQLite-Persistenz
- Liquibase-Migrationen
- fachliche Validierungen
- Textblockimport und Importvorschau
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
- Parser mit realistischen Textressourcen

## Noch nicht enthalten

In diesem Dokumentations-Bootstrap werden bewusst noch keine Builddateien, Anwendungsklassen oder Migrationen angelegt. Sie folgen in einem eigenen technischen Bootstrap-Inkrement.
