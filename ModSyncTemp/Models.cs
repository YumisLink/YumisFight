using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace YumisModSync;

public sealed record ServerProfile(string BaseUrl, string PublicKeySha256, string Username, string Password, string PclSha256)
{
    public static ServerProfile Load()
    {
        using var stream = typeof(ServerProfile).Assembly.GetManifestResourceStream("YumisModSync.Profile.json")!;
        var profile = JsonSerializer.Deserialize<ServerProfile>(stream, Json.Options) ?? throw new InvalidDataException("缺少服务器配置。");
        if (!Uri.TryCreate(profile.BaseUrl, UriKind.Absolute, out var uri) || uri.Scheme != "https" ||
            !Hashing.ValidHex(profile.PublicKeySha256, 64) || !Hashing.ValidHex(profile.PclSha256, 64) || string.IsNullOrEmpty(profile.Password))
            throw new InvalidDataException("服务器配置无效，请重新构建发布包。");
        return profile;
    }
}

public sealed class ReleaseManifest
{
    public int SchemaVersion { get; set; } = 1;
    public string ReleaseId { get; set; } = "";
    public DateTimeOffset PublishedAt { get; set; }
    public string Minecraft { get; set; } = "26.2";
    public string NeoForge { get; set; } = "26.2.0.93";
    public List<ModFile> Files { get; set; } = [];

    public void Validate(ServerProfile profile)
    {
        if (SchemaVersion != 1 || ReleaseId.Length is < 1 or > 128 ||
            Files.Count is < 1 or > 2048 || Minecraft.Length is < 1 or > 64 || NeoForge.Length is < 1 or > 64)
            throw new InvalidDataException("发布清单格式或版本要求无效。");
        var names = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        long total = 0;
        foreach (var file in Files)
        {
            Paths.ValidateJarName(file.Name);
            if (!names.Add(file.Name) || file.Size is < 1 or > 2L * 1024 * 1024 * 1024 ||
                !Hashing.ValidHex(file.Sha256, 64) || !Hashing.ValidHex(file.Sha1, 40) || !Hashing.ValidHex(file.Sha512, 128))
                throw new InvalidDataException($"文件记录无效：{file.Name}");
            total = checked(total + file.Size);
            if (total > 16L * 1024 * 1024 * 1024 || file.Sources.Count is < 1 or > 12)
                throw new InvalidDataException("清单下载量或来源数量无效。");
            foreach (var source in file.Sources)
                if (!Sources.Valid(source, profile, file)) throw new InvalidDataException($"来源无效：{file.Name}");
            if (file.Sources.Any(s => s.Kind != "server") && file.Sources.Any(s => s.Kind == "server"))
                throw new InvalidDataException("具有外部来源的文件不能同时声明服务器副本。");
        }
    }
}

public sealed class ModFile
{
    public string Name { get; set; } = "";
    public long Size { get; set; }
    public string Sha256 { get; set; } = "";
    public string Sha1 { get; set; } = "";
    public string Sha512 { get; set; } = "";
    public List<DownloadSource> Sources { get; set; } = [];
    public string SourceStatus { get; set; } = "";
}

public sealed record DownloadSource(string Kind, string Url);
public sealed record GameInstance(string Name, string Directory, string ModsDirectory);
public sealed record SyncProgress(string Stage, string Detail, double? Percent = null);
public sealed class ActionProgress(Action<SyncProgress> report) : IProgress<SyncProgress>
{
    public void Report(SyncProgress value) => report(value);
}

public static class Sources
{
    public static string? ExternalKind(string value)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) || uri.Scheme != "https" || uri.UserInfo != "") return null;
        if (uri.Host.Equals("cdn.modrinth.com", StringComparison.OrdinalIgnoreCase)) return "modrinth";
        if (uri.Host.Equals("forgecdn.net", StringComparison.OrdinalIgnoreCase) || uri.Host.EndsWith(".forgecdn.net", StringComparison.OrdinalIgnoreCase)) return "curseforge";
        return null;
    }

    public static bool Valid(DownloadSource source, ServerProfile profile, ModFile file)
    {
        if (source.Kind is "modrinth" or "curseforge") return ExternalKind(source.Url) == source.Kind;
        return source.Kind == "server" && source.Url == new Uri(new Uri(profile.BaseUrl), $"objects/{file.Sha256}.jar").AbsoluteUri;
    }
}

public static class Json
{
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true,
        WriteIndented = true,
        MaxDepth = 24,
        Converters = { new JsonStringEnumConverter() }
    };
    public static T Read<T>(string path) => JsonSerializer.Deserialize<T>(File.ReadAllText(path, Encoding.UTF8), Options)
        ?? throw new InvalidDataException($"无法读取 {Path.GetFileName(path)}");
    public static void Write<T>(string path, T value)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temporary = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        using (var stream = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None, 4096, FileOptions.WriteThrough))
        {
            JsonSerializer.Serialize(stream, value, Options);
            stream.Flush(true);
        }
        Paths.MoveDurably(temporary, path, true);
    }
}

public static class Hashing
{
    public static bool ValidHex(string value, int count) => value.Length == count && value.All(c => c is >= '0' and <= '9' or >= 'a' and <= 'f');
    public static string Text(string text) => Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes(text)));
    public static async Task<ModFile> DescribeAsync(string path, CancellationToken ct = default)
    {
        using var sha1 = IncrementalHash.CreateHash(HashAlgorithmName.SHA1);
        using var sha256 = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        using var sha512 = IncrementalHash.CreateHash(HashAlgorithmName.SHA512);
        await using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read, 131072, true);
        long size = stream.Length;
        byte[] buffer = new byte[131072];
        int read;
        while ((read = await stream.ReadAsync(buffer, ct)) != 0)
        {
            sha1.AppendData(buffer, 0, read); sha256.AppendData(buffer, 0, read); sha512.AppendData(buffer, 0, read);
        }
        return new ModFile { Name = Path.GetFileName(path), Size = size,
            Sha1 = Convert.ToHexStringLower(sha1.GetHashAndReset()), Sha256 = Convert.ToHexStringLower(sha256.GetHashAndReset()),
            Sha512 = Convert.ToHexStringLower(sha512.GetHashAndReset()) };
    }

    public static async Task<bool> MatchesAsync(string path, ModFile expected, CancellationToken ct = default)
    {
        if (!File.Exists(path) || new FileInfo(path).Length != expected.Size) return false;
        await using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read, 131072, true);
        return Convert.ToHexStringLower(await SHA256.HashDataAsync(stream, ct)) == expected.Sha256;
    }
}
