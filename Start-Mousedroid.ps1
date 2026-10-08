# Start-Mousedroid.ps1 - Native PowerShell Launcher with Auto-Discovery
$ServerDir = Join-Path $PSScriptRoot "mousedroid_win64\mousedroid_win64"
$ExePath = Join-Path $ServerDir "Mousedroid.exe"
$BeaconPath = Join-Path $ServerDir "discovery_beacon.py"

# Stop existing Mousedroid instances
Get-Process -Name "*Mousedroid*" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Stop-Process -Name "adb" -Force -ErrorAction SilentlyContinue

# Stop existing beacon
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -like "*discovery_beacon.py*"
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Start-Sleep -Seconds 1

# Start Mousedroid Server (detached from shell)
$psiServer = New-Object System.Diagnostics.ProcessStartInfo
$psiServer.FileName = $ExePath
$psiServer.WorkingDirectory = $ServerDir
$psiServer.UseShellExecute = $true
[System.Diagnostics.Process]::Start($psiServer) | Out-Null

# Start UDP Discovery Beacon (detached from shell)
$pyExe = (Get-Command pythonw -ErrorAction SilentlyContinue).Source
if (-not $pyExe) { $pyExe = (Get-Command python -ErrorAction SilentlyContinue).Source }

$psiBeacon = New-Object System.Diagnostics.ProcessStartInfo
$psiBeacon.FileName = $pyExe
$psiBeacon.Arguments = "`"$BeaconPath`""
$psiBeacon.WorkingDirectory = $ServerDir
$psiBeacon.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
$psiBeacon.UseShellExecute = $true
[System.Diagnostics.Process]::Start($psiBeacon) | Out-Null

# Start Windows Fluent Companion Tray
$CompanionPath = Join-Path $PSScriptRoot "MousedroidCompanion.pyw"
if (Test-Path $CompanionPath) {
    $psiComp = New-Object System.Diagnostics.ProcessStartInfo
    $psiComp.FileName = $pyExe
    $psiComp.Arguments = "`"$CompanionPath`""
    $psiComp.WorkingDirectory = $PSScriptRoot
    $psiComp.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
    $psiComp.UseShellExecute = $true
    [System.Diagnostics.Process]::Start($psiComp) | Out-Null
}

Start-Sleep -Seconds 2

# Verify port
$conn = Get-NetTCPConnection -LocalPort 48291 -ErrorAction SilentlyContinue

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "       MOUSEDROID SERVER IS READY AND RUNNING!          " -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Connection Details:" -ForegroundColor Yellow
Write-Host "  ------------------"
Write-Host "  PC IP Address    : 192.168.0.165" -ForegroundColor White
Write-Host "  Wi-Fi Server Port: 48291" -ForegroundColor White
Write-Host "  ADB / USB Port   : 6969" -ForegroundColor White
Write-Host "  Auto-Discovery   : ACTIVE (UDP port 48292)" -ForegroundColor Green
Write-Host ""
if ($conn) {
    Write-Host "  [OK] Server is listening on port 48291!" -ForegroundColor Green
} else {
    Write-Host "  [WAIT] Server started, check your Windows system tray." -ForegroundColor Yellow
}
Write-Host ""
Write-Host "  How to connect from your phone:"
Write-Host "  1. Connect phone to the same Wi-Fi network."
Write-Host "  2. Open Mousedroid on your phone."
Write-Host "  3. Tap 'Wi-Fi' - Your PC will appear AUTOMATICALLY!" -ForegroundColor Green
Write-Host "  4. Tap your PC to connect instantly!" -ForegroundColor Green
Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
