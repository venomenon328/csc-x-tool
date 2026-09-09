# Agenteneinstieg für CSC X Tool

## Verbindlicher Einstieg

Bei Entwicklungsarbeit vollständig lesen:

1. [Gemeinsamen Workflow](docs/dev-rules/WORKFLOW.md).
2. [Projektprofil](docs/PROJECT_PROFILE.md) und die dort für den Auftrag bezeichneten Fachquellen.
3. Aktuellen vollständigen Issue-/Paket-Body, sofern vorhanden; bei Review oder Nacharbeit zusätzlich PR, tatsächlichen Diff und konkret benannten Reviewstand.

Quellen aus dem beauftragten Arbeitsbranch lesen, sonst aus dem aktuellen `main`. Vorhandene Bereichs-/Override-Regeln berücksichtigen. Bei einer neuen Idee kein bestehendes Issue voraussetzen. Fehlende Pflichtquellen nicht durch Erinnerungen ersetzen.

Nur vor noch auszuführender Implementierung beziehungsweise konkreten technischen Nacharbeiten zusätzlich [Modellauswahl](docs/dev-rules/MODEL_SELECTION.md) und [Modellkatalog](docs/dev-rules/MODEL_CATALOG.md) vollständig heranziehen. Keine rückblickende Empfehlung nach abgeschlossener Arbeit.

## Unmittelbar wichtige Grenzen

Auf der lokalen Windows-Workstation keine Agenten-Builds, Tests, Dependency-Installationen, Lint-/Typecheck-, automatisierten Browser-/GUI- oder Devserver-Arbeitslasten starten. Auch direkte Toolaufrufe und die vorhandenen Schutzwrapper sind keine Umgehung. Automatisierte Verifikation erfolgt remote; die genaue Abgrenzung steht im [Projektprofil](docs/PROJECT_PROFILE.md).

Keine echten Benutzerbestände oder privaten Analyseexporte als Testdaten verwenden oder im öffentlichen Repository veröffentlichen. Fachliche und technische Produktverträge bleiben in ihren jeweiligen Dokumenten; diese Einführung ändert sie nicht.

## Fachliche CSC-Analysen

Kandidatenbewertung und Tippspielanalyse sind keine Implementierungsaufträge. Dafür [Analyse-Einstieg](docs/ai-workflows/README.md) und dessen auftragsbezogene Pflichtquellen vollständig lesen. Die privaten Analyseexporte bleiben fachliche Datenquellen; der Verzicht auf eine Drive-Laufzeitabhängigkeit für Entwicklungsregeln verbietet diese Datenquelle nicht.

## Code Review Rules

Insbesondere Datenintegrität, Import-/Export-/Restore-Verträge, Trennung von aktiven und historischen Wettbewerbsdaten sowie die korrekte Zuordnung von CI- und manuellen Nachweisen prüfen. Allgemeine Befugnisse, Reviewkategorien und Mergegrenzen definiert ausschließlich die eingebundene Workflowfassung. Herkunft und bewusst ersetzte Altregeln stehen in [DEV_RULES_ADOPTION.md](docs/DEV_RULES_ADOPTION.md).
