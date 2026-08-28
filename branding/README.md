# CSC-X-Branding-Quelle

`source/csc-x-tool-original.png` ist die für Issue #43 freigegebene, kanonische Bildquelle.

- SHA-256: `231712672d01890bbaccf30f92f93096ba141f14680a3f44ce34914fecb1be2e`
- Abmessungen: 1254 × 1254 px
- Format: RGBA-PNG mit Transparenz

Die folgenden Verbrauchs-Assets entstehen ausschließlich per technisch notwendiger, deterministischer Ableitung aus dieser Datei:

- `frontend/src/assets/csc-x-tool-logo.png`: auf den nicht transparenten Bildbereich beschnittenes Web-Logo.
- `frontend/public/csc-x-tool.ico`: Favicon mit 16, 32, 48, 64, 128 und 256 px großen PNG-Icon-Einträgen.
- `launcher/assets/csc-x-tool.ico`: bytegleiche Kopie des Favicons für `jpackage`.

Auf einem Windows-System mit PowerShell und WPF-Unterstützung werden die Assets reproduzierbar aktualisiert mit:

```powershell
.\scripts\generate-branding-assets.ps1
```

Das Skript validiert vor jeder Ableitung die SHA-256-Prüfsumme und die erwarteten Dimensionen. Es entfernt nur vollständig transparente Außenränder, skaliert proportional mit transparenter Fläche und erstellt die ICO-Container selbst; es führt keine generative oder inhaltliche Bildbearbeitung aus.
