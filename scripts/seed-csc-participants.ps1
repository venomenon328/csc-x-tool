[CmdletBinding()]
param(
    [string]$BaseUrl
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function U([string]$Value) {
    return [regex]::Unescape($Value)
}

$targets = @(
    [pscustomobject]@{ Name = 'Die Ente'; CountryCode = 'VA' },
    [pscustomobject]@{ Name = 'Dr. King Schultz'; CountryCode = 'KR' },
    [pscustomobject]@{ Name = U 'The Rock Chick\u2122'; CountryCode = 'CH' },
    [pscustomobject]@{ Name = 'Fletcher Cox'; CountryCode = 'LC' },
    [pscustomobject]@{ Name = 'Grissom'; CountryCode = 'JP' },
    [pscustomobject]@{ Name = 'Rated M'; CountryCode = 'BR' },
    [pscustomobject]@{ Name = 'OMW'; CountryCode = 'WS' },
    [pscustomobject]@{ Name = 'George Russell'; CountryCode = 'IE' },
    [pscustomobject]@{ Name = 'Ravenous'; CountryCode = 'XS' },
    [pscustomobject]@{ Name = 'Daniel.'; CountryCode = 'NL' },
    [pscustomobject]@{ Name = 'Berggorilla'; CountryCode = 'CG' },
    [pscustomobject]@{ Name = 'Antichrist'; CountryCode = 'IT' },
    [pscustomobject]@{ Name = 'Ratcatcher 2'; CountryCode = 'PT' },
    [pscustomobject]@{ Name = 'snaggletooth'; CountryCode = 'GR' },
    [pscustomobject]@{ Name = 'Worm'; CountryCode = 'DE' },
    [pscustomobject]@{ Name = 'Joshi Judas Zwen'; CountryCode = 'MX' },
    [pscustomobject]@{ Name = 'DerFalke15'; CountryCode = 'BE' },
    [pscustomobject]@{ Name = 'The Final Boss'; CountryCode = 'AR' },
    [pscustomobject]@{ Name = 'Julian'; CountryCode = 'JM' },
    [pscustomobject]@{ Name = U 'Stra\u00dfenk\u00f6ter'; CountryCode = 'AU' },
    [pscustomobject]@{ Name = U 'Kl\u00f6tenKlaus'; CountryCode = 'SE' },
    [pscustomobject]@{ Name = 'Contiomagus'; CountryCode = 'CV' },
    [pscustomobject]@{ Name = 'Toblerone Driver'; CountryCode = 'TR' },
    [pscustomobject]@{ Name = 'JohnDoe'; CountryCode = 'KP' },
    [pscustomobject]@{ Name = 'Herr Malzen'; CountryCode = 'CA' },
    [pscustomobject]@{ Name = 'Kingtoo'; CountryCode = 'MN' },
    [pscustomobject]@{ Name = 'reddit-nutzer'; CountryCode = 'CZ' },
    [pscustomobject]@{ Name = 'Cortez'; CountryCode = 'FI' },
    [pscustomobject]@{ Name = 'Sentino'; CountryCode = 'AM' },
    [pscustomobject]@{ Name = 'Steven_Blueheart'; CountryCode = 'UA' },
    [pscustomobject]@{ Name = 'Mark Webber'; CountryCode = 'NZ' }
)

if ($targets.Count -ne 31) {
    throw "Expected exactly 31 external CSC participants, got $($targets.Count)."
}
if (@($targets | Where-Object { $_.Name -ieq 'venomenon' }).Count -ne 0) {
    throw 'The local CSC user venomenon must never be part of the participant seed.'
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        throw 'LOCALAPPDATA is unavailable. Pass -BaseUrl explicitly.'
    }
    $instancePath = Join-Path $env:LOCALAPPDATA 'CSC-X-Tool\runtime\instance.json'
    if (-not (Test-Path -LiteralPath $instancePath)) {
        throw "No running CSC-X-Tool instance was found at $instancePath. Start the app or pass -BaseUrl http://127.0.0.1:<port>."
    }
    $instance = Get-Content -LiteralPath $instancePath -Raw | ConvertFrom-Json
    if ($null -eq $instance.port) {
        throw "The runtime file $instancePath does not contain a port."
    }
    $BaseUrl = "http://127.0.0.1:$($instance.port)"
}

$BaseUrl = $BaseUrl.TrimEnd('/')
$baseUri = [Uri]$BaseUrl
if ($baseUri.Scheme -ne 'http' -or $baseUri.Host -ne '127.0.0.1') {
    throw 'For security, -BaseUrl must point to http://127.0.0.1:<port>.'
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-RestMethod -Uri "$BaseUrl/api/system/health" -Method Get -WebSession $session
$csrf = Invoke-RestMethod -Uri "$BaseUrl/api/system/csrf" -Method Get -WebSession $session
$headers = @{}
$headers[[string]$csrf.headerName] = [string]$csrf.token

function Invoke-JsonWrite {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][object]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 6 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Uri "$BaseUrl$Path" -Method $Method -Headers $headers -WebSession $session -ContentType 'application/json; charset=utf-8' -Body $bytes
}

function Test-ParticipantName {
    param(
        [Parameter(Mandatory = $true)][object]$Participant,
        [Parameter(Mandatory = $true)][string]$Name
    )

    if ([string]::Equals([string]$Participant.displayName, $Name, [StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    foreach ($alias in @($Participant.aliases)) {
        if ([string]::Equals([string]$alias, $Name, [StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
    }
    return $false
}

$existing = @(Invoke-RestMethod -Uri "$BaseUrl/api/participants?includeInactive=true" -Method Get -WebSession $session)
$createdCount = 0
$updatedCount = 0
$skippedCount = 0

foreach ($target in $targets) {
    $matches = @($existing | Where-Object { Test-ParticipantName -Participant $_ -Name $target.Name })
    if ($matches.Count -gt 1) {
        throw "Participant '$($target.Name)' matches more than one existing participant. Resolve the ambiguity manually before seeding."
    }

    if ($matches.Count -eq 0) {
        $created = Invoke-JsonWrite -Method Post -Path '/api/participants' -Body ([ordered]@{
            displayName = $target.Name
            countryCode = $target.CountryCode
            aliases = @()
            active = $true
        })
        $existing += $created
        $createdCount++
        Write-Host "Created  $($target.Name) [$($target.CountryCode)]"
        continue
    }

    $match = $matches[0]
    $sameName = [string]::Equals([string]$match.displayName, [string]$target.Name, [StringComparison]::Ordinal)
    $sameCountry = [string]::Equals([string]$match.countryCode, [string]$target.CountryCode, [StringComparison]::OrdinalIgnoreCase)
    $alreadyActive = $match.active -eq $true
    if ($sameName -and $sameCountry -and $alreadyActive) {
        $skippedCount++
        Write-Host "Skipped  $($target.Name) [$($target.CountryCode)]"
        continue
    }

    $updated = Invoke-JsonWrite -Method Patch -Path "/api/participants/$($match.id)" -Body ([ordered]@{
        displayName = $target.Name
        countryCode = $target.CountryCode
        active = $true
    })
    $existing = @($existing | Where-Object { $_.id -ne $match.id }) + $updated
    $updatedCount++
    Write-Host "Updated  $($target.Name) [$($target.CountryCode)]"
}

Write-Host "Done. Created: $createdCount; updated: $updatedCount; unchanged: $skippedCount; total external participants: $($targets.Count)."
