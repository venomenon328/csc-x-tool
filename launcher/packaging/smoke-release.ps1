[CmdletBinding()]
param(
    [string]$AppImagePath,
    [string]$MsiPath,
    [string]$WixBin,
    [switch]$SkipInstaller
)

$ErrorActionPreference = 'Stop'
$config = Import-PowerShellDataFile (Join-Path $PSScriptRoot 'release.psd1')
if ([string]::IsNullOrWhiteSpace($AppImagePath)) {
    $AppImagePath = Join-Path $PSScriptRoot "output\app-image\$($config.ApplicationName)"
}
if ([string]::IsNullOrWhiteSpace($MsiPath)) {
    $MsiPath = Join-Path $PSScriptRoot "output\installer\CSC-X-Tool-$($config.ApplicationVersion).msi"
}
$appImage = (Resolve-Path $AppImagePath).Path
$launcher = Join-Path $appImage "$($config.ApplicationName).exe"
if (-not (Test-Path $launcher -PathType Leaf)) {
    throw "Die gepackte EXE fehlt: $launcher"
}
if (-not $SkipInstaller -and -not (Test-Path $MsiPath -PathType Leaf)) {
    throw "Das MSI fehlt: $MsiPath"
}
if (-not $SkipInstaller) {
    if ($WixBin) {
        $env:Path = "$(Resolve-Path $WixBin);$env:Path"
    }
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or -not (Get-Command light.exe -ErrorAction SilentlyContinue)) {
        throw 'WiX 3.14 mit candle.exe und light.exe ist für den Upgrade-Smoketest erforderlich.'
    }
    $candleVersion = (& candle.exe '-?' 2>&1 | Out-String)
    if ($candleVersion -notmatch 'version 3\.14\.1\.') {
        throw "WiX 3.14.1 ist erforderlich, gefunden wurde: $($candleVersion.Trim())"
    }
}

