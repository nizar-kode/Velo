@echo off
title Velo Desktop Launcher
color 0B
cls
echo ========================================================
echo               VELO DESKTOP RECEIVER
echo ========================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "PORTABLE_DIR=%SCRIPT_DIR%Velo_Windows_Portable"

if exist "%PORTABLE_DIR%\Start_Velo.bat" (
    call "%PORTABLE_DIR%\Start_Velo.bat"
    goto :eof
)

REM Fallback if portable folder not found
cd /d "%SCRIPT_DIR%"
taskkill /F /IM Velo.exe >NUL 2>&1
taskkill /F /IM Mousedroid.exe >NUL 2>&1
start "" "%SCRIPT_DIR%Velo_Windows_Portable\Velo.exe"
start "" pythonw "%SCRIPT_DIR%discovery_beacon.py"
pause
