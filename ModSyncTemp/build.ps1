param(
    [string]$ProfilePath,
    [string]$PclPath = 'C:\Users\63569\Desktop\哼，没有机械之魂的家伙！\Plain Craft Launcher 2.exe'
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$repoRoot = Split-Path -Parent $projectRoot
$taskRoot = Join-Path $repoRoot 'temp\mod-sync'
if (-not $ProfilePath) { $ProfilePath = Join-Path $taskRoot 'private\profile.json' }
if (-not (Test-Path -LiteralPath $ProfilePath)) { throw '先运行 setup-server.ps1，或提供已有的私有 ProfilePath。' }
$profile = Get-Content -LiteralPath $ProfilePath -Raw -Encoding UTF8 | ConvertFrom-Json
$pclInfo = [System.Diagnostics.FileVersionInfo]::GetVersionInfo($PclPath)
$pclHash = (Get-FileHash -LiteralPath $PclPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($pclInfo.FileVersion -ne '2.13.1.1' -or $pclHash -ne $profile.pclSha256) { throw '只允许内嵌经核实、未修改的官方 PCL 2.13.1.1。' }
$assets = Join-Path $taskRoot 'assets'
New-Item -ItemType Directory -Path $assets -Force | Out-Null
Copy-Item -LiteralPath $PclPath -Destination (Join-Path $assets 'PCL.exe') -Force
$licence = @'
本文件附带的 PCL.exe 为未经修改的官方 Plain Craft Launcher 2.13.1.1 原样副本。
作者：龙腾猫跃。版权所有：成都瓜皮龙科技有限公司。
官方下载：https://meloong.com/pcl
分发有限许可：https://shimo.im/docs/rGrd8pY8xWkt6ryW#anchor-X0bo
帮助：https://github.com/Meloong-Git/PCLHelp

Yumis ModSync 为独立的模组同步工具。PCL 官方未参与本工具开发。
请在 PCL 自己的首次使用界面阅读并决定是否接受其协议。
'@
[System.IO.File]::WriteAllText((Join-Path $assets 'PCL-LICENCE.txt'), $licence, [System.Text.UTF8Encoding]::new($false))
$publish = Join-Path $taskRoot 'publish'
& 'C:\Program Files\dotnet\dotnet.exe' publish (Join-Path $projectRoot 'YumisModSync.csproj') -c Release -r win-x64 --self-contained true -o $publish "-p:ProfilePath=$ProfilePath" "-p:AssetsRoot=$assets\"
if ($LASTEXITCODE -ne 0) { throw '同步器构建失败。' }
$exe = Join-Path $publish 'YumisModSync.exe'
Get-Item -LiteralPath $exe | Select-Object FullName,Length
