# Velo

<p align="center">
  <strong>Ultra-responsive PC control, trackpad, and presentation pointer from your phone.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Windows%2011%20%7C%20Android%208.0+-blue?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/.NET-10.0%20WPF-purple?style=flat-square" alt=".NET 10" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-brightgreen?style=flat-square" alt="Compose M3" />
  <img src="https://img.shields.io/badge/Network-100%25%20Offline%20LAN-success?style=flat-square" alt="Offline LAN" />
  <img src="https://img.shields.io/badge/Tests-Passing%20(14%2F14)-teal?style=flat-square" alt="Tests" />
  <img src="https://img.shields.io/badge/License-MIT-orange?style=flat-square" alt="License" />
</p>

---

## Overview

**Velo** is a high-performance, private, zero-cloud remote control system that turns your Android smartphone into an ultra-responsive touchpad, presentation pointer, and wireless controller for your Windows PC over local Wi-Fi.

No cloud servers. No external telemetry. No account sign-ins. 100% local network execution.

- **Desktop Receiver**: Velo Desktop (`Velo.exe`, .NET 10 WPF)
- **Mobile Controller**: Velo Remote (`com.velo.remote`, Kotlin + Jetpack Compose)

---

## Architecture Diagram

```
+-------------------------------------------------------+
|              Velo Remote (Android Phone)              |
|               Package: com.velo.remote                |
|                                                       |
|  [ Jetpack Compose M3 UI ]   [ Tactile Touch Surface ]|
|                 │                           │         |
|                 ▼                           ▼         |
|      [ UdpDiscoveryClient ]      [ VeloWebSocketClient ]
+─────────────────┼───────────────────────────┼─────────+
                  │                           │
          UDP Broadcast (51820)       WebSocket (51821 /velo)
          "VELO_PROBE"                Encrypted JSON Envelopes
                  │                           │
                  ▼                           ▼
+─────────────────┼───────────────────────────┼─────────+
|      [ DiscoveryBeacon ]             [ VeloServer ]   |
|                 │                           │         |
|                 ▼                           ▼         |
|    [ SecurityManager (PIN & 256-bit Token Auth) ]     |
|                             │                         |
|                             ▼                         |
|           [ Native Win32 SendInput Engine ]           |
|            (Subpixel Cursor Accumulator)              |
|                                                       |
|             Velo Desktop Receiver (Windows PC)        |
+-------------------------------------------------------+
```

---

## Key Features

1. **Zero-Config LAN Discovery**:
   - PC broadcasts `VELO_BEACON|<HOSTNAME>|<IP>|51821` on UDP port `51820`.
   - Android sends `VELO_PROBE` upon launch.
   - Nearby PCs appear instantly in "Nearby Computers" within 1–2 seconds without manually typing IP addresses.

2. **Cryptographic PIN & Token Pairing**:
   - Rolling 6-digit PIN displayed on the desktop screen.
   - Paired devices receive a 256-bit cryptographically secure token stored in `%LOCALAPPDATA%/Velo/paired_devices.json`.
   - All unauthenticated commands are rejected at the network perimeter.

3. **Hardware-Native Mouse Dispatching**:
   - Emits hardware input events through the native Win32 `user32.dll SendInput` API.
   - Uses subpixel float-to-integer accumulators to eliminate cursor stutter and ensure silky-smooth tracking.

4. **Modern Jetpack Compose Material 3 Interface**:
   - High-contrast slate and emerald dark-mode theme.
   - Haptic-assisted left/right click buttons and multi-touch surface.

5. **Presentation Pointer & Virtual Overlay**:
   - Integrated presenter controls and desktop overlay support for conferences and presentations.

---

## Project Structure

```
├── AirPilot/
│   ├── android/
│   │   └── AirPilotAndroid/     # Kotlin, Jetpack Compose Material 3, OkHttp, Coroutines
│   │       ├── app/src/main/    # com.velo.remote package
│   │       └── app/src/test/    # Protocol serialization unit tests
│   ├── windows/
│   │   ├── VeloDesktop/         # C# .NET 10, WPF, System Tray, SendInput API (Velo.exe)
│   │   ├── VeloDesktop.Tests/   # Unit & integration test suite (xUnit)
│   │   └── VeloDesktop.sln      # Solution file
│   ├── shared/
│   │   └── protocol/            # Protocol JSON schemas and specifications
│   └── docs/
│       ├── architecture.md      # Detailed system architecture
│       ├── protocol.md          # Packet formats, beacon specs, and schema
│       ├── setup.md             # Developer build and deployment guide
│       ├── testing.md           # Test suite procedures and verification
│       └── roadmap.md           # Project roadmap and milestones
├── Start-Velo.bat               # Windows batch launcher
├── Start-Velo.ps1               # Native PowerShell detached launcher
└── README.md                    # Root documentation
```

---

## Installation & Quick Start

### 1. Launching Velo Desktop (Windows)

#### Option A: Quick Launch Scripts
Double-click `Start-Velo.bat` or run via PowerShell:
```powershell
.\Start-Velo.ps1
```

#### Option B: Build & Run from Source (.NET 10)
```powershell
cd AirPilot\windows\VeloDesktop
& "$env:USERPROFILE\.dotnet\dotnet.exe" run
```

*Note: The receiver minimizes to the system notification area (system tray). Double-click the tray icon to reopen the main window.*

### 2. Launching Velo Remote (Android)

Requirements: Android 8.0+ device on the same local Wi-Fi network.

```powershell
cd AirPilot\android\AirPilotAndroid
.\gradlew installDebug
```
Or start via ADB:
```powershell
adb shell am start -n com.velo.remote/.MainActivity
```

### 3. Pairing
1. Keep Velo Desktop open on your PC; observe the 6-digit PIN.
2. Open Velo on your phone.
3. Tap your computer under **Nearby Computers**.
4. Enter the 6-digit PIN and tap **Pair**. You are now connected!

---

## Automated Test Suites

### Windows Test Suite (.NET 10 xUnit)
```powershell
& "$env:USERPROFILE\.dotnet\dotnet.exe" test AirPilot\windows\VeloDesktop.Tests\VeloDesktop.Tests.csproj
```
> **10 passed, 0 failed**

### Android Test Suite (Gradle JUnit)
```powershell
cd AirPilot\android\AirPilotAndroid
.\gradlew test
```
> **BUILD SUCCESSFUL, 4 passed**

---

## Acknowledgments & Credits

> [!NOTE]
> **Open Source Heritage & Lineage**
>
> Velo explicitly credits **darusc** and the original **Droid Studio / Mousedroid** project ([https://github.com/darusc/Mousedroid](https://github.com/darusc/Mousedroid)) for the foundational architecture and concept that inspired Velo.
>
> Velo builds upon this open-source heritage with a modern .NET 10 WPF receiver, Jetpack Compose Material 3 UI, zero-config UDP discovery, and virtual laser overlay.

---

## License

This project is licensed under the [MIT License](LICENSE).
