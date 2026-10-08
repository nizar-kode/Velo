@echo off
title Mousedroid Firewall Helper
echo ========================================================
echo         ALLOWING MOUSEDROID THROUGH FIREWALL
echo ========================================================
echo.
echo Requesting administrator privileges...

net session >nul 2>&1
if %errorLevel% NEQ 0 (
    echo [ERROR] Please right-click this file and choose "Run as administrator".
    echo.
    pause
    exit /b 1
)

echo Adding Windows Firewall rules for Ports 48291 (TCP/UDP) and 48292 (UDP)...
netsh advfirewall firewall delete rule name="Mousedroid Wi-Fi Server" >nul 2>&1
netsh advfirewall firewall delete rule name="Mousedroid UDP Discovery" >nul 2>&1

netsh advfirewall firewall add rule name="Mousedroid Wi-Fi Server" dir=in action=allow protocol=TCP localport=48291 profile=any >nul
netsh advfirewall firewall add rule name="Mousedroid Wi-Fi UDP" dir=in action=allow protocol=UDP localport=48291 profile=any >nul
netsh advfirewall firewall add rule name="Mousedroid UDP Discovery" dir=in action=allow protocol=UDP localport=48292 profile=any >nul

echo.
echo [SUCCESS] Firewall rules added successfully!
echo You can now run Start_Mousedroid.bat normally.
echo.
pause
