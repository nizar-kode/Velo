using System.IO;
using System.Security.Cryptography;
using System.Text.Json;

namespace Velo.Desktop.Services;

public class PairedDeviceRecord
{
    public string DeviceId { get; set; } = string.Empty;
    public string DeviceName { get; set; } = string.Empty;
    public string Token { get; set; } = string.Empty;
    public DateTime PairedAt { get; set; } = DateTime.UtcNow;
    public DateTime LastSeenAt { get; set; } = DateTime.UtcNow;
}

public class SecurityManager
{
    private static readonly Lazy<SecurityManager> _instance = new(() => new SecurityManager());
    public static SecurityManager Instance => _instance.Value;

    private readonly string _storagePath;
    private readonly Dictionary<string, PairedDeviceRecord> _pairedDevices = new(StringComparer.OrdinalIgnoreCase);
    private readonly object _lock = new();

    public string CurrentPin { get; private set; } = string.Empty;
    public event Action? StateChanged;

    public SecurityManager()
    {
        var appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        var dir = Path.Combine(appData, "Velo");
        Directory.CreateDirectory(dir);
        _storagePath = Path.Combine(dir, "paired_devices.json");

        LoadPairedDevices();
        GenerateNewPin();
    }

    public string GenerateNewPin()
    {
        lock (_lock)
        {
            int number = RandomNumberGenerator.GetInt32(100000, 999999);
            CurrentPin = number.ToString("D6");
            try
            {
                var pinPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Velo", "pairing_code.txt");
                File.WriteAllText(pinPath, CurrentPin);
            }
            catch { }
            VeloLogger.Instance.Info("Security", $"New pairing code generated: {CurrentPin}");
            StateChanged?.Invoke();
            return CurrentPin;
        }
    }

    public (bool Success, string? Token, string Message) TryPair(string deviceId, string deviceName, string pin)
    {
        if (string.IsNullOrWhiteSpace(deviceId))
        {
            VeloLogger.Instance.Warn("Security", "Pairing rejected: empty deviceId");
            return (false, null, "Invalid device ID");
        }

        lock (_lock)
        {
            VeloLogger.Instance.Info("Security", $"Pairing requested by '{deviceName}' (ID: {deviceId})");

            if (string.IsNullOrWhiteSpace(pin) || pin.Trim() != CurrentPin)
            {
                VeloLogger.Instance.Warn("Security", $"Pairing rejected for '{deviceName}': PIN mismatch");
                return (false, null, "Invalid pairing code");
            }

            // Generate 256-bit crypto token
            byte[] tokenBytes = new byte[32];
            RandomNumberGenerator.Fill(tokenBytes);
            string token = Convert.ToHexString(tokenBytes).ToLowerInvariant();

            var record = new PairedDeviceRecord
            {
                DeviceId = deviceId,
                DeviceName = deviceName,
                Token = token,
                PairedAt = DateTime.UtcNow,
                LastSeenAt = DateTime.UtcNow
            };

            _pairedDevices[deviceId] = record;
            SavePairedDevices();

            VeloLogger.Instance.Info("Security", $"Pairing successful for '{deviceName}'");

            // Rotate PIN after successful pairing
            GenerateNewPin();
            StateChanged?.Invoke();

            return (true, token, "Device paired successfully");
        }
    }

    public bool ValidateToken(string deviceId, string token)
    {
        if (string.IsNullOrWhiteSpace(deviceId) || string.IsNullOrWhiteSpace(token))
            return false;

        lock (_lock)
        {
            if (_pairedDevices.TryGetValue(deviceId, out var record))
            {
                if (CryptographicOperations.FixedTimeEquals(
                    System.Text.Encoding.UTF8.GetBytes(record.Token),
                    System.Text.Encoding.UTF8.GetBytes(token)))
                {
                    record.LastSeenAt = DateTime.UtcNow;
                    SavePairedDevices();
                    VeloLogger.Instance.Info("Security", $"Client authenticated: '{record.DeviceName}'");
                    return true;
                }
            }

            VeloLogger.Instance.Warn("Security", $"Authentication rejected for deviceId: {deviceId}");
            return false;
        }
    }

    public void ResetPairing()
    {
        lock (_lock)
        {
            _pairedDevices.Clear();
            SavePairedDevices();
            GenerateNewPin();
            VeloLogger.Instance.Info("Security", "All paired devices revoked and reset.");
            StateChanged?.Invoke();
        }
    }

    public IReadOnlyCollection<PairedDeviceRecord> GetPairedDevices()
    {
        lock (_lock)
        {
            return _pairedDevices.Values.ToList();
        }
    }

    private void LoadPairedDevices()
    {
        try
        {
            if (File.Exists(_storagePath))
            {
                var json = File.ReadAllText(_storagePath);
                var list = JsonSerializer.Deserialize<List<PairedDeviceRecord>>(json);
                if (list != null)
                {
                    foreach (var item in list)
                    {
                        _pairedDevices[item.DeviceId] = item;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            VeloLogger.Instance.Error("Security", $"Failed to load paired devices: {ex.Message}");
        }
    }

    private void SavePairedDevices()
    {
        try
        {
            var json = JsonSerializer.Serialize(_pairedDevices.Values.ToList(), new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(_storagePath, json);
        }
        catch (Exception ex)
        {
            VeloLogger.Instance.Error("Security", $"Failed to save paired devices: {ex.Message}");
        }
    }
}
