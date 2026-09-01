param(
    [string]$ZipPath,
    [string]$DownloadDirectory,
    [string]$DriveDirectory,
    [switch]$Configure
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptVersion = "1.0"
$ExpectedFormat = "csc-x-tool-analysis"
$ConfigDirectory = Join-Path $env:APPDATA "CSC X Tool"
$ConfigPath = Join-Path $ConfigDirectory "analysis-export-publisher.json"

function Read-JsonFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    try {
        return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    catch {
        throw "JSON-Datei ist ungueltig oder nicht lesbar: $Path`n$($_.Exception.Message)"
    }
}

function Write-Utf8Json {
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $json = $Value | ConvertTo-Json -Depth 10
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $json + [Environment]::NewLine, $utf8NoBom)
}

function Get-NormalizedDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Description wurde nicht gefunden: $Path"
    }

    return (Resolve-Path -LiteralPath $Path).Path
}

function Get-ArchiveId {
    param([Parameter(Mandatory = $true)][string]$GeneratedAt)

    try {
        $timestamp = [DateTimeOffset]::Parse(
            $GeneratedAt,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind
        )
    }
    catch {
        throw "generatedAt ist kein gueltiger Zeitstempel: $GeneratedAt"
    }

    return $timestamp.ToUniversalTime().ToString("yyyyMMdd'T'HHmmssfff'Z'")
}

function Assert-Package {
    param([Parameter(Mandatory = $true)][string]$PackageRoot)

    $manifestPath = Join-Path $PackageRoot "manifest.json"
    $analysisPath = Join-Path $PackageRoot "analysis.json"

    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "manifest.json fehlt im Analysepaket: $PackageRoot"
    }
    if (-not (Test-Path -LiteralPath $analysisPath -PathType Leaf)) {
        throw "analysis.json fehlt im Analysepaket: $PackageRoot"
    }

    $manifest = Read-JsonFile $manifestPath
    $analysis = Read-JsonFile $analysisPath

    if ($manifest.format -ne $ExpectedFormat) {
        throw "Falscher Exporttyp in manifest.json: '$($manifest.format)'. Erwartet: '$ExpectedFormat'."
    }
    if ($analysis.format -ne $ExpectedFormat) {
        throw "Falscher Exporttyp in analysis.json: '$($analysis.format)'. Erwartet: '$ExpectedFormat'."
    }
    if ($null -eq $manifest.formatVersion -or $null -eq $analysis.formatVersion) {
        throw "formatVersion fehlt in manifest.json oder analysis.json."
    }
    if ([string]$manifest.formatVersion -ne [string]$analysis.formatVersion) {
        throw "formatVersion unterscheidet sich zwischen manifest.json und analysis.json."
    }
    if (-not $manifest.generatedAt -or -not $analysis.generatedAt) {
        throw "generatedAt fehlt in manifest.json oder analysis.json."
    }

    $manifestTimestamp = [DateTimeOffset]::Parse([string]$manifest.generatedAt)
    $analysisTimestamp = [DateTimeOffset]::Parse([string]$analysis.generatedAt)
    if ($manifestTimestamp.ToUniversalTime() -ne $analysisTimestamp.ToUniversalTime()) {
        throw "generatedAt unterscheidet sich zwischen manifest.json und analysis.json."
    }

    if ($null -eq $manifest.files) {
        throw "Die Dateiliste fehlt in manifest.json."
    }

    foreach ($fileName in @($manifest.files)) {
        if ([string]::IsNullOrWhiteSpace([string]$fileName)) {
            throw "manifest.json enthaelt einen leeren Dateinamen."
        }

        $listedPath = Join-Path $PackageRoot ([string]$fileName)
        if (-not (Test-Path -LiteralPath $listedPath -PathType Leaf)) {
            throw "Im Manifest aufgefuehrte Datei fehlt: $fileName"
        }
    }

    return [PSCustomObject]@{
        Manifest = $manifest
        Analysis = $analysis
        ManifestPath = $manifestPath
        AnalysisPath = $analysisPath
        GeneratedAt = $manifestTimestamp.ToUniversalTime()
        ArchiveId = Get-ArchiveId ([string]$manifest.generatedAt)
    }
}

