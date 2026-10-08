using System.IO;
using System.Net;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using Velo.Desktop.Models;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

namespace Velo.Desktop.Services;

public class VeloServer : IAsyncDisposable
{
    private static readonly Lazy<VeloServer> _instance = new(() => new VeloServer());
    public static VeloServer Instance => _instance.Value;

    public const int DefaultPort = 51821;

    private WebApplication? _app;
    private CancellationTokenSource? _cts;
    private readonly object _lock = new();

    public string? ActiveDeviceName { get; private set; }
    public bool IsClientConnected { get; private set; }
    public event Action? ConnectionStateChanged;

    public async Task StartAsync(int port = DefaultPort)
    {
        if (_app != null) return;
        _cts = new CancellationTokenSource();

        try
        {
            var builder = WebApplication.CreateEmptyBuilder(new WebApplicationOptions
            {
                Args = Array.Empty<string>()
            });

            builder.WebHost.UseKestrel(options =>
            {
                options.Listen(IPAddress.Any, port);
            });

            builder.Logging.ClearProviders();
            builder.Services.AddRouting();

            var app = builder.Build();

            app.UseWebSockets(new WebSocketOptions
            {
                KeepAliveInterval = TimeSpan.FromSeconds(15)
            });

            Func<HttpContext, Task> wsHandler = async (HttpContext context) =>
            {
                if (context.WebSockets.IsWebSocketRequest)
                {
                    using var webSocket = await context.WebSockets.AcceptWebSocketAsync();
                    await HandleWebSocketAsync(webSocket, context.Connection.RemoteIpAddress?.ToString() ?? "unknown");
                }
                else
                {
                    context.Response.StatusCode = (int)HttpStatusCode.BadRequest;
                    await context.Response.WriteAsync("Velo WebSocket server endpoint");
                }
            };

            app.Map("/velo", wsHandler);
            app.Map("/airpilot", wsHandler);

            _app = app;
            await _app.StartAsync(_cts.Token);
            VeloLogger.Instance.Info("Server", $"Velo WebSocket receiver listening on port {port} (/velo)");
        }
        catch (Exception ex)
        {
            VeloLogger.Instance.Error("Server", $"Failed to start Velo server: {ex.Message}");
            throw;
        }
    }

    private async Task HandleWebSocketAsync(WebSocket socket, string clientIp)
    {
        VeloLogger.Instance.Info("Network", $"Client connected from {clientIp}");
        bool isAuthenticated = false;
        string currentDeviceId = string.Empty;
        string currentDeviceName = "Unknown Device";

        var buffer = new byte[8192];

        try
        {
            while (socket.State == WebSocketState.Open && !_cts!.Token.IsCancellationRequested)
            {
                using var ms = new MemoryStream();
                WebSocketReceiveResult result;

                do
                {
                    result = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), _cts.Token);
                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        await socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", CancellationToken.None);
                        return;
                    }
                    ms.Write(buffer, 0, result.Count);
                }
                while (!result.EndOfMessage);

                ms.Seek(0, SeekOrigin.Begin);
                string messageText = Encoding.UTF8.GetString(ms.ToArray());

                if (string.IsNullOrWhiteSpace(messageText)) continue;

                var envelope = JsonSerializer.Deserialize<VeloMessage>(messageText);
                if (envelope == null)
                {
                    VeloLogger.Instance.Warn("Protocol", "Malformed WebSocket message received");
                    continue;
                }

                if (!isAuthenticated)
                {
                    if (envelope.Type == "pair_request")
                    {
                        var req = envelope.Payload.HasValue
                            ? JsonSerializer.Deserialize<PairRequestPayload>(envelope.Payload.Value.GetRawText())
                            : null;

                        if (req != null)
                        {
                            var (success, token, msg) = SecurityManager.Instance.TryPair(req.DeviceId, req.DeviceName, req.Pin);
                            var resp = new VeloMessage
                            {
                                Version = 1,
                                Type = "pair_response",
                                Payload = JsonSerializer.SerializeToElement(new PairResponsePayload
                                {
                                    Status = success ? "success" : "rejected",
                                    Token = token,
                                    Message = msg
                                })
                            };

                            await SendMessageAsync(socket, resp);

                            if (success)
                            {
                                isAuthenticated = true;
                                currentDeviceId = req.DeviceId;
                                currentDeviceName = req.DeviceName;
                                UpdateClientState(true, currentDeviceName);
                            }
                        }
                    }
                    else if (envelope.Type == "auth")
                    {
                        var req = envelope.Payload.HasValue
                            ? JsonSerializer.Deserialize<AuthPayload>(envelope.Payload.Value.GetRawText())
                            : null;

                        if (req != null && SecurityManager.Instance.ValidateToken(req.DeviceId, req.Token))
                        {
                            isAuthenticated = true;
                            currentDeviceId = req.DeviceId;
                            currentDeviceName = SecurityManager.Instance.GetPairedDevices()
                                .FirstOrDefault(d => d.DeviceId == req.DeviceId)?.DeviceName ?? "Paired Device";

                            var resp = new VeloMessage
                            {
                                Version = 1,
                                Type = "auth_response",
                                Payload = JsonSerializer.SerializeToElement(new AuthResponsePayload
                                {
                                    Status = "authenticated"
                                })
                            };
                            await SendMessageAsync(socket, resp);
                            UpdateClientState(true, currentDeviceName);
                        }
                        else
                        {
                            var resp = new VeloMessage
                            {
                                Version = 1,
                                Type = "auth_response",
                                Payload = JsonSerializer.SerializeToElement(new AuthResponsePayload
                                {
                                    Status = "unauthorized",
                                    Message = "Invalid credentials"
                                })
                            };
                            await SendMessageAsync(socket, resp);
                            await socket.CloseAsync(WebSocketCloseStatus.PolicyViolation, "Unauthorized", CancellationToken.None);
                            return;
                        }
                    }
                    else
                    {
                        VeloLogger.Instance.Warn("Security", $"Command '{envelope.Type}' rejected: connection not authenticated");
                    }
                    continue;
                }

