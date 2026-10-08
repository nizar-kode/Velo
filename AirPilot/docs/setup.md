# Velo Setup & Build Guide

## Prerequisites

### 1. Windows Machine (Receiver)
- Windows 10 / 11 (64-bit)
- .NET 10 SDK (installed at `%USERPROFILE%\.dotnet` or system path)
- Windows Firewall configured to allow incoming UDP on port `51820` and TCP on port `51821` on Private Networks.

### 2. Android Device (Controller)
- Physical Device: Android 8.0+ device (e.g. Samsung Galaxy A34)
- Wi-Fi enabled and connected to the same local subnet as the Windows PC
- Developer Mode & USB Debugging enabled (for deploying via `adb`)

### 3. Developer Workstation Tools
- Java JDK 17 or 21 (`java -version`)
- Android CLI / Android SDK Build Tools 34+
- `adb` (Android Debug Bridge)

---

## Building the Windows Receiver (Velo Desktop)

1. Open PowerShell and navigate to the Windows project directory:
   ```powershell
   cd AirPilot\windows\VeloDesktop
   ```
2. Build the project using .NET 10:
   ```powershell
   & "$env:USERPROFILE\.dotnet\dotnet.exe" build -c Release
   ```
3. Run the application:
   ```powershell
   & "$env:USERPROFILE\.dotnet\dotnet.exe" run -c Release
   ```
   Or launch via root script:
   ```powershell
   .\Start-Velo.ps1
   ```
   The Velo receiver icon will appear in the system tray, and the Main Window will display the current local IP address, service port (`51821`), and a random 6-digit **Pairing Code**.

---

## Building and Running the Android App (Velo Remote)

1. Connect your Android device to the PC via USB and verify ADB connection:
   ```powershell
   adb devices
   ```
2. Navigate to the Android project directory:
   ```powershell
   cd AirPilot\android\AirPilotAndroid
   ```
3. Build the debug APK:
   ```powershell
   .\gradlew assembleDebug
   ```
4. Install and run on your device:
   ```powershell
   .\gradlew installDebug
   ```
   Or launch via ADB:
   ```powershell
   adb shell am start -n com.velo.remote/.MainActivity
   ```

---

## End-to-End Pairing Procedure

1. Connect phone and PC to the identical Wi-Fi network (or mobile hotspot).
2. Launch Velo on Windows. Note the 6-digit code.
3. Launch Velo on your Android phone.
4. The PC appears automatically under "Nearby Computers".
5. Tap **Pair**, enter the 6-digit code, and tap **Pair**.
6. The controller interface activates instantly.
