@echo off
title Mousedroid Server Launcher (Portable)
color 0B
cls
echo ========================================================
echo         MOUSEDROID PORTABLE SERVER LAUNCHER
echo ========================================================
echo.

cd /d "%~dp0"

REM Stop any old running instances
taskkill /F /IM Mousedroid.exe >NUL 2>&1
taskkill /F /IM discovery_beacon.exe >NUL 2>&1
taskkill /F /IM MousedroidCompanion.exe >NUL 2>&1
taskkill /F /IM adb.exe >NUL 2>&1
ping 127.0.0.1 -n 2 >NUL

REM 1. Start Native C++ Server
echo [1/3] Starting Mousedroid Server (Port 48291)...
start "" "%~dp0Mousedroid.exe"

REM 2. Start Standalone UDP Auto-Discovery Beacon
echo [2/3] Starting UDP Auto-Discovery Beacon (Port 48292)...
start "" "%~dp0discovery_beacon.exe"

REM 3. Start Windows 11 Fluent System Tray Companion
echo [3/3] Starting Fluent Tray Companion...
start "" "%~dp0MousedroidCompanion.exe"

ping 127.0.0.1 -n 3 >NUL

cls
echo ========================================================
echo        MOUSEDROID IS READY ON THIS COMPUTER!
echo ========================================================
echo.
echo  Wi-Fi Server Port  : 48291
echo  Auto-Discovery     : ACTIVE (UDP port 48292)
echo  ADB / USB Port     : 6969
echo.
echo  How to connect from your phone:
echo  -------------------------------
echo  1. Make sure your phone is connected to the same Wi-Fi.
echo  2. Open the Mousedroid app on your phone.
echo  3. Tap 'Wi-Fi' - THIS PC WILL APPEAR AUTOMATICALLY!
echo  4. Tap to connect instantly!
echo.
echo  (Mousedroid runs in your Windows System Tray near the clock.)
echo ========================================================
echo.
pause
