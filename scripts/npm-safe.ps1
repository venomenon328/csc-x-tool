[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $NpmCommand
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontendRoot = Join-Path $repositoryRoot 'frontend'
$managedNodeRoot = Join-Path $frontendRoot 'node'
$currentProcess = [System.Diagnostics.Process]::GetCurrentProcess()

if (-not ('CscXTool.CommandLineArguments' -as [type])) {
    Add-Type @'
using System;
using System.Runtime.InteropServices;

namespace CscXTool {
    public static class CommandLineArguments {
        [DllImport("shell32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr CommandLineToArgvW(string commandLine, out int argumentCount);

        [DllImport("kernel32.dll")]
        private static extern IntPtr LocalFree(IntPtr memory);

        public static string[] Split(string commandLine) {
            if (String.IsNullOrWhiteSpace(commandLine)) {
                return Array.Empty<string>();
            }

            int argumentCount;
            IntPtr argumentVector = CommandLineToArgvW(commandLine, out argumentCount);
            if (argumentVector == IntPtr.Zero) {
                throw new InvalidOperationException("The npm arguments could not be parsed.");
            }

            try {
                var arguments = new string[argumentCount];
                for (var index = 0; index < argumentCount; index++) {
                    arguments[index] = Marshal.PtrToStringUni(Marshal.ReadIntPtr(argumentVector, index * IntPtr.Size));
                }
                return arguments;
            } finally {
                LocalFree(argumentVector);
            }
        }
    }
}
'@
}

$NpmArgs = [CscXTool.CommandLineArguments]::Split($env:CSC_X_NPM_ARGUMENT_LINE)

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
if (Test-Path (Join-Path $managedNodeRoot 'node.exe') -PathType Leaf) {
    $env:Path = "$managedNodeRoot;$env:Path"
}

Push-Location $frontendRoot
try {
    & npm $NpmCommand @NpmArgs
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $exitCode
