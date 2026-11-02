param([string]$SshHost = 'homo')
$ErrorActionPreference = 'Stop'
if ($SshHost -notmatch '^[A-Za-z0-9._-]+$') { throw '无效的 SSH 别名。' }
$taskRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'temp\mod-sync'
$private = Join-Path $taskRoot 'private'
New-Item -ItemType Directory -Path $private -Force | Out-Null
$profilePath = Join-Path $private 'profile.json'
if (Test-Path -LiteralPath $profilePath) {
    $profile = Get-Content -LiteralPath $profilePath -Raw -Encoding UTF8 | ConvertFrom-Json
} else {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $profile = [ordered]@{
        baseUrl = 'https://119.91.113.235/yumis-mods/'
        publicKeySha256 = ''
        username = 'mods'
        password = [Convert]::ToBase64String($bytes).TrimEnd('=')
        pclSha256 = '30eb93c62fc51ede0898648e67455478a3d5a4f279938ae65aec77b84cb9703f'
    }
    [System.IO.File]::WriteAllText($profilePath, ($profile | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
}
# Never pass the read-only password in process arguments or print it.
$password64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($profile.password))
$script = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'server\bootstrap.sh') -Raw -Encoding UTF8
$script = $script.Replace('__PASSWORD_BASE64__', $password64).Replace("`r`n", "`n")
$start = [System.Diagnostics.ProcessStartInfo]::new()
$start.FileName = 'C:\Windows\System32\OpenSSH\ssh.exe'
$start.Arguments = "-o BatchMode=yes -o ConnectTimeout=15 $SshHost sudo -n bash -s"
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardInput = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$start.StandardInputEncoding = [System.Text.UTF8Encoding]::new($false)
$process = [System.Diagnostics.Process]::Start($start)
$outTask = $process.StandardOutput.ReadToEndAsync()
$errTask = $process.StandardError.ReadToEndAsync()
$process.StandardInput.Write($script)
$process.StandardInput.Close()
$process.WaitForExit()
$stdout = $outTask.Result
$stderr = $errTask.Result
if ($process.ExitCode -ne 0) { throw "服务器配置失败：$stderr" }
$pin = ($stdout.Trim() -split "`n")[-1].Trim()
if ($pin -notmatch '^[a-f0-9]{64}$') { throw '服务器没有返回有效的公钥摘要。' }
$profile.publicKeySha256 = $pin
[System.IO.File]::WriteAllText($profilePath, ($profile | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
$process.Dispose()
Write-Output '独立 HTTPS 入口已配置；私有构建配置保存在 temp/mod-sync/private/profile.json。'
