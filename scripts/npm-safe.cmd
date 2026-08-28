@echo off
setlocal
set "SCRIPT_DIRECTORY=%~dp0"
set "NPM_COMMAND=%~1"
shift
set "CSC_X_NPM_ARGUMENT_LINE="
:collect_arguments
if "%~1"=="" goto run
set "CSC_X_NPM_ARGUMENT_LINE=%CSC_X_NPM_ARGUMENT_LINE% "%~1""
shift
goto collect_arguments
:run
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%SCRIPT_DIRECTORY%npm-safe.ps1" -NpmCommand "%NPM_COMMAND%"
exit /b %ERRORLEVEL%
