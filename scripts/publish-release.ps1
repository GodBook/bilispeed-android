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
Push-Location $projectRoot
try {
    if (& git status --porcelain) { throw 'Commit and push the source changes before publishing.' }
    $commit = & git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the release commit.' }
    $arguments = @('release', 'create', "v$versionName", $apkPath, $manifestPath,
        (Join-Path $outputFolder 'SHA256SUMS.txt'), '--repo', $repository, '--target', $commit,
        '--title', "BiliSpeed $versionName", '--notes-file', (Join-Path $projectRoot 'release-notes.md'))
    if ($Draft) { $arguments += '--draft' }
    else { $arguments += @('--latest', '--verify-tag') }
    # Published releases are immutable in this script: it never replaces existing assets.
    & gh @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Could not publish the GitHub Release.' }
} finally {
    Pop-Location
}
