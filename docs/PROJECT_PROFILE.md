# Projektprofil: CSC X Tool

## Zweck und Quellen

`venomenon328/csc-x-tool` ist eine lokale Einzelbenutzer-Webanwendung für den CyBoard Song Contest. Der [Workflow](dev-rules/WORKFLOW.md) regelt die Entwicklung; dieses Profil konkretisiert Quellen, technische Grenzen und Prüfungen. Die [Herkunftsnotiz](DEV_RULES_ADOPTION.md) dokumentiert die Einführung.

Bei Produktänderungen die betroffenen vollständigen Abschnitte der [Produktspezifikation](specification.md), [Entscheidungen](decisions.md) und [Architektur](architecture.md) lesen. Die im Issue ausdrücklich vorgeschriebenen vollständigen Dokumente bleiben vollständig zu lesen. Zusätzlich nach Gegenstand:

| Gegenstand | Fachliche Pflichtquelle |
| --- | --- |
| Historische Wettbewerbe, Stimmzettel und Analyseexport | [Erweiterungsspezifikation](historical-contests-ballots-analysis.md) |
| Externe Profile, Bewertungen und Tippspiel | [Analyseprotokoll](external-ai-analysis.md) und [Analyse-Einstieg](ai-workflows/README.md) mit dem passenden konkreten Ablauf |
| Herkunft fachlicher Referenzen | [Quellenabgrenzung](reference/README.md) |
| Launcher, Installer oder Release | [Paketierung](../launcher/packaging/README.md) und [Release-Checkliste](release-checklist-0.1.0.md) |
| Build- oder Ressourcenfragen | Tatsächliche [Build-CI](../.github/workflows/build.yml), [Windows-Release-CI](../.github/workflows/windows-release.yml) und [Werkzeugdokumentation](../scripts/README-resource-safety.md) |

Reine Prozessdokumentation verlangt keine erneute komplette Produktanalyse. Aktueller Lieferumfang und Status stehen im Issue/PR. Ältere Roadmap-/Bootstrap-Statusangaben sind kein Beleg des aktuellen Implementierungsstands. Baseline und ausdrücklich beschlossene Erweiterungen gemeinsam berücksichtigen; keine ältere Modellskizze gegen eine spätere Fachentscheidung ausspielen.

## Begründete technische Grenzen

Die vorhandene Architektur bleibt unverändert: Spring-Boot-Prozess mit gebündeltem React-/TypeScript-Frontend, gleiche Origin, Loopback-Bindung, lokale SQLite-Datei und explizite JDBC-/Liquibase-Persistenz. Keine neue Laufzeitplattform oder Datenbank durch diese Regelintegration. Integritäts-, Backup-, Restore- und Exportkompatibilität werden anhand der jeweiligen Fachverträge geprüft, nicht durch eine pauschale Prozessausnahme ersetzt.

Das Verbot lokaler Agenten-Arbeitslasten auf der Windows-Workstation wird nach Bewertung bewusst beibehalten: frühere Läufe haben die Arbeitsfähigkeit des Rechners beeinträchtigt, ohne dass die Ursache belastbar geklärt wäre. Nicht durch Stresstests diagnostizieren. Verboten sind dort Builds, Tests, Dependency-Installationen, Lint/Typecheck, direkte Toolbinaries, automatisierte Browser/GUI, Watch-Modi und Java-/Node-Devserver zur Verifikation. Die vorhandenen Safe-Wrapper schaffen keine Ausnahme. Eine Ausnahme benötigt eine ausdrückliche Freigabe für den konkreten Diagnosebefehl.

Lesen, Editieren und leichte Git-Operationen sind lokal erlaubt. Alle automatisierten Prüfungen laufen auf GitHub Actions oder einem geeigneten anderen Remote-Runner; das Verbot betrifft die lokale Workstation, nicht den vorhandenen Windows-CI-Runner. Allgemeine Entwicklerbefehle in README und Werkzeugdokumentation sind daher keine lokale Agentenausführungserlaubnis.

Private Teilnehmer-, Stimmzettel-, Zuordnungs- und Analyseexporte gehören nicht in das öffentliche Produktrepository. Tests verwenden isolierte Daten. Eine Veröffentlichung in private Analyseablagen benötigt den entsprechenden Auftrag und ist keine Nebenwirkung eines Code-Commits.

