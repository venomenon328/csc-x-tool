# Release-Checkliste 0.1.0

Diese Checkliste trennt automatisiert nachgewiesene Punkte von der verpflichtenden manuellen Windows-/Vivaldi-Abnahme. Ein Punkt wird nur als erledigt markiert, wenn der jeweilige Test tatsächlich ausgeführt wurde.

## Automatisiert

- [ ] Root-Verifikation auf Windows: `.\scripts\mvn-safe.cmd clean verify` (genau einmal ausgeführt; der Lauf deckte vor der Korrektur TypeScript-/Lint-Fehler auf und darf gemäß Auftrag nicht wiederholt werden).
- [x] App-Image und MSI: `.\launcher\packaging\build-release.ps1 -Clean`
- [x] Paketierter Smoke: `.\launcher\packaging\smoke-release.ps1`
- [x] Nachweis: Start ohne externes Java/Node, Loopback-Bindung, Runtime-Datei, Zweitstart, Schreib-/Lesezyklus, Backup/Restore, CSRF-Shutdown und Neustart.
- [x] Nachweis: frische per-user Installation, synthetisches 0.1.1-Upgrade und Deinstallation bei erhaltenem externem Storage.
- [x] MSI `CSC-X-Tool-0.1.0.msi` und SHA-256-Prüfsumme erzeugt.

## Manuelle Windows-/Vivaldi-Abnahme

- [ ] Über den installierten Startmenüeintrag starten; keine sichtbare Dauerkonsole.
- [ ] Vivaldi ist als Windows-Standardbrowser konfiguriert und öffnet die Anwendung tatsächlich.
- [ ] Zweiter Start öffnet/fokussiert dieselbe Anwendung ohne zweite Backendinstanz.
- [ ] Alle Hauptbereiche: Übersicht, Kandidaten, Voting, Teilnehmer, Ergebnis und Daten/Sicherungen navigieren.
- [ ] Realistischen CSC-Forumblock als Rich Clipboard per `Strg+V` einfügen und Linkziele prüfen.
- [ ] YouTube-Einbettung und vorgesehenen externen Fallback prüfen.
- [ ] Kandidaten und Votingliste per Drag-and-drop prüfen.
- [x] #40 Kandidaten-DnD mit mindestens zehn Kandidaten einschließlich eines ausgeblendeten verworfenen Kandidaten prüfen; Reihenfolge nach Drop, Reload und Anwendungsneustart in Vivaldi unter Windows bestätigt (28.08.2026).
- [ ] Fachlichen Kernpfad aus Spezifikation §20 mit realistischen Daten bis zum Ergebnisabschluss durchführen.
- [ ] Backup/Restore, Neustart und `Anwendung beenden` im installierten Produkt prüfen.
- [ ] Upgrade und Deinstallation mit realem `%LOCALAPPDATA%/CSC-X-Tool/` ohne Datenverlust prüfen.

## Freigabehinweis

Der 0.1.0-MSI ist unsigniert. Vor einer externen Verteilung den erwartbaren SmartScreen-Hinweis im Release-Hinweis erwähnen. Kein `v0.1.0`-Tag und kein finales GitHub Release entstehen aus dem Arbeitsbranch.
