@echo off
title Mousedroid Server Launcher
color 0B
cls
echo ========================================================
echo               MOUSEDROID WIRELESS REMOTE
echo ========================================================
echo.
echo  Starting server components...
echo.

cd /d "%~dp0engine"

REM Stop any old running instances
taskkill /F /IM Mousedroid.exe >NUL 2>&1
taskkill /F /IM discovery_beacon.exe >NUL 2>&1
taskkill /F /IM MousedroidCompanion.exe >NUL 2>&1
taskkill /F /IM adb.exe >NUL 2>&1
ping 127.0.0.1 -n 2 >NUL

REM 1. Start Native C++ Server
echo  [1/3] Starting Input Engine...
start "" "%~dp0engine\Mousedroid.exe"

REM 2. Start Auto-Discovery Beacon
echo  [2/3] Starting Auto-Discovery...
start "" "%~dp0engine\discovery_beacon.exe"

REM 3. Start System Tray Dashboard
echo  [3/3] Starting Dashboard...
start "" "%~dp0engine\MousedroidCompanion.exe"

ping 127.0.0.1 -n 3 >NUL

cls
color 0A
echo ========================================================
echo        SUCCESS! MOUSEDROID IS RUNNING ON THIS PC
echo ========================================================
echo.
echo  Mousedroid is now active in your Windows System Tray
echo  (bottom-right corner near the clock).
echo.
echo  --------------------------------------------------------
echo  HOW TO CONNECT FROM YOUR PHONE:
echo  --------------------------------------------------------
echo  1. Make sure your phone and this PC are on the same Wi-Fi.
echo  2. Open the Mousedroid app on your phone.
echo  3. Tap "Wi-Fi Remote (Auto-Connect)".
echo  4. THIS PC WILL APPEAR IN THE LIST AUTOMATICALLY!
echo  5. Tap your PC's name to connect. Done!
echo.
echo  (If the PC does not appear, close this and run:
echo   "FIREWALL_HELPER_IF_BLOCKED.bat" as administrator)
echo ========================================================
echo.
pause
