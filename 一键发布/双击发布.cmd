@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1"
if errorlevel 1 (echo. & echo FAILED - see details above.) else (echo. & echo COMPLETED.)
pause
