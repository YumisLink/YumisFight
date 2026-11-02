namespace YumisModSync;

public sealed class Workspace
{
    public string PclRoot { get; }
    public string Root { get; }
    public string Cache => SafePath("cache");
    public string Helper => SafePath("pcl-helper");
    public string Transactions => SafePath("transactions");
    public string Backups => SafePath("backups");
    public string LogPath => SafePath(Path.Combine("logs", "sync.log"));
    private string SafePath(string relative) { var path = Path.Combine(Root, relative); Paths.NoReparseAncestors(path, PclRoot); return path; }

    public Workspace(string pclRoot)
    {
        PclRoot = Path.GetFullPath(pclRoot);
        Root = Path.Combine(PclRoot, "temp", "YumisModSync");
        Paths.NoReparseAncestors(Root, PclRoot);
        Directory.CreateDirectory(Root);
        var marker = Path.Combine(Root, "owner.json");
        Paths.NoReparseAncestors(marker, PclRoot);
        if (File.Exists(marker))
        {
            if (File.ReadAllText(marker) != "YumisModSync/v1") throw new IOException("工作目录归属不匹配。");
        }
        else
        {
            if (Directory.EnumerateFileSystemEntries(Root).Any()) throw new IOException("工作目录已有未知文件，请另行处理。");
            File.WriteAllText(marker, "YumisModSync/v1");
        }
        AssertNoLinks(Root);
        Directory.CreateDirectory(Cache); Directory.CreateDirectory(Path.GetDirectoryName(LogPath)!);
    }

    private static void AssertNoLinks(string root)
    {
        var pending = new Stack<string>(); pending.Push(root);
        while (pending.TryPop(out var directory))
            foreach (var path in Directory.EnumerateFileSystemEntries(directory))
            {
                var attributes = File.GetAttributes(path);
                if ((attributes & FileAttributes.ReparsePoint) != 0) throw new IOException("工作目录中存在链接，已停止以保护其他目录。");
                if ((attributes & FileAttributes.Directory) != 0) pending.Push(path);
            }
    }

    public string Cached(ModFile file) => SafePath(Path.Combine("cache", file.Sha256 + ".jar"));
    public void Log(string text)
    {
        File.AppendAllText(LogPath, $"{DateTimeOffset.Now:O} {text}{Environment.NewLine}");
    }
    public void DeleteOwnedDirectory(string path)
    {
        Paths.RequireInside(path, Root); Paths.NoReparseAncestors(path, Root);
        if (!Directory.Exists(path)) return;
        if (Directory.EnumerateFileSystemEntries(path, "*", SearchOption.AllDirectories)
            .Any(p => (File.GetAttributes(p) & FileAttributes.ReparsePoint) != 0)) throw new IOException("缓存中存在链接，拒绝递归清理。");
        Directory.Delete(path, true);
    }
}

public static class Paths
{
    public static void MoveDurably(string source, string target, bool overwrite = false)
    {
        if (!MoveFileEx(source, target, 8u | (overwrite ? 1u : 0u))) throw new System.ComponentModel.Win32Exception(System.Runtime.InteropServices.Marshal.GetLastWin32Error());
    }
    [System.Runtime.InteropServices.DllImport("kernel32.dll", EntryPoint = "MoveFileExW", CharSet = System.Runtime.InteropServices.CharSet.Unicode, SetLastError = true)]
    private static extern bool MoveFileEx(string existing, string target, uint flags);
    public static bool Same(string left, string right) => Path.TrimEndingDirectorySeparator(Path.GetFullPath(left))
        .Equals(Path.TrimEndingDirectorySeparator(Path.GetFullPath(right)), StringComparison.OrdinalIgnoreCase);
    public static void RequireInside(string path, string root)
    {
        var prefix = Path.TrimEndingDirectorySeparator(Path.GetFullPath(root)) + Path.DirectorySeparatorChar;
        if (!Path.GetFullPath(path).StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) throw new IOException("路径超出允许目录。");
    }
    public static void NoReparseAncestors(string path, string boundary)
    {
        var current = Path.GetFullPath(path);
        while (true)
        {
            if ((Directory.Exists(current) || File.Exists(current)) && (File.GetAttributes(current) & FileAttributes.ReparsePoint) != 0)
                throw new IOException($"不支持链接目录：{current}");
            if (Same(current, boundary)) return;
            current = Path.GetDirectoryName(current) ?? throw new IOException("目录边界无效。");
        }
    }
    public static void ValidateJarName(string name)
    {
        if (string.IsNullOrWhiteSpace(name) || name.Length > 240 || name != Path.GetFileName(name) ||
            name.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0 || !name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) ||
            name.EndsWith(' ') || name.EndsWith('.') || name.Contains('/') || name.Contains('\\')) throw new InvalidDataException("无效的 JAR 文件名。");
        var stem = name.Split('.')[0].ToUpperInvariant();
        if (stem is "CON" or "PRN" or "AUX" or "NUL" || (stem.Length == 4 && (stem.StartsWith("COM") || stem.StartsWith("LPT")) && stem[3] is >= '1' and <= '9'))
            throw new InvalidDataException("文件名使用了 Windows 保留名称。");
    }
    public static IEnumerable<string> Jars(string directory) => Directory.Exists(directory)
        ? Directory.EnumerateFiles(directory).Where(p => p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) : [];
}
