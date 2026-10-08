using System.IO;
using System.Windows;
using Velo.Desktop.Services;

namespace Velo.Desktop;

public partial class App : System.Windows.Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        var logPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Velo", "startup.log");
        Directory.CreateDirectory(Path.GetDirectoryName(logPath)!);
        File.WriteAllText(logPath, $"OnStartup called at {DateTime.Now}\n");

        AppDomain.CurrentDomain.UnhandledException += (s, args) =>
        {
            File.AppendAllText(logPath, $"UnhandledException: {args.ExceptionObject}\n");
        };

        DispatcherUnhandledException += (s, args) =>
        {
            File.AppendAllText(logPath, $"DispatcherUnhandledException: {args.Exception}\n");
        };

        try
        {
            var pin = SecurityManager.Instance.CurrentPin;
            Console.WriteLine();
            Console.WriteLine("==================================================");
            Console.WriteLine("           VELO DESKTOP RECEIVER v1.0             ");
            Console.WriteLine("==================================================");
            Console.ForegroundColor = ConsoleColor.Cyan;
            Console.WriteLine($"\n  >>> PAIRING CODE: {pin} <<<\n");
            Console.ResetColor();
            Console.WriteLine("  Enter this 6-digit code on your Velo Remote app.");
            Console.WriteLine("  Starting application window...\n");

            var win = new MainWindow();
            win.Show();
            win.Activate();
            win.Focus();
        }
        catch (Exception ex)
        {
            File.AppendAllText(logPath, $"Exception in OnStartup: {ex}\n");
        }
    }
}
