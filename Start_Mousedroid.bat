@echo off
title Mousedroid Server Launcher
color 0A
cls
echo ========================================================
echo               MOUSEDROID SERVER LAUNCHER
echo ========================================================
echo.

REM Determine executable path
set "SERVER_DIR=%~dp0mousedroid_win64\mousedroid_win64"
if not exist "%SERVER_DIR%\Mousedroid.exe" (
    set "SERVER_DIR=%~dp0"
)

REM Check if Mousedroid is already running
tasklist /FI "IMAGENAME eq Mousedroid.exe" 2>NUL | find /I /N "Mousedroid.exe" >NUL
if "%ERRORLEVEL%"=="0" (
    echo [WARNING] Mousedroid is ALREADY RUNNING in the background.
    echo Stopping old instance to ensure a clean start...
    taskkill /F /IM Mousedroid.exe >NUL 2>&1
    ping 127.0.0.1 -n 2 >NUL
)

REM Clear lingering adb processes if holding port
taskkill /F /IM adb.exe >NUL 2>&1

REM Start Mousedroid Server
echo [INFO] Starting Mousedroid Server on Port 48291...
cd /d "%SERVER_DIR%"
start "" "%SERVER_DIR%\Mousedroid.exe"

REM Start UDP Auto-Discovery Beacon
start /b pythonw "%SERVER_DIR%\discovery_beacon.py" >NUL 2>&1

REM Start Windows Fluent Companion Tray
if exist "%~dp0MousedroidCompanion.pyw" (
    start /b pythonw "%~dp0MousedroidCompanion.pyw" >NUL 2>&1
)

ping 127.0.0.1 -n 3 >NUL

cls
echo ========================================================
echo            MOUSEDROID SERVER IS READY AND RUNNING!
echo ========================================================
echo.
echo  PC Connection Details:
echo  ----------------------
echo  Your PC IP Address : 192.168.0.165
echo  Wi-Fi Server Port  : 48291
echo  ADB / USB Port     : 6969
echo  Auto-Discovery     : ACTIVE (UDP port 48292)
echo.
echo  How to connect from your phone:
echo  1. Connect your phone to the same Wi-Fi network.
echo  2. Open the Mousedroid app on your phone.
echo  3. Choose 'Wi-Fi' - Your PC will appear AUTOMATICALLY!
echo  4. Tap your PC to connect instantly!
echo.
echo ========================================================
echo (You can close this window at any time. Mousedroid stays
echo  active in your Windows system tray near the clock.)
echo ========================================================
echo.
pause
