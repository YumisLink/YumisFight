using System.Diagnostics;
using System.IO.Compression;
using System.Text;
using System.Text.Json;
using Microsoft.Win32;

namespace YumisModSync;

public sealed class PclRegistrationLease
{
    public string HelperMinecraft { get; set; } = "";
    public string SourceMinecraft { get; set; } = "";
    public bool AddedSource { get; set; }
    public bool ValueExisted { get; set; }
    public string OriginalValue { get; set; } = "";

    private static string Normalize(string path) => Path.TrimEndingDirectorySeparator(Path.GetFullPath(path));
    private static string? EntryPath(string entry)
    {
        var split = entry.IndexOf('>');
        if (split < 0) return null;
        try { return Normalize(entry[(split + 1)..]); } catch { return null; }
    }
    public static PclRegistrationLease Create(Workspace workspace)
    {
        using var key = Registry.CurrentUser.CreateSubKey(@"Software\PCL");
        var value = key.GetValue("LaunchFolders");
        if (value != null && key.GetValueKind("LaunchFolders") != RegistryValueKind.String)
            throw new IOException("PCL 游戏目录设置不是预期的字符串，已停止协作。");
        var lease = new PclRegistrationLease { HelperMinecraft = Path.Combine(workspace.Helper, ".minecraft"),
            SourceMinecraft = Path.Combine(workspace.PclRoot, ".minecraft"), ValueExisted = value != null, OriginalValue = value as string ?? "" };
        var entries = lease.OriginalValue.Split('|', StringSplitOptions.RemoveEmptyEntries).ToList();
        lease.AddedSource = !entries.Any(e => EntryPath(e) is { } path && Paths.Same(path, lease.SourceMinecraft));
        Json.Write(Path.Combine(workspace.Root, "pcl-registration.json"), lease);
        if (lease.AddedSource)
        {
            entries.Add("Yumis同步缓存>" + lease.SourceMinecraft.TrimEnd('\\') + "\\");
            key.SetValue("LaunchFolders", string.Join('|', entries), RegistryValueKind.String);
        }
        return lease;
    }

    public static void Recover(Workspace workspace)
    {
        var journal = Path.Combine(workspace.Root, "pcl-registration.json");
        if (!File.Exists(journal)) return;
        var lease = Json.Read<PclRegistrationLease>(journal);
        if (!Paths.Same(lease.HelperMinecraft, Path.Combine(workspace.Helper, ".minecraft")) ||
            !Paths.Same(lease.SourceMinecraft, Path.Combine(workspace.PclRoot, ".minecraft"))) throw new IOException("PCL 临时目录恢复记录无效。");
        using var key = Registry.CurrentUser.CreateSubKey(@"Software\PCL");
        if (key.GetValue("LaunchFolders") is { } raw && raw is not string) throw new IOException("PCL 目录设置格式变化，已保留恢复记录。");
        var current = key.GetValue("LaunchFolders") as string ?? "";
        var keep = current.Split('|', StringSplitOptions.RemoveEmptyEntries).Where(entry =>
        {
            var path = EntryPath(entry);
            if (path == null) return true;
            if (Paths.Same(path, lease.HelperMinecraft)) return false;
            return !(lease.AddedSource && entry.StartsWith("Yumis同步缓存>", StringComparison.Ordinal) && Paths.Same(path, lease.SourceMinecraft));
        }).ToList();
        // PCL itself removes offline/temporarily unavailable registered roots at startup.
        // Restore such original entries without replacing subsequent unrelated edits.
        foreach (var original in lease.OriginalValue.Split('|', StringSplitOptions.RemoveEmptyEntries))
        {
            var oldPath = EntryPath(original);
            var split = original.IndexOf('>');
            var oldName = split < 0 ? original : original[..split];
            if (!keep.Any(entry => entry.Equals(original, StringComparison.Ordinal) ||
                (oldPath != null && EntryPath(entry) is { } path && Paths.Same(path, oldPath)) ||
                entry.StartsWith(oldName + ">", StringComparison.OrdinalIgnoreCase))) keep.Add(original);
        }
        if (keep.Count == 0 && !lease.ValueExisted) key.DeleteValue("LaunchFolders", false);
        else key.SetValue("LaunchFolders", string.Join('|', keep), RegistryValueKind.String);
        File.Delete(journal);
    }
}

