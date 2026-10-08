using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using Velo.Desktop.Models;
using Velo.Desktop.Services;
using Xunit;

namespace Velo.Desktop.Tests;

public class EndToEndPipelineTests
{
    private const int TestPort = 51830;

    [Fact]
    public async Task Complete_Pairing_Auth_And_MouseMove_Pipeline_Succeeds()
    {
        // 1. Start Velo Server on dedicated test port
        await VeloServer.Instance.StartAsync(TestPort);

        try
        {
            using var clientWs = new ClientWebSocket();
            var uri = new Uri($"ws://127.0.0.1:{TestPort}/velo");

            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(10));
            await clientWs.ConnectAsync(uri, cts.Token);
            Assert.Equal(WebSocketState.Open, clientWs.State);

            // 2. Send invalid pairing PIN
            var invalidPairReq = new VeloMessage
            {
                Version = 1,
                Type = "pair_request",
                Payload = JsonSerializer.SerializeToElement(new PairRequestPayload
                {
                    DeviceId = "e2e-device-a34",
                    DeviceName = "Velo Remote A34",
                    Pin = "000000"
                })
            };
            await SendWsMessageAsync(clientWs, invalidPairReq);

            var invalidPairResp = await ReceiveWsMessageAsync(clientWs);
            Assert.NotNull(invalidPairResp);
            Assert.Equal("pair_response", invalidPairResp.Type);

            var invalidPayload = JsonSerializer.Deserialize<PairResponsePayload>(invalidPairResp.Payload!.Value.GetRawText());
            Assert.NotNull(invalidPayload);
            if (SecurityManager.Instance.CurrentPin != "000000")
            {
                Assert.Equal("rejected", invalidPayload.Status);
            }

            // 3. Send valid pairing PIN
            string correctPin = SecurityManager.Instance.CurrentPin;
            var validPairReq = new VeloMessage
            {
                Version = 1,
                Type = "pair_request",
                Payload = JsonSerializer.SerializeToElement(new PairRequestPayload
                {
                    DeviceId = "e2e-device-a34",
                    DeviceName = "Velo Remote A34",
                    Pin = correctPin
                })
            };
            await SendWsMessageAsync(clientWs, validPairReq);

            var validPairResp = await ReceiveWsMessageAsync(clientWs);
            Assert.NotNull(validPairResp);
            Assert.Equal("pair_response", validPairResp.Type);

            var validPayload = JsonSerializer.Deserialize<PairResponsePayload>(validPairResp.Payload!.Value.GetRawText());
            Assert.NotNull(validPayload);
            Assert.Equal("success", validPayload.Status);
            Assert.NotNull(validPayload.Token);
            string issuedToken = validPayload.Token;

            // 4. Send Mouse Move command
            var moveCmd = new VeloMessage
            {
                Version = 1,
                Type = "mouse_move",
                Payload = JsonSerializer.SerializeToElement(new MouseMovePayload
                {
                    Dx = 5.0,
                    Dy = -3.0
                })
            };
            await SendWsMessageAsync(clientWs, moveCmd);

            // 5. Send Ping and expect Pong
            var pingCmd = new VeloMessage
            {
                Version = 1,
                Type = "ping",
                Payload = JsonSerializer.SerializeToElement(new { })
            };
            await SendWsMessageAsync(clientWs, pingCmd);

            var pongResp = await ReceiveWsMessageAsync(clientWs);
            Assert.NotNull(pongResp);
            Assert.Equal("pong", pongResp.Type);

            // Clean close
            await clientWs.CloseAsync(WebSocketCloseStatus.NormalClosure, "Test done", CancellationToken.None);

            // 6. Test reconnection with existing token
            using var reconnectWs = new ClientWebSocket();
            await reconnectWs.ConnectAsync(uri, cts.Token);

            var authReq = new VeloMessage
            {
                Version = 1,
                Type = "auth",
                Payload = JsonSerializer.SerializeToElement(new AuthPayload
                {
                    DeviceId = "e2e-device-a34",
                    Token = issuedToken
                })
            };
            await SendWsMessageAsync(reconnectWs, authReq);

            var authResp = await ReceiveWsMessageAsync(reconnectWs);
            Assert.NotNull(authResp);
            Assert.Equal("auth_response", authResp.Type);

            var authPayload = JsonSerializer.Deserialize<AuthResponsePayload>(authResp.Payload!.Value.GetRawText());
            Assert.NotNull(authPayload);
            Assert.Equal("authenticated", authPayload.Status);

            await reconnectWs.CloseAsync(WebSocketCloseStatus.NormalClosure, "Auth test done", CancellationToken.None);
        }
        finally
        {
            await VeloServer.Instance.DisposeAsync();
        }
    }

    private static async Task SendWsMessageAsync(ClientWebSocket ws, VeloMessage message)
    {
        string json = JsonSerializer.Serialize(message);
        byte[] bytes = Encoding.UTF8.GetBytes(json);
        await ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None);
    }

    private static async Task<VeloMessage?> ReceiveWsMessageAsync(ClientWebSocket ws)
    {
        var buffer = new byte[8192];
        using var ms = new MemoryStream();
        WebSocketReceiveResult result;

        do
        {
            result = await ws.ReceiveAsync(new ArraySegment<byte>(buffer), CancellationToken.None);
            ms.Write(buffer, 0, result.Count);
        }
        while (!result.EndOfMessage);

        ms.Seek(0, SeekOrigin.Begin);
        string text = Encoding.UTF8.GetString(ms.ToArray());
        return JsonSerializer.Deserialize<VeloMessage>(text);
    }
}
