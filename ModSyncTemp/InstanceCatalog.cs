using System.Text.Json;

namespace YumisModSync;

public static class InstanceCatalog
{
    public static List<GameInstance> Find(string pclRoot, ReleaseManifest release)
    {
        var root = Path.Combine(pclRoot, ".minecraft", "versions");
        var result = new List<GameInstance>();
        var excluded = new List<string>();
        if (!Directory.Exists(root)) return result;
        Paths.NoReparseAncestors(root, pclRoot);
        foreach (var directory in Directory.EnumerateDirectories(root))
        {
            if ((File.GetAttributes(directory) & FileAttributes.ReparsePoint) != 0) continue;
            var id = Path.GetFileName(directory);
            var path = Path.Combine(directory, id + ".json");
            if (!File.Exists(path)) continue;
            try
            {
                using var data = ReadMerged(root, id, new HashSet<string>(StringComparer.OrdinalIgnoreCase));
                var json = data.RootElement;
                var args = json.TryGetProperty("arguments", out var arguments) && arguments.TryGetProperty("game", out var game)
                    ? game.EnumerateArray().Where(x => x.ValueKind == JsonValueKind.String).Select(x => x.GetString()!).ToArray() : [];
                var mc = Parameter(args, "--fml.mcVersion") ?? String(json, "clientVersion") ?? String(json, "id");
                var forge = Parameter(args, "--fml.neoForgeVersion");
                if (forge == null && json.TryGetProperty("libraries", out var libraries))
                    forge = libraries.EnumerateArray().Select(l => String(l, "name")).FirstOrDefault(n => n?.StartsWith("net.neoforged:neoforge:") == true)?.Split(':')[2];
                if (mc != release.Minecraft || forge != release.NeoForge) continue;
                var setup = Path.Combine(directory, "PCL", "Setup.ini");
                if (!File.Exists(setup) || !File.ReadLines(setup).Any(line => line.Trim().Equals("VersionArgumentIndieV2:True", StringComparison.OrdinalIgnoreCase)))
                { excluded.Add(id + " 未开启版本隔离"); continue; }
                result.Add(new(id, directory, Path.Combine(directory, "mods")));
            }
            catch (JsonException) { }
            catch (Exception error) when (error is IOException or UnauthorizedAccessException) { excluded.Add(id + " 的版本信息不可读"); }
        }
        if (result.Count == 0 && excluded.Count != 0) throw new IOException("未找到可同步实例：" + string.Join("；", excluded));
        return result;
    }

    private static string? Parameter(string[] args, string key)
    {
        var index = Array.IndexOf(args, key); return index >= 0 && index + 1 < args.Length ? args[index + 1] : null;
    }
    private static string? String(JsonElement e, string key) => e.TryGetProperty(key, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() : null;

    private static JsonDocument ReadMerged(string root, string id, HashSet<string> visited)
    {
        if (id != Path.GetFileName(id) || id.Contains('/') || id.Contains('\\') || !visited.Add(id) || visited.Count > 8) throw new JsonException("版本继承无效。");
        var file = Path.Combine(root, id, id + ".json");
        var node = System.Text.Json.Nodes.JsonNode.Parse(File.ReadAllText(file))!.AsObject();
        if (node["inheritsFrom"]?.GetValue<string>() is { } parent)
        {
            using var baseDocument = ReadMerged(root, parent, visited);
            var merged = System.Text.Json.Nodes.JsonNode.Parse(baseDocument.RootElement.GetRawText())!.AsObject();
            foreach (var property in node) merged[property.Key] = property.Value?.DeepClone();
            node = merged;
        }
        return JsonDocument.Parse(node.ToJsonString());
    }
}