public sealed class PclHelper(Workspace workspace, ServerProfile profile,
    Func<IReadOnlyList<PclProcess>, Task<CloseChoice>> decideClose, IProgress<SyncProgress> progress)
{
    public async Task ExtractAsync(CancellationToken ct)
    {
        var systemTemp = Path.GetFullPath(Path.GetTempPath());
        if (workspace.Helper.StartsWith(systemTemp, StringComparison.OrdinalIgnoreCase)) throw new IOException("PCL 助手不能放在 Windows 临时目录，请把同步器移到正常的 PCL 目录。");
        Paths.NoReparseAncestors(workspace.Helper, workspace.PclRoot);
        Directory.CreateDirectory(workspace.Helper);
        var path = Path.Combine(workspace.Helper, "Plain Craft Launcher 2.exe");
        if (File.Exists(path))
        {
            await using var input = File.OpenRead(path);
            if (Convert.ToHexStringLower(await System.Security.Cryptography.SHA256.HashDataAsync(input, ct)) == profile.PclSha256) return;
        }
        using var resource = typeof(PclHelper).Assembly.GetManifestResourceStream("YumisModSync.PCL.exe")!;
        var temporary = path + ".extract";
        await using (var output = File.Create(temporary)) await resource.CopyToAsync(output, ct);
        await using (var input = File.OpenRead(temporary))
            if (Convert.ToHexStringLower(await System.Security.Cryptography.SHA256.HashDataAsync(input, ct)) != profile.PclSha256)
                throw new InvalidDataException("内置 PCL 助手校验失败。");
        File.Move(temporary, path, true);
        using var licence = typeof(PclHelper).Assembly.GetManifestResourceStream("YumisModSync.PCL-LICENCE.txt")!;
        await using var licenceOutput = File.Create(Path.Combine(workspace.Helper, "PCL-LICENCE.txt"));
        await licence.CopyToAsync(licenceOutput, ct);
    }

    public async Task FetchAsync(ReleaseManifest release, IReadOnlyList<ModFile> files, CancellationToken ct)
    {
        files = files.GroupBy(file => file.Sha256, StringComparer.Ordinal).Select(group => group.First()).ToList();
        if (files.Count == 0) return;
        await ExtractAsync(ct);
        if (ProcessControl.FindPclProcesses().Count != 0) throw new IOException("PCL 已重新打开，请关闭后重试。");
        ProcessControl.EnsurePclMutexFree();
        var package = Path.Combine(workspace.Helper, "modpack.mrpack");
        var runId = "YumisSync-" + Guid.NewGuid().ToString("N");
        var instance = Path.Combine(workspace.Helper, ".minecraft", "versions", runId);
        var runRecord = Path.Combine(workspace.Root, "helper-run.json");
        if (File.Exists(package) || File.Exists(Path.Combine(workspace.Helper, "modpack.zip")))
            throw new IOException("助手目录存在上次安装包，请点击清理缓存后重试。");
        var log = Path.Combine(workspace.Helper, "PCL", "Log1.txt");
        if (File.Exists(log)) File.Delete(log); // Owned log only; prevents old success messages from matching.
        Json.Write(runRecord, new HelperRun { Instance = instance, RunId = runId });
        using (var archive = ZipFile.Open(package, ZipArchiveMode.Create))
        {
            using var stream = archive.CreateEntry("modrinth.index.json").Open();
            JsonSerializer.Serialize(stream, new
            {
                formatVersion = 1, game = "minecraft", versionId = release.ReleaseId + "-" + runId,
                name = runId, dependencies = new Dictionary<string, string> { ["minecraft"] = release.Minecraft },
                files = files.Select(file => new
                {
                    path = "mods/" + file.Sha256 + ".jar", hashes = new { sha1 = file.Sha1, sha512 = file.Sha512 },
                    env = new { client = "required", server = "unsupported" }, fileSize = file.Size,
                    downloads = file.Sources.Where(s => s.Kind == "curseforge").Select(s => s.Url).Distinct().ToArray()
                }).ToArray()
            }, Json.Options);
        }
        PclRegistrationLease.Create(workspace);
        Process? process = null; PclProcess? identity = null; bool completed = false; bool userCancelled = false;
        try
        {
            progress.Report(new("PCL 下载", "准备官方 PCL 下载窗口；首次可能需要确认协议"));
            process = Process.Start(new ProcessStartInfo(Path.Combine(workspace.Helper, "Plain Craft Launcher 2.exe"))
            { WorkingDirectory = workspace.Helper, UseShellExecute = false }) ?? throw new IOException("无法启动 PCL 助手。");
            identity = new(process.Id, process.StartTime.ToUniversalTime(), process.MainModule!.FileName!);
            var started = DateTime.UtcNow;
            var verified = new HashSet<string>();
            while (true)
            {
                ct.ThrowIfCancellationRequested();
                if (process.HasExited) throw new IOException("PCL 助手提前退出，模组保持原状。");
                if (DateTime.UtcNow - started > TimeSpan.FromMinutes(40)) throw new TimeoutException("PCL 下载等待超过 40 分钟，请检查网络或协议确认窗口后重试。");
                var text = ReadSharedLog(log);
                if (text.Contains("自动安装整合包失败：") || text.Contains("自动安装整合包已取消："))
                    throw new IOException("PCL 下载失败或被取消。请查看助手窗口；本版本没有服务器兜底副本的文件需要重试外部来源。");
                foreach (var file in files.Where(f => !verified.Contains(f.Sha256)))
                {
                    try
                    {
                        if (await Hashing.MatchesAsync(Path.Combine(instance, "mods", file.Sha256 + ".jar"), file, ct)) verified.Add(file.Sha256);
                    }
                    catch (IOException) { }
                }
                progress.Report(new("PCL 下载", $"已校验 {verified.Count}/{files.Count} 个文件 · 等待官方 PCL 完成安装准备", verified.Count * 100d / files.Count));
                var loaderFinished = text.Split('\n').Any(line => line.Contains(runId) && line.Contains("Modrinth 整合包安装") && line.Contains("状态改变：Finished"));
                var overallSuccess = text.Contains("自动安装整合包成功，清理安装包：");
                if ((loaderFinished || overallSuccess) && !File.Exists(package) && verified.Count == files.Count) break;
                await Task.Delay(750, ct);
            }
            await ProcessControl.CloseAsync([identity], decideClose, progress, ct);
            if (!process.HasExited) throw new IOException("PCL 助手仍未退出。");
            foreach (var file in files)
            {
                var source = Path.Combine(instance, "mods", file.Sha256 + ".jar");
                if (!await Hashing.MatchesAsync(source, file, ct)) throw new InvalidDataException($"PCL 文件最终校验失败：{file.Name}");
                File.Copy(source, workspace.Cached(file), true);
                if (!await Hashing.MatchesAsync(workspace.Cached(file), file, ct)) throw new InvalidDataException("写入下载缓存失败。");
            }
            completed = true;
        }
        catch (OperationCanceledException) { userCancelled = true; throw; }
        finally
        {
            if (process != null && !process.HasExited && identity != null && !userCancelled)
            {
                try { await ProcessControl.CloseAsync([identity], decideClose, progress, CancellationToken.None); }
                catch (Exception error) { workspace.Log("助手清理未完成：" + error.Message); }
            }
            if (process == null || process.HasExited)
            {
                PclRegistrationLease.Recover(workspace);
                if (Directory.Exists(instance)) workspace.DeleteOwnedDirectory(instance);
                if (File.Exists(package)) File.Delete(package);
                File.Delete(runRecord);
            }
            process?.Dispose();
            if (!completed) progress.Report(new("未完成", "下载未完成，正式 mods 保持原状"));
        }
    }

    public static void RecoverRun(Workspace workspace)
    {
        PclRegistrationLease.Recover(workspace);
        var record = Path.Combine(workspace.Root, "helper-run.json");
        if (!File.Exists(record)) return;
        var run = Json.Read<HelperRun>(record);
        var versions = Path.Combine(workspace.Helper, ".minecraft", "versions");
        Paths.RequireInside(run.Instance, versions);
        if (Path.GetFileName(run.Instance) != run.RunId || !run.RunId.StartsWith("YumisSync-")) throw new IOException("助手恢复记录无效。");
        if (Directory.Exists(run.Instance)) workspace.DeleteOwnedDirectory(run.Instance);
        var package = Path.Combine(workspace.Helper, "modpack.mrpack");
        if (File.Exists(package)) File.Delete(package);
        File.Delete(record);
    }

    private static string ReadSharedLog(string path)
    {
        try
        {
            using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            if (stream.Length > 16 * 1024 * 1024) stream.Seek(-16 * 1024 * 1024, SeekOrigin.End);
            using var reader = new StreamReader(stream, Encoding.UTF8); return reader.ReadToEnd();
        }
        catch (IOException) { return ""; }
    }

    public sealed class HelperRun
    {
        public string Instance { get; set; } = "";
        public string RunId { get; set; } = "";
    }
}
