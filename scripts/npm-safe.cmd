@echo off
setlocal
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0npm-safe.ps1" %*
exit /b %ERRORLEVEL%
