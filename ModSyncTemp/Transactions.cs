namespace YumisModSync;

public sealed class TransactionJournal
{
    public string Id { get; set; } = "";
    public string Target { get; set; } = "";
    public string Phase { get; set; } = "prepared";
    public List<ModFile> Before { get; set; } = [];
    public List<ModFile> After { get; set; } = [];
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public string? PreviousBackupFolder { get; set; }
}

public sealed class BackupRecord
{
    public string Target { get; set; } = "";
    public string Folder { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
    public List<ModFile> Files { get; set; } = [];
}

public sealed class ModTransactions(Workspace workspace)
{
    private string Key(string target) => Hashing.Text(Path.GetFullPath(target).ToUpperInvariant());
    private string JournalPath(string directory) => Path.Combine(directory, "journal.json");
    private string BackupIndex(string target) => Path.Combine(workspace.Backups, Key(target), "latest.json");

    public async Task RecoverAllAsync(IProgress<SyncProgress> progress, CancellationToken ct)
    {
        if (!Directory.Exists(workspace.Transactions)) return;
        foreach (var directory in Directory.EnumerateDirectories(workspace.Transactions))
        {
            ct.ThrowIfCancellationRequested();
            var path = JournalPath(directory);
            if (!File.Exists(path))
            {
                // Before a durable journal exists no target mutation has occurred.
                workspace.DeleteOwnedDirectory(directory); continue;
            }
            var journal = Json.Read<TransactionJournal>(path);
            Validate(journal, directory);
            progress.Report(new("恢复", "正在恢复上次未完成的模组操作"));
            ProcessControl.EnsureGameClosed(journal.Target);
            if (journal.Phase == "committed")
            {
                try { await RequireSnapshotAsync(journal.Target, journal.After, ct); }
                catch (IOException)
                {
                    await RollbackAsync(journal, directory, ct);
                    workspace.DeleteOwnedDirectory(directory);
                    progress.Report(new("已恢复", "检测到提交集合不完整，已恢复原 JAR"));
                    continue;
                }
                await FinalizeBackupAsync(journal, directory, ct);
            }
            else
            {
                if (journal.Phase == "applying") await RollbackAsync(journal, directory, ct);
                workspace.DeleteOwnedDirectory(directory);
            }
        }
    }

    private void Validate(TransactionJournal journal, string directory)
    {
        Paths.RequireInside(directory, workspace.Transactions);
        if (journal.Id != Path.GetFileName(directory) || journal.Phase is not ("prepared" or "applying" or "committed"))
            throw new InvalidDataException("事务记录无效，已停止恢复。");
        ValidateTarget(journal.Target);
        foreach (var entry in journal.Before.Concat(journal.After))
        {
            Paths.ValidateJarName(entry.Name);
            if (!Hashing.ValidHex(entry.Sha256, 64)) throw new InvalidDataException("事务文件校验值无效。");
        }
    }

    private void ValidateTarget(string target)
    {
        Paths.RequireInside(target, Path.Combine(workspace.PclRoot, ".minecraft", "versions"));
        if (!Path.GetFileName(target).Equals("mods", StringComparison.OrdinalIgnoreCase)) throw new IOException("目标必须为实例的 mods 目录。");
        Paths.NoReparseAncestors(target, workspace.PclRoot);
    }