                // Authenticated command handling
                ProcessControlCommand(envelope);

                if (envelope.Type == "ping")
                {
                    var pong = new VeloMessage
                    {
                        Version = 1,
                        Type = "pong",
                        Payload = JsonSerializer.SerializeToElement(new { })
                    };
                    await SendMessageAsync(socket, pong);
                }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            VeloLogger.Instance.Warn("Network", $"Client connection error: {ex.Message}");
        }
        finally
        {
            VeloLogger.Instance.Info("Network", $"Client '{currentDeviceName}' disconnected");
            UpdateClientState(false, null);
        }
    }

    private void ProcessControlCommand(VeloMessage envelope)
    {
        try
        {
            switch (envelope.Type)
            {
                case "mouse_move":
                    if (envelope.Payload.HasValue)
                    {
                        var move = JsonSerializer.Deserialize<MouseMovePayload>(envelope.Payload.Value.GetRawText());
                        if (move != null)
                        {
                            NativeInputSimulator.Instance.MoveMouseRelative(move.Dx, move.Dy);
                        }
                    }
                    break;

                case "mouse_click":
                    if (envelope.Payload.HasValue)
                    {
                        var click = JsonSerializer.Deserialize<MouseClickPayload>(envelope.Payload.Value.GetRawText());
                        NativeInputSimulator.Instance.MouseClick(click?.Button ?? "left");
                    }
                    else
                    {
                        NativeInputSimulator.Instance.MouseClick("left");
                    }
                    break;

                case "mouse_down":
                    if (envelope.Payload.HasValue)
                    {
                        var down = JsonSerializer.Deserialize<MouseDownUpPayload>(envelope.Payload.Value.GetRawText());
                        NativeInputSimulator.Instance.MouseDown(down?.Button ?? "left");
                    }
                    break;

                case "mouse_up":
                    if (envelope.Payload.HasValue)
                    {
                        var up = JsonSerializer.Deserialize<MouseDownUpPayload>(envelope.Payload.Value.GetRawText());
                        NativeInputSimulator.Instance.MouseUp(up?.Button ?? "left");
                    }
                    break;

                case "mouse_double_click":
                    NativeInputSimulator.Instance.MouseClick("left");
                    Thread.Sleep(30);
                    NativeInputSimulator.Instance.MouseClick("left");
                    break;

                case "mouse_scroll":
                    if (envelope.Payload.HasValue)
                    {
                        var scroll = JsonSerializer.Deserialize<MouseScrollPayload>(envelope.Payload.Value.GetRawText());
                        if (scroll != null)
                        {
                            NativeInputSimulator.Instance.MouseScroll(scroll.Dx, scroll.Dy);
                        }
                    }
                    break;
            }
        }
        catch (Exception ex)
        {
            VeloLogger.Instance.Error("Input", $"Error processing command {envelope.Type}: {ex.Message}");
        }
    }

    private async Task SendMessageAsync(WebSocket socket, VeloMessage message)
    {
        if (socket.State != WebSocketState.Open) return;
        string json = JsonSerializer.Serialize(message);
        byte[] bytes = Encoding.UTF8.GetBytes(json);
        await socket.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None);
    }

    private void UpdateClientState(bool connected, string? deviceName)
    {
        lock (_lock)
        {
            IsClientConnected = connected;
            ActiveDeviceName = deviceName;
        }

        try
        {
            ConnectionStateChanged?.Invoke();
        }
        catch { }
    }

    public async ValueTask DisposeAsync()
    {
        _cts?.Cancel();
        if (_app != null)
        {
            try
            {
                await _app.StopAsync();
                await _app.DisposeAsync();
            }
            catch { }
            _app = null;
        }
    }
}

// Backward-compatibility alias
public static class AirPilotServer
{
    public static VeloServer Instance => VeloServer.Instance;
}
