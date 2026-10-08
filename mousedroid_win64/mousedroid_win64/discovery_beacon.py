import socket
import time
import threading
import ipaddress
import psutil
import sys

DISCOVERY_PORT = 48292
WIFI_PORT = 48291

def get_broadcast_addresses():
    """Find all valid subnet broadcast addresses for active non-loopback IPv4 interfaces."""
    broadcasts = set()
    broadcasts.add('255.255.255.255')
    
    try:
        for iface_name, snics in psutil.net_if_addrs().items():
            for snic in snics:
                if snic.family == socket.AF_INET:
                    ip = snic.address
                    mask = snic.netmask
                    # Skip loopback and APIPA
                    if ip.startswith('127.') or ip.startswith('169.254.'):
                        continue
                    if mask:
                        try:
                            net = ipaddress.IPv4Network(f"{ip}/{mask}", strict=False)
                            broadcasts.add(str(net.broadcast_address))
                        except Exception:
                            pass
    except Exception as e:
        print(f"[BEACON] Error enumerating network interfaces: {e}")
        
    return list(broadcasts)

def get_local_ip():
    """Determine primary LAN IP address (preferring 192.168.x.x or 10.x.x.x over virtual adapters)."""
    # First check psutil for active Wi-Fi or Ethernet
    try:
        for iface_name, snics in psutil.net_if_addrs().items():
            # Prioritize Wi-Fi or Ethernet
            is_preferred = any(w in iface_name.lower() for w in ['wi-fi', 'wifi', 'wlan', 'ethernet'])
            for snic in snics:
                if snic.family == socket.AF_INET:
                    ip = snic.address
                    if not ip.startswith('127.') and not ip.startswith('169.254.') and not ip.startswith('172.26.'):
                        if is_preferred:
                            return ip
    except Exception:
        pass

    # Fallback to connect trick
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

def get_host_name():
    try:
        return socket.gethostname()
    except Exception:
        return "Windows PC"

def run_beacon():
    local_ip = get_local_ip()
    hostname = get_host_name()
    broadcast_targets = get_broadcast_addresses()
    beacon_msg = f"MOUSEDROID_BEACON|{hostname}|{local_ip}|{WIFI_PORT}".encode('utf-8')

    print("=" * 60)
    print(f"[BEACON] MOUSEDROID ZERO-CONFIG UDP BEACON")
    print(f"[BEACON] Hostname:        {hostname}")
    print(f"[BEACON] Local IP:        {local_ip}")
    print(f"[BEACON] Remote Port:     {WIFI_PORT}")
    print(f"[BEACON] Discovery Port:  {DISCOVERY_PORT}")
    print(f"[BEACON] Broadcast Addrs: {', '.join(broadcast_targets)}")
    print("=" * 60)

    # Socket for listening to incoming probes from Android phones
    listener = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    try:
        listener.bind(('0.0.0.0', DISCOVERY_PORT))
    except Exception as e:
        print(f"[BEACON WARNING] Could not bind port {DISCOVERY_PORT}: {e}")

    # Broadcaster socket
    broadcaster = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    broadcaster.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    # Thread to listen for probe requests from Android phone
    def listen_probes():
        while True:
            try:
                data, addr = listener.recvfrom(1024)
                text = data.decode('utf-8', errors='ignore').strip()
                if "MOUSEDROID_PROBE" in text:
                    listener.sendto(beacon_msg, addr)
                    print(f"[BEACON] Responded instantly to probe from {addr[0]}:{addr[1]}")
            except Exception:
                break

    probe_thread = threading.Thread(target=listen_probes, daemon=True)
    probe_thread.start()

    # Periodic broadcast loop
    while True:
        try:
            # Re-check broadcast targets periodically in case network changed
            targets = get_broadcast_addresses()
            for baddr in targets:
                try:
                    broadcaster.sendto(beacon_msg, (baddr, DISCOVERY_PORT))
                except Exception:
                    pass
            time.sleep(1.5)
        except Exception as e:
            print(f"[BEACON ERROR] {e}")
            time.sleep(2)

if __name__ == "__main__":
    run_beacon()
