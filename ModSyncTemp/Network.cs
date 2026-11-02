using System.Net;
using System.Net.Http;
using System.Net.Security;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;

namespace YumisModSync;

public sealed class Network : IDisposable
{
    private readonly ServerProfile profile;
    private readonly HttpClient server;
    public HttpClient External { get; }

    public Network(ServerProfile profile)
    {
        this.profile = profile;
        var handler = new HttpClientHandler { AllowAutoRedirect = false };
        handler.ServerCertificateCustomValidationCallback = (_, certificate, _, errors) =>
            VerifyPin(certificate, errors, profile.PublicKeySha256);
        server = new HttpClient(handler) { Timeout = Timeout.InfiniteTimeSpan };
        server.DefaultRequestHeaders.Authorization = new("Basic", Convert.ToBase64String(Encoding.UTF8.GetBytes(profile.Username + ":" + profile.Password)));
        server.DefaultRequestHeaders.UserAgent.ParseAdd("YumisModSync/1.0");
        External = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false }) { Timeout = Timeout.InfiniteTimeSpan };
        External.DefaultRequestHeaders.UserAgent.ParseAdd("YumisModSync/1.0");
    }

    public static bool VerifyPin(X509Certificate2? cert, SslPolicyErrors errors, string expected)
    {
        if (cert == null || (errors & (SslPolicyErrors.RemoteCertificateNameMismatch | SslPolicyErrors.RemoteCertificateNotAvailable)) != 0 ||
            DateTime.UtcNow < cert.NotBefore.ToUniversalTime() || DateTime.UtcNow > cert.NotAfter.ToUniversalTime()) return false;
        using var rsa = cert.GetRSAPublicKey();
        using var ec = cert.GetECDsaPublicKey();
        var key = rsa?.ExportSubjectPublicKeyInfo() ?? ec?.ExportSubjectPublicKeyInfo();
        return key != null && CryptographicOperations.FixedTimeEquals(SHA256.HashData(key), Convert.FromHexString(expected));
    }

    public async Task<ReleaseManifest> GetReleaseAsync(CancellationToken ct)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct); timeout.CancelAfter(TimeSpan.FromSeconds(30));
        using var response = await server.GetAsync(new Uri(new Uri(profile.BaseUrl), "current.json"), HttpCompletionOption.ResponseHeadersRead, timeout.Token);
        response.EnsureSuccessStatusCode();
        await using var stream = await response.Content.ReadAsStreamAsync(timeout.Token);
        using var data = new MemoryStream(); byte[] buffer = new byte[8192]; int read;
        while ((read = await stream.ReadAsync(buffer, timeout.Token)) != 0)
        {
            if (data.Length + read > 2 * 1024 * 1024) throw new InvalidDataException("服务器清单过大。");
            data.Write(buffer, 0, read);
        }
        var manifest = JsonSerializer.Deserialize<ReleaseManifest>(data.ToArray(), Json.Options) ?? throw new InvalidDataException("服务器清单为空。");
        manifest.Validate(profile); return manifest;
    }

    public async Task DownloadAsync(DownloadSource source, ModFile file, string destination,
        IProgress<SyncProgress> progress, CancellationToken ct)
    {
        if (!Sources.Valid(source, profile, file)) throw new InvalidDataException("下载来源未通过校验。");
        Exception? last = null;
        for (var attempt = 0; attempt < 2; attempt++)
        {
            ct.ThrowIfCancellationRequested();
            var part = destination + "." + Guid.NewGuid().ToString("N") + ".part";
            try
            {
                using var overall = CancellationTokenSource.CreateLinkedTokenSource(ct); overall.CancelAfter(TimeSpan.FromMinutes(15));
                var client = source.Kind == "server" ? server : External;
                var uri = new Uri(source.Url);
                HttpResponseMessage? response = null;
                try
                {
                    for (var redirect = 0; redirect <= 3; redirect++)
                    {
                        response = await client.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, overall.Token);
                        if ((int)response.StatusCode is < 300 or > 399) break;
                        var next = response.Headers.Location;
                        if (source.Kind == "server" || next == null) throw new HttpRequestException("拒绝服务器重定向。");
                        uri = next.IsAbsoluteUri ? next : new Uri(uri, next);
                        if (Sources.ExternalKind(uri.AbsoluteUri) != source.Kind) throw new HttpRequestException("下载重定向到了未批准的来源。");
                        response.Dispose(); response = null;
                    }
                    if (response == null) throw new HttpRequestException("下载重定向过多。");
                    response.EnsureSuccessStatusCode();
                    if (response.Content.Headers.ContentLength is { } length && length != file.Size) throw new InvalidDataException("下载文件大小与清单不一致。");
                    Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
                    await using var input = await response.Content.ReadAsStreamAsync(overall.Token);
                    await using (var output = new FileStream(part, FileMode.CreateNew, FileAccess.Write, FileShare.None, 131072, true))
                    {
                        byte[] buffer = new byte[131072]; long received = 0; var lastUpdate = DateTime.UtcNow;
                        while (true)
                        {
                            using var stall = CancellationTokenSource.CreateLinkedTokenSource(overall.Token); stall.CancelAfter(TimeSpan.FromSeconds(40));
                            var read = await input.ReadAsync(buffer, stall.Token); if (read == 0) break;
                            received += read;
                            if (received > file.Size) throw new InvalidDataException("下载超出清单大小。");
                            await output.WriteAsync(buffer.AsMemory(0, read), overall.Token);
                            if ((DateTime.UtcNow - lastUpdate).TotalMilliseconds > 150)
                            {
                                progress.Report(new("下载", $"{file.Name} · {source.Kind} · {received / 1048576d:F1}/{file.Size / 1048576d:F1} MiB", received * 100d / file.Size));
                                lastUpdate = DateTime.UtcNow;
                            }
                        }
                        await output.FlushAsync(overall.Token); output.Flush(true);
                    }
                    if (!await Hashing.MatchesAsync(part, file, overall.Token)) throw new InvalidDataException("下载内容的 SHA-256 与清单不一致。");
                    File.Move(part, destination, true); return;
                }
                finally { response?.Dispose(); }
            }
            catch (Exception error) when (!ct.IsCancellationRequested && error is HttpRequestException or IOException or InvalidDataException or OperationCanceledException)
            {
                last = error;
                if (attempt == 0) await Task.Delay(1000, ct);
            }
            finally { if (File.Exists(part)) File.Delete(part); }
        }
        throw new IOException($"下载失败：{file.Name}（{source.Kind}）。{last?.Message}", last);
    }

    public void Dispose() { server.Dispose(); External.Dispose(); }
}
