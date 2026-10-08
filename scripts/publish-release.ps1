param([switch]$Draft)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$properties = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'version.properties') | ConvertFrom-StringData
$repository = $properties.repository
$versionName = $properties.versionName
$outputFolder = Join-Path $projectRoot 'artifacts'
$manifestPath = Join-Path $outputFolder 'update.json'
$manifest = Get-Content -Raw -LiteralPath $manifestPath -Encoding UTF8 | ConvertFrom-Json
$apkName = "BiliSpeed-$versionName-Android16.apk"
$apkPath = Join-Path $outputFolder $apkName
if ($manifest.versionCode -ne [int]$properties.versionCode -or $manifest.versionName -cne $versionName -or
        $manifest.packageName -cne 'app.bilispeed.browser' -or
        $manifest.apkUrl -cne "https://github.com/$repository/releases/download/v$versionName/$apkName") {
    throw 'The update manifest does not match version.properties. Build again first.'
}
$hash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hash -cne $manifest.sha256 -or (Get-Item -LiteralPath $apkPath).Length -ne $manifest.size) {
    throw 'The APK does not match its update manifest. Build again first.'
}
$checksumPath = Join-Path $outputFolder 'SHA256SUMS.txt'
if ((Get-Content -Raw -LiteralPath $checksumPath -Encoding UTF8).Trim() -cne "$hash  $apkName" -or
        $manifest.notes -cne [System.IO.File]::ReadAllText((Join-Path $projectRoot 'release-notes.md')).Trim() -or
        $manifest.schemaVersion -ne 1 -or $manifest.minSdk -ne 26) {
    throw 'Release metadata or notes changed after the build. Build again first.'
}
Push-Location $projectRoot
try {
    if (& git status --porcelain) { throw 'Commit and push the source changes before publishing.' }
    $commit = & git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the release commit.' }
    if (-not $Draft) {
        $tag = "refs/tags/v$versionName"
        $tagCommit = & git rev-parse "$tag^{commit}"
        if ($LASTEXITCODE -ne 0 -or $tagCommit -cne $commit) {
            throw 'The release tag must point to the current source commit.'
        }
        $tagObject = & git rev-parse $tag
        if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the local release tag.' }
        $remoteTag = & git ls-remote --exit-code origin $tag
        if ($LASTEXITCODE -ne 0 -or ($remoteTag -split '\s+')[0] -cne $tagObject) {
            throw 'Push the matching release tag and source commit before publishing.'
        }
    }
    $arguments = @('release', 'create', "v$versionName", $apkPath, $manifestPath,
        $checksumPath, '--repo', $repository, '--target', $commit,
        '--title', "BiliSpeed $versionName", '--notes-file', (Join-Path $projectRoot 'release-notes.md'))
    if ($Draft) { $arguments += '--draft' }
    else { $arguments += @('--latest', '--verify-tag') }
    # Published releases are immutable in this script: it never replaces existing assets.
    & gh @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Could not publish the GitHub Release.' }
} finally {
    Pop-Location
}
