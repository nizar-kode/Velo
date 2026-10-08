using System.Runtime.InteropServices;

namespace Velo.Desktop.Services;

public class NativeInputSimulator
{
    private static readonly Lazy<NativeInputSimulator> _instance = new(() => new NativeInputSimulator());
    public static NativeInputSimulator Instance => _instance.Value;

    private double _subPixelX;
    private double _subPixelY;
    private readonly object _lock = new();

    public void MoveMouseRelative(double dx, double dy)
    {
        int moveX = 0;
        int moveY = 0;

        lock (_lock)
        {
            _subPixelX += dx;
            _subPixelY += dy;

            moveX = (int)_subPixelX;
            moveY = (int)_subPixelY;

            _subPixelX -= moveX;
            _subPixelY -= moveY;
        }

        if (moveX == 0 && moveY == 0) return;

        var input = new INPUT
        {
            type = INPUT_MOUSE,
            u = new InputUnion
            {
                mi = new MOUSEINPUT
                {
                    dx = moveX,
                    dy = moveY,
                    mouseData = 0,
                    dwFlags = MOUSEEVENTF_MOVE,
                    time = 0,
                    dwExtraInfo = UIntPtr.Zero
                }
            }
        };

        SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>());
    }

    public void MouseClick(string button = "left")
    {
        uint downFlag = button switch
        {
            "right" => MOUSEEVENTF_RIGHTDOWN,
            "middle" => MOUSEEVENTF_MIDDLEDOWN,
            _ => MOUSEEVENTF_LEFTDOWN
        };

        uint upFlag = button switch
        {
            "right" => MOUSEEVENTF_RIGHTUP,
            "middle" => MOUSEEVENTF_MIDDLEUP,
            _ => MOUSEEVENTF_LEFTUP
        };

        var inputs = new INPUT[2];
        inputs[0] = new INPUT
        {
            type = INPUT_MOUSE,
            u = new InputUnion
            {
                mi = new MOUSEINPUT { dwFlags = downFlag }
            }
        };
        inputs[1] = new INPUT
        {
            type = INPUT_MOUSE,
            u = new InputUnion
            {
                mi = new MOUSEINPUT { dwFlags = upFlag }
            }
        };

        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
    }

    public void MouseDown(string button = "left")
    {
        uint downFlag = button switch
        {
            "right" => MOUSEEVENTF_RIGHTDOWN,
            "middle" => MOUSEEVENTF_MIDDLEDOWN,
            _ => MOUSEEVENTF_LEFTDOWN
        };

        var input = new INPUT
        {
            type = INPUT_MOUSE,
            u = new InputUnion
            {
                mi = new MOUSEINPUT { dwFlags = downFlag }
            }
        };

        SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>());
    }

    public void MouseUp(string button = "left")
    {
        uint upFlag = button switch
        {
            "right" => MOUSEEVENTF_RIGHTUP,
            "middle" => MOUSEEVENTF_MIDDLEUP,
            _ => MOUSEEVENTF_LEFTUP
        };

        var input = new INPUT
        {
            type = INPUT_MOUSE,
            u = new InputUnion
            {
                mi = new MOUSEINPUT { dwFlags = upFlag }
            }
        };

        SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>());
    }

    public void MouseScroll(double dx, double dy)
    {
        var inputs = new List<INPUT>();

        if (Math.Abs(dy) > 0.01)
        {
            inputs.Add(new INPUT
            {
                type = INPUT_MOUSE,
                u = new InputUnion
                {
                    mi = new MOUSEINPUT
                    {
                        dwFlags = MOUSEEVENTF_WHEEL,
                        mouseData = (int)dy
                    }
                }
            });
        }

        if (Math.Abs(dx) > 0.01)
        {
            inputs.Add(new INPUT
            {
                type = INPUT_MOUSE,
                u = new InputUnion
                {
                    mi = new MOUSEINPUT
                    {
                        dwFlags = MOUSEEVENTF_HWHEEL,
                        mouseData = (int)dx
                    }
                }
            });
        }

        if (inputs.Count > 0)
        {
            SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf<INPUT>());
        }
    }

    #region Win32 API Definitions

    private const int INPUT_MOUSE = 0;
    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    private const uint MOUSEEVENTF_WHEEL = 0x0800;
    private const uint MOUSEEVENTF_HWHEEL = 0x1000;

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public int type;
        public InputUnion u;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public HARDWAREINPUT hi;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public int mouseData;
        public uint dwFlags;
        public uint time;
        public UIntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public UIntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct HARDWAREINPUT
    {
        public uint uMsg;
        public ushort wParamL;
        public ushort wParamH;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, [In] INPUT[] pInputs, int cbSize);

    #endregion
}
