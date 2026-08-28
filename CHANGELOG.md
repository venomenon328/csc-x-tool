# Changelog

Alle wesentlichen Änderungen dieses Projekts werden hier festgehalten.

## 0.1.0 - 2026-08-28

- Windows-Launcher mit OS-Dateisperre, Health-geprüfter Eininstanz-Semantik, atomarer Runtime-Information und Standardbrowser-Integration.
- Kontrolliertes, CSRF-geschütztes Beenden mit Warten auf den bestehenden fairen Datenbank-Lock.
- Same-Origin-/CSRF-Härtung, restriktive CSP und begrenztes lokales Rolling-Logging.
- Globale Suche über Kandidaten und Wettbewerbsbeiträge.
- Reproduzierbarer JDK-21-jpackage-App-Image-/MSI-Build samt Prüfsumme, CI-Workflow und paketiertem Windows-Smoke.

Der MSI ist in 0.1.0 absichtlich nicht code-signiert; Windows/SmartScreen kann deshalb einen Warnhinweis anzeigen.
