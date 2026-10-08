@echo off
title AirPilot Receiver
set "DOTNET_ROOT=%USERPROFILE%\.dotnet"
set "PATH=%USERPROFILE%\.dotnet;%PATH%"
cd /d "%~dp0windows\AirPilotWindows"
"%USERPROFILE%\.dotnet\dotnet.exe" run
