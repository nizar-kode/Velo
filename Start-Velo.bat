@echo off
title Velo Desktop Receiver
echo ========================================================
echo               VELO DESKTOP RECEIVER
echo ========================================================
set "DOTNET_ROOT=%USERPROFILE%\.dotnet"
set "PATH=%USERPROFILE%\.dotnet;%PATH%"
cd /d "%~dp0AirPilot\windows\VeloDesktop"
"%USERPROFILE%\.dotnet\dotnet.exe" run
