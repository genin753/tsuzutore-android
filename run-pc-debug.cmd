@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-pc-debug.ps1" %*
set "pcDebugExit=%ERRORLEVEL%"
if not "%pcDebugExit%"=="0" (
    echo.
    echo PC debug failed. See PC-DEBUG.md and .tooling\pc-debug-logs.
    pause
)
exit /b %pcDebugExit%