$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$mods = 'C:\Users\63569\Desktop\哼，没有机械之魂的家伙！\.minecraft\versions\26.2-NeoForge_26.2.0.93\mods'
$jarName = 'YumisDungeonAndFight-0.1.0.jar'
$jar = Join-Path $repo "temp\build\libs\$jarName"
$publisher = Join-Path $repo 'temp\mod-sync\publish\YumisModSync.exe'
$work = Join-Path $repo 'temp\one-click-release'
$report = Join-Path $work 'report.json'
$remoteTemp = '/tmp/yumis-fight-' + [guid]::NewGuid().ToString('N') + '.jar'

try {
    if (-not (Test-Path -LiteralPath $mods -PathType Container)) { throw "Mods directory not found: $mods" }
    if (-not (Test-Path -LiteralPath $publisher -PathType Leaf)) { throw "Publisher not found: $publisher. Run ModSyncTemp/build.ps1 first." }
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    Write-Host '[1/4] Building JAR...'
    & (Join-Path $repo 'build.ps1') -Tasks jar -Offline -NoExport
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "JAR not found: $jar" }
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
    Copy-Item -LiteralPath $jar -Destination (Join-Path $mods $jarName) -Force
    if ((Get-FileHash -LiteralPath (Join-Path $mods $jarName) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) { throw 'Local JAR verification failed.' }

    Write-Host '[2/4] Uploading JAR to game server...'
    & scp -q -B $jar "homo:$remoteTemp"
    if ($LASTEXITCODE -ne 0) { throw 'JAR upload failed.' }
    $remote = @"
set -eu
trap 'rm -f $remoteTemp $remoteTemp.marker' EXIT
echo '$hash  $remoteTemp' | sha256sum -c -
sudo -n systemctl stop yumis-minecraft
touch $remoteTemp.marker
if ! sudo -n install -o yumis-minecraft -g yumis-minecraft -m 644 $remoteTemp /srv/yumis-minecraft/mods/$jarName; then
  sudo -n systemctl start yumis-minecraft || true
  exit 1
fi
sudo -n systemctl start yumis-minecraft
sudo -n sha256sum /srv/yumis-minecraft/mods/$jarName
for i in `$(seq 1 60); do
  if ! systemctl is-active --quiet yumis-minecraft; then exit 1; fi
  if sudo -n test /srv/yumis-minecraft/logs/latest.log -nt $remoteTemp.marker && sudo -n grep -q 'Done (' /srv/yumis-minecraft/logs/latest.log; then
    sudo -n grep 'Done (' /srv/yumis-minecraft/logs/latest.log | tail -n 1
    exit 0
  fi
  sleep 2
done
echo 'Server startup was not confirmed within 120 seconds.' >&2
exit 1
"@
    Write-Host '[3/4] Restarting game server and checking startup...'
    & ssh -o BatchMode=yes homo $remote
    if ($LASTEXITCODE -ne 0) { throw 'Server update or startup check failed. Check systemctl status and latest.log.' }

    Write-Host '[4/4] Publishing the complete local mods list...'
    Remove-Item -LiteralPath $report,"$report.log","$report.error" -Force -ErrorAction SilentlyContinue
    $arguments = @('--publish', '--headless', '--source', "`"$mods`"", '--output', "`"$work`"", '--report', "`"$report`"")
    $process = Start-Process -FilePath $publisher -ArgumentList $arguments -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        if (Test-Path -LiteralPath "$report.error") { Get-Content -LiteralPath "$report.error" | Write-Host }
        throw "Mods publication failed: $($process.ExitCode). The game server may already be updated."
    }
    Get-Content -LiteralPath $report
    Write-Host "Completed. JAR SHA-256: $hash"
} catch {
    Write-Error $_
    exit 1
}
