using System.Collections.Concurrent;
using System.IO;

namespace Velo.Desktop.Services;

public record LogEntry(DateTime Timestamp, string Level, string Category, string Message);

public class VeloLogger
{
    private static readonly Lazy<VeloLogger> _instance = new(() => new VeloLogger());
    public static VeloLogger Instance => _instance.Value;

    private readonly ConcurrentQueue<LogEntry> _recentLogs = new();
    private const int MaxLogHistory = 500;
    private readonly string _logDirectory;
    private readonly string _logFilePath;
    private readonly object _fileLock = new();

    public event Action<LogEntry>? LogAdded;

    public string LogDirectory => _logDirectory;
    public string LogFilePath => _logFilePath;
    public IReadOnlyCollection<LogEntry> Logs => _recentLogs.ToArray();

    public VeloLogger()
    {
        _logDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Velo");
        try
        {
            Directory.CreateDirectory(_logDirectory);
        }
        catch { }
        _logFilePath = Path.Combine(_logDirectory, "velo.log");
    }

    public void Log(string level, string category, string message)
    {
        var entry = new LogEntry(DateTime.Now, level, category, message);
        _recentLogs.Enqueue(entry);

        while (_recentLogs.Count > MaxLogHistory && _recentLogs.TryDequeue(out _)) { }

        try
        {
            LogAdded?.Invoke(entry);
        }
        catch { }

        string line = $"[{entry.Timestamp:yyyy-MM-dd HH:mm:ss.fff}] [{entry.Level}] [{entry.Category}] {entry.Message}";
        Console.WriteLine(line);

        try
        {
            lock (_fileLock)
            {
                File.AppendAllText(_logFilePath, line + Environment.NewLine);
            }
        }
        catch { }
    }

    public void Info(string category, string message) => Log("INFO", category, message);
    public void Warn(string category, string message) => Log("WARN", category, message);
    public void Error(string category, string message) => Log("ERROR", category, message);
    public void Debug(string category, string message) => Log("DEBUG", category, message);
}

// Backward-compatibility alias
public static class AirPilotLogger
{
    public static VeloLogger Instance => VeloLogger.Instance;
}
