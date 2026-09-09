# Modellkatalog

**Extern geprüft: 2026-09-09.** Dieser Katalog enthält datierte Produktinformationen und lokale Startpunkte, keine Zusicherung der Verfügbarkeit im Nutzerkonto. Die stabilen Entscheidungsregeln stehen in [MODEL_SELECTION.md](MODEL_SELECTION.md).

## Dokumentierte Modelle und lokale Zuordnung

| Modell | Explizite Modell-ID | Lokaler Startpunkt, kein Leistungsversprechen |
| --- | --- | --- |
| GPT-5.6 Luna | `gpt-5.6-luna` | Eindeutige mechanische Änderungen ohne fachlichen oder stilistischen Gestaltungsspielraum |
| GPT-5.6 Terra | `gpt-5.6-terra` | Normale, klar definierte Feature-/Fixarbeit |
| GPT-5.6 Sol | `gpt-5.6-sol` | Anspruchsvolles Verständnis, differenzierte Redaktion/Kuratierung und schwierige Implementierung |
| GPT-6 Astra | `gpt-6-astra` | Schwierige, nicht sinnvoll weiter teilbare Gesamtprozesse |

Die Namen und IDs sind in der offiziellen Modellübersicht dokumentiert. Die konkrete Paketzuordnung ist unsere Heuristik, nicht das Ergebnis eigener Benchmarks. Sie enthält keine Obergrenzen: Zusätzlicher erwarteter Qualitätsnutzen kann auch bei kleinen Paketen eine stärkere Klasse rechtfertigen; maßgeblich ist [MODEL_SELECTION.md](MODEL_SELECTION.md). Keine feste ewige Whitelist: Bei Ablösung oder Nichtverfügbarkeit anhand offizieller Quellen und der tatsächlich verwendeten Oberfläche aktualisieren.

## Bezeichnungen und Grenzen

Die offizielle Codex-Dokumentation unterscheidet unter anderem Low/Light, Medium, High, Extra High und Max; Bezeichnungen und Auswahlmöglichkeiten hängen von der Oberfläche ab. In Empfehlungen die tatsächlich auswählbare Bezeichnung verwenden. Ultra wird dort als Mehragentenmodus beschrieben und ist nicht einfach eine weitere Stufe derselben Einzelausführung.

OpenAI weist auf Abhängigkeiten von Rollout, Anmeldung und Client hin. Diese Recherche bestätigt daher **nicht**, welche Kombination der Nutzer in seiner Installation auswählen kann. API-Angebot, Codex-Zugriff und Abrechnung nicht gleichsetzen. Preise und Quoten werden hier bewusst nicht festgeschrieben.

## Quellen und Aktualisierung

Offizielle Quellen, gelesen am oben genannten Prüfdatum:

- [Codex: Modelle, Reasoning und Verfügbarkeit](https://developers.openai.com/codex/models).
- [OpenAI API: Modellübersicht und IDs](https://developers.openai.com/api/docs/models).

Vor einer konkreten Empfehlung den Katalog gegen aktuelle offizielle Angaben und bekannte Clientverfügbarkeit prüfen, wenn Änderungen plausibel sind oder die bisherigen Informationen nicht ausreichen. Aktualisierte Erkenntnisse samt Datum dokumentieren. Ist aktuelle Prüfung nicht möglich, die Unsicherheit benennen; keine frei erfundenen Namen, Einstellungen oder Zugangsbehauptungen.
