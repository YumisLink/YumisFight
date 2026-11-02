using System.Diagnostics;
using System.Runtime.InteropServices;

namespace YumisModSync;

public enum CloseChoice { Force, Wait, Cancel }
public sealed record PclProcess(int Id, DateTime Started, string Path);

public static class ProcessControl
{
    public static string FindLauncher(string root)
    {
        var matches = Directory.EnumerateFiles(root, "*.exe").Where(IsPclFile).ToList();
        var known = Path.Combine(root, "Plain Craft Launcher 2.exe");
        if (matches.Any(p => Paths.Same(p, known))) return known;
        if (matches.Count == 1) return matches[0];
        throw new IOException(matches.Count == 0 ? "请将 YumisModSync.exe 放在原来的 PCL 同目录。" : "目录中有多个 PCL，请保留一个日常启动器再使用。");
    }

    public static bool IsPclFile(string path)
    {
        try { return FileVersionInfo.GetVersionInfo(path).ProductName == "Plain Craft Launcher"; }
        catch { return false; }
    }

    public static List<PclProcess> FindPclProcesses()
    {
        var result = new List<PclProcess>();
        foreach (var process in Process.GetProcesses())
        {
            using (process)
            {
                try
                {
                    if (process.Id == Environment.ProcessId) continue;
                    var path = process.MainModule?.FileName;
                    if (path != null && IsPclFile(path)) result.Add(new(process.Id, process.StartTime.ToUniversalTime(), path));
                }
                catch (Exception ex) when (ex is System.ComponentModel.Win32Exception or InvalidOperationException) { }
            }
        }
        return result;
    }

    private static Process? GetSameProcess(PclProcess identity)
    {
        Process? process = null;
        try
        {
            process = Process.GetProcessById(identity.Id);
            if (!process.HasExited && process.StartTime.ToUniversalTime() == identity.Started &&
                process.MainModule?.FileName is { } path && Paths.Same(path, identity.Path) && IsPclFile(path)) return process;
        }
        catch (Exception ex) when (ex is ArgumentException or System.ComponentModel.Win32Exception or InvalidOperationException) { }
        process?.Dispose(); return null;
    }

    public static bool IsAlive(PclProcess identity) { using var process = GetSameProcess(identity); return process != null; }

    public static void EnsurePclMutexFree()
    {
        try
        {
            if (!Mutex.TryOpenExisting("PCL_SingletonMutex", out var mutex)) return;
            using (mutex)
            {
                var acquired = false;
                try
                {
                    try { acquired = mutex.WaitOne(0); }
                    catch (AbandonedMutexException) { acquired = true; }
                    if (!acquired) throw new IOException("PCL 仍在运行。若启动器以管理员权限启动，请先手动关闭它再重试。");
                }
                finally { if (acquired) mutex.ReleaseMutex(); }
            }
        }
        catch (UnauthorizedAccessException error) { throw new IOException("无法检查 PCL 的运行锁，请手动关闭其他 PCL 后重试。", error); }
    }

    public static async Task CloseAsync(IReadOnlyList<PclProcess> processes,
        Func<IReadOnlyList<PclProcess>, Task<CloseChoice>> decide, IProgress<SyncProgress> progress,
        CancellationToken ct, TimeSpan? grace = null)
    {
        if (processes.Count == 0) return;
        foreach (var identity in processes)
            using (var process = GetSameProcess(identity)) process?.CloseMainWindow();
        while (true)
        {
            var until = DateTime.UtcNow + (grace ?? TimeSpan.FromSeconds(15));
            List<PclProcess> remaining;
            do
            {
                ct.ThrowIfCancellationRequested();
                remaining = processes.Where(identity => { using var p = GetSameProcess(identity); return p != null; }).ToList();
                if (remaining.Count == 0) return;
                progress.Report(new("关闭 PCL", $"等待正常退出 · 剩余 {Math.Max(0, (until - DateTime.UtcNow).Seconds)} 秒"));
                await Task.Delay(250, ct);
            } while (DateTime.UtcNow < until);
            var choice = await decide(remaining).WaitAsync(ct);
            if (choice == CloseChoice.Cancel) throw new OperationCanceledException("已取消同步。");
            if (choice == CloseChoice.Wait) continue;
            foreach (var identity in remaining)
            {
                using var process = GetSameProcess(identity);
                if (process == null) continue;
                // Never terminate a process tree: Minecraft/Java are separate processes.
                process.Kill(false);
                await process.WaitForExitAsync(ct).WaitAsync(TimeSpan.FromSeconds(10), ct);
            }
            return;
        }
    }

