@echo off
setlocal
title Velo Desktop Receiver
echo ========================================================
echo               VELO DESKTOP RECEIVER
echo ========================================================
echo Ultra-responsive PC control, trackpad, and presentation pointer
echo.

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%AirPilot\windows\VeloDesktop"
set "PUBLISHED_EXE=%SCRIPT_DIR%AirPilot_Windows_Mousely\Velo.exe"
set "USER_DOTNET=%USERPROFILE%\.dotnet\dotnet.exe"

REM 1. Prefer user profile .NET 10 SDK if installed
if exist "%USER_DOTNET%" (
    echo [INFO] Launching Velo via User .NET SDK...
    set "DOTNET_ROOT=%USERPROFILE%\.dotnet"
    set "PATH=%USERPROFILE%\.dotnet;%PATH%"
    cd /d "%PROJECT_DIR%"
    "%USER_DOTNET%" run
    goto :eof
)

REM 2. Check if dotnet is in PATH
where dotnet >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [INFO] Launching Velo via System .NET SDK...
    cd /d "%PROJECT_DIR%"
    dotnet run
    goto :eof
)

REM 3. Fallback to published standalone Velo.exe
if exist "%PUBLISHED_EXE%" (
    echo [INFO] Launching published Velo.exe receiver...
    cd /d "%SCRIPT_DIR%AirPilot_Windows_Mousely"
    start "" "%PUBLISHED_EXE%"
    goto :eof
)

echo [ERROR] Could not find .NET 10 SDK or pre-built Velo.exe.
echo Please install .NET 10 from https://dotnet.microsoft.com/download
pause
exit /b 1