## Prüfpfad

Vor Merge ist der aktuelle PR-Prüfpfad **Build / Root build** verpflichtend. Er führt remote `./mvnw clean verify` aus und enthält die Backend- sowie Frontendprüfungen. Keine komplette Frontendsequenz zusätzlich unmittelbar davor oder danach aus Ritual ausführen. Konkrete zusätzliche Tests richten sich nach betroffenem Verhalten und Issue.

Den vollständigen Paketdiff und seine Dateiverweise prüfen; Whitespaceprüfung mit `git diff --check <Basis-SHA> <Head-SHA>` in einer erlaubten Umgebung. Bei Datenänderungen reale SQLite-, Migrations-, Transaktions- und Kompatibilitätsnachweise gemäß Fachvertrag; keine Ersatzdatenbank als stillschweigende Vereinfachung. CI muss zum aktuellen Head beziehungsweise zugehörigen Test-Merge-Stand passen. Fehlgeschlagene, fehlende und übersprungene Pflichtprüfungen sind kein Erfolg.

Bei dieser reinen Dokumenteinführung bleiben Build, Testcode und Workflows unverändert. Der bestehende vollständige CI-Lauf bleibt Pflicht; eine erneute manuelle Abnahme unveränderten Produktverhaltens ist nicht erforderlich.

## Manuelle Abnahme und Release

Bei UI-/Browser-/Windows-relevanten Produktänderungen das konkrete Akzeptanzszenario im Issue/PR bestimmen. Erforderliche manuelle Produktabnahme vor Merge durchführen, soweit nicht ausdrücklich als Releasegate oder nicht blockierend entschieden. Vivaldi-/Installer-Releaseprüfungen aus der Release-Checkliste bleiben vor Freigabe des betreffenden Releases erforderlich. Automatisierte Chromium- oder Buildnachweise nicht als menschlichen Vivaldi-Test ausgeben.

Ein normaler Merge startet die Build-CI, kein produktives Deployment. Der getrennte Windows-Paketierungsworkflow wird durch ausdrücklichen manuellen Start oder `v*`-Tag ausgelöst und erzeugt Artefakte. Tag, Paketierung oder Veröffentlichung sind kein Bestandteil einer bloßen Mergefreigabe. Kein Eingriff in reale Benutzerdaten ohne passenden Auftrag.

## Prozesszuständigkeit und Branches

Der aktuelle Issue-Body bestimmt den Auftrag; dauerhafte Fachentscheidungen bleiben in den Fachquellen. Für allgemeine Quellenpriorität, Paket-/PR-Schnitt, Dokumentpflege, Review, Übergabe, Modellauswahl und Freigaben ersetzt der gemeinsame Workflow die entsprechenden Prozesspassagen des alten Implementierungsplans, insbesondere dessen Abschnitte 1, 2.5, 15 und 18. Fachliche Akzeptanzkriterien und Testszenarien dieser Dokumente werden dadurch nicht aufgehoben. Abschnitt 20 des ursprünglichen Plans ist ein historischer Bootstrap-Auftrag, keine automatisch auszuführende nächste Aufgabe.

Zielbranch `main`; neue Branches nach gemeinsamer Konvention. Vorbereitung erstellt standardmäßig noch keinen Branch/PR; ein Implementierungsauftrag erlaubt dies. Bereits ausdrücklich vereinbarte Arbeitsbranches und Abhängigkeiten bleiben gültig. PR bis zur Abnahme Draft; Standardmerge Squash nach Freigabe, keine automatische Branchlöschung. Technischen Branchschutz nicht als eingerichtet voraussetzen oder ungefragt ändern.

Modell-/Reasoning-Empfehlungen folgen nur der lokalen Regelkopie. Die frühere Datei `Codex-Empfehlung.txt` wird bei Umstellung der Projekteinstellungen als Pflichtquelle ersetzt. Die [bereitgestellten Einstellungen](CHATGPT_PROJECT_INSTRUCTIONS.md) sind erst nach dem Einführungsmerge zu aktivieren; ihre Ablage im Repository ändert die ChatGPT-Oberfläche nicht.
