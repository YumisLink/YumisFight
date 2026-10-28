# 底层架构文件：提交与上传清单检查。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。
[CmdletBinding()]
param(
    [ValidateSet('Worktree','Staged','Range','Package')][string]$Mode='Staged',
    [string]$Base,
    [string]$Head='HEAD',
    [string]$PackageRoot,
    [string]$ApprovalFile
)
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $repoRoot
try {
    function GitLines([string[]]$GitArgs) {
        $result=@(& git -c core.quotepath=false @GitArgs)
        if($LASTEXITCODE -ne 0){throw "Git inspection failed: $GitArgs"}
        return $result
    }
    function IsTemporary([string]$Path) {
        return $Path -match '(^|/)(temp|dist|build|run|logs|screenshots|\.gradle|__pycache__|\.pytest_cache|test-results|test-reports)(/|$)|(^|/)art/previews(/|$)|(^|/)doc/本机环境\.md$|\.(log|tmp|pyc|bak|orig|rej)$'
    }
    function IsTest([string]$Path) {
        return $Path -match '(^|/)(src/test|fixtures|testdata|tests|verification)(/|$)|(^|/)(test_[^/]*|[^/]*_test)\.py$|(^|/)[^/]*(Test|Verification|VerificationDriver)[^/]*\.java$|(^|/)tools/verify_[^/]*\.py$'
    }
    $approved=$null
    if($ApprovalFile) {
        $approvalPath=(Resolve-Path -LiteralPath $ApprovalFile).Path
        $approved=Get-Content -LiteralPath $approvalPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if(-not $approved.userConfirmed){throw 'Approval record must represent an actual user confirmation.'}
    }
    $protected=(Get-Content -LiteralPath 'architecture/protected-files.json' -Raw -Encoding UTF8 | ConvertFrom-Json).files
    $failures=[Collections.Generic.List[string]]::new()
    $scope=switch($Mode){'Worktree'{'stage'} 'Staged'{'commit'} default{'upload'}}
    $files=@()
    if($Mode -eq 'Worktree') {
        $files=@(GitLines @('diff','--name-only','HEAD'))+@(GitLines @('ls-files','--others','--exclude-standard'))
    } elseif($Mode -eq 'Staged') {
        $files=@(GitLines @('diff','--cached','--name-only'))
    } elseif($Mode -eq 'Range') {
        if(-not $Base){throw 'Range inspection requires an explicit Base revision.'}
        $files=@(GitLines @('diff','--name-only',$Base,$Head))
    } else {
        if(-not $PackageRoot){throw 'Package inspection requires an explicit PackageRoot.'}
        $packagePath=(Resolve-Path -LiteralPath $PackageRoot).Path
        if(-not (Test-Path -LiteralPath $packagePath -PathType Container)){throw 'PackageRoot must be an unpacked directory; inspect archive contents before upload.'}
        $files=@(Get-ChildItem -LiteralPath $packagePath -File -Recurse | ForEach-Object {$_.FullName.Substring($packagePath.Length+1).Replace('\','/')})
    }
    $files=@($files | Sort-Object -Unique)
    foreach($path in $files) {
        $exists=$true
        if($Mode -eq 'Worktree'){$exists=Test-Path -LiteralPath $path}
        elseif($Mode -eq 'Staged') {& git cat-file -e (':'+$path) 2>$null;$exists=$LASTEXITCODE -eq 0}
        elseif($Mode -eq 'Range') {& git cat-file -e ($Head+':'+$path) 2>$null;$exists=$LASTEXITCODE -eq 0}
        if($exists -and (IsTemporary $path)){$failures.Add("Temporary artifact is forbidden: $path")}
        $blob='deleted'
        if($exists) {
            if($Mode -eq 'Worktree'){$blob=(GitLines @('hash-object','--path',$path,$path)) -join ''}
            elseif($Mode -eq 'Staged'){$blob=(GitLines @('rev-parse',(':'+$path))) -join ''}
            elseif($Mode -eq 'Range'){$blob=(GitLines @('rev-parse',($Head+':'+$path))) -join ''}
            else{$blob=(Get-FileHash -LiteralPath (Join-Path $packagePath $path) -Algorithm SHA256).Hash.ToLowerInvariant()}
        }
        if(IsTest $path) {
            $entry=@($approved.tests | Where-Object {$_.path -ceq $path -and $_.blob -ceq $blob -and $_.actions -contains $scope})
            if($entry.Count -ne 1){$failures.Add("Test requires user approval for $scope (blob $blob): $path")}
        }
        if($protected -contains $path) {
            if(-not $approved.architecture.fetchConfirmed -or -not ($approved.architecture.files -contains $path)){
                $failures.Add("Architecture change needs explicit authorization and batch fetch confirmation: $path")
            }
        }
    }
    if($Mode -eq 'Staged' -and $files.Count -gt 0) {
        $environmentText=Get-Content -LiteralPath 'doc/本机环境.md' -Raw -Encoding UTF8
        $aliasMatch=[regex]::Match($environmentText,'(?m)^电脑代号：([a-z0-9-]+)\r?$')
        if(-not $aliasMatch.Success){$failures.Add('Missing local computer alias.')}
        else {
            $added=@(GitLines @('diff','--cached','--name-only','--diff-filter=A'))
            $prefix='playerread/更新/'+$aliasMatch.Groups[1].Value+'/'
            if(-not @($added | Where-Object {$_.StartsWith($prefix) -and $_ -match '/\d{14}-.+\.md$'}).Count){$failures.Add('Each commit needs a new announcement in this computer directory.')}
        }
    }
    if($Mode -eq 'Range') {
        foreach($commit in @(GitLines @('rev-list','--reverse',($Base+'..'+$Head)))) {
            $added=@(GitLines @('diff-tree','--root','--no-commit-id','--name-only','--diff-filter=A','-r',$commit))
            if(-not @($added | Where-Object {$_ -match '^playerread/更新/[a-z0-9-]+/\d{14}-.+\.md$'}).Count){$failures.Add("Commit lacks a computer announcement: $commit")}
            # Inspect every commit, including temporary/test files added and deleted within the range.
            foreach($changed in @(GitLines @('diff-tree','--root','--no-commit-id','--name-only','--diff-filter=ACMR','-r',$commit))) {
                if(IsTemporary $changed){$failures.Add("Commit contains a temporary artifact: $commit $changed")}
                if(IsTest $changed) {
                    $hash=(GitLines @('rev-parse',($commit+':'+$changed))) -join ''
                    if(-not @($approved.tests | Where-Object {$_.path -ceq $changed -and $_.blob -ceq $hash -and $_.actions -contains 'upload'}).Count){$failures.Add("Test blob in history requires upload approval: $commit $changed")}
                }
            }
        }
    }
    if($failures.Count){$failures | ForEach-Object {Write-Output $_};exit 1}
    Write-Output ("Submission inspection passed: "+$Mode+", "+$files.Count+" changed files. This does not stage, commit or upload anything.")
} finally {Pop-Location}

