# Mousedroid Project Context & Resolution Guide

## 1. Project Overview & Architecture

[Mousedroid](https://github.com/darusc/Mousedroid) is an open-source wireless remote-control system allowing an Android smartphone to function as a trackpad, mouse, keyboard, and media controller for desktop operating systems (Windows and Linux).

The system consists of two primary components:
1. **Android Client (`/client`)**: Written in Kotlin with Jetpack Components, Coroutines, StateFlow, and ViewBinding.
2. **Desktop Server (`/server` & `/mousedroid_win64`)**: Written in C++ using `wxWidgets` (for GUI and system tray) and `standalone Asio` (for asynchronous network I/O and Win32 `SendInput` event dispatching).

### Network Transport Modes
- **Wi-Fi Mode**: Android client establishes a TCP connection to the desktop server over LAN, accompanied by a UDP channel for low-latency mouse cursor coordinates.
- **USB / ADB Mode**: Android client connects over USB via Android Debug Bridge (ADB) port forwarding (`adb forward tcp:6969 tcp:6969` / `adb reverse`), communicating through a localhost loopback tunnel.
- **Bluetooth HID Mode**: Uses Android's `BluetoothHidDevice` API where supported.

---

## 2. Root Cause Analysis: The Port Collision Crash

### The Observed Error
```
Unhandled standard exception of type 'class std::system_error' with message 
'bind: Only one usage of each socket address (protocol/network address/port) is normally permitted.'; 
terminating the application.
```

### Why It Occurred
1. **Wi-Fi and ADB Port Competition**: In original Mousedroid v1.5, both the Wi-Fi remote-control listener and the ADB forward command targeted TCP port `6969`. When ADB forwarded `127.0.0.1:6969` or another service used 6969, `Mousedroid.exe` attempted to bind `0.0.0.0:6969`. On Windows, this triggered `WSAEADDRINUSE` (WinSock error 10048).
2. **Hidden Background Instances**: When `Mousedroid.exe` starts, it creates a notification icon in the Windows Taskbar Notification Area (system tray). When users double-click `Mousedroid.exe` again thinking it didn't open, the second process attempts to bind the identical port. Because original v1.5 lacked exception handling on `acceptor.bind()`, an unhandled `std::system_error` was thrown, crashing the application.
3. **Hardcoded Port in Machine Code**: The pre-compiled Windows v1.5 release binary (`mousedroid_win64/Mousedroid.exe`) hardcoded the port number (`mov edx, 6969` -> `ba 39 1b 00 00`) at binary offset `0x443e3`.

---

## 3. Implemented Fixes & Architectural Separation

### A. Dedicated Wi-Fi Port (48291) vs. ADB Port (6969)
- **Wi-Fi Listener**: Moved to default port **`48291`**.
- **ADB Forwarding**: Remains isolated on **`6969`**.
- Sockets can now coexist with zero port collisions.

### B. Windows Executable Binary Patching
- The release binary [`mousedroid_win64/mousedroid_win64/Mousedroid.exe`](file:///E:/code/antigravity/mously.ly/mousedroid_win64/mousedroid_win64/Mousedroid.exe) was patched at offset `0x443e3`:
  - `BA 39 1B 00 00` (`mov edx, 6969`) $\rightarrow$ `BA A3 BC 00 00` (`mov edx, 48291`).
  - Wi-Fi server now automatically binds to `0.0.0.0:48291`.
  - The original v1.5 binary is preserved as [`Mousedroid.original.exe`](file:///E:/code/antigravity/mously.ly/mousedroid_win64/mousedroid_win64/Mousedroid.original.exe).

### C. Android Client Updates (`app-debug.apk`)
- [`ConnectionManager.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/networking/ConnectionManager.kt):
  - `DEFAULT_WIFI_PORT = 48291`
  - `DEFAULT_ADB_PORT = 6969`
- UI Support: Added custom port input fields to [`device_add_fragment.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/res/layout/device_add_fragment.xml) and automatic port fallback in [`NetUtils.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/networking/NetUtils.kt).
- APK compiled and verified: [`app-debug.apk`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/build/outputs/apk/debug/app-debug.apk).

---

## 4. Zero-Config Auto-Discovery System (AirPilot-Style)

### Why Devices Did Not Appear Before
In original Mousedroid:
- Bluetooth mode queried local `pairedDevices`.
- Wi-Fi mode **only** queried local `SharedPreferences("devices")`. There was **zero network scanning or beacon broadcasting code**.
- Unless the user manually clicked `+` and typed their IP, the list was completely empty.

### The New Auto-Discovery Pipeline
```
[ Windows PC (Nizar) ]                                  [ Android Smartphone ]
       │                                                          │
       ├─► (Periodic UDP Broadcast to 255.255.255.255:48292) ────►│
       │   "MOUSEDROID_BEACON|DESKTOP-ABC|192.168.0.165|48291"    │ (WifiDiscoveryManager)
       │                                                          │
       │◄─ (Immediate Probe Broadcast) ───────────────────────────┤
       │   "MOUSEDROID_PROBE"                                     │
       │                                                          │
       ├─► (Direct Unicast Response) ────────────────────────────►│
       │   "MOUSEDROID_BEACON|DESKTOP-ABC|192.168.0.165|48291"    ▼
       │                                                 [ Appears in Device List! ]
       │                                                 "DESKTOP-ABC (192.168.0.165)"
       │                                                          │
       │◄── (User Taps Device - 1 Tap Connect!) ──────────────────┘
```

1. **PC-Side UDP Beacon**:
   - [`discovery_beacon.py`](file:///E:/code/antigravity/mously.ly/discovery_beacon.py) runs alongside the server.
   - Listens on UDP port `48292` for probe requests and broadcasts every 2 seconds.
   - Automatically included when launching via [`Start-Mousedroid.ps1`](file:///E:/code/antigravity/mously.ly/Start-Mousedroid.ps1) or [`Start_Mousedroid.bat`](file:///E:/code/antigravity/mously.ly/Start_Mousedroid.bat).
2. **Android-Side Discovery Manager**:
   - [`WifiDiscoveryManager.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/networking/WifiDiscoveryManager.kt):
   - Acquires Android Wi-Fi `MulticastLock`.
   - Sends probe packet immediately on screen load and listens for beacons.
   - Automatically injects discovered devices into [`DeviceListViewModel.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/viewmodels/DeviceListViewModel.kt).
   - Devices persist in `SharedPreferences` so they remain remembered even if Wi-Fi blips.

### 4.1 Root Cause & Full Resolution of "Nothing Appearing" Issue

During device discovery testing, several hidden bugs were diagnosed and resolved:
1. **ViewModel State Population Bug (`DeviceListViewModel.kt`)**:
   - In `updateState()`, an `else` branch was missing. The Wi-Fi population logic was nested inside `if (mode == BLUETOOTH)`. In Bluetooth mode, it immediately overwrote paired devices with an empty list; in Wi-Fi mode, it never executed `setState()`. Both modes resulted in permanently empty lists.
   - **Resolution**: Refactored `updateState()` with proper `if-else` branches so Bluetooth populates paired devices and Wi-Fi populates discovered devices.
2. **ViewModel Lifetime Scoping Bug (`DeviceList.kt`)**:
   - `deviceListViewModel` was scoped to `by activityViewModels`. Opening Bluetooth mode first locked the ViewModel into `Connection.Mode.BLUETOOTH`. Subsequent navigation to Wi-Fi reused the Bluetooth instance, causing `startAutoDiscovery` to abort prematurely (`if (mode != WIFI) return`).
   - **Resolution**: Changed to `by viewModels` (scoped to Fragment lifecycle). Each mode now initializes its own fresh ViewModel with the appropriate factory.
3. **Missing Android Permissions**:
   - `CHANGE_WIFI_MULTICAST_STATE` and `ACCESS_WIFI_STATE` were absent from `AndroidManifest.xml`, causing `MulticastLock.acquire()` to fail and dropping incoming UDP broadcasts.
   - `BLUETOOTH_SCAN` was missing, and runtime checks in `MainActivity.kt` used `SDK_INT >= S_V2` (32) instead of `S` (31).
   - **Resolution**: Added all missing permissions to `AndroidManifest.xml` and updated runtime permission checks to `Build.VERSION_CODES.S` requesting both `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`.
4. **Windows Multi-Homed UDP Broadcast (`discovery_beacon.py`)**:
   - Broadcasting solely to `255.255.255.255` was routed by Windows out of `vEthernet` (Hyper-V / WSL) rather than the physical Wi-Fi interface (`192.168.0.165`).
   - **Resolution**: `discovery_beacon.py` now dynamically computes all interface subnet broadcast addresses (e.g., `192.168.0.255`) and broadcasts to all active subnets simultaneously.
5. **USB Cable Detection Hijacking (`ConnectionViewModel.kt`)**:
   - When charging via USB, `hasUsbConnection()` returned true, routing the user to ADB (`port 6969`) rather than the Wi-Fi device list.
   - **Resolution**: `startServerMode()` now prioritizes active Wi-Fi connections, directing the user to the auto-discovery device list.
6. **Empty State Guidance (`fragment_device_list.xml` & `DeviceList.kt`)**:
   - When no devices are paired, the app now shows helpful diagnostic hints rather than a pitch-black screen.

### 4.2 SecurityException Crash Resolution
- **Observed Behavior**: The Android app crashed immediately whenever touching the trackpad or clicking the laser button.
- **Root Cause**: `GestureHandler.kt` (single tap & double tap) and `Presenter.kt` invoked `HapticHelper.startLaserHum(context)` which called `Vibrator.vibrate()`. Android throws an unhandled `java.lang.SecurityException: Requires VIBRATE permission` when `android.permission.VIBRATE` is missing from `AndroidManifest.xml`.
- **Resolution**:
  1. Added `<uses-permission android:name="android.permission.VIBRATE" />` to [`AndroidManifest.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/AndroidManifest.xml).
  2. Wrapped vibrator execution inside safe `try-catch` blocks within [`HapticHelper.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/utils/HapticHelper.kt) to ensure hardware resilience.

### 4.3 Redesigned Air Mouse & Laser Experience
- **Problem**: The previous laser design forced users to hold down a giant circular disc with their thumb while waving their wrist, causing hand fatigue and awkward aiming.
- **Redesigned Solution**:
  1. **Continuous Toggle ON/OFF Switch**: Tap `⚡ START AIR MOUSE` once, and your phone acts as a free-floating air mouse (like an LG Magic Remote). Wave your hand naturally without keeping any finger pressed.
  2. **Ergonomic Thumb Clicks**: Added dedicated glass `Left Click`, `Right Click`, and `🎯 Recenter` buttons right under the thumb.
  3. **Integrated Presentation Paddles**: `◀ Previous Slide` and `Next Slide ▶` buttons directly on the air mouse screen.
  4. **Momentary Hold Trigger Bar**: Retained a comfortable bottom trigger bar for quick momentary pointing bursts.
  5. **Instant Tab Switcher**: Placed a glass segmented switch (`[ 🖱️ Trackpad ] [ 🎯 Air Laser ]`) at the top of the main screen for seamless 1-tap switching without digging into side drawers.

### 4.4 Full Glassmorphism (Frosted Glass) Design System
- **Background (`bg_space_glass.xml`)**: Deep space navy/obsidian (`#0A0E1A`) with ambient glowing radial neon orbs (`#38BDF8` cyan and `#6366F1` indigo).
- **Glass Cards (`glass_card.xml`, `glass_card_active.xml`)**: Translucent frosted surfaces with 20dp corners and 1.2dp specular highlight borders.
- **Glass Touchpad Surface (`glass_touchpad_surface.xml`)**: Frosted glass trackpad with glowing specular border.
- **Glass Buttons (`glass_button_default.xml`, `glass_button_primary.xml`, `glass_button_laser.xml`)**: Translucent glass buttons with glowing gradients and tactile haptics.
- **Complete Visual Overhaul**: Applied across `Main`, `DeviceList`, `Touchpad`, `Presenter`, and `Navigation Drawer`.

---

## 5. Modern Presenter Mode & Air-Laser System

### A. Gyroscope Air-Wand Pointer Engine
- [`GyroscopeLaserManager.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/sensors/GyroscopeLaserManager.kt):
  - Uses Android's hardware `Sensor.TYPE_ROTATION_VECTOR` (fusion-filtered gyro + accelerometer + magnetometer).
  - Calculates delta azimuth (yaw) and elevation (pitch) in radians.
  - Normalizes $-\pi \leftrightarrow +\pi$ boundary transitions and applies a micro-deadzone filter to eliminate hand tremors.
  - Streams high-frequency relative $(\Delta x, \Delta y)$ mouse packets over UDP (`48291`).

### B. Tactile Haptics Engine
- [`HapticHelper.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/utils/HapticHelper.kt):
  - Provides physical click sensation on slide changes and mouse clicks.
  - Generates a soft electric hum vibration on the phone while holding the laser button.

### C. Presenter UI & Navigation
- Layout [`fragment_presenter.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/res/layout/fragment_presenter.xml) and Fragment [`Presenter.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/fragments/Presenter.kt):
  - **🔴 Big Glowing Laser Button**: Hold down to fire the laser beam and aim by tilting the phone in the air.
  - **◀ PREVIOUS / NEXT ▶ Paddles**: Large touch targets mapped to `VK_PRIOR` and `VK_NEXT`.
  - **Presentation Action Bar**: Start Show (`F5`), Black Screen (`B`), White Screen (`W`), Exit (`Esc`).
  - **Presentation Timer**: Stopwatch tracking elapsed presentation minutes and seconds.

### D. Windows Desktop Laser Dot Overlay
- [`LaserPointerOverlay.py`](file:///E:/code/antigravity/mously.ly/LaserPointerOverlay.py) & [`Start_Laser_Pointer.bat`](file:///E:/code/antigravity/mously.ly/Start_Laser_Pointer.bat) / [`Start-LaserPointer.ps1`](file:///E:/code/antigravity/mously.ly/Start-LaserPointer.ps1):
  - Lightweight, click-through (`WS_EX_TRANSPARENT | WS_EX_LAYERED`) transparent overlay.
  - Projects a bright glowing neon red laser dot with white core and pulsing halo directly at the Windows mouse cursor.

---

## 6. How to Use the System Now

1. **On PC**:
   - In PowerShell: run `powershell -ExecutionPolicy Bypass -File .\Start-Mousedroid.ps1`
   - Or in File Explorer: double-click `Start_Mousedroid.bat`.
   - The server, the UDP Auto-Discovery beacon, and the Fluent Companion start automatically!

2. **On Phone**:
   - Install the new redesigned APK: [`Mousedroid-Premium-v2.apk`](file:///E:/code/antigravity/mously.ly/Mousedroid-Premium-v2.apk) (or stable fallback [`Mousedroid-Laser-Debug.apk`](file:///E:/code/antigravity/mously.ly/Mousedroid-Laser-Debug.apk)).
   - Open Mousedroid and tap **Server (USB/WIFI)**.
   - **Your PC (`Nizar-Pc - 192.168.0.165:48291`) appears automatically in the list!**
   - Tap your PC $\rightarrow$ **Connected instantly with 1 tap, zero typing required!**

---

## 7. Premium UI/UX Modernization (2026 Commercial Standard)

### A. 3D Interactive Holographic Dot Surface
- Custom View: [`ParticleTouchOverlayView.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/views/ParticleTouchOverlayView.kt)
- Features:
  - **Stationary 3D Dot Matrix**: The surface is composed of a fixed, evenly spaced grid of dots representing a virtual 3D plane anchored to the screen.
  - **Physical Elastic Deformation**: The dots themselves never translate across the screen; instead, your finger creates a moving 3D indentation / wave that propagates through the grid.
  - **Depth-Based Perspective Displacement**: Dots directly under the fingertip depress inward into the 3D space, radially pulling neighboring dots toward the touch center with realistic perspective elevation tilt.
  - **Surrounding Elastic Crest**: An elastic rebound wave bulges upward surrounding the depression with glowing specular cyan highlights (`#E0F2FE` / `#7DD3FC`).
  - **Variable Dot Size & Lighting**: Recessed areas render smaller, darker dots (`#0284C7`), while raised crests expand in size and intensity (`#38BDF8`).
  - **Discrete Dots (No Blobs)**: Fixed grid spacing prevents dots from merging into a solid particle blob; every point in the digital lattice remains crisp and individually visible.
  - **Harmonic Settling & Elastic Recovery**: Gently breathes when stationary, and smoothly springs back to the flat resting plane when the finger lifts.
  - **Zero Input Lag**: Completely decoupled from touch dispatch (`isClickable = false`, `isFocusable = false`), running at 60/120fps with 0 GC allocations.

### B. Integrated Ergonomic Click Paddles Deck
- Layout: [`fragment_touchpad.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/res/layout/fragment_touchpad.xml)
- Drawables: [`glass_paddle_left.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/res/drawable/glass_paddle_left.xml), [`glass_paddle_mid.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/res/drawable/glass_paddle_mid.xml), [`glass_paddle_right.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/res/drawable/glass_paddle_right.xml).
- Eliminates clunky separated buttons in favor of an integrated unibody glass deck with Left paddle (50%), tactile Middle Click chip, and Right paddle (50%).

### C. Floating Glass Bottom Navigation Bar
- Layout: [`fragment_input.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/res/layout/fragment_input.xml) & Fragment [`Input.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/fragments/Input.kt)
- 4 Destinations:
  1. `Trackpad` (`ic_touchpad`): Flat touchpad with reactive particles + Air Laser switcher.
  2. `Presenter` (`ic_presentation`): Handheld audience presentation remote with slide paddles, timer, and air pointer.
  3. `Shortcuts` (`ic_baseline_keyboard_24`): Windows shortcut chip matrix + soft keyboard trigger.
  4. `Media` (`ic_play`): Polished glass media playback deck.
- Floating glass capsule (`glass_bottom_nav_bg.xml`) with active pill indicator (`glass_nav_item_active.xml`).
- Seamlessly auto-hides in landscape fullscreen mode.

### D. Modern Shortcuts Screen
- Layout: [`fragment_shortcuts.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/res/layout/fragment_shortcuts.xml) & Fragment [`Shortcuts.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/fragments/Shortcuts.kt)
- Glass chips for `Ctrl+C`, `Ctrl+V`, `Ctrl+X`, `Ctrl+Z`, `Ctrl+A`, `Ctrl+S`, `Alt+Tab`, `Win+D`, `Win+Tab`, `Esc`, `Enter`, `Backspace`, `Delete`.
- Large prominent button to summon Android soft keyboard.

### E. Dedicated Media Controller Screen
- Layout: [`fragment_media.xml`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/res/layout/fragment_media.xml) & Fragment [`Media.kt`](file:///E:/code/antigravity/mously.ly/Mousedroid/client/app/src/main/java/com/darusc/mousedroid/fragments/Media.kt)
- Large circular glass Play/Pause button, Skip Previous / Next, Replay / Forward 10s, Volume Up/Down, and Mute.

### F. Windows 11 Fluent Receiver Companion
- Companion: [`MousedroidCompanion.pyw`](file:///E:/code/antigravity/mously.ly/MousedroidCompanion.pyw)
- Windows Notification Area (System Tray) icon with right-click menu and fluent dark acrylic dashboard (`#0B0F14`).
- Real-time client connection detector, laser overlay toggle, and server restart controls.

### G. Build Artifacts
- **Stable Reference APK**: [`Mousedroid-Laser-Debug.apk`](file:///E:/code/antigravity/mously.ly/Mousedroid-Laser-Debug.apk) (13,228,811 bytes)
- **Redesigned Premium APK**: [`Mousedroid-Premium-v2.apk`](file:///E:/code/antigravity/mously.ly/Mousedroid-Premium-v2.apk) (13,232,403 bytes)
- **Windows Server Executables**: [`mousedroid_win64/mousedroid_win64/Mousedroid.exe`](file:///E:/code/antigravity/mously.ly/mousedroid_win64/mousedroid_win64/Mousedroid.exe) & [`Mousedroid.original.exe`](file:///E:/code/antigravity/mously.ly/mousedroid_win64/mousedroid_win64/Mousedroid.original.exe)

