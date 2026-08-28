[CmdletBinding()]
param(
    [ValidateSet('app-image', 'msi', 'all')]
    [string]$Target = 'all',
    [switch]$SkipBuild,
    [switch]$Clean,
    [string]$OutputDirectory,
    [string]$WixBin
)

$ErrorActionPreference = 'Stop'
$config = Import-PowerShellDataFile (Join-Path $PSScriptRoot 'release.psd1')
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot 'output'
}
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$icon = Join-Path $repositoryRoot 'launcher\assets\csc-x-tool.ico'
$jar = Join-Path $repositoryRoot "backend\target\csc-x-tool-backend-$($config.ApplicationVersion).jar"

function Invoke-ReleaseTool {
    param([string]$FilePath, [string[]]$Arguments)
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Der Release-Befehl '$FilePath' ist mit Exit-Code $LASTEXITCODE fehlgeschlagen."
    }
}

function Add-WixToPath {
    param([string]$RequestedWixBin)
    if ($RequestedWixBin) {
        $resolved = (Resolve-Path $RequestedWixBin).Path
        $env:Path = "$resolved;$env:Path"
    }
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    $light = Get-Command light.exe -ErrorAction SilentlyContinue
    if (-not $candle -or -not $light) {
        throw "WiX $($config.WixVersion) ist für den MSI-Build erforderlich. Installieren Sie es z. B. mit 'choco install wixtoolset --version $($config.WixVersion)' oder übergeben Sie -WixBin mit dem WiX-bin-Verzeichnis."
    }
    $candleVersion = (& $candle.Source '-?' 2>&1 | Out-String)
    if ($candleVersion -notmatch 'version 3\.14\.1\.') {
        throw "WiX 3.14.1 ist erforderlich, gefunden wurde: $($candleVersion.Trim())"
    }
}

if ($env:OS -ne 'Windows_NT') {
    throw 'Der Windows-Releasepfad muss unter Windows ausgeführt werden.'
}
if (-not (Test-Path $icon -PathType Leaf)) {
    throw "Das projektinterne Icon fehlt: $icon"
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw 'jpackage aus einem JDK 21 ist für den Release-Build erforderlich.'
}
$jpackageVersion = (& jpackage --version).Trim()
if (-not $jpackageVersion.StartsWith('21.')) {
    throw "jpackage 21 ist erforderlich, gefunden wurde '$jpackageVersion'."
}

if (-not $SkipBuild) {
    Invoke-ReleaseTool (Join-Path $repositoryRoot 'scripts\mvn-safe.cmd') @('-pl', 'backend', '-am', 'package', '-DskipTests')
}
if (-not (Test-Path $jar -PathType Leaf)) {
    throw "Das produktive Boot-JAR fehlt: $jar. Führen Sie den Build ohne -SkipBuild aus."
}

$output = [System.IO.Path]::GetFullPath($OutputDirectory)
$appImageDestination = Join-Path $output $config.AppImageDirectory
$installerDestination = Join-Path $output $config.InstallerDirectory
$jpackageInput = Join-Path $output '.jpackage-input'
if ($Clean -and (Test-Path $output)) {
    Remove-Item -LiteralPath $output -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $appImageDestination | Out-Null
New-Item -ItemType Directory -Force -Path $installerDestination | Out-Null
if (Test-Path $jpackageInput) {
    Remove-Item -LiteralPath $jpackageInput -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $jpackageInput | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $jpackageInput (Split-Path $jar -Leaf))
$appImage = Join-Path $appImageDestination $config.ApplicationName

if ($Target -in @('app-image', 'all')) {
    if (Test-Path $appImage) {
        Remove-Item -LiteralPath $appImage -Recurse -Force
    }
    Invoke-ReleaseTool 'jpackage' @(
        '--type', 'app-image',
        '--dest', $appImageDestination,
        '--name', $config.ApplicationName,
        '--app-version', $config.ApplicationVersion,
        '--input', $jpackageInput,
        '--main-jar', (Split-Path $jar -Leaf),
        '--icon', $icon,
        '--java-options', '-Dcsc-x-tool.desktop.enabled=true',
        '--java-options', '-Dserver.port=0'
    )
}

if ($Target -in @('msi', 'all')) {
    Add-WixToPath $WixBin
    if (-not (Test-Path $appImage -PathType Container)) {
        throw "Das getestete App-Image fehlt: $appImage. Erzeugen Sie es zuerst mit -Target app-image oder all."
    }
    Get-ChildItem -LiteralPath $installerDestination -Filter '*.msi' -File -ErrorAction SilentlyContinue | Remove-Item -Force
    Invoke-ReleaseTool 'jpackage' @(
        '--type', 'msi',
        '--dest', $installerDestination,
        '--name', $config.ApplicationName,
        '--app-version', $config.ApplicationVersion,
        '--app-image', $appImage,
        '--win-per-user-install',
        '--win-menu',
        '--win-menu-group', 'CSC-X-Tool',
        '--win-upgrade-uuid', $config.UpgradeUuid
    )
    $generatedMsi = Get-ChildItem -LiteralPath $installerDestination -Filter '*.msi' -File | Select-Object -First 1
    if (-not $generatedMsi) {
        throw 'jpackage hat kein MSI-Artefakt erzeugt.'
    }
    $releaseMsi = Join-Path $installerDestination "CSC-X-Tool-$($config.ApplicationVersion).msi"
    if ($generatedMsi.FullName -ne $releaseMsi) {
        Move-Item -LiteralPath $generatedMsi.FullName -Destination $releaseMsi -Force
    }
    $hash = Get-FileHash -LiteralPath $releaseMsi -Algorithm SHA256
    Set-Content -LiteralPath "$releaseMsi.sha256" -Value "$($hash.Hash.ToLowerInvariant()) *$(Split-Path $releaseMsi -Leaf)" -NoNewline
}

Write-Host "Release-Artefakte bereit unter: $output"
