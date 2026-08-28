[CmdletBinding()]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]] $NpmArgs
)

$ErrorActionPreference = 'Stop'

if (-not $NpmArgs -or $NpmArgs.Count -eq 0) {
    throw 'Mindestens ein npm-Argument ist erforderlich, z. B. test, ci oder run build.'
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontendRoot = Join-Path $repositoryRoot 'frontend'
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

$env:NODE_OPTIONS = '--max-old-space-size=512'
$env:GOMAXPROCS = '2'
$env:UV_THREADPOOL_SIZE = '2'
$env:VITEST_MAX_WORKERS = '1'

Push-Location $frontendRoot
try {
    & npm @NpmArgs
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $exitCode