    public static void EnsureGameClosed(string directory)
    {
        var target = Path.GetFullPath(directory);
        if (Path.GetFileName(target).Equals("mods", StringComparison.OrdinalIgnoreCase)) target = Path.GetDirectoryName(target)!;
        if (!Directory.Exists(target)) return;
        target = FinalDirectoryPath(target);
        dynamic? locator = null; dynamic? service = null; dynamic? collection = null;
        try
        {
            locator = Activator.CreateInstance(Type.GetTypeFromProgID("WbemScripting.SWbemLocator")!);
            service = locator!.ConnectServer(".", "root\\cimv2");
            collection = service.ExecQuery("SELECT ProcessId,CommandLine,Name FROM Win32_Process WHERE Name='java.exe' OR Name='javaw.exe'");
            foreach (dynamic process in collection)
            {
                try
                {
                    string? command = process.Properties_.Item("CommandLine").Value as string;
                    if (command == null) throw new IOException("有无法检查的 Java 进程，请确认游戏已退出，并用相同用户权限运行。");
                    var args = SplitCommandLine(command);
                    for (var i = 0; i + 1 < args.Length; i++)
                        if (args[i] == "--gameDir")
                        {
                            if (!Path.IsPathFullyQualified(args[i + 1])) throw new IOException("无法确认相对路径的游戏目录，请先退出游戏。");
                            var gamePath = FinalDirectoryPath(args[i + 1]);
                            if (Paths.Same(gamePath, target) || gamePath.StartsWith(Path.TrimEndingDirectorySeparator(target) + "\\", StringComparison.OrdinalIgnoreCase))
                                throw new IOException("目标游戏仍在运行，请退出游戏后重试。");
                        }
                }
                finally { Marshal.FinalReleaseComObject(process); }
            }
        }
        catch (IOException) { throw; }
        catch (Exception error) { throw new IOException("无法确认游戏占用，已停止修改。请确认 Windows WMI 服务可用。", error); }
        finally
        {
            if (collection != null) Marshal.FinalReleaseComObject(collection);
            if (service != null) Marshal.FinalReleaseComObject(service);
            if (locator != null) Marshal.FinalReleaseComObject(locator);
        }
    }

    public static string FinalDirectoryPath(string directory)
    {
        using var handle = CreateFile(Path.GetFullPath(directory), 0, 7, IntPtr.Zero, 3, 0x02000000, IntPtr.Zero);
        if (handle.IsInvalid) throw new IOException("无法核实游戏目录的最终路径。", new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error()));
        var buffer = new System.Text.StringBuilder(32768);
        var size = GetFinalPathNameByHandle(handle, buffer, (uint)buffer.Capacity, 0);
        if (size == 0 || size >= buffer.Capacity) throw new IOException("无法核实游戏目录的最终路径。");
        var path = buffer.ToString();
        if (path.StartsWith(@"\\?\UNC\")) path = @"\\" + path[8..];
        else if (path.StartsWith(@"\\?\")) path = path[4..];
        return Path.TrimEndingDirectorySeparator(path);
    }
    [DllImport("kernel32.dll", EntryPoint = "CreateFileW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern Microsoft.Win32.SafeHandles.SafeFileHandle CreateFile(string path, uint access, uint share, IntPtr security, uint creation, uint flags, IntPtr template);
    [DllImport("kernel32.dll", EntryPoint = "GetFinalPathNameByHandleW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern uint GetFinalPathNameByHandle(Microsoft.Win32.SafeHandles.SafeFileHandle handle, System.Text.StringBuilder path, uint size, uint flags);

    private static string[] SplitCommandLine(string command)
    {
        var pointer = CommandLineToArgvW(command, out var count);
        if (pointer == IntPtr.Zero) throw new IOException("无法解析游戏进程参数。");
        try { return Enumerable.Range(0, count).Select(i => Marshal.PtrToStringUni(Marshal.ReadIntPtr(pointer, i * IntPtr.Size))!).ToArray(); }
        finally { LocalFree(pointer); }
    }
    [DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)] private static extern IntPtr CommandLineToArgvW(string command, out int count);
    [DllImport("kernel32.dll")] private static extern IntPtr LocalFree(IntPtr pointer);
}