function Test-SamePublishedExport {
    param(
        [Parameter(Mandatory = $true)][string]$CurrentPath,
        [Parameter(Mandatory = $true)]$NewPackage,
        [Parameter(Mandatory = $true)][string]$NewZipSha256
    )

    if (-not (Test-Path -LiteralPath $CurrentPath -PathType Container)) {
        return $false
    }

    $currentPackage = Assert-Package $CurrentPath
    if ($currentPackage.GeneratedAt -ne $NewPackage.GeneratedAt) {
        return $false
    }

    $metadataPath = Join-Path $CurrentPath "upload-meta.json"
    if (Test-Path -LiteralPath $metadataPath -PathType Leaf) {
        $metadata = Read-JsonFile $metadataPath
        if ($metadata.sourceSha256 -and ([string]$metadata.sourceSha256).ToLowerInvariant() -eq $NewZipSha256.ToLowerInvariant()) {
            return $true
        }
    }

    $currentManifestHash = (Get-FileHash -LiteralPath (Join-Path $CurrentPath "manifest.json") -Algorithm SHA256).Hash
    $newManifestHash = (Get-FileHash -LiteralPath $NewPackage.ManifestPath -Algorithm SHA256).Hash
    $currentAnalysisHash = (Get-FileHash -LiteralPath (Join-Path $CurrentPath "analysis.json") -Algorithm SHA256).Hash
    $newAnalysisHash = (Get-FileHash -LiteralPath $NewPackage.AnalysisPath -Algorithm SHA256).Hash

    return $currentManifestHash -eq $newManifestHash -and $currentAnalysisHash -eq $newAnalysisHash
}

$config = $null
if (Test-Path -LiteralPath $ConfigPath -PathType Leaf) {
    $config = Read-JsonFile $ConfigPath
}

if (-not $DownloadDirectory -and $null -ne $config -and $config.downloadDirectory) {
    $DownloadDirectory = [string]$config.downloadDirectory
}
if (-not $DriveDirectory -and $null -ne $config -and $config.driveDirectory) {
    $DriveDirectory = [string]$config.driveDirectory
}

if ($Configure) {
    if (-not $DownloadDirectory -or -not $DriveDirectory) {
        throw "Fuer -Configure muessen -DownloadDirectory und -DriveDirectory angegeben werden."
    }

    $DownloadDirectory = Get-NormalizedDirectory $DownloadDirectory "Download-Verzeichnis"
    $DriveDirectory = Get-NormalizedDirectory $DriveDirectory "Google-Drive-Verzeichnis"

    New-Item -ItemType Directory -Path $ConfigDirectory -Force | Out-Null
    Write-Utf8Json ([ordered]@{
        downloadDirectory = $DownloadDirectory
        driveDirectory = $DriveDirectory
    }) $ConfigPath

    Write-Host "Konfiguration gespeichert: $ConfigPath"
}

if (-not $DownloadDirectory -or -not $DriveDirectory) {
    throw @"
Keine vollstaendige lokale Konfiguration gefunden.
Einmalig aus dem Repository-Root ausfuehren:

  .\scripts\publish-analysis-export.ps1 -Configure -DownloadDirectory '<Download-Pfad>' -DriveDirectory '<Drive-Pfad>'
"@
}

$DownloadDirectory = Get-NormalizedDirectory $DownloadDirectory "Download-Verzeichnis"
$DriveDirectory = Get-NormalizedDirectory $DriveDirectory "Google-Drive-Verzeichnis"

if (-not $ZipPath) {
    $latestZip = Get-ChildItem -LiteralPath $DownloadDirectory -File -Filter "analysis-*.zip" |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1

    if ($null -eq $latestZip) {
        throw "Kein Analyseexport 'analysis-*.zip' in '$DownloadDirectory' gefunden."
    }

    $ZipPath = $latestZip.FullName
}
elseif (-not (Test-Path -LiteralPath $ZipPath -PathType Leaf)) {
    throw "Angegebene ZIP-Datei wurde nicht gefunden: $ZipPath"
}
else {
    $ZipPath = (Resolve-Path -LiteralPath $ZipPath).Path
}

if ([System.IO.Path]::GetExtension($ZipPath) -ne ".zip") {
    throw "Die Quelldatei ist keine ZIP-Datei: $ZipPath"
}

$tempRoot = Join-Path $env:TEMP ("csc-analysis-publish-" + [Guid]::NewGuid().ToString("N"))
$extractRoot = Join-Path $tempRoot "extract"
$stagePath = $null
$archivedCurrentPath = $null

New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null

