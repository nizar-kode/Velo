@echo off
title Mousedroid Laser Pointer Overlay
start /b pythonw "%~dp0LaserPointerOverlay.py"
echo [INFO] Laser Pointer Overlay is now active!
echo A glowing neon laser dot will track your cursor anywhere on screen.
echo To close, end 'pythonw.exe' in Task Manager or run: taskkill /F /IM pythonw.exe
