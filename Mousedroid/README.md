<h1 align="center">
  <sub>
    <img src="imgs/logo.png" width="150">
  </sub>
  <br>
  Mousedroid
</h1>

<p align="center">Transform your Android phone into a high-performance cross-platform mouse & keyboard.</p>

<p align="center">
  <a href="https://github.com/darusc/mousedroid/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/darusc/mousedroid?style=for-the-badge" alt="License">
  </a>
  <a href="https://github.com/darusc/mousedroid/releases">
    <img src="https://img.shields.io/github/v/release/darusc/mousedroid?style=for-the-badge" alt="Release">
  </a>
  <a href="https://github.com/darusc/mousedroid/releases">
    <img src="https://img.shields.io/github/downloads/darusc/mousedroid/total?style=for-the-badge" alt="Downloads">
  </a>
  <a href="https://apt.izzysoft.de/fdroid/index/apk/com.darusc.mousedroid">
    <img src="https://img.shields.io/badge/IzzyOnDroid-Repo-blue?style=for-the-badge" alt="IzzyOnDroid">
  </a>
</p>

## Description

Mousedroid is a versatile, cross-platform application that turns your Android phone into a remote input peripheral. Control your PC or laptop with a precision touchpad, a full QWERTY keyboard, or a dedicated numpad. Whether you're navigating a media center from the couch or need an emergency mouse, Mousedroid works seamlessly over Wi-Fi, Bluetooth, or USB.

### Supported Platforms
* **PC:** Windows 10/11 and Linux (Ubuntu 22.04+ recommended).
* **Phone:** Android 9.0 and newer.

## Key Features

* **Three Connection Modes:** 
    * **Bluetooth:** Direct pairing for a cable-free experience without needing a shared network.
    * **Wi-Fi:** Wireless freedom across your local network.
    * **USB (Wired):** Near-zero latency using ADB.
* **Full Input Suite:** Switch between a responsive Touchpad, a full Keyboard, and a Numpad.
* **Smart Gestures:** Multi-touch support for right-clicking, scrolling, and zooming.
* **Cross-Platform:** Native server support for both Windows and Linux users.

## Gestures Guide

| Action | Gesture |
| :--- | :--- |
| **Left Click** | Tap the screen once |
| **Double Click** | Double tap the screen |
| **Right Click** | Tap the screen once with two fingers |
| **Scroll** | Slide two fingers horizontally or vertically |
| **Drag & Drop** | Long press and drag |
| **Zoom** | Pinch & zoom |

---

## How to Use

### 1. Download & Install
* **On PC:** [Download](https://github.com/darusc/mousedroid/releases) the latest binaries for your OS.
* **On Android:** [Download](https://github.com/darusc/mousedroid/releases) and install the APK.

### 2. Choose Your Connection
* **Bluetooth Mode:** Pair your phone and PC via system Bluetooth settings. 
* **Wi-Fi Mode:** Ensure both devices are on the same network. Enter the PC's IP address (e.g. `192.168.0.165` or `192.168.0.165:48291`) into the app and connect.
  * **Default Wi-Fi Port:** `48291` (TCP for control/handshake, UDP for low-latency mouse/keyboard transmission).
  * **Configurable:** Change `WIFI_PORT=48291` in `config.ini` on the PC or specify the port in the Android device dialog.
* **Wired (USB) Mode:** 
  1. Enable **USB Debugging** in your phone's Developer Options.
  2. Connect via USB cable.
  3. Start the server. ADB communicates independently over TCP port `6969` (`adb reverse tcp:6969 tcp:6969`).

---

## Network Architecture & Protocol

| Component | Default Port | Protocol | Purpose / Description |
| :--- | :--- | :--- | :--- |
| **Wi-Fi Server (TCP)** | `48291` (configurable) | TCP | Client handshake, device metadata exchange, activation, state synchronization. |
| **Wi-Fi Server (UDP)** | `48291` (configurable) | UDP | High-throughput, low-latency streaming of touchpad gestures and keyboard inputs. |
| **ADB Bridge (USB)** | `6969` | TCP | Dedicated port-forwarding channel for zero-latency wired connection via `adb reverse`. |

---

## Troubleshooting & Notes

### Connection Issues
* **Firewall:** On Windows, ensure `Mousedroid.exe` is allowed through the Firewall for both **Private** and **Public** networks on TCP/UDP port `48291`.
* **Port Conflict / Already in Use:** Mousedroid enforces exclusive port binding so that only one server instance binds to the Wi-Fi port. If port `48291` is occupied or if you receive a bind warning, verify no other Mousedroid instance is running, or set `WIFI_PORT=<port>` in `config.ini`. ADB operates independently on port `6969`.
* **Host Unreachable:** If using Wi-Fi, verify that "AP Isolation" is disabled in your router settings.
* **USB Not Detected:** Check your cable and ensure your PC recognizes the phone via `adb devices`.

### Linux Specifics
* **Wayland vs Xorg:** Some features, like the taskbar icon, may not be visible on Wayland. Switching to Xorg typically resolves this.
* **Permissions:** If the mouse doesn't move on Linux, ensure you ran the `install.sh` script to set up the necessary `uinput` permissions.

### Bluetooth Support
* For the best experience on modern Android versions, grant the requested Bluetooth permissions so the desktop server can correctly display your device's friendly name.

---

## Reporting Issues

If you encounter a bug or crash, please open a [New Issue](https://github.com/darusc/mousedroid/issues). 
**Please include:**
* Your PC Operating System (e.g., Windows 11, Ubuntu 22.04).
* Your Phone Model and Android Version.
* The connection method you were using when the issue occurred.