$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("csc-x-tool-release-smoke-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $testRoot | Out-Null

function Start-PackagedApp {
    param([string]$Executable, [string]$StorageRoot)
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Executable
    $startInfo.UseShellExecute = $false
    $startInfo.EnvironmentVariables['CSC_X_TOOL_STORAGE_ROOT'] = $StorageRoot
    $startInfo.EnvironmentVariables['CSC_X_TOOL_DESKTOP_SUPPRESS_BROWSER'] = 'true'
    $startInfo.EnvironmentVariables.Remove('JAVA_HOME')
    $startInfo.EnvironmentVariables.Remove('JDK_HOME')
    $startInfo.EnvironmentVariables.Remove('JRE_HOME')
    $startInfo.EnvironmentVariables['PATH'] = @(
        $startInfo.EnvironmentVariables['PATH'] -split ';' | Where-Object { $_ -notmatch '(?i)(java|jdk|node)' }
    ) -join ';'
    return [System.Diagnostics.Process]::Start($startInfo)
}

function Wait-ForPackagedHealth {
    param([string]$StorageRoot, [System.Diagnostics.Process]$Process)
    $instance = Join-Path $StorageRoot 'runtime\instance.json'
    $deadline = (Get-Date).AddSeconds(45)
    do {
        if ($Process.HasExited) {
            throw "Die gepackte Anwendung endete vor dem Health-Check (Exit-Code $($Process.ExitCode))."
        }
        if (Test-Path $instance) {
            $runtime = Get-Content -LiteralPath $instance -Raw | ConvertFrom-Json
            if ($runtime.port -lt 1 -or $runtime.port -gt 65535) { throw 'instance.json enthält keinen gültigen Port.' }
            $base = "http://127.0.0.1:$($runtime.port)"
            try {
                $health = Invoke-WebRequest -Uri "$base/api/system/health" -UseBasicParsing
                if ($health.StatusCode -eq 200 -and $health.Content -match '"status"\s*:\s*"UP"') {
                    $listeners = Get-NetTCPConnection -State Listen -LocalPort $runtime.port -ErrorAction Stop
                    if ($listeners.LocalAddress -notcontains '127.0.0.1') {
                        throw "Der gepackte Server bindet nicht ausschließlich an 127.0.0.1."
                    }
                    return [pscustomobject]@{ Base = $base; RuntimePath = $instance; Port = $runtime.port }
                }
            } catch [System.Net.WebException] {
                # Der Server darf während des Starts noch nicht erreichbar sein.
            }
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    throw 'Die gepackte Anwendung wurde nicht rechtzeitig gesund.'
}

function New-CsrfSession {
    param([string]$Base)
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $response = Invoke-WebRequest -Uri "$Base/api/system/csrf" -WebSession $session -UseBasicParsing
    $csrf = $response.Content | ConvertFrom-Json
    return [pscustomobject]@{ Session = $session; Header = $csrf.headerName; Token = $csrf.token }
}

function Invoke-CscJson {
    param(
        [string]$Method, [string]$Uri, [object]$Csrf, [string]$Body
    )
    $parameters = @{ Uri = $Uri; Method = $Method; UseBasicParsing = $true }
    if ($Csrf) {
        $parameters.WebSession = $Csrf.Session
        $parameters.Headers = @{ $Csrf.Header = $Csrf.Token; Origin = ([uri]$Uri).GetLeftPart([System.UriPartial]::Authority) }
    }
    if ($Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body
    }
    $response = Invoke-WebRequest @parameters
    return $response.Content | ConvertFrom-Json
}

function Stop-PackagedApp {
    param([string]$Base, [object]$Csrf, [System.Diagnostics.Process]$Process, [string]$RuntimePath)
    $null = Invoke-CscJson 'POST' "$Base/api/system/shutdown" $Csrf $null
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $Process.Refresh()
        if ($Process.HasExited) { break }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    if (-not $Process.HasExited) { throw 'Der kontrollierte Shutdown der gepackten Anwendung ist abgelaufen.' }
    if (Test-Path $RuntimePath) { throw 'instance.json wurde beim kontrollierten Shutdown nicht entfernt.' }
}

function Get-BackupArtifactCount {
    param([string]$StorageRoot)
    return @(Get-ChildItem -LiteralPath (Join-Path $StorageRoot 'backups') -Filter '*.cscbackup' -File -Recurse -ErrorAction SilentlyContinue).Count
}

function Assert-SecondLaunchReusesInstance {
    param([string]$Executable, [string]$StorageRoot, [string]$RuntimePath)
    $second = Start-PackagedApp $Executable $StorageRoot
    $deadline = (Get-Date).AddSeconds(15)
    do {
        $second.Refresh()
        if ($second.HasExited) { break }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    if (-not $second.HasExited -or $second.ExitCode -ne 0) {
        throw 'Ein zweiter Launcher-Aufruf hat keine bestehende Instanz sauber wiederverwendet.'
    }
    if (-not (Test-Path $RuntimePath)) { throw 'Der zweite Launcher-Aufruf hat die laufende Instanzdatei entfernt.' }
}

function Invoke-Msi {
    param([string]$Arguments)
    $process = Start-Process -FilePath 'msiexec.exe' -ArgumentList $Arguments -Wait -PassThru
    if ($process.ExitCode -notin 0, 3010) { throw "msiexec ist mit Exit-Code $($process.ExitCode) fehlgeschlagen." }
}

function Find-InstalledLauncher {
    $roots = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\CSC X Tool'),
        (Join-Path $env:LOCALAPPDATA 'CSC X Tool')
    )
    foreach ($root in $roots) {
        $candidate = Join-Path $root "$($config.ApplicationName).exe"
        if (Test-Path $candidate -PathType Leaf) { return $candidate }
    }
    throw 'Die per-user MSI-Installation hat keinen erwarteten Launcher abgelegt.'
}

function Find-ExistingCscInstallation {
    $uninstallRoots = @(
        'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall',
        'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall',
        'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall'
    )
    foreach ($root in $uninstallRoots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        $existing = Get-ChildItem -LiteralPath $root -ErrorAction SilentlyContinue |
            Get-ItemProperty -ErrorAction SilentlyContinue |
            Where-Object { $_.DisplayName -eq $config.ApplicationName } |
            Select-Object -First 1
        if ($existing) { return $existing }
    }
    return $null
}

try {
    $appStorage = Join-Path $testRoot 'app-image-storage'
    $first = Start-PackagedApp $launcher $appStorage
    $running = Wait-ForPackagedHealth $appStorage $first
    Assert-SecondLaunchReusesInstance $launcher $appStorage $running.RuntimePath
    $csrf = New-CsrfSession $running.Base

    $candidate = Invoke-CscJson 'POST' "$($running.Base)/api/shows/1/candidates" $csrf '{"artist":"Packaging Smoke","title":"Persisted Song","youtubeUrl":"https://youtu.be/dQw4w9WgXcQ","comment":null}'
    $backup = Invoke-CscJson 'POST' "$($running.Base)/api/data/backups" $csrf $null
    $null = Invoke-CscJson 'PATCH' "$($running.Base)/api/shows/1/candidates/$($candidate.id)" $csrf '{"artist":"Packaging Smoke","title":"Persisted Song","youtubeUrl":"https://youtu.be/dQw4w9WgXcQ","comment":"changed after backup","status":"OFFEN"}'
    $preview = Invoke-CscJson 'POST' "$($running.Base)/api/data/restore/preview/backups/$($backup.id)" $csrf $null
    $null = Invoke-CscJson 'POST' "$($running.Base)/api/data/restore" $csrf ("{`"token`":`"$($preview.token)`"}")
    $restored = Invoke-WebRequest -Uri "$($running.Base)/api/shows/1/candidates" -WebSession $csrf.Session -UseBasicParsing
    if ($restored.Content -notmatch 'Packaging Smoke' -or $restored.Content -match 'changed after backup') {
        throw 'Der Backup-/Restore-Roundtrip der gepackten Anwendung hat den erwarteten Stand nicht wiederhergestellt.'
    }
    $backupCountBeforeShutdown = Get-BackupArtifactCount $appStorage
    Stop-PackagedApp $running.Base $csrf $first $running.RuntimePath
    if ((Get-BackupArtifactCount $appStorage) -ne $backupCountBeforeShutdown) {
        throw 'Der kontrollierte Shutdown hat unerwartet ein zusätzliches Backup erzeugt.'
    }

    $restarted = Start-PackagedApp $launcher $appStorage
    $afterRestart = Wait-ForPackagedHealth $appStorage $restarted
    $preserved = Invoke-WebRequest -Uri "$($afterRestart.Base)/api/shows/1/candidates" -UseBasicParsing
    if ($preserved.Content -notmatch 'Packaging Smoke') { throw 'Die Daten haben den gepackten Neustart nicht überlebt.' }
    Stop-PackagedApp $afterRestart.Base (New-CsrfSession $afterRestart.Base) $restarted $afterRestart.RuntimePath

    if (-not $SkipInstaller) {
        $existingInstall = Find-ExistingCscInstallation
        if ($existingInstall) { throw 'Ein bestehendes CSC X Tool ist bereits installiert; der Installer-Smoke ändert diese Installation bewusst nicht.' }

        $installerStorage = Join-Path $testRoot 'installer-storage'
        Invoke-Msi "/i `"$((Resolve-Path $MsiPath).Path)`" /qn /norestart"
        $installedLauncher = Find-InstalledLauncher
        $installed = Start-PackagedApp $installedLauncher $installerStorage
        $installedRunning = Wait-ForPackagedHealth $installerStorage $installed
        Stop-PackagedApp $installedRunning.Base (New-CsrfSession $installedRunning.Base) $installed $installedRunning.RuntimePath

        $upgradeDestination = Join-Path $testRoot 'upgrade'
        New-Item -ItemType Directory -Force -Path $upgradeDestination | Out-Null
        & jpackage '--type' 'msi' '--dest' $upgradeDestination '--name' $config.ApplicationName '--app-version' '0.1.1' '--app-image' $appImage '--win-per-user-install' '--win-menu' '--win-menu-group' 'CSC-X-Tool' '--win-upgrade-uuid' $config.UpgradeUuid
        if ($LASTEXITCODE -ne 0) { throw 'Der synthetische 0.1.1-Upgrade-Installer konnte nicht erzeugt werden.' }
        $upgradeMsi = (Get-ChildItem $upgradeDestination -Filter '*.msi' -File | Select-Object -First 1).FullName
        Invoke-Msi "/i `"$upgradeMsi`" /qn /norestart"
        if (-not (Test-Path (Find-InstalledLauncher) -PathType Leaf)) { throw 'Der Upgradepfad hat den Launcher nicht erhalten.' }
        Invoke-Msi "/x `"$upgradeMsi`" /qn /norestart"
        if (-not (Test-Path (Join-Path $installerStorage 'data\csc-x-tool.db') -PathType Leaf)) {
            throw 'Die Deinstallation hat den externen Benutzer-Storage unerwartet entfernt.'
        }
    }
    Write-Host 'Paketierter App-Image- und Installer-Smoke erfolgreich.'
} finally {
    if (Test-Path $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
}
