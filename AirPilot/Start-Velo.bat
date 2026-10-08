@echo off
title Velo Desktop Receiver
set "DOTNET_ROOT=%USERPROFILE%\.dotnet"
set "PATH=%USERPROFILE%\.dotnet;%PATH%"
cd /d "%~dp0windows\VeloDesktop"
"%USERPROFILE%\.dotnet\dotnet.exe" run
