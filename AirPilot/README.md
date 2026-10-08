# Velo (Desktop Companion & Mobile Remote)

> Ultra-responsive PC control, trackpad, and presentation pointer from your phone.

Velo transforms your Android smartphone into a responsive, low-latency touchpad, presentation pointer, and media controller for your Windows PC over your local Wi-Fi network.

No cloud servers. No external telemetry. No accounts. 100% offline local network control.

---

## Architecture Overview

```
AirPilot/
├── android/
│   └── AirPilotAndroid/       # Kotlin, Jetpack Compose Material 3, OkHttp, Coroutines
├── windows/
│   ├── VeloDesktop/           # C# .NET 10, WPF, System Tray, SendInput API (Velo.exe)
│   ├── VeloDesktop.Tests/     # Unit & integration test suite (xUnit)
│   └── VeloDesktop.sln        # Visual Studio / .NET 10 Solution
├── shared/
│   └── protocol/              # Protocol schemas and specifications
├── docs/
│   ├── architecture.md        # Detailed system design
│   ├── protocol.md            # Packet specs and command definitions
│   ├── setup.md               # Build and setup instructions
│   ├── testing.md             # Test plan and validation checklists
│   └── roadmap.md             # Project milestones and feature progression
└── README.md
```

---

## Key Features

- **Zero-Config LAN Discovery**: Android discovers the Windows receiver automatically via UDP broadcast beacons (`VELO_BEACON|<HOSTNAME>|<IP>|51821`). No manual IP typing required.
- **PIN-Based Pairing & Security**: A 6-digit PIN is displayed on the Windows screen. Paired devices receive a 256-bit cryptographically secure token for subsequent sessions. Unauthenticated packets are rejected.
- **Native Hardware Input**: Dispatches native Windows hardware inputs via the Win32 `SendInput` API with subpixel movement accumulation for silky smooth cursor tracking.
- **Resilient Auto-Reconnection**: Automatically recovers from temporary Wi-Fi drops and sleep cycles with exponential backoff.

---

## Quick Start

### 1. Run the Windows Receiver (Velo Desktop)
Requirements: Windows 11 / 10, .NET 10 SDK.
```powershell
cd windows\VeloDesktop
& "$env:USERPROFILE\.dotnet\dotnet.exe" run
```

### 2. Run the Android Controller (Velo Remote)
Requirements: Android 8.0+, Android Studio / SDK Build Tools.
```powershell
cd android\AirPilotAndroid
.\gradlew installDebug
```

For comprehensive instructions, see [docs/setup.md](docs/setup.md).

---

## Acknowledgments & Credits

Velo explicitly credits **darusc** and the original **Droid Studio / Mousedroid** project ([https://github.com/darusc/Mousedroid](https://github.com/darusc/Mousedroid)) for the foundational architecture and concept that inspired Velo.

Velo builds upon this open-source heritage with a modern .NET 10 WPF receiver, Jetpack Compose Material 3 UI, zero-config UDP discovery, and virtual laser overlay.
