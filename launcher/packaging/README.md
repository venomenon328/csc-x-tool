# Windows-Release-Build

Die kanonischen Windows-Einstiegspunkte sind:

```powershell
.\launcher\packaging\build-release.ps1 -Clean
.\launcher\packaging\smoke-release.ps1
```

`build-release.ps1` baut zunächst das produktive Boot-JAR über `scripts/mvn-safe.cmd`, erstellt daraus das App-Image und erzeugt anschließend den per-user MSI-Installer. Es erwartet JDK 21 mit `jpackage` sowie WiX `3.14.1.20250415` mit `candle.exe` und `light.exe` im `PATH`. Ein abweichendes WiX-bin-Verzeichnis kann mit `-WixBin` übergeben werden. WiX ist nur eine Build-Voraussetzung.

Das Ergebnis liegt unter `launcher/packaging/output/`:

- `app-image/CSC X Tool/`
- `installer/CSC-X-Tool-0.1.0.msi`
- `installer/CSC-X-Tool-0.1.0.msi.sha256`

`smoke-release.ps1` verwendet einen temporären Storage Root und die ausschließlich für Tests vorgesehene Browserunterdrückung. Es prüft Start ohne externes Java/Node, `instance.json`, Loopback-Bindung, zweiten Launcher-Aufruf, API-Schreib-/Lesezyklus, Backup/Restore, CSRF-Shutdown, Neustart und optional frische per-user Installation, synthetisches 0.1.1-Upgrade sowie Deinstallation bei erhaltenem externem Storage. Mit `-SkipInstaller` kann ausschließlich der App-Image-Smoke isoliert werden.

Für ein nicht im `PATH` installiertes WiX-Binärverzeichnis wird derselbe Pfad an beide Schritte übergeben:

```powershell
.\launcher\packaging\build-release.ps1 -Clean -WixBin C:\tools\wix314\bin
.\launcher\packaging\smoke-release.ps1 -WixBin C:\tools\wix314\bin
```

Der feste Upgrade-Code in `release.psd1` bleibt für die gesamte 0.1.x-Linie unverändert. Der MSI ist für 0.1.0 absichtlich unsigniert; Windows/SmartScreen kann deshalb einen Warnhinweis anzeigen.
