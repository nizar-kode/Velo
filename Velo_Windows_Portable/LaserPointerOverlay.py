import sys
import ctypes
from ctypes import wintypes
import tkinter as tk

user32 = ctypes.windll.user32

# Win32 Constants
GWL_EXSTYLE = -20
WS_EX_LAYERED = 0x00080000
WS_EX_TRANSPARENT = 0x00000020
WS_EX_TOOLWINDOW = 0x00000080
WS_EX_TOPMOST = 0x00000008

class POINT(ctypes.Structure):
    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]

class LaserPointerOverlay:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("Mousedroid Laser Pointer")
        self.root.overrideredirect(True)
        self.root.attributes("-topmost", True)
        self.root.config(bg="#000001")
        self.root.wm_attributes("-transparentcolor", "#000001")

        self.size = 48  # Total dot canvas size
        self.radius = self.size // 2

        self.canvas = tk.Canvas(
            self.root,
            width=self.size,
            height=self.size,
            bg="#000001",
            highlightthickness=0
        )
        self.canvas.pack()

        # Render layered glowing laser dot
        # 1. Outer subtle halo
        self.canvas.create_oval(
            2, 2, self.size - 2, self.size - 2,
            fill="#880011", outline=""
        )
        # 2. Main bright neon red core
        self.canvas.create_oval(
            self.radius - 9, self.radius - 9,
            self.radius + 9, self.radius + 9,
            fill="#FF0033", outline="#FF6688", width=1
        )
        # 3. Intense white hot-spot center
        self.canvas.create_oval(
            self.radius - 3, self.radius - 3,
            self.radius + 3, self.radius + 3,
            fill="#FFFFFF", outline=""
        )

        self.root.update()
        self.make_click_through()
        self.update_position()

    def make_click_through(self):
        """Make the window completely click-through and hidden from Alt+Tab."""
        hwnd = user32.GetParent(self.root.winfo_id())
        ex_style = user32.GetWindowLongW(hwnd, GWL_EXSTYLE)
        user32.SetWindowLongW(
            hwnd,
            GWL_EXSTYLE,
            ex_style | WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW | WS_EX_TOPMOST
        )

    def update_position(self):
        pt = POINT()
        user32.GetCursorPos(ctypes.byref(pt))
        x = pt.x - self.radius
        y = pt.y - self.radius
        self.root.geometry(f"{self.size}x{self.size}+{x}+{y}")
        self.root.after(10, self.update_position)

    def run(self):
        self.root.mainloop()

if __name__ == "__main__":
    overlay = LaserPointerOverlay()
    overlay.run()
