# Einführung von dev-rules

## Herkunft und Aktivierung

| Merkmal | Wert |
| --- | --- |
| Quelle | `venomenon328/dev-rules`, Ordner `rules/` |
| Exakter Quellcommit | `a662d3c2c1ba004de5bb65fbd16f8091c4abcce4` |
| Paketversion | `0.1.0-rc.2` |
| Quell-/Ziel-Tree des Regelpakets | `76f2e0b84f447658b234e1ecb41328cb541ec0ce` |
| Ziel | `docs/dev-rules/`, fünf unveränderte Dateien |
| Einführungsauftrag | [Issue #168](https://github.com/venomenon328/csc-x-tool/issues/168), zugehöriger Einführungs-PR |
| Projektbasis | `d87c7f8684a25e49134a434f8c5e02cd4c8ee010` |

Eigentümerauftrag vom 9. September 2026: direkte Einführung ohne Pilot. Nur fachliche Altvorgaben besitzen im Abgleich automatischen Bestandsschutz. Nichtfachliche Vorgaben werden bewertet, nicht ungeprüft übernommen. Die technischen Entscheidungen unten sind bewusst begründete Fortführungen, keine Behauptung, dass jede alte Prozessregel fachlich sei.

Im Arbeitsbranch gilt die Struktur für diesen Einführungsauftrag. Projektweite Aktivierung mit dem freigegebenen Merge des verknüpften Einführungs-PRs; tatsächlichen Mergecommit und Nachweise im PR dokumentieren. Die ChatGPT-Projekteinstellungen werden separat nach diesem Merge durch den Nutzer mit dem [bereitgestellten Text](CHATGPT_PROJECT_INSTRUCTIONS.md) ersetzt. Diese Eingabe ist durch die Datei selbst nicht erledigt. Keine Pilot-, Release- oder Tag-Voraussetzung, kein automatisches Nachladen des zentralen `main`.

## Bewerteter Regelabgleich

| Bisherige Vorgabe | Behandlung und Grund |
| --- | --- |
| Fachliche Produkt-, Import-/Export-, Backup-/Restore- und Analyseverträge | Unverändert in den bestehenden Fachquellen; auftragsbezogen über das Projektprofil erreichbar. |
| Lokales Windows-Agenten-Testverbot | Beibehalten: konkret dokumentierte Beeinträchtigung der Arbeitsfähigkeit. Der sichere Nachweisweg ist Remote-CI. |
| Root-Gesamtbuild und getrennte Releaseprüfung | Beibehalten: reale integrierte Prüf- und Paketierungswege. Keine zusätzliche identische lokale Vollprüfung erforderlich. |
| Allgemeine Prozesspassagen in Implementierungsplan und README | Durch WORKFLOW und dieses Projektprofil ersetzt; keine zweite allgemeine Rangfolge oder PR-/Freigabelogik. Fachtests bleiben erhalten. |
| Veraltete operative Bootstrap-/Roadmap-Anweisungen | Kein aktueller Auftrag; aktuelle Issues/PRs sind für laufenden Scope maßgeblich. Keine rückwirkende Überarbeitung sämtlicher Historie. |
| Frühere externe Modellheuristik | Durch unveränderte lokale MODEL_SELECTION/MODEL_CATALOG ersetzt, einschließlich High-Standard und Qualitätsabwägung. |
| Private Drive-Analyseexporte | Kein Entwicklerregel-Duplikat: fachliche Datenquelle bleibt gemäß Analyseprotokoll zulässig; nicht öffentlich einchecken. |

Die bestehende Toolchain und technische Architektur bleiben erhalten, weil diese Einführung keine Produktmigration beauftragt. Fachliche Änderungen benötigen weiterhin eine konkrete Entscheidung, neue Prozesse keine pauschale Übernahme alter Regeln.

## Laufende Arbeit und Nachweis

Bestehende Issues, Branches und Abnahmen werden nicht zurückgesetzt. Beim nächsten Vorbereitungs-/Reviewübergang geltenden Branchstand und relevante Unterschiede prüfen. Noch alte Arbeitsbranches bei Bedarf bewusst integrieren oder einen befristeten Übergang im betreffenden Issue festhalten; keine stillschweigende Wahl zwischen widersprüchlichen Regelständen. Neue Features oder Mergefreigaben entstehen dadurch nicht.

Vor Abnahme die fünf Snapshot-Dateien anhand der genannten Quellversion vergleichen, neue lokale Pfade prüfen und den vollständigen Dokumentdiff gegen den Auftrag reviewen. CI-Belege und Ergebnis stehen mit Commitbezug im Einführungs-PR. Fachspezifikationen, Produktcode, Tests und CI werden durch diese Einführung nicht geändert. Die praktische Evaluation erfolgt an normalen Aufgaben, ohne zusätzliches Pilotgate.
