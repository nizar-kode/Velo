@echo off
title Mousedroid Firewall Helper
cls
color 0E
echo ========================================================
echo         MOUSEDROID FIREWALL PERMISSION HELPER
echo ========================================================
echo.
echo Checking administrator privileges...

net session >nul 2>&1
if %errorLevel% NEQ 0 (
    cls
    color 0C
    echo ========================================================
    echo  ADMINISTRATOR PERMISSION REQUIRED
    echo ========================================================
    echo.
    echo  Please RIGHT-CLICK this file and choose:
    echo.
    echo         "Run as administrator"
    echo.
    echo ========================================================
    echo.
    pause
    exit /b 1
)

color 0B
echo.
echo Allowing Mousedroid through Windows Firewall...
netsh advfirewall firewall delete rule name="Mousedroid Wi-Fi Server" >nul 2>&1
netsh advfirewall firewall delete rule name="Mousedroid Wi-Fi UDP" >nul 2>&1
netsh advfirewall firewall delete rule name="Mousedroid UDP Discovery" >nul 2>&1

netsh advfirewall firewall add rule name="Mousedroid Wi-Fi Server" dir=in action=allow protocol=TCP localport=48291 profile=any >nul
netsh advfirewall firewall add rule name="Mousedroid Wi-Fi UDP" dir=in action=allow protocol=UDP localport=48291 profile=any >nul
netsh advfirewall firewall add rule name="Mousedroid UDP Discovery" dir=in action=allow protocol=UDP localport=48292 profile=any >nul

cls
color 0A
echo ========================================================
echo  FIREWALL CONFIGURED SUCCESSFULLY!
echo ========================================================
echo.
echo  Windows Defender Firewall has permitted Mousedroid.
echo  You can now double-click "START_MOUSEDROID.bat".
echo.
echo ========================================================
echo.
pause
