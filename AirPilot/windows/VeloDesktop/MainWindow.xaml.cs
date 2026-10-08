using System.Drawing;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Windows;
using System.Windows.Media;
using Velo.Desktop.Models;
using Velo.Desktop.Services;

namespace Velo.Desktop;

public partial class MainWindow : Window
{
    private System.Windows.Forms.NotifyIcon? _notifyIcon;

    public MainWindow()
    {
        InitializeComponent();

        Loaded += MainWindow_Loaded;
        Closing += MainWindow_Closing;
        StateChanged += MainWindow_StateChanged;

        SecurityManager.Instance.StateChanged += UpdateSecurityUI;
        VeloServer.Instance.ConnectionStateChanged += UpdateConnectionUI;
        VeloLogger.Instance.LogAdded += OnLogAdded;
    }

    private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        VeloLogger.Instance.Info("App", "Velo application started");

        SetupTrayIcon();
        UpdateSecurityUI();
        UpdateConnectionUI();
        DisplayLocalIps();

        try
        {
            // Start UDP discovery beacon
            DiscoveryBeacon.Instance.Start();

            // Start WebSocket server
            await VeloServer.Instance.StartAsync();
            VeloLogger.Instance.Info("App", "Velo receiver started and ready for pairing/connections");
        }
        catch (Exception ex)
        {
            VeloLogger.Instance.Error("App", $"Startup failure: {ex.Message}");
            System.Windows.MessageBox.Show($"Failed to start Velo services: {ex.Message}", "Velo Error", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void SetupTrayIcon()
    {
        var iconStream = typeof(MainWindow).Assembly.GetManifestResourceStream("icon.ico");
        Icon appIcon = iconStream != null ? new Icon(iconStream) : SystemIcons.Application;

        _notifyIcon = new System.Windows.Forms.NotifyIcon
        {
            Icon = appIcon,
            Visible = true,
            Text = "Velo Desktop Receiver"
        };

        _notifyIcon.DoubleClick += (s, e) =>
        {
            Show();
            WindowState = WindowState.Normal;
            Activate();
        };

        var contextMenu = new System.Windows.Forms.ContextMenuStrip();
        var itemStatus = new System.Windows.Forms.ToolStripMenuItem("Velo: Available");
        itemStatus.Enabled = false;

        var itemOpen = new System.Windows.Forms.ToolStripMenuItem("Open Velo", null, (s, e) =>
        {
            Show();
            WindowState = WindowState.Normal;
            Activate();
        });

        var itemDisconnect = new System.Windows.Forms.ToolStripMenuItem("Disconnect Device", null, (s, e) =>
        {
            VeloLogger.Instance.Info("App", "Disconnect requested from tray");
        });

        var itemReset = new System.Windows.Forms.ToolStripMenuItem("Reset Pairing", null, (s, e) =>
        {
            SecurityManager.Instance.ResetPairing();
        });

        var itemAbout = new System.Windows.Forms.ToolStripMenuItem("About & Credits", null, (s, e) =>
        {
            ShowAboutDialog();
        });

        var itemExit = new System.Windows.Forms.ToolStripMenuItem("Exit", null, (s, e) =>
        {
            _notifyIcon.Visible = false;
            _notifyIcon.Dispose();
            _notifyIcon = null;
            System.Windows.Application.Current.Shutdown();
        });

        contextMenu.Items.Add(itemStatus);
        contextMenu.Items.Add(new System.Windows.Forms.ToolStripSeparator());
        contextMenu.Items.Add(itemOpen);
        contextMenu.Items.Add(itemDisconnect);
        contextMenu.Items.Add(itemReset);
        contextMenu.Items.Add(itemAbout);
        contextMenu.Items.Add(new System.Windows.Forms.ToolStripSeparator());
        contextMenu.Items.Add(itemExit);

        _notifyIcon.ContextMenuStrip = contextMenu;
    }

    private void MainWindow_StateChanged(object? sender, EventArgs e)
    {
        if (WindowState == WindowState.Minimized)
        {
            Hide();
            _notifyIcon?.ShowBalloonTip(1500, "Velo", "Velo is running in the background", System.Windows.Forms.ToolTipIcon.Info);
        }
    }

    private void MainWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        // Minimize to tray instead of quitting
        e.Cancel = true;
        Hide();
        _notifyIcon?.ShowBalloonTip(1500, "Velo", "Velo minimized to system tray. Right-click icon to exit.", System.Windows.Forms.ToolTipIcon.Info);
    }

    private void UpdateSecurityUI()
    {
        Dispatcher.Invoke(() =>
        {
            TxtPairingCode.Text = SecurityManager.Instance.CurrentPin;
            TxtPairedCount.Text = $"Paired devices: {SecurityManager.Instance.GetPairedDevices().Count}";
        });
    }

    private void UpdateConnectionUI()
    {
        Dispatcher.Invoke(() =>
        {
            bool connected = VeloServer.Instance.IsClientConnected;
            string? devName = VeloServer.Instance.ActiveDeviceName;

            if (connected)
            {
                StatusDot.Background = new SolidColorBrush(System.Windows.Media.Color.FromRgb(16, 185, 129)); // Green
                TxtStatus.Text = $"Connected to {devName}";
                TxtConnectedDevice.Text = $"● {devName} (Active)";
                TxtConnectedDevice.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(52, 211, 153));
                if (_notifyIcon != null)
                {
                    _notifyIcon.Text = $"Velo: Connected ({devName})".Length > 63 ? $"Velo: Connected" : $"Velo: Connected ({devName})";
                }
            }
            else
            {
                StatusDot.Background = new SolidColorBrush(System.Windows.Media.Color.FromRgb(56, 189, 248)); // Blue
                TxtStatus.Text = "Available (Listening on :51821)";
                TxtConnectedDevice.Text = "None (Waiting for device)";
                TxtConnectedDevice.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(248, 250, 252));
                if (_notifyIcon != null)
                {
                    _notifyIcon.Text = "Velo: Available";
                }
            }
        });
    }

    private void DisplayLocalIps()
    {
        var ips = new List<string>();
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus == OperationalStatus.Up &&
                (ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ||
                 ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet))
            {
                string name = ni.Name.ToLowerInvariant();
                string desc = ni.Description.ToLowerInvariant();
                bool isVirtual = name.Contains("vethernet") || name.Contains("wsl") ||
                                 name.Contains("hyper-v") || name.Contains("virtual") ||
                                 name.Contains("vmware") || name.Contains("box") ||
                                 name.Contains("tailscale") || name.Contains("zerotier") ||
                                 name.Contains("tap") || name.Contains("bluetooth") ||
                                 desc.Contains("virtual") || desc.Contains("hyper-v") ||
                                 desc.Contains("vmware") || desc.Contains("wsl");

                if (isVirtual) continue;

                foreach (var ip in ni.GetIPProperties().UnicastAddresses)
                {
                    if (ip.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ip.Address))
                    {
                        string str = ip.Address.ToString();
                        if (!str.StartsWith("169.254.") && !str.StartsWith("127."))
                        {
                            ips.Add(str);
                        }
                    }
                }
            }
        }

        TxtLocalIp.Text = ips.Count > 0 ? $"IP: {string.Join(", ", ips)}" : "IP: 127.0.0.1 (No LAN detected)";
    }

    private void OnLogAdded(LogEntry entry)
    {
        Dispatcher.InvokeAsync(() =>
        {
            TxtLogs.AppendText($"[{entry.Timestamp:HH:mm:ss}] [{entry.Category}] {entry.Message}\n");
            LogScrollViewer.ScrollToEnd();
        });
    }

    private void BtnNewCode_Click(object sender, RoutedEventArgs e)
    {
        SecurityManager.Instance.GenerateNewPin();
    }

    private void BtnResetPairing_Click(object sender, RoutedEventArgs e)
    {
        var result = System.Windows.MessageBox.Show(
            "This will revoke all paired devices. You will need to pair your phone again. Proceed?",
            "Reset Pairing",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning);

        if (result == MessageBoxResult.Yes)
        {
            SecurityManager.Instance.ResetPairing();
        }
    }

    private void BtnClearLogs_Click(object sender, RoutedEventArgs e)
    {
        TxtLogs.Clear();
    }

    private void BtnMinimizeToTray_Click(object sender, RoutedEventArgs e)
    {
        Hide();
        _notifyIcon?.ShowBalloonTip(1500, "Velo", "Velo is running in the tray", System.Windows.Forms.ToolTipIcon.Info);
    }

    private void BtnAbout_Click(object sender, RoutedEventArgs e)
    {
        ShowAboutDialog();
    }

    private void ShowAboutDialog()
    {
        string message =
            "VELO DESKTOP RECEIVER v1.0\n" +
            "Ultra-responsive PC control, trackpad, and presentation pointer from your phone.\n\n" +
            "--------------------------------------------------\n" +
            "ACKNOWLEDGMENTS & CREDITS\n" +
            "--------------------------------------------------\n" +
            "Explicitly crediting darusc and the original Droid Studio / Mousedroid project " +
            "(https://github.com/darusc/Mousedroid) for the foundational architecture and concept that inspired Velo.\n\n" +
            "Velo builds upon this open-source heritage with a modern .NET 10 WPF receiver, " +
            "Jetpack Compose Material 3 UI, zero-config UDP discovery, and virtual laser overlay.\n\n" +
            "License: MIT License\n" +
            "Status: Active LAN Receiver";

        System.Windows.MessageBox.Show(message, "About Velo & Credits", MessageBoxButton.OK, MessageBoxImage.Information);
    }
}
