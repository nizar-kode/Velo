# Stop-LaserPointer.ps1
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like "*LaserPointerOverlay.py*" } | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host "Stopped Laser Pointer Overlay (PID: $($_.ProcessId))" -ForegroundColor Yellow
}
Write-Host "Laser pointer overlay closed." -ForegroundColor Green