    public async Task CommitAsync(string target, IReadOnlyList<ModFile> desired, Func<ModFile, string> source,
        IProgress<SyncProgress> progress, CancellationToken ct, Action<string>? checkpoint = null, IReadOnlyList<ModFile>? expectedBefore = null)
    {
        ValidateTarget(target); Directory.CreateDirectory(target);
        var desiredNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var file in desired)
        {
            Paths.ValidateJarName(file.Name);
            if (!desiredNames.Add(file.Name) || !Hashing.ValidHex(file.Sha256, 64) || file.Size < 1) throw new InvalidDataException("目标文件集合无效。");
            if (!await Hashing.MatchesAsync(source(file), file, ct)) throw new InvalidDataException($"暂存文件校验失败：{file.Name}");
        }
        var before = expectedBefore?.ToList() ?? await CaptureSnapshotAsync(target, ct);
        await RequireSnapshotAsync(target, before, ct);
        var id = Guid.NewGuid().ToString("N");
        var directory = Path.Combine(workspace.Transactions, id);
        var old = Path.Combine(directory, "old"); Directory.CreateDirectory(old);
        foreach (var file in before)
        {
            await CopyDurablyAsync(Path.Combine(target, file.Name), Path.Combine(old, file.Name), false, ct);
            if (!await Hashing.MatchesAsync(Path.Combine(old, file.Name), file, ct)) throw new IOException("创建备份时目标发生变化。");
        }
        var index = BackupIndex(target);
        var previousBackup = File.Exists(index) ? Json.Read<BackupRecord>(index).Folder : null;
        var journal = new TransactionJournal { Id = id, Target = target, Before = before, After = desired.ToList(), PreviousBackupFolder = previousBackup };
        Json.Write(JournalPath(directory), journal);
        var durableCommit = false;
        try
        {
            await RequireSnapshotAsync(target, before, ct);
            ProcessControl.EnsureGameClosed(target);
            ct.ThrowIfCancellationRequested();
            journal.Phase = "applying"; Json.Write(JournalPath(directory), journal);
            checkpoint?.Invoke("applying");
            progress.Report(new("替换", "正在提交完整模组集合；完成后启动 PCL"));
            foreach (var file in before) File.Delete(Path.Combine(target, file.Name));
            checkpoint?.Invoke("removed");
            foreach (var file in desired) await CopyDurablyAsync(source(file), Path.Combine(target, file.Name), false, CancellationToken.None);
            checkpoint?.Invoke("installed");
            await RequireSnapshotAsync(target, desired, CancellationToken.None);
            journal.Phase = "committed"; Json.Write(JournalPath(directory), journal);
            durableCommit = true;
            checkpoint?.Invoke("committed");
        }
        catch
        {
            if (!durableCommit && journal.Phase != "prepared") await RollbackAsync(journal, directory, CancellationToken.None);
            if (!durableCommit) workspace.DeleteOwnedDirectory(directory);
            throw;
        }
        await FinalizeBackupAsync(journal, directory, CancellationToken.None);
    }

    public static async Task<List<ModFile>> CaptureSnapshotAsync(string target, CancellationToken ct)
    {
        var snapshot = new List<ModFile>();
        foreach (var file in Paths.Jars(target))
        {
            Paths.ValidateJarName(Path.GetFileName(file));
            if ((File.GetAttributes(file) & FileAttributes.ReparsePoint) != 0) throw new IOException("mods 中的 JAR 不能为链接。");
            snapshot.Add(await Hashing.DescribeAsync(file, ct));
        }
        return snapshot;
    }

    public static async Task RequireSnapshotAsync(string target, IReadOnlyList<ModFile> snapshot, CancellationToken ct)
    {
        var names = Paths.Jars(target).Select(Path.GetFileName).ToHashSet(StringComparer.OrdinalIgnoreCase);
        if (!names.SetEquals(snapshot.Select(f => f.Name))) throw new IOException("mods 文件集合已变化，请重新检查。");
        foreach (var file in snapshot)
            if (!await Hashing.MatchesAsync(Path.Combine(target, file.Name), file, ct)) throw new IOException($"文件已变化或校验失败：{file.Name}");
    }

