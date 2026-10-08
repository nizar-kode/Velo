import os
import sys
import time
import socket
import threading
import subprocess
import ctypes
from ctypes import wintypes
import tkinter as tk
from tkinter import ttk
from PIL import Image, ImageTk

try:
    import win32gui
    import win32con
except ImportError:
    win32gui = None
    win32con = None

# Windows DWM attributes for modern Windows 11 Fluent dark window
DWMWA_USE_IMMERSIVE_DARK_MODE = 20
DWMWA_WINDOW_CORNER_PREFERENCE = 33
DWMWCP_ROUND = 2

WM_TRAYICON = win32con.WM_USER + 20 if win32con else 1024 + 20

class MousedroidCompanionApp:
    def __init__(self):
        if getattr(sys, 'frozen', False):
            self.script_dir = os.path.dirname(sys.executable)
        else:
            self.script_dir = os.path.dirname(os.path.abspath(__file__))
        self.server_dir = os.path.join(self.script_dir, "mousedroid_win64", "mousedroid_win64")
        self.exe_path = os.path.join(self.server_dir, "Mousedroid.exe")
        self.beacon_path = os.path.join(self.script_dir, "discovery_beacon.py")
        self.laser_script = os.path.join(self.script_dir, "LaserPointerOverlay.py")

        self.root = tk.Tk()
        self.root.title("Mousedroid Server Companion")
        self.root.geometry("380x480")
        self.root.resizable(False, False)
        self.root.configure(bg="#0B0F14")

        # Make window tool-window / dark fluent
        self.apply_fluent_theme()

        self.is_connected = False
        self.connected_client = None
        self.laser_process = None
        self.server_running = False

        self.build_ui()
        self.setup_tray_icon()

        # Start background monitor thread
        self.running = True
        self.monitor_thread = threading.Thread(target=self.monitor_loop, daemon=True)
        self.monitor_thread.start()

        # Hide on initial launch if desired, or show briefly
        self.root.protocol("WM_DELETE_WINDOW", self.hide_window)

    def apply_fluent_theme(self):
        self.root.update_idletasks()
        try:
            hwnd = ctypes.windll.user32.GetParent(self.root.winfo_id())
            # Enable dark titlebar
            dark_mode = ctypes.c_int(1)
            ctypes.windll.dwmapi.DwmSetWindowAttribute(
                hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ctypes.byref(dark_mode), ctypes.sizeof(dark_mode)
            )
            # Enable rounded corners
            corner_pref = ctypes.c_int(DWMWCP_ROUND)
            ctypes.windll.dwmapi.DwmSetWindowAttribute(
                hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ctypes.byref(corner_pref), ctypes.sizeof(corner_pref)
            )
        except Exception:
            pass

    def build_ui(self):
        # Header Container
        header_frame = tk.Frame(self.root, bg="#0B0F14", pady=16, padx=20)
        header_frame.pack(fill="x")

        title_row = tk.Frame(header_frame, bg="#0B0F14")
        title_row.pack(fill="x")

        lbl_title = tk.Label(
            title_row, text="Mousedroid", font=("Segoe UI Variable Display", 18, "bold"),
            fg="#FFFFFF", bg="#0B0F14"
        )
        lbl_title.pack(side="left")

        self.lbl_status_badge = tk.Label(
            title_row, text="● READY", font=("Segoe UI Variable Text", 9, "bold"),
            fg="#38BDF8", bg="#14243B", padx=8, pady=3
        )
        self.lbl_status_badge.pack(side="right")

        lbl_subtitle = tk.Label(
            header_frame, text="Fluent Remote Control Server • Windows 11",
            font=("Segoe UI Variable Text", 10), fg="#64748B", bg="#0B0F14"
        )
        lbl_subtitle.pack(anchor="w", pady=(4, 0))

        # Main Status Card
        card_frame = tk.Frame(self.root, bg="#111827", highlightbackground="#1E293B", highlightthickness=1, padx=16, pady=16)
        card_frame.pack(fill="x", padx=20, pady=8)

        # Connection Row
        row1 = tk.Frame(card_frame, bg="#111827")
        row1.pack(fill="x", pady=4)
        tk.Label(row1, text="Client Status:", font=("Segoe UI Variable Text", 10), fg="#94A3B8", bg="#111827").pack(side="left")
        self.lbl_client_status = tk.Label(row1, text="Waiting for device...", font=("Segoe UI Variable Text", 10, "bold"), fg="#E2E8F0", bg="#111827")
        self.lbl_client_status.pack(side="right")

        # Network Info Row
        row2 = tk.Frame(card_frame, bg="#111827")
        row2.pack(fill="x", pady=4)
        tk.Label(row2, text="Wi-Fi Port:", font=("Segoe UI Variable Text", 10), fg="#94A3B8", bg="#111827").pack(side="left")
        self.lbl_wifi_port = tk.Label(row2, text="48291 (Active)", font=("Segoe UI Variable Text", 10, "bold"), fg="#38BDF8", bg="#111827")
        self.lbl_wifi_port.pack(side="right")

        # Auto-Discovery Row
        row3 = tk.Frame(card_frame, bg="#111827")
        row3.pack(fill="x", pady=4)
        tk.Label(row3, text="Auto-Discovery:", font=("Segoe UI Variable Text", 10), fg="#94A3B8", bg="#111827").pack(side="left")
        tk.Label(row3, text="UDP 48292 (Beaconing)", font=("Segoe UI Variable Text", 10, "bold"), fg="#34D399", bg="#111827").pack(side="right")

        # IP Address Row
        row4 = tk.Frame(card_frame, bg="#111827")
        row4.pack(fill="x", pady=4)
        tk.Label(row4, text="Local IP Address:", font=("Segoe UI Variable Text", 10), fg="#94A3B8", bg="#111827").pack(side="left")
        self.lbl_local_ip = tk.Label(row4, text=self.get_local_ip(), font=("Segoe UI Variable Text", 10), fg="#E2E8F0", bg="#111827")
        self.lbl_local_ip.pack(side="right")

        # Action Buttons Section
        btn_container = tk.Frame(self.root, bg="#0B0F14", padx=20, pady=12)
        btn_container.pack(fill="x")

        self.btn_laser = tk.Button(
            btn_container, text="🔴 Toggle Laser Dot Overlay",
            font=("Segoe UI Variable Text", 10, "bold"), fg="#FFFFFF", bg="#1E293B",
            activebackground="#334155", activeforeground="#FFFFFF",
            relief="flat", bd=0, pady=8, cursor="hand2", command=self.toggle_laser_overlay
        )
        self.btn_laser.pack(fill="x", pady=4)

        self.btn_restart = tk.Button(
            btn_container, text="🔄 Restart Server",
            font=("Segoe UI Variable Text", 10), fg="#CBD5E1", bg="#1E293B",
            activebackground="#334155", activeforeground="#FFFFFF",
            relief="flat", bd=0, pady=8, cursor="hand2", command=self.restart_server
        )
        self.btn_restart.pack(fill="x", pady=4)

        self.btn_hide = tk.Button(
            btn_container, text="Minimize to System Tray",
            font=("Segoe UI Variable Text", 10), fg="#94A3B8", bg="#0F172A",
            activebackground="#1E293B", activeforeground="#CBD5E1",
            relief="flat", bd=0, pady=8, cursor="hand2", command=self.hide_window
        )
        self.btn_hide.pack(fill="x", pady=4)

        # Footer
        lbl_hint = tk.Label(
            self.root, text="Left-click tray icon to restore this window",
            font=("Segoe UI Variable Text", 9), fg="#475569", bg="#0B0F14"
        )
        lbl_hint.pack(side="bottom", pady=12)

    def get_local_ip(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception:
            return "127.0.0.1"

    def toggle_laser_overlay(self):
        if self.laser_process and self.laser_process.poll() is None:
            self.laser_process.terminate()
            self.laser_process = None
            self.btn_laser.config(text="🔴 Start Laser Dot Overlay", bg="#1E293B")
        else:
            laser_exe = os.path.join(self.script_dir, "LaserPointerOverlay.exe")
            if os.path.exists(laser_exe):
                self.laser_process = subprocess.Popen([laser_exe], creationflags=0x08000000)
                self.btn_laser.config(text="🛑 Stop Laser Dot Overlay", bg="#7F1D1D")
            elif os.path.exists(self.laser_script):
                pyw = (sys.executable.replace("python.exe", "pythonw.exe") 
                       if "python.exe" in sys.executable else sys.executable)
                self.laser_process = subprocess.Popen(
                    [pyw, self.laser_script],
                    creationflags=0x08000000
                )
                self.btn_laser.config(text="🛑 Stop Laser Dot Overlay", bg="#7F1D1D")

    def restart_server(self):
        parent_bat = os.path.join(os.path.dirname(self.script_dir), "START_MOUSEDROID.bat")
        curr_bat = os.path.join(self.script_dir, "START_MOUSEDROID.bat")
        start_bat = parent_bat if os.path.exists(parent_bat) else (curr_bat if os.path.exists(curr_bat) else None)
        if start_bat:
            subprocess.Popen(["cmd.exe", "/c", start_bat], creationflags=0x08000000)
        else:
            ps1_script = os.path.join(self.script_dir, "Start-Mousedroid.ps1")
            if os.path.exists(ps1_script):
                subprocess.Popen(["powershell", "-ExecutionPolicy", "Bypass", "-File", ps1_script], creationflags=0x08000000)

    def monitor_loop(self):
        while self.running:
            try:
                connected = False
                client_info = None

                # Check active TCP connections via psutil (0 subprocesses, 0 console windows)
                try:
                    import psutil
                    conns = [c for c in psutil.net_connections(kind='tcp') 
                             if c.laddr and c.laddr.port == 48291 and c.status == 'ESTABLISHED']
                    if conns:
                        connected = True
                        if conns[0].raddr:
                            client_info = f"{conns[0].raddr.ip}:{conns[0].raddr.port}"
                except Exception:
                    pass

                # Update UI on main thread
                self.root.after(0, self.update_status, connected, client_info)
            except Exception:
                pass
            time.sleep(2)

    def update_status(self, connected, client_info):
        self.is_connected = connected
        if connected:
            self.lbl_status_badge.config(text="● CONNECTED", fg="#34D399", bg="#064E3B")
            self.lbl_client_status.config(text=f"Phone ({client_info})", fg="#34D399")
        else:
            self.lbl_status_badge.config(text="● READY", fg="#38BDF8", bg="#14243B")
            self.lbl_client_status.config(text="Waiting for device...", fg="#94A3B8")

    def hide_window(self):
        self.root.withdraw()

    def show_window(self):
        self.root.deiconify()
        self.root.lift()
        self.root.focus_force()

    def setup_tray_icon(self):
        if not win32gui:
            return

        icon_path = os.path.join(self.server_dir, "app.ico")
        if not os.path.exists(icon_path):
            return

        hicon = win32gui.LoadImage(
            0, icon_path, win32con.IMAGE_ICON, 0, 0,
            win32con.LR_LOADFROMFILE | win32con.LR_DEFAULTSIZE
        )

        wc = win32gui.WNDCLASS()
        wc.lpszClassName = "MousedroidCompanionTray"
        wc.lpfnWndProc = self.tray_wnd_proc
        try:
            class_atom = win32gui.RegisterClass(wc)
        except Exception:
            class_atom = 0

        self.hwnd_tray = win32gui.CreateWindow(
            wc.lpszClassName, "Mousedroid Tray", 0, 0, 0, 0, 0, 0, 0, 0, None
        )

        nid = (
            self.hwnd_tray, 0,
            win32gui.NIF_ICON | win32gui.NIF_MESSAGE | win32gui.NIF_TIP,
            WM_TRAYICON, hicon, "Mousedroid Server (Port 48291)"
        )
        win32gui.Shell_NotifyIcon(win32gui.NIM_ADD, nid)

    def tray_wnd_proc(self, hwnd, msg, wparam, lparam):
        if msg == WM_TRAYICON:
            if lparam == win32con.WM_LBUTTONUP:
                self.show_window()
            elif lparam == win32con.WM_RBUTTONUP:
                self.show_tray_menu()
        return win32gui.DefWindowProc(hwnd, msg, wparam, lparam)

    def show_tray_menu(self):
        menu = win32gui.CreatePopupMenu()
        win32gui.AppendMenu(menu, win32con.MF_STRING, 101, "Open Mousedroid Dashboard")
        win32gui.AppendMenu(menu, win32con.MF_STRING, 102, "Toggle Laser Overlay")
        win32gui.AppendMenu(menu, win32con.MF_SEPARATOR, 0, "")
        win32gui.AppendMenu(menu, win32con.MF_STRING, 103, "Exit")

        pos = win32gui.GetCursorPos()
        win32gui.SetForegroundWindow(self.hwnd_tray)
        cmd = win32gui.TrackPopupMenu(
            menu, win32con.TPM_RETURNCMD | win32con.TPM_NONOTIFY,
            pos[0], pos[1], 0, self.hwnd_tray, None
        )
        win32gui.DestroyMenu(menu)

        if cmd == 101:
            self.show_window()
        elif cmd == 102:
            self.toggle_laser_overlay()
        elif cmd == 103:
            self.quit_app()

    def quit_app(self):
        self.running = False
        if win32gui and hasattr(self, 'hwnd_tray'):
            nid = (self.hwnd_tray, 0)
            win32gui.Shell_NotifyIcon(win32gui.NIM_DELETE, nid)
        if self.laser_process and self.laser_process.poll() is None:
            self.laser_process.terminate()
        self.root.destroy()
        sys.exit(0)

if __name__ == "__main__":
    app = MousedroidCompanionApp()
    app.root.mainloop()