try {
    Write-Host "Analyseexport: $ZipPath"
    Expand-Archive -LiteralPath $ZipPath -DestinationPath $extractRoot -Force

    $manifestMatches = @(Get-ChildItem -LiteralPath $extractRoot -Recurse -File -Filter "manifest.json")
    if ($manifestMatches.Count -ne 1) {
        throw "Es wurde nicht genau eine manifest.json im ZIP gefunden (gefunden: $($manifestMatches.Count))."
    }

    $packageRoot = $manifestMatches[0].Directory.FullName
    $package = Assert-Package $packageRoot
    $zipSha256 = (Get-FileHash -LiteralPath $ZipPath -Algorithm SHA256).Hash.ToLowerInvariant()

    Write-Host "Format:       $ExpectedFormat v$($package.Manifest.formatVersion)"
    Write-Host "generatedAt:  $($package.GeneratedAt.ToString('o'))"
    Write-Host "ZIP SHA-256:  $zipSha256"

    $currentPath = Join-Path $DriveDirectory "current"
    $archiveRoot = Join-Path $DriveDirectory "archive"
    $incomingRoot = Join-Path $DriveDirectory "_incoming"

    New-Item -ItemType Directory -Path $archiveRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $incomingRoot -Force | Out-Null

    if (Test-SamePublishedExport $currentPath $package $zipSha256) {
        Write-Host "Der Export ist bereits der aktuelle Drive-Stand. Keine Aenderung notwendig."
        exit 0
    }

    $stagePath = Join-Path $incomingRoot ($package.ArchiveId + "-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $stagePath -Force | Out-Null

    foreach ($item in Get-ChildItem -LiteralPath $packageRoot -Force) {
        Copy-Item -LiteralPath $item.FullName -Destination $stagePath -Recurse -Force
    }

    $stagedPackage = Assert-Package $stagePath
    if ($stagedPackage.GeneratedAt -ne $package.GeneratedAt) {
        throw "Der temporaere Drive-Stand stimmt nicht mit dem Quellpaket ueberein."
    }

    $sourceManifestHash = (Get-FileHash -LiteralPath $package.ManifestPath -Algorithm SHA256).Hash
    $stageManifestHash = (Get-FileHash -LiteralPath (Join-Path $stagePath "manifest.json") -Algorithm SHA256).Hash
    $sourceAnalysisHash = (Get-FileHash -LiteralPath $package.AnalysisPath -Algorithm SHA256).Hash
    $stageAnalysisHash = (Get-FileHash -LiteralPath (Join-Path $stagePath "analysis.json") -Algorithm SHA256).Hash
    if ($sourceManifestHash -ne $stageManifestHash -or $sourceAnalysisHash -ne $stageAnalysisHash) {
        throw "Die kopierten Kern-Dateien stimmen nicht mit dem entpackten Quellpaket ueberein."
    }

    if (Test-Path -LiteralPath $currentPath -PathType Container) {
        $currentPackage = Assert-Package $currentPath
        $archivedCurrentPath = Join-Path $archiveRoot $currentPackage.ArchiveId

        if (Test-Path -LiteralPath $archivedCurrentPath) {
            throw "Archivziel existiert bereits; der aktuelle Stand wird nicht ueberschrieben: $archivedCurrentPath"
        }

        Write-Host "Archiviere bisherigen current-Stand nach '$archivedCurrentPath' ..."
        Move-Item -LiteralPath $currentPath -Destination $archivedCurrentPath
    }

    try {
        Write-Host "Aktiviere neuen current-Stand ..."
        Move-Item -LiteralPath $stagePath -Destination $currentPath
        $stagePath = $null
    }
    catch {
        if ($archivedCurrentPath -and
            -not (Test-Path -LiteralPath $currentPath) -and
            (Test-Path -LiteralPath $archivedCurrentPath)) {
            Write-Warning "Aktivierung fehlgeschlagen; versuche den vorherigen current-Stand wiederherzustellen."
            Move-Item -LiteralPath $archivedCurrentPath -Destination $currentPath
            $archivedCurrentPath = $null
        }
        throw
    }

    $publishedPackage = Assert-Package $currentPath
    if ($publishedPackage.GeneratedAt -ne $package.GeneratedAt) {
        throw "Der aktivierte current-Stand besitzt einen unerwarteten generatedAt-Wert."
    }

    Write-Utf8Json ([ordered]@{
        publisher = "scripts/publish-analysis-export.ps1"
        publisherVersion = $ScriptVersion
        sourceZip = [System.IO.Path]::GetFileName($ZipPath)
        sourceSha256 = $zipSha256
        generatedAt = [string]$package.Manifest.generatedAt
        format = [string]$package.Manifest.format
        formatVersion = $package.Manifest.formatVersion
        publishedAt = [DateTimeOffset]::UtcNow.ToString("o")
    }) (Join-Path $currentPath "upload-meta.json")

    Write-Host ""
    Write-Host "Lokale Veroeffentlichung abgeschlossen."
    Write-Host "Current:  $currentPath"
    Write-Host "Archiv:   $archiveRoot"
    Write-Host ""
    Write-Host "Google Drive for Desktop uebernimmt jetzt den Cloud-Sync."
    Write-Host "Vor einer KI-Analyse warten, bis Drive 'Up to date' / 'Aktuell' meldet."
}
finally {
    if ($stagePath -and (Test-Path -LiteralPath $stagePath)) {
        Remove-Item -LiteralPath $stagePath -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
