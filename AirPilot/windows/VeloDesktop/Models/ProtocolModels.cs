using System.Text.Json;
using System.Text.Json.Serialization;

namespace Velo.Desktop.Models;

public class VeloMessage
{
    [JsonPropertyName("version")]
    public int Version { get; set; } = 1;

    [JsonPropertyName("type")]
    public string Type { get; set; } = string.Empty;

    [JsonPropertyName("timestamp")]
    public long Timestamp { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    [JsonPropertyName("payload")]
    public JsonElement? Payload { get; set; }
}

// Backward-compatibility alias
public class AirPilotMessage : VeloMessage { }

public class DiscoveryBeaconMessage
{
    [JsonPropertyName("service")]
    public string Service { get; set; } = "Velo";

    [JsonPropertyName("version")]
    public int Version { get; set; } = 1;

    [JsonPropertyName("computerName")]
    public string ComputerName { get; set; } = Environment.MachineName;

    [JsonPropertyName("os")]
    public string Os { get; set; } = "Windows 11";

    [JsonPropertyName("port")]
    public int Port { get; set; } = 51821;

    [JsonPropertyName("status")]
    public string Status { get; set; } = "Available";

    [JsonPropertyName("instanceId")]
    public string InstanceId { get; set; } = Guid.NewGuid().ToString("N");
}

public class PairRequestPayload
{
    [JsonPropertyName("deviceId")]
    public string DeviceId { get; set; } = string.Empty;

    [JsonPropertyName("deviceName")]
    public string DeviceName { get; set; } = string.Empty;

    [JsonPropertyName("pin")]
    public string Pin { get; set; } = string.Empty;
}

public class PairResponsePayload
{
    [JsonPropertyName("status")]
    public string Status { get; set; } = string.Empty; // "success" | "rejected"

    [JsonPropertyName("token")]
    public string? Token { get; set; }

    [JsonPropertyName("message")]
    public string Message { get; set; } = string.Empty;
}

public class AuthPayload
{
    [JsonPropertyName("deviceId")]
    public string DeviceId { get; set; } = string.Empty;

    [JsonPropertyName("token")]
    public string Token { get; set; } = string.Empty;
}

public class AuthResponsePayload
{
    [JsonPropertyName("status")]
    public string Status { get; set; } = string.Empty; // "authenticated" | "unauthorized"

    [JsonPropertyName("serverTime")]
    public long ServerTime { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    [JsonPropertyName("message")]
    public string? Message { get; set; }
}

public class MouseMovePayload
{
    [JsonPropertyName("dx")]
    public double Dx { get; set; }

    [JsonPropertyName("dy")]
    public double Dy { get; set; }
}

public class MouseClickPayload
{
    [JsonPropertyName("button")]
    public string Button { get; set; } = "left"; // "left" | "right" | "middle"
}

public class MouseDownUpPayload
{
    [JsonPropertyName("button")]
    public string Button { get; set; } = "left";
}

public class MouseScrollPayload
{
    [JsonPropertyName("dx")]
    public double Dx { get; set; }

    [JsonPropertyName("dy")]
    public double Dy { get; set; }
}
