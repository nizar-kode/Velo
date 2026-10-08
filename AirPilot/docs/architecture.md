# Velo System Architecture

## 1. High-Level Concept

**Velo** turns an Android smartphone (e.g. Samsung Galaxy A34) into an ultra-responsive, low-latency touchpad and presentation pointer for a Windows 11 PC. It operates 100% locally over Wi-Fi without cloud dependencies, external relays, or account sign-ins.

```
+------------------------------------+
|  Velo Remote (Android)             |
|  Package: com.velo.remote          |
|                                    |
|  [Jetpack Compose / Material 3 UI] |
|                 |                  |
|          [Touch Surface]           |
|                 |                  |
|    [Discovery / Pairing Manager]   |
|                 |                  |
|     [WebSocket Client (OkHttp)]    |
+-----------------+------------------+
                  |
         Local Wi-Fi Network
         (UDP 51820 & WS 51821)
                  |
+-----------------v------------------+
|  Velo Desktop Receiver (WPF/.NET)  |
|                                    |
|      [UDP Discovery Beacon]        |
|                 |                  |
|     [Kestrel WebSocket Server]     |
|                 |                  |
|    [Session & Security Manager]    |
|                 |                  |
|      [Input Dispatcher Engine]     |
|        /        |        \         |
|   SendInput  CoreAudio  Overlay    |
|   (Cursor/   (Volume/   (Laser     |
|   Keyboard)   Media)    Pointer)   |
+------------------------------------+
```

---

## 2. Windows Receiver Component Architecture

The Windows receiver is built with **C# / .NET 10** as a lightweight system tray application (`VeloDesktop` / `Velo.exe`) using WPF:

### 2.1 Modules
1. **Tray Application & Window (`VeloDesktop`)**:
   - Resides in the Windows system notification area.
   - Shows live server status (e.g., Connected device name, IP, Port).
   - Generates and presents 6-digit pairing code PIN.
   - Displays Acknowledgments & Credits modal.
2. **Auto-Discovery Beacon (`DiscoveryBeacon`)**:
   - Binds to UDP port `51820`.
   - Listens for `VELO_PROBE` and broadcasts `VELO_BEACON|<HOSTNAME>|<IP>|51821`.
3. **Control Server (`VeloServer`)**:
   - Embedded WebSocket server running on `ws://0.0.0.0:51821/velo`.
4. **Security & Pairing Manager (`SecurityManager`)**:
   - Manages PIN generation, verification, and rotation.
   - Stores paired device records and crypto tokens in `%LOCALAPPDATA%/Velo/paired_devices.json`.
5. **Native Input Engine (`NativeInputSimulator`)**:
   - Dispatches Win32 hardware inputs using `user32.dll SendInput`.
   - Includes subpixel fractional movement accumulation.
6. **Diagnostics & Structured Logger (`VeloLogger`)**:
   - Ring-buffered in-memory logging exposed to the WPF UI and console.

---

## 3. Android Controller Component Architecture

The Android controller is built with **Kotlin, Jetpack Compose, Material 3, and Coroutines** (`Velo Remote`):

1. **Presentation Layer**:
   - Material 3 dark-mode theme with high-contrast slate, cyan, and emerald accents.
   - Fluid drag and tap detection for cursor movement and clicks.
   - About Velo modal with comprehensive ethics attribution.
2. **Networking**:
   - `UdpDiscoveryClient`: Discovers nearby Velo PC receivers within 1–2 seconds.
   - `VeloWebSocketClient`: OkHttp-based WebSocket client managing auto-reconnect, keepalive pings, and binary-grade streaming.
3. **State Management**:
   - `VeloViewModel`: Single source of truth driving Compose UI via StateFlow.

---

## 4. Acknowledgments & Credits

Velo builds upon the foundational architecture and concept originated by **darusc** in the **Droid Studio / Mousedroid** project (https://github.com/darusc/Mousedroid). Velo modernizes this heritage with a .NET 10 WPF receiver, Jetpack Compose Material 3 UI, zero-config UDP discovery, and virtual laser pointer overlay.
