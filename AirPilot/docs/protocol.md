# Velo Communication Protocol Specification (v1)

## Overview

**Velo** uses a local-network-only, ultra-low-latency communication protocol consisting of:
1. **Device Discovery**: UDP broadcast on port `51820`.
2. **Control & Pairing**: WebSocket on port `51821` (`ws://<host>:51821/velo`).

No external servers, cloud accounts, or telemetry relays are involved. All communication happens strictly on the local area network (LAN).

---

## 1. Network Ports

| Function | Transport | Default Port | Description |
|---|---|---|---|
| Discovery Broadcast | UDP | `51820` | Server periodic broadcast and client discovery probes |
| Control Connection | WebSocket (TCP) | `51821` | Full-duplex command stream & pairing handshake (`/velo`) |

---

## 2. Device Discovery (UDP)

The Velo Windows receiver listens on and broadcasts to UDP port `51820`.

### 2.1 Server Periodic Announcement (Heartbeat)
Every 2 seconds, the Windows receiver broadcasts a pipe-delimited packet to `255.255.255.255:51820`:

```
VELO_BEACON|<HOSTNAME>|<IP>|51821
```

Example:
```
VELO_BEACON|Desktop-Nizar|192.168.1.150|51821
```

*(Note: JSON fallback payloads `{"service":"Velo", ...}` are also supported for backward-compatibility.)*

### 2.2 Client Discovery Query (Probe)
When the Velo Remote Android application launches or refreshes its discovery list, it broadcasts a probe to `255.255.255.255:51820`:

```
VELO_PROBE
```

Upon receiving this probe, the Windows receiver immediately replies via unicast UDP to the sender's IP and port with its beacon payload:
```
VELO_BEACON|<HOSTNAME>|<IP>|51821
```

---

## 3. Protocol Envelope

Every WebSocket message exchanged between Android and Windows follows this JSON envelope:

```json
{
  "version": 1,
  "type": "<command_type>",
  "timestamp": 1727188800000,
  "payload": { ... }
}
```

### Supported Message Types
- `pair_request` / `pair_response`: PIN-based authentication exchange
- `auth` / `auth_response`: Token-based session resumption
- `mouse_move`: Low-latency relative cursor displacement (`dx`, `dy`)
- `mouse_click`: Click event (`left`, `right`, `middle`)
- `mouse_down` / `mouse_up`: Drag-and-drop mouse button states
- `mouse_scroll`: Two-finger vertical and horizontal scrolling (`dx`, `dy`)
- `ping` / `pong`: Connection heartbeat

---

## 4. Security & Pairing Flow

1. On startup, Velo Desktop generates a random 6-digit PIN displayed on screen.
2. Velo Remote sends `pair_request` with device ID, device name, and PIN.
3. If valid, Velo Desktop generates a 256-bit cryptographic token stored in `%LOCALAPPDATA%/Velo/paired_devices.json` and replies with status `success` and token.
4. Subsequent connections send `auth` with the token.
