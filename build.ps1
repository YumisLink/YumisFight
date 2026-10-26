param([string[]]$Tasks=@('build'), [switch]$Offline, [switch]$NoExport)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
function Test-Java25([string]$Path) {
    if (-not $Path -or -not (Test-Path -LiteralPath (Join-Path $Path 'release'))) { return $false }
    return [bool](Select-String -LiteralPath (Join-Path $Path 'release') -Pattern '^JAVA_VERSION="25[."]')
}
if (-not (Test-Java25 $env:JAVA_HOME)) {
    $taskJdk=Join-Path (Split-Path -Parent $PSScriptRoot) 'YumisSkyIsland\.tools\jdk-25.0.4.1+1'
    if (Test-Java25 $taskJdk) { $env:JAVA_HOME=$taskJdk }
    else { throw '需要 Java 25 JDK；请把 JAVA_HOME 指向其安装目录。' }
}
$gradleCommand=Get-Command gradle -ErrorAction SilentlyContinue
$taskGradle=Join-Path (Split-Path -Parent $PSScriptRoot) 'YumisSkyIsland\.tools\gradle-9.5.1\bin\gradle.bat'
if ($gradleCommand) { $runner=$gradleCommand.Source }
elseif (Test-Path -LiteralPath $taskGradle) { $runner=$taskGradle }
else { $runner=Join-Path $PSScriptRoot 'gradlew.bat' }
$arguments=@('--project-cache-dir','temp/gradle')
if ($Offline) { $arguments+='--offline' }
& $runner @arguments @Tasks
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (-not $NoExport -and ($Tasks -contains 'build' -or $Tasks -contains 'jar')) {
    $destination=Join-Path $PSScriptRoot 'dist'
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'temp/build/libs') -Filter '*.jar' | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $destination -Force
    }
    Get-ChildItem -LiteralPath $destination -Filter '*.jar' | Sort-Object Name | ForEach-Object {
        $hash=Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        '{0}  {1}' -f $hash.Hash.ToLowerInvariant(),$_.Name
    } | Set-Content -LiteralPath (Join-Path $destination 'SHA256SUMS.txt') -Encoding utf8
    Write-Output "JAR 与 SHA-256 已导出到 $destination"
}
