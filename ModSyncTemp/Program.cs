using System.Text;
using System.Windows;

namespace YumisModSync;

public static class Program
{
    [STAThread]
    public static int Main(string[] args)
    {
        try
        {
            if (!OperatingSystem.IsWindowsVersionAtLeast(10, 0, 19045)) throw new PlatformNotSupportedException("需要 Windows 10 22H2 或 Windows 11 的 64 位系统。");
            var profile = ServerProfile.Load();
            if (args.Contains("--headless") && args.Contains("--publish"))
            {
                var source = Argument(args, "--source") ?? throw new ArgumentException("需要 --source mods目录。");
                var output = Argument(args, "--output") ?? Path.Combine(AppContext.BaseDirectory, "temp", "YumisModSync-admin");
                var report = Argument(args, "--report");
                var progress = new ActionProgress(p => { if (report != null) { Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(report))!); File.AppendAllText(report + ".log", p.Stage + " " + p.Detail + Environment.NewLine); } });
                var result = new Publisher(profile, progress).PublishAsync(source, output, CancellationToken.None).GetAwaiter().GetResult();
                if (report != null) Json.Write(report, result);
                return 0;
            }
            var application = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
            application.DispatcherUnhandledException += (_, e) =>
            {
                MessageBox.Show(e.Exception.Message, "Yumis ModSync", MessageBoxButton.OK, MessageBoxImage.Error); e.Handled = true;
            };
            application.Run(new MainWindow(profile, args)); return 0;
        }
        catch (Exception error)
        {
            var report = Argument(args, "--report");
            if (report != null) { Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(report))!); File.WriteAllText(report + ".error", error.ToString(), Encoding.UTF8); }
            else MessageBox.Show(error.Message, "Yumis ModSync", MessageBoxButton.OK, MessageBoxImage.Error);
            return 1;
        }
    }
    public static string? Argument(string[] args, string name) { var i = Array.IndexOf(args, name); return i >= 0 && i + 1 < args.Length ? args[i + 1] : null; }
}