    private async Task RollbackAsync(TransactionJournal journal, string directory, CancellationToken ct)
    {
        ProcessControl.EnsureGameClosed(journal.Target);
        var old = Directory.Exists(Path.Combine(directory, "old")) ? Path.Combine(directory, "old")
            : Path.Combine(workspace.Backups, Key(journal.Target), journal.Id);
        foreach (var file in journal.Before)
            if (!await Hashing.MatchesAsync(Path.Combine(old, file.Name), file, ct)) throw new IOException("恢复备份校验失败，已保留事务资料，请勿启动游戏。");
        var beforeNames = journal.Before.Select(f => f.Name).ToHashSet(StringComparer.OrdinalIgnoreCase);
        foreach (var path in Paths.Jars(journal.Target).ToList())
        {
            var name = Path.GetFileName(path);
            var after = journal.After.FirstOrDefault(f => f.Name.Equals(name, StringComparison.OrdinalIgnoreCase));
            if (after != null && await Hashing.MatchesAsync(path, after, ct)) File.Delete(path);
            else if (!beforeNames.Contains(name)) PreserveConflict(path, journal.Id);
        }
        foreach (var file in journal.Before)
        {
            var targetFile = Path.Combine(journal.Target, file.Name);
            if (File.Exists(targetFile) && !await Hashing.MatchesAsync(targetFile, file, ct))
            {
                // Preserve any concurrent edit instead of destroying it during recovery.
                PreserveConflict(targetFile, journal.Id);
            }
            await CopyDurablyAsync(Path.Combine(old, file.Name), targetFile, true, ct);
        }
        await RequireSnapshotAsync(journal.Target, journal.Before, ct);
        workspace.Log($"已恢复事务 {journal.Id}");
    }

    private async Task FinalizeBackupAsync(TransactionJournal journal, string directory, CancellationToken ct)
    {
        var parent = Path.Combine(workspace.Backups, Key(journal.Target)); Directory.CreateDirectory(parent);
        var folder = Path.Combine(parent, journal.Id);
        var old = Path.Combine(directory, "old");
        if (!Directory.Exists(folder)) Directory.Move(old, folder);
        foreach (var file in journal.Before)
            if (!await Hashing.MatchesAsync(Path.Combine(folder, file.Name), file, ct)) throw new IOException("上次备份校验失败，事务资料已保留。");
        var index = BackupIndex(journal.Target);
        Json.Write(index, new BackupRecord { Target = journal.Target, Folder = folder, CreatedAt = journal.CreatedAt, Files = journal.Before });
        if (journal.PreviousBackupFolder is { } previous && !Paths.Same(previous, folder))
        {
            Paths.RequireInside(previous, parent); workspace.DeleteOwnedDirectory(previous);
        }
        workspace.DeleteOwnedDirectory(directory);
    }

    private void PreserveConflict(string path, string id)
    {
        var conflict = Path.Combine(workspace.Root, "recovery-conflicts", id);
        Paths.NoReparseAncestors(conflict, workspace.PclRoot); Directory.CreateDirectory(conflict);
        Paths.MoveDurably(path, Path.Combine(conflict, Guid.NewGuid().ToString("N") + "-" + Path.GetFileName(path)));
    }
    private static async Task CopyDurablyAsync(string source, string target, bool overwrite, CancellationToken ct)
    {
        await using var input = new FileStream(source, FileMode.Open, FileAccess.Read, FileShare.Read, 131072, true);
        await using var output = new FileStream(target, overwrite ? FileMode.Create : FileMode.CreateNew, FileAccess.Write, FileShare.None, 131072, FileOptions.Asynchronous | FileOptions.WriteThrough);
        await input.CopyToAsync(output, ct); await output.FlushAsync(ct); output.Flush(true);
    }

    public async Task RestoreAsync(string target, IProgress<SyncProgress> progress, CancellationToken ct)
    {
        var index = BackupIndex(target);
        if (!File.Exists(index)) throw new IOException("该实例还没有上次备份。");
        var backup = Json.Read<BackupRecord>(index);
        if (!Paths.Same(backup.Target, target)) throw new InvalidDataException("备份目标不匹配。");
        Paths.RequireInside(backup.Folder, workspace.Backups);
        await CommitAsync(target, backup.Files, file => Path.Combine(backup.Folder, file.Name), progress, ct);
    }
}
