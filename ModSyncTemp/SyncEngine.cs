using System.Diagnostics;

namespace YumisModSync;

public sealed class SyncEngine(Workspace workspace, ServerProfile profile, IProgress<SyncProgress> progress,
    Func<IReadOnlyList<PclProcess>, Task<CloseChoice>> closeDecision,
    Func<IReadOnlyList<GameInstance>, Task<GameInstance?>> chooseInstance)
{
    public GameInstance? Selected { get; private set; }

    private FileStream LockWorkspace() => new(Path.Combine(workspace.Root, "sync.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);

    private async Task PrepareAsync(CancellationToken ct)
    {
        ProcessControl.EnsureGameClosed(Path.Combine(workspace.PclRoot, ".minecraft"));
        await ProcessControl.CloseAsync(ProcessControl.FindPclProcesses(), closeDecision, progress, ct);
        ProcessControl.EnsurePclMutexFree();
        PclHelper.RecoverRun(workspace);
        await new ModTransactions(workspace).RecoverAllAsync(progress, ct);
    }

    private async Task<GameInstance> SelectAsync(ReleaseManifest manifest)
    {
        var instances = InstanceCatalog.Find(workspace.PclRoot, manifest);
        if (instances.Count == 0) throw new IOException($"没有找到已隔离的 Minecraft {manifest.Minecraft} / NeoForge {manifest.NeoForge} 实例，请先在 PCL 安装并开启版本隔离。");
        var chosen = instances.Count == 1 ? instances[0] : await chooseInstance(instances);
        if (chosen == null) throw new OperationCanceledException("已取消选择实例。");
        Selected = chosen; return chosen;
    }

    public async Task SyncAsync(CancellationToken ct, bool launchPcl = true)
    {
        using var workspaceLock = LockWorkspace();
        var launcher = ProcessControl.FindLauncher(workspace.PclRoot);
        await PrepareAsync(ct);
        progress.Report(new("检查", "获取并固定本次服务器清单"));
        using var network = new Network(profile);
        var release = await network.GetReleaseAsync(ct);
        var instance = await SelectAsync(release);
        Paths.NoReparseAncestors(instance.ModsDirectory, workspace.PclRoot);
        ProcessControl.EnsureGameClosed(instance.ModsDirectory);
        var baseline = await ModTransactions.CaptureSnapshotAsync(instance.ModsDirectory, ct);
        progress.Report(new("发布版本", $"{release.ReleaseId} · {release.Files.Count} 个 JAR · {instance.Name}"));
        var desiredNames = release.Files.Select(f => f.Name).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var changed = Paths.Jars(instance.ModsDirectory).Any(p => !desiredNames.Contains(Path.GetFileName(p)));
        var ready = new HashSet<string>();
        var totalBytes = release.Files.Sum(file => file.Size);
        double ReadyPercent() => release.Files.Where(file => ready.Contains(file.Sha256)).Sum(file => file.Size) * 100d / totalBytes;
        foreach (var file in release.Files)
        {
            ct.ThrowIfCancellationRequested();
            var local = Path.Combine(instance.ModsDirectory, file.Name);
            var localMatches = await Hashing.MatchesAsync(local, file, ct);
            if (!localMatches) changed = true;
            if (localMatches)
            {
                if (!await Hashing.MatchesAsync(workspace.Cached(file), file, ct)) File.Copy(local, workspace.Cached(file), true);
                ready.Add(file.Sha256); progress.Report(new("一致", file.Name, ReadyPercent()));
            }
            else if (await Hashing.MatchesAsync(workspace.Cached(file), file, ct))
            {
                ready.Add(file.Sha256); progress.Report(new("缓存", file.Name, ReadyPercent()));
            }
        }
        foreach (var file in release.Files.Where(f => !ready.Contains(f.Sha256)))
        {
            foreach (var source in file.Sources.Where(s => s.Kind is "modrinth" or "server").OrderBy(s => s.Kind == "modrinth" ? 0 : 1))
            {
                try
                {
                    var fileProgress = new ActionProgress(value => progress.Report(value with
                    { Percent = Math.Min(100, ReadyPercent() + file.Size * (value.Percent ?? 0) / totalBytes),
                        Detail = $"已准备 {release.Files.Count(f => ready.Contains(f.Sha256))}/{release.Files.Count} · " + value.Detail }));
                    await network.DownloadAsync(source, file, workspace.Cached(file), fileProgress, ct);
                    ready.Add(file.Sha256); progress.Report(new("已下载", file.Name, ReadyPercent())); break;
                }
                catch (Exception error) when (!ct.IsCancellationRequested && error is IOException or InvalidDataException or HttpRequestException or TimeoutException)
                {
                    workspace.Log($"{file.Name} / {source.Kind}: {error.Message}");
                    progress.Report(new("来源失败", file.Name + " · " + source.Kind + "，检查下一来源"));
                }
            }
        }
        var cf = release.Files.Where(f => !ready.Contains(f.Sha256) && f.Sources.Any(s => s.Kind == "curseforge")).ToList();
        if (cf.Count != 0)
        {
            await ProcessControl.CloseAsync(ProcessControl.FindPclProcesses(), closeDecision, progress, ct);
            await new PclHelper(workspace, profile, closeDecision, progress).FetchAsync(release, cf, ct);
            foreach (var file in cf) ready.Add(file.Sha256);
        }
        var missing = release.Files.Where(f => !ready.Contains(f.Sha256)).ToList();
        if (missing.Count != 0) throw new IOException("来源不可用，正式 mods 保持原状：\n" + string.Join('\n', missing.Select(f => f.Name)));
        foreach (var file in release.Files)
            if (!await Hashing.MatchesAsync(workspace.Cached(file), file, ct)) throw new InvalidDataException($"下载缓存最终校验失败：{file.Name}");
        await ModTransactions.RequireSnapshotAsync(instance.ModsDirectory, baseline, ct);
        ProcessControl.EnsureGameClosed(instance.ModsDirectory);
        if (changed)
        {
            await ProcessControl.CloseAsync(ProcessControl.FindPclProcesses(), closeDecision, progress, ct);
            ProcessControl.EnsurePclMutexFree();
            await new ModTransactions(workspace).CommitAsync(instance.ModsDirectory, release.Files, workspace.Cached, progress, ct, expectedBefore: baseline);
        }
        Json.Write(Path.Combine(workspace.Root, "last-success.json"), release);
        progress.Report(new("完成", changed ? "模组同步完成，上一次 JAR 集合已备份" : "模组已与清单一致，无需替换", 100));
        if (launchPcl && !ct.IsCancellationRequested) OpenLauncher(launcher);
    }

    public async Task RestoreAsync(CancellationToken ct)
    {
        using var workspaceLock = LockWorkspace();
        await PrepareAsync(ct);
        ReleaseManifest release;
        var saved = Path.Combine(workspace.Root, "last-success.json");
        if (File.Exists(saved)) release = Json.Read<ReleaseManifest>(saved);
        else { using var network = new Network(profile); release = await network.GetReleaseAsync(ct); }
        release.Validate(profile);
        var instance = await SelectAsync(release);
        await new ModTransactions(workspace).RestoreAsync(instance.ModsDirectory, progress, ct);
        progress.Report(new("已恢复", "已恢复上次 JAR 集合；本次不重新同步。下次检查会采用服务器当前版本。", 100));
    }

    public async Task ClearCacheAsync(CancellationToken ct)
    {
        using var workspaceLock = LockWorkspace();
        await PrepareAsync(ct);
        workspace.DeleteOwnedDirectory(workspace.Helper);
        workspace.DeleteOwnedDirectory(workspace.Cache);
        Directory.CreateDirectory(workspace.Cache);
        progress.Report(new("已清理", "下载缓存和 PCL 助手缓存已清理，最近一次备份保留", 100));
    }

    public static void OpenLauncher(string path) => Process.Start(new ProcessStartInfo(path) { WorkingDirectory = Path.GetDirectoryName(path), UseShellExecute = false });
}
