using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using Velo.Desktop.Models;
using Velo.Desktop.Services;
using Xunit;

namespace Velo.Desktop.Tests;

public class ProtocolAndSecurityTests
{
    [Fact]
    public void Protocol_Serialize_And_Deserialize_MouseMove()
    {
        var original = new VeloMessage
        {
            Version = 1,
            Type = "mouse_move",
            Timestamp = 1727188800000,
            Payload = JsonSerializer.SerializeToElement(new MouseMovePayload
            {
                Dx = 15.2,
                Dy = -7.8
            })
        };

        string json = JsonSerializer.Serialize(original);
        var deserialized = JsonSerializer.Deserialize<VeloMessage>(json);

        Assert.NotNull(deserialized);
        Assert.Equal(1, deserialized.Version);
        Assert.Equal("mouse_move", deserialized.Type);
        Assert.True(deserialized.Payload.HasValue);

        var payload = JsonSerializer.Deserialize<MouseMovePayload>(deserialized.Payload.Value.GetRawText());
        Assert.NotNull(payload);
        Assert.Equal(15.2, payload.Dx);
        Assert.Equal(-7.8, payload.Dy);
    }

    [Fact]
    public void SecurityManager_PinGeneration_Produces_6Digits()
    {
        var manager = new SecurityManager();
        string pin = manager.GenerateNewPin();

        Assert.NotNull(pin);
        Assert.Equal(6, pin.Length);
        Assert.True(int.TryParse(pin, out int value));
        Assert.InRange(value, 100000, 999999);
    }

    [Fact]
    public void SecurityManager_Pairing_ValidPin_Succeeds_And_GeneratesToken()
    {
        var manager = new SecurityManager();
        string pin = manager.CurrentPin;

        var (success, token, message) = manager.TryPair("test-device-01", "Test Galaxy A34", pin);

        Assert.True(success);
        Assert.NotNull(token);
        Assert.Equal(64, token.Length); // 256-bit hex
        Assert.Contains("success", message, StringComparison.OrdinalIgnoreCase);

        // Validate token
        bool isValid = manager.ValidateToken("test-device-01", token);
        Assert.True(isValid);

        // Validate wrong token fails
        bool isWrongValid = manager.ValidateToken("test-device-01", "invalid_token_1234");
        Assert.False(isWrongValid);

        // Validate wrong device fails
        bool isWrongDevValid = manager.ValidateToken("wrong-device", token);
        Assert.False(isWrongDevValid);
    }

    [Fact]
    public void SecurityManager_Pairing_InvalidPin_Fails()
    {
        var manager = new SecurityManager();
        var (success, token, message) = manager.TryPair("test-device-02", "Test Phone", "000000");

        if (manager.CurrentPin != "000000")
        {
            Assert.False(success);
            Assert.Null(token);
            Assert.Contains("invalid", message, StringComparison.OrdinalIgnoreCase);
        }
    }

    [Fact]
    public void SecurityManager_ResetPairing_RevokesAllTokens()
    {
        var manager = new SecurityManager();
        string pin = manager.CurrentPin;
        var (success, token, _) = manager.TryPair("test-device-03", "Test Phone", pin);
        Assert.True(success);

        manager.ResetPairing();

        bool isValid = manager.ValidateToken("test-device-03", token!);
        Assert.False(isValid);
    }

    [Fact]
    public void NativeInputSimulator_SubpixelAccumulator_Works()
    {
        // Testing that subpixel accumulation logic handles fractions without throwing
        NativeInputSimulator.Instance.MoveMouseRelative(0.4, 0.4);
        NativeInputSimulator.Instance.MoveMouseRelative(0.6, 0.6);
        // Completed without exception
        Assert.True(true);
    }

    [Fact]
    public void VeloLogger_WritesToLocalAppDirectory()
    {
        string testMessage = $"Test log entry {Guid.NewGuid()}";
        VeloLogger.Instance.Info("TestCategory", testMessage);

        Assert.True(Directory.Exists(VeloLogger.Instance.LogDirectory));
        Assert.True(File.Exists(VeloLogger.Instance.LogFilePath));

        string content = File.ReadAllText(VeloLogger.Instance.LogFilePath);
        Assert.Contains(testMessage, content);
    }

    [Fact]
    public void DiscoveryBeacon_GetLocalIpAddress_ReturnsValidLanIp()
    {
        string ip = DiscoveryBeacon.Instance.GetLocalIpAddress();
        Assert.False(string.IsNullOrWhiteSpace(ip));
        // IP must not be link-local APIPA
        Assert.False(ip.StartsWith("169.254."));
    }
}
