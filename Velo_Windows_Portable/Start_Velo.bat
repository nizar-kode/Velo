@echo off
title Velo Desktop Receiver
color 0B
cls
echo ========================================================
echo               VELO DESKTOP RECEIVER
echo ========================================================
echo.

cd /d "%~dp0"

REM Stop old instances
taskkill /F /IM Velo.exe >NUL 2>&1
taskkill /F /IM Mousedroid.exe >NUL 2>&1
taskkill /F /IM discovery_beacon.exe >NUL 2>&1
taskkill /F /IM VeloCompanion.exe >NUL 2>&1
taskkill /F /IM MousedroidCompanion.exe >NUL 2>&1
taskkill /F /IM LaserPointerOverlay.exe >NUL 2>&1
taskkill /F /IM adb.exe >NUL 2>&1
ping 127.0.0.1 -n 2 >NUL

REM 1. Start Native Velo Server (Listening on 48291)
echo [1/3] Starting Velo Core Receiver (Port 48291)...
if exist "%~dp0Velo.exe" (
    start "" "%~dp0Velo.exe"
)

REM 2. Start UDP Auto-Discovery Beacon (Port 48292)
echo [2/3] Starting UDP Zero-Config Discovery Beacon (Port 48292)...
if exist "%~dp0discovery_beacon.exe" (
    start "" "%~dp0discovery_beacon.exe"
) else (
    where pythonw >NUL 2>&1
    if "%ERRORLEVEL%"=="0" (
        start "" pythonw "%~dp0discovery_beacon.py"
    ) else (
        where python >NUL 2>&1
        if "%ERRORLEVEL%"=="0" (
            start /b python "%~dp0discovery_beacon.py" >NUL 2>&1
        )
    )
)

REM 3. Start Velo Companion Tray Dashboard
echo [3/3] Starting Velo Tray Dashboard...
if exist "%~dp0VeloCompanion.exe" (
    start "" "%~dp0VeloCompanion.exe"
) else (
    where pythonw >NUL 2>&1
    if "%ERRORLEVEL%"=="0" (
        if exist "%~dp0VeloCompanion.pyw" (
            start "" pythonw "%~dp0VeloCompanion.pyw"
        )
    )
)

ping 127.0.0.1 -n 2 >NUL

cls
echo ========================================================
echo           VELO DESKTOP IS READY AND RUNNING!
echo ========================================================
echo.
echo  Receiver Status:
echo  ----------------
echo  Wi-Fi Receiver Port  : 48291 (Active)
echo  LAN Auto-Discovery   : ACTIVE (UDP port 48292)
echo  USB / ADB Port       : 6969
echo.
echo  How to connect from your phone:
echo  -------------------------------
echo  1. Make sure your phone is connected to the same Wi-Fi.
echo  2. Open the Velo app on your phone.
echo  3. Choose 'Wi-Fi' - THIS PC WILL APPEAR AUTOMATICALLY!
echo  4. Tap your PC to connect instantly!
echo.
echo  (Velo runs in your Windows System Tray near the clock.)
echo ========================================================
echo.
pause
