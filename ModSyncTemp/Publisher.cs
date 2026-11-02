using System.Diagnostics;
using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace YumisModSync;

public sealed record PublishResult(string ReleaseId, int Files, int ModrinthFiles, int CurseForgeFiles, int UploadedFiles, string ManifestPath);

public sealed class Publisher(ServerProfile profile, IProgress<SyncProgress> progress)
{
    public async Task<(ReleaseManifest Manifest, Dictionary<string, string> Local)> PrepareAsync(string source, string output, CancellationToken ct)
    {
        source = Path.GetFullPath(source);
        if (!Directory.Exists(source) || !Path.GetFileName(source).Equals("mods", StringComparison.OrdinalIgnoreCase)) throw new IOException("请选择实例的 mods 目录。");
        var release = new ReleaseManifest { ReleaseId = DateTimeOffset.UtcNow.ToOffset(TimeSpan.FromHours(8)).ToString("yyyyMMddHHmmss") + "-" + Guid.NewGuid().ToString("N")[..12], PublishedAt = DateTimeOffset.UtcNow };
        var local = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var path in Paths.Jars(source).Order(StringComparer.OrdinalIgnoreCase))
        {
            ct.ThrowIfCancellationRequested(); Paths.ValidateJarName(Path.GetFileName(path));
            if ((File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0) throw new IOException("发布源不能包含链接 JAR。");
            progress.Report(new("发布准备", "计算文件哈希 · " + Path.GetFileName(path)));
            var file = await Hashing.DescribeAsync(path, ct); release.Files.Add(file); local[file.Name] = path;
        }
        if (release.Files.Count == 0) throw new IOException("mods 中没有启用 JAR，拒绝发布空清单。");
        // Source instance must match the fixed first channel, not just its folder name.
        var pclRoot = Path.GetFullPath(Path.Combine(source, "..", "..", "..", ".."));
        var instances = InstanceCatalog.Find(pclRoot, release);
        if (!instances.Any(i => Paths.Same(i.ModsDirectory, source))) throw new IOException("发布源不属于已隔离的 Minecraft 26.2 / NeoForge 26.2.0.93 实例。");
        ReadPclCache(release.Files);
        using var network = new Network(profile);
        try
        {
            // Keep verified source knowledge even when Windows clears PCL's temp cache.
            var previous = await network.GetReleaseAsync(ct);
            foreach (var file in release.Files)
                foreach (var known in previous.Files.Where(old => old.Sha256 == file.Sha256 && old.Size == file.Size))
                    foreach (var knownSource in known.Sources.Where(s => s.Kind != "server")) AddSource(file, knownSource);
        }
        catch (Exception error) when (!ct.IsCancellationRequested && error is HttpRequestException or IOException or InvalidDataException or OperationCanceledException)
        {
            progress.Report(new("来源提示", "没有可复用的既有清单，继续核实本机缓存与 Modrinth。"));
        }
        try
        {
            progress.Report(new("识别来源", "按真实文件哈希查询 Modrinth"));
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct); timeout.CancelAfter(TimeSpan.FromSeconds(40));
            using var content = new StringContent(JsonSerializer.Serialize(new { hashes = release.Files.Select(f => f.Sha1).Distinct().ToArray(), algorithm = "sha1" }), Encoding.UTF8, "application/json");
            using var response = await network.External.PostAsync("https://api.modrinth.com/v2/version_files", content, timeout.Token);
            response.EnsureSuccessStatusCode();
            var bytes = await response.Content.ReadAsByteArrayAsync(timeout.Token);
            if (bytes.Length > 16 * 1024 * 1024) throw new InvalidDataException("Modrinth 响应过大。");
            using var data = JsonDocument.Parse(bytes);
            foreach (var file in release.Files)
            {
                if (!data.RootElement.TryGetProperty(file.Sha1, out var version) || !version.TryGetProperty("files", out var candidates)) continue;
                foreach (var candidate in candidates.EnumerateArray())
                {
                    if (!candidate.TryGetProperty("hashes", out var hashes) || !hashes.TryGetProperty("sha1", out var sha1) ||
                        sha1.GetString() != file.Sha1 || candidate.GetProperty("size").GetInt64() != file.Size) continue;
                    if (hashes.TryGetProperty("sha512", out var sha512) && sha512.GetString() != file.Sha512) continue;
                    var url = candidate.GetProperty("url").GetString()!;
                    if (Sources.ExternalKind(url) == "modrinth") AddSource(file, new("modrinth", url));
                }
            }
        }
        catch (Exception error) when (!ct.IsCancellationRequested && error is HttpRequestException or OperationCanceledException or JsonException or InvalidDataException)
        {
            progress.Report(new("来源提示", "Modrinth 查询未完成，保留已核实缓存；其余文件使用服务器。"));
        }
        foreach (var file in release.Files)
        {
            file.Sources = file.Sources.OrderBy(s => s.Kind == "modrinth" ? 0 : 1).ToList();
            if (file.Sources.Count == 0)
            {
                file.SourceStatus = "外部来源未识别；服务器托管";
                file.Sources.Add(new("server", new Uri(new Uri(profile.BaseUrl), $"objects/{file.Sha256}.jar").AbsoluteUri));
            }
            else file.SourceStatus = "已按当前文件哈希匹配外部来源";
            progress.Report(new("来源", $"{file.Name} → {string.Join(" / ", file.Sources.Select(s => s.Kind).Distinct())}"));
        }
        release.Validate(profile); Directory.CreateDirectory(output);
        Json.Write(Path.Combine(output, "current.json"), release);
        return (release, local);
    }

    private static void AddSource(ModFile file, DownloadSource source)
    {
        if (file.Sources.All(s => s.Url != source.Url)) file.Sources.Add(source);
    }

    public static void ReadPclCache(IReadOnlyList<ModFile> files, string? cachePath = null)
    {
        cachePath ??= Path.Combine(Path.GetTempPath(), "PCL", "Cache", "LocalMod.json");
        if (!File.Exists(cachePath) || new FileInfo(cachePath).Length > 32 * 1024 * 1024) return;
        try
        {
            using var document = JsonDocument.Parse(File.ReadAllText(cachePath));
            var root = document.RootElement;
            if (!root.TryGetProperty("version", out var version) || version.GetInt32() != 19) return;
            var lookup = files.GroupBy(f => f.Sha1).ToDictionary(g => g.Key, g => g.ToList());
            foreach (var property in root.EnumerateObject())
            {
                if (property.Value.ValueKind != JsonValueKind.Object || !property.Value.TryGetProperty("Version", out var entry) ||
                    !entry.TryGetProperty("Hash", out var hash) || hash.GetString() is not { } hashText || !lookup.TryGetValue(hashText, out var matches) ||
                    !entry.TryGetProperty("Size", out var size) || !entry.TryGetProperty("DownloadUrls", out var urls)) continue;
                foreach (var file in matches.Where(f => f.Size == size.GetInt64()))
                    foreach (var value in urls.EnumerateArray())
                        if (value.GetString() is { } url && Sources.ExternalKind(url) is { } kind) AddSource(file, new(kind, url));
            }
        }
        catch (Exception ex) when (ex is JsonException or IOException or InvalidOperationException) { }
    }

    public async Task<PublishResult> PublishAsync(string source, string outputRoot, CancellationToken ct)
    {
        var staging = Path.Combine(outputRoot, "publish", Guid.NewGuid().ToString("N"));
        var (release, local) = await PrepareAsync(source, staging, ct);
        var objects = Path.Combine(staging, "objects"); Directory.CreateDirectory(objects);
        var uploaded = release.Files.Where(f => f.Sources.Any(s => s.Kind == "server")).ToList();
        foreach (var file in uploaded)
        {
            var path = Path.Combine(objects, file.Sha256 + ".jar"); File.Copy(local[file.Name], path);
            if (!await Hashing.MatchesAsync(path, file, ct)) throw new IOException("发布过程中本机文件发生变化。");
        }
        // A source snapshot is retained for every file, including external-only ones.
        foreach (var file in release.Files)
            if (!await Hashing.MatchesAsync(local[file.Name], file, ct)) throw new IOException("发布过程中本机文件发生变化，请重新发布。");
        var remote = "/srv/yumis-mods/incoming/" + release.ReleaseId;
        progress.Report(new("上传", $"清单 {release.Files.Count} 个文件，仅上传 {uploaded.Count} 个无外部源 JAR"));
        await Remote.RunAsync("ssh", ["-o", "BatchMode=yes", "-o", "ConnectTimeout=15", "homo", "mkdir -p " + remote + "/objects"], null, ct);
        await Remote.RunAsync("scp", ["-q", "-B", Path.Combine(staging, "current.json"), "homo:" + remote + "/current.json"], null, ct);
        foreach (var file in uploaded)
        {
            progress.Report(new("上传", file.Name));
            await Remote.RunAsync("scp", ["-q", "-B", Path.Combine(objects, file.Sha256 + ".jar"), "homo:" + remote + "/objects/" + file.Sha256 + ".jar"], null, ct);
        }
        using var resource = typeof(Publisher).Assembly.GetManifestResourceStream("YumisModSync.CommitRelease.py")!;
        using var reader = new StreamReader(resource, Encoding.UTF8);
        var script = await reader.ReadToEndAsync(ct);
        progress.Report(new("发布", "服务器校验后原子切换清单"));
        await Remote.RunAsync("ssh", ["-o", "BatchMode=yes", "homo", "sudo -n python3 - " + release.ReleaseId], script, ct);
        return new(release.ReleaseId, release.Files.Count, release.Files.Count(f => f.Sources.Any(s => s.Kind == "modrinth")),
            release.Files.Count(f => f.Sources.Any(s => s.Kind == "curseforge")), uploaded.Count, Path.Combine(staging, "current.json"));
    }
}

public static class Remote
{
    public static async Task<string> RunAsync(string command, IReadOnlyList<string> arguments, string? input, CancellationToken ct)
    {
        var path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows), "System32", "OpenSSH", command + ".exe");
        var start = new ProcessStartInfo(path) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardInput = true,
            RedirectStandardError = true, RedirectStandardOutput = true, StandardInputEncoding = new UTF8Encoding(false),
            StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8 };
        foreach (var arg in arguments) start.ArgumentList.Add(arg);
        using var process = Process.Start(start) ?? throw new IOException("无法启动本机 OpenSSH。");
        var stdout = process.StandardOutput.ReadToEndAsync(ct); var stderr = process.StandardError.ReadToEndAsync(ct);
        try
        {
            if (input != null) await process.StandardInput.WriteAsync(input.AsMemory(), ct);
            process.StandardInput.Close();
            await process.WaitForExitAsync(ct);
            var output = await stdout; var error = await stderr;
            if (process.ExitCode != 0) throw new IOException($"{command} 失败：{error.Trim()}");
            return output;
        }
        finally { if (!process.HasExited) process.Kill(false); }
    }
}
