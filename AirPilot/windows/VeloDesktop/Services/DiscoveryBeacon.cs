using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using Velo.Desktop.Models;

namespace Velo.Desktop.Services;

public class DiscoveryBeacon : IDisposable
{
    private static readonly Lazy<DiscoveryBeacon> _instance = new(() => new DiscoveryBeacon());
    public static DiscoveryBeacon Instance => _instance.Value;

    public const int DiscoveryPort = 51820;
    public const int ServicePort = 51821;

    private UdpClient? _udpClient;
    private CancellationTokenSource? _cts;
    private Task? _broadcastTask;
    private Task? _listenerTask;
    private bool _isDisposed;

    public void Start()
    {
        if (_cts != null) return;
        _cts = new CancellationTokenSource();

        try
        {
            _udpClient = new UdpClient();
            _udpClient.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            _udpClient.EnableBroadcast = true;
            _udpClient.Client.Bind(new IPEndPoint(IPAddress.Any, DiscoveryPort));

            VeloLogger.Instance.Info("Discovery", $"Velo discovery beacon listening on UDP port {DiscoveryPort}");

            _listenerTask = Task.Run(() => ListenLoopAsync(_cts.Token));
            _broadcastTask = Task.Run(() => BroadcastLoopAsync(_cts.Token));
        }
        catch (Exception ex)
        {
            VeloLogger.Instance.Error("Discovery", $"Failed to start UDP discovery beacon: {ex.Message}");
        }
    }

    public void Stop()
    {
        _cts?.Cancel();
        try
        {
            _udpClient?.Close();
        }
        catch { }

        _cts = null;
        _udpClient = null;
        VeloLogger.Instance.Info("Discovery", "Discovery beacon stopped");
    }

    private async Task BroadcastLoopAsync(CancellationToken token)
    {
        var targetEndpoint = new IPEndPoint(IPAddress.Broadcast, DiscoveryPort);

        while (!token.IsCancellationRequested)
        {
            try
            {
                byte[] data = CreateAdvertisementPacket();
                if (_udpClient != null)
                {
                    await _udpClient.SendAsync(data, data.Length, targetEndpoint);
                }
            }
            catch (Exception ex) when (!token.IsCancellationRequested)
            {
                VeloLogger.Instance.Warn("Discovery", $"Broadcast send error: {ex.Message}");
            }

            try
            {
                await Task.Delay(2000, token);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    private async Task ListenLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                if (_udpClient == null) break;
                var result = await _udpClient.ReceiveAsync(token);
                string text = Encoding.UTF8.GetString(result.Buffer);

                if (text.Contains("VELO_PROBE", StringComparison.OrdinalIgnoreCase) ||
                    text.Contains("discover_query", StringComparison.OrdinalIgnoreCase))
                {
                    VeloLogger.Instance.Debug("Discovery", $"Probe received from {result.RemoteEndPoint}. Responding unicast.");
                    byte[] response = CreateAdvertisementPacket();
                    await _udpClient.SendAsync(response, response.Length, result.RemoteEndPoint);
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                if (!token.IsCancellationRequested)
                {
                    VeloLogger.Instance.Warn("Discovery", $"Listen loop error: {ex.Message}");
                    await Task.Delay(500, token);
                }
            }
        }
    }

    public byte[] CreateAdvertisementPacket()
    {
        string host = Environment.MachineName;
        string ip = GetLocalIpAddress();
        string beacon = $"VELO_BEACON|{host}|{ip}|{ServicePort}";
        return Encoding.UTF8.GetBytes(beacon);
    }

    public string GetLocalIpAddress()
    {
        try
        {
            foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus == OperationalStatus.Up &&
                    (ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ||
                     ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet))
                {
                    foreach (var ip in ni.GetIPProperties().UnicastAddresses)
                    {
                        if (ip.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ip.Address))
                        {
                            return ip.Address.ToString();
                        }
                    }
                }
            }
        }
        catch { }
        return "127.0.0.1";
    }

    public void Dispose()
    {
        if (_isDisposed) return;
        _isDisposed = true;
        Stop();
    }
}
