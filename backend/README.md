# Backend

Dieses Modul enthält das lokale Spring-Boot-Backend. Es liefert die API und im Produktionsbuild die vom Frontend-Modul bereitgestellte SPA aus.

## Verantwortlichkeiten

- REST-API und Auslieferung des gebauten Frontends
- `GET /api/system/health` als technischer Health-Endpunkt
- lokale SQLite-Datasource mit Liquibase-Migrationen und verbindungslokalen SQLite-Regeln
- `GET /api/shows` und `PATCH /api/shows/{showId}` für die Mottoshow-Übersicht
- einheitliches API-Fehlerobjekt als Basis für spätere Endpunkte
- Auslieferung der gebauten React-SPA unter derselben Origin
- explizite SPA-Weiterleitung nur für die vorgesehenen Browserrouten

## P1-Paketgrenze

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

P1 enthält nur die lokale Datenbasis und die zwölf Mottoshows. Tabellen und Fachlogik für Kandidaten, Teilnehmer, Beiträge, Ranking, Ergebnisse, Sicherungen und den Launcher folgen erst gemäß Roadmap.

## Ressourcen

```text
src/main/resources/
├── application.yml
├── db/changelog/
└── static/
```

Das Frontend-Modul paketiert die Vite-Ausgabe unter `META-INF/resources`; Spring Boot findet sie als statische Anwendung auf dem Klassenpfad. Im Entwicklungsbetrieb kann Vite einen eigenen Dev-Server verwenden; im Produktionsbetrieb existiert nur der Spring-Boot-Prozess.

## Tests

Der Smoke-Test startet das Backend mit dem paketierten Frontend und prüft `/`, alle vorgesehenen direkten SPA-Routen, `/api/system/health` und den Schutz davor, dass ein unbekannter `/api/**`-Pfad im SPA-Fallback landet.

Migration-, Repository- und WAL-Tests verwenden echte temporäre SQLite-Dateien. Der Artifact-Smoke-Test setzt ebenfalls einen temporären Storage Root und prüft zusätzlich `/api/shows`.
