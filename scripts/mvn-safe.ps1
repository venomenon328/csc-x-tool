[CmdletBinding()]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]] $MavenArgs
)

$ErrorActionPreference = 'Stop'

if (-not $MavenArgs -or $MavenArgs.Count -eq 0) {
    $MavenArgs = @('clean', 'verify')
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$currentProcess = [System.Diagnostics.Process]::GetCurrentProcess()

try {
    $currentProcess.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::BelowNormal
} catch {
    Write-Warning "Prozesspriorität konnte nicht auf 'BelowNormal' gesetzt werden: $($_.Exception.Message)"
}

$allowedProcessors = [Math]::Min(2, [Environment]::ProcessorCount)
[long] $affinityMask = [Math]::Pow(2, $allowedProcessors) - 1
try {
    $currentProcess.ProcessorAffinity = [IntPtr] $affinityMask
} catch {
    Write-Warning "CPU-Affinität konnte nicht auf $allowedProcessors logische Prozessoren begrenzt werden: $($_.Exception.Message)"
}

Push-Location $repositoryRoot
try {
    & (Join-Path $repositoryRoot 'mvnw.cmd') @MavenArgs
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $exitCode
