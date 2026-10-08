# Mousedroid Protocol Specification

## 1. Architecture Overview

Mousedroid provides low-latency remote control of PC inputs (mouse, gestures, keyboard, numpad) from an Android client device across three physical channels:
- **Wi-Fi (Local LAN)**
- **USB (ADB Reverse Tethering)**
- **Bluetooth (HID Profile)**

### Port Assignment & Separation

| Service / Channel | Default Port | Config Key | Transport | Direction | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Wi-Fi Control Channel** | `48291` | `WIFI_PORT` in `config.ini` | TCP | Android Client &rarr; PC Server | Device metadata handshake, connection management, activation state |
| **Wi-Fi Input Stream** | `48291` | `WIFI_PORT` in `config.ini` | UDP | Android Client &rarr; PC Server | High-rate mouse delta movements, scroll ticks, and keyboard keycodes |
| **ADB USB Channel** | `6969` | `ADB_PORT` in `config.ini` | TCP | Android Client &rarr; ADB Daemon &rarr; PC Server | Zero-latency wired tethering via `adb reverse tcp:6969 tcp:6969` |

---

## 2. Wi-Fi Remote-Control Protocol

### 2.1 Handshake (TCP Port 48291)
1. Upon connecting, the Android client initiates a TCP socket to `<PC_IP>:<WIFI_PORT>` (default: `48291`).
2. The client immediately transmits its device identification packet (up to 128 bytes):
   ```
   [Device Name];[Device Model];[Manufacturer]
   ```
3. The server registers the connected device and updates the GUI device list.
4. TCP connection remains open to detect disconnections and heartbeat state.

### 2.2 Input Streaming (UDP Port 48291)
- Touchpad motions, clicks, and gestures are dispatched as UDP datagrams directly to `<PC_IP>:<WIFI_PORT>`.
- UDP datagrams contain HID report packets:
  - **Mouse delta packet**: `[REPORT_ID, BUTTONS_BITMASK, DELTA_X, DELTA_Y, SCROLL_V, SCROLL_H]`
  - **Keyboard packet**: `[REPORT_ID, MODIFIERS, RESERVED, KEYCODE_1, ... KEYCODE_6]`
  - **Battery report packet**: `[BATTERY_REPORT_ID, PERCENTAGE]`

---

## 3. ADB Wired Remote-Control Protocol (USB)

- ADB mode operates independently over TCP port `6969`.
- The Windows server initiates:
  ```bash
  adb reverse tcp:6969 tcp:6969
  ```
- The Android client connects locally to `127.0.0.1:6969`.
- Traffic is tunneled through the ADB daemon over the USB cable directly to the PC's localhost ADB acceptor on port `6969`.
- Because ADB does not forward UDP packets, both handshake and input event streaming in USB mode are multiplexed over this TCP connection.

---

## 4. Socket Binding & Error Handling

### 4.1 Exclusive Port Binding
On Windows, the Wi-Fi server sets `SO_EXCLUSIVEADDRUSE` (and disables `SO_REUSEADDR`) on both TCP and UDP sockets. This guarantees that:
- Only one Wi-Fi server instance can bind to port `48291` at any given time.
- Any secondary instance attempting to bind will receive `WSAEADDRINUSE`.

### 4.2 Graceful Error Handling
- Socket binding failures (e.g. `WSAEADDRINUSE` / "bind: Only one usage of each socket address is normally permitted") are caught in `Server::InitSockets()`.
- The application logs the error without crashing.
- `wxApplication::OnInit()` detects the unbound state and presents an informative dialog instructing the user to terminate existing instances or select a different `WIFI_PORT` in `config.ini`.
- The ADB acceptor operates independently and does not prevent the application from functioning if either channel experiences a localized conflict.

---

## 5. Configuration & Custom Ports

### Windows (`config.ini`)
```ini
MINIMIZE_TASKBAR=0
MOVE_SENSITIVITY=10
RUN_STARTUP=0
SCROLL_SENSITIVITY=3
WIFI_PORT=48291
ADB_PORT=6969
```

### Android Client
- Devices can be added using standard IP (`192.168.0.165`) with the port input field defaulting to `48291`.
- Alternatively, endpoint addresses can be entered directly as `IP:PORT` (e.g., `192.168.0.165:48291`).
- Backward compatibility: connecting to a legacy server running on port `6969` is fully supported by entering `192.168.0.165:6969`.
