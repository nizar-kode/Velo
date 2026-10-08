# Start-LaserPointer.ps1 - Native PowerShell Launcher
$script = Join-Path $PSScriptRoot "LaserPointerOverlay.py"

# Stop any existing overlay first
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -in @("python.exe", "pythonw.exe") -and $_.CommandLine -like "*LaserPointerOverlay.py*"
} | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}

# Resolve Python executable
$pyExe = (Get-Command pythonw -ErrorAction SilentlyContinue).Source
if (-not $pyExe) {
    $pyExe = (Get-Command python -ErrorAction SilentlyContinue).Source
}

# Launch fully detached using ProcessStartInfo
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $pyExe
$psi.Arguments = "`"$script`""
$psi.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
$psi.UseShellExecute = $true
[System.Diagnostics.Process]::Start($psi) | Out-Null

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   MOUSEDROID LASER POINTER OVERLAY IS NOW ACTIVE!      " -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "A glowing neon red laser dot will track your cursor."
Write-Host "To turn it off, run: .\Stop-LaserPointer.ps1" -ForegroundColor Yellow
Write-Host ""
