# Start-Velo.ps1 - Native PowerShell Launcher for Velo Desktop
[CmdletBinding()]
param()

$ProjectDir = Join-Path $PSScriptRoot "AirPilot\windows\VeloDesktop"
$PublishedExe = Join-Path $PSScriptRoot "AirPilot_Windows_Mousely\Velo.exe"
$DotnetExe = Join-Path $env:USERPROFILE ".dotnet\dotnet.exe"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "             STARTING VELO DESKTOP RECEIVER             " -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Tagline: Ultra-responsive PC control, trackpad, and presentation pointer" -ForegroundColor Gray
Write-Host ""

# Stop any running Velo instances
Get-Process -Name "Velo", "AirPilot*" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

# Resolve execution method: prefer dotnet run from project dir
if ((Test-Path $DotnetExe) -and (Test-Path $ProjectDir)) {
    Write-Host "[INFO] Launching Velo via .NET 10 SDK..." -ForegroundColor Cyan
    $env:DOTNET_ROOT = Split-Path $DotnetExe
    $env:PATH = "$env:DOTNET_ROOT;$env:PATH"

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $DotnetExe
    $psi.Arguments = "run"
    $psi.WorkingDirectory = $ProjectDir
    $psi.UseShellExecute = $true
    [System.Diagnostics.Process]::Start($psi) | Out-Null
} elseif (Test-Path $PublishedExe) {
    Write-Host "[INFO] Launching published Velo.exe binary..." -ForegroundColor Cyan
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $PublishedExe
    $psi.WorkingDirectory = Split-Path $PublishedExe
    $psi.UseShellExecute = $true
    [System.Diagnostics.Process]::Start($psi) | Out-Null
} else {
    Write-Error "Could not find .NET 10 or Velo executable."
    exit 1
}

Start-Sleep -Seconds 2

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "        VELO RECEIVER IS ACTIVE AND BROADCASTING        " -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Service Details:" -ForegroundColor Yellow
Write-Host "  ------------------"
Write-Host "  WebSocket Port : 51821 (/velo)" -ForegroundColor White
Write-Host "  Discovery Port : 51820 (UDP)" -ForegroundColor White
Write-Host "  Beacon Packet  : VELO_BEACON|<HOSTNAME>|<IP>|51821" -ForegroundColor Green
Write-Host "  Probe String   : VELO_PROBE" -ForegroundColor Green
Write-Host ""
Write-Host "  How to Connect:" -ForegroundColor Yellow
Write-Host "  1. Connect your Android phone to the same Wi-Fi."
Write-Host "  2. Open Velo on your phone."
Write-Host "  3. This PC will appear automatically in 'Nearby Computers'."
Write-Host "  4. Tap Pair and enter the 6-digit code shown on your screen."
Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
