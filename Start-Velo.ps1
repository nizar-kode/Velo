# Start-Velo.ps1 - Native PowerShell Launcher for Velo Desktop
[CmdletBinding()]
param()

$ScriptDir = $PSScriptRoot
$PortableDir = Join-Path $ScriptDir "Velo_Windows_Portable"
$VeloExe = Join-Path $PortableDir "Velo.exe"
$BeaconPy = Join-Path $PortableDir "discovery_beacon.py"
$CompanionPy = Join-Path $PortableDir "VeloCompanion.pyw"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "             STARTING VELO DESKTOP RECEIVER             " -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Ultra-responsive PC control, trackpad, and presentation pointer" -ForegroundColor Gray
Write-Host ""

# Stop any lingering instances
Get-Process -Name "Velo", "Mousedroid", "discovery_beacon", "VeloCompanion", "MousedroidCompanion", "LaserPointerOverlay" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

if (Test-Path $VeloExe) {
    Write-Host "[1/3] Launching Velo core receiver (Port 48291)..." -ForegroundColor Cyan
    Start-Process -FilePath $VeloExe -WorkingDirectory $PortableDir

    Write-Host "[2/3] Launching UDP auto-discovery beacon (Port 48292)..." -ForegroundColor Cyan
    if (Test-Path (Join-Path $PortableDir "discovery_beacon.exe")) {
        Start-Process -FilePath (Join-Path $PortableDir "discovery_beacon.exe") -WorkingDirectory $PortableDir
    } elseif (Get-Command pythonw -ErrorAction SilentlyContinue) {
        Start-Process -FilePath "pythonw" -ArgumentList "`"$BeaconPy`"" -WorkingDirectory $PortableDir
    }

    Write-Host "[3/3] Launching Velo Fluent tray dashboard..." -ForegroundColor Cyan
    if (Test-Path (Join-Path $PortableDir "VeloCompanion.exe")) {
        Start-Process -FilePath (Join-Path $PortableDir "VeloCompanion.exe") -WorkingDirectory $PortableDir
    } elseif ((Test-Path $CompanionPy) -and (Get-Command pythonw -ErrorAction SilentlyContinue)) {
        Start-Process -FilePath "pythonw" -ArgumentList "`"$CompanionPy`"" -WorkingDirectory $PortableDir
    }

    Start-Sleep -Seconds 2

    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Cyan
    Write-Host "        VELO RECEIVER IS ACTIVE AND READY!              " -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  Service Details:" -ForegroundColor Yellow
    Write-Host "  Wi-Fi Port     : 48291 (TCP/UDP)" -ForegroundColor White
    Write-Host "  Discovery Port : 48292 (UDP)" -ForegroundColor White
    Write-Host "  Auto-Discovery : ACTIVE" -ForegroundColor Green
    Write-Host ""
    Write-Host "  How to Connect:" -ForegroundColor Yellow
    Write-Host "  1. Open Velo on your phone."
    Write-Host "  2. Tap 'Wi-Fi' - THIS PC WILL APPEAR AUTOMATICALLY!"
    Write-Host "  3. Tap to connect instantly."
    Write-Host ""
    Write-Host "  (Velo runs in your Windows System Tray near the clock.)"
} else {
    Write-Error "Could not find $VeloExe. Please check your installation."
}
