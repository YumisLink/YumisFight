param()
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
# Quick local export: offline jar, rename to metadata version, deploy to desktop mods.
# Formal export rule: bump version in src/main/resources/META-INF/neoforge.mods.toml BEFORE running this.
# 2026-11-26 user decision: exports no longer run :test (src/test only holds helpers, Gradle 9 fails on no discovered tests).
$libs=Join-Path $PSScriptRoot 'temp\build\libs'
if(Test-Path -LiteralPath $libs){
  Get-ChildItem -LiteralPath $libs -Filter 'YumisDungeonAndFight-*' | Remove-Item -Force
}
& .\build.ps1 -Tasks jar -Offline -NoExport
if($LASTEXITCODE -ne 0){ exit $LASTEXITCODE }
$jar=Get-ChildItem -LiteralPath $libs -Filter 'YumisDungeonAndFight-*.jar' |
  Where-Object { $_.Name -notmatch '-sources' } | Select-Object -First 1
if(-not $jar){ throw 'no jar produced in temp\build\libs' }
$meta=Select-String -LiteralPath (Join-Path $PSScriptRoot 'src\main\resources\META-INF\neoforge.mods.toml') -Pattern '^version="([^"]+)"' |
  Select-Object -First 1
if(-not $meta){ throw 'cannot read mod version from neoforge.mods.toml' }
$version=$meta.Matches[0].Groups[1].Value
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($jar.FullName)
try{
  $entry=$zip.GetEntry('META-INF/neoforge.mods.toml')
  $reader=New-Object IO.StreamReader($entry.Open())
  $text=$reader.ReadToEnd(); $reader.Close()
  if($text -notmatch [regex]::Escape('version="'+$version+'"')){ throw 'jar metadata version mismatch: '+$version }
}finally{ $zip.Dispose() }
$name='YumisDungeonAndFight-'+$version+'.jar'
$target=Join-Path $libs $name
Move-Item -LiteralPath $jar.FullName -Destination $target -Force
$mc=Get-ChildItem 'C:\Users\63569\Desktop' -Directory |
  Where-Object { Test-Path (Join-Path $_.FullName '.minecraft') } | Select-Object -First 1
if(-not $mc){ throw 'minecraft folder not found on Desktop' }
$mods=Join-Path $mc.FullName '.minecraft\versions\26.2-NeoForge_26.2.0.93\mods'
$destination=Join-Path $mods $name
if(Test-Path -LiteralPath $destination){
  Write-Output ('NOTE: destination already has '+$name+'; bump mods.toml if this should be a new formal export')
}
Copy-Item -LiteralPath $target -Destination $destination -Force
Get-ChildItem -LiteralPath $mods -Filter 'YumisDungeonAndFight-*.jar' |
  Where-Object { $_.Name -ne $name } | Remove-Item -Force
$hash=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output ('version: '+$version)
Write-Output ('deployed: '+$mods)
Write-Output ('sha256: '+$hash)
