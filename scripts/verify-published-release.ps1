param([string]$AndroidSdk = 'D:\dev\android-sdk')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$properties = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'version.properties') | ConvertFrom-StringData
$version = $properties.versionName
$repository = $properties.repository
if ($version -notmatch '^\d+\.\d+\.\d+$' -or $repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Invalid release identity.' }
$artifacts = Join-Path $projectRoot 'artifacts'
$destination = Join-Path $artifacts "published-$version"
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$headers = @{ 'User-Agent' = 'BiliSpeed-release-verification'; Accept = 'application/vnd.github+json' }
# Deliberately anonymous: this verifies the channel available to app users.
$release = Invoke-RestMethod "https://api.github.com/repos/$repository/releases/tags/v$version" -Headers $headers
$latest = Invoke-RestMethod "https://api.github.com/repos/$repository/releases/latest" -Headers $headers
if ($release.draft -or $release.prerelease -or $release.tag_name -cne "v$version" -or $latest.tag_name -cne "v$version") { throw 'The formal release is not the latest public version.' }
$apkName = "BiliSpeed-$version-Android16.apk"
$names = @($apkName, 'update.json', 'SHA256SUMS.txt')
if ($release.assets.Count -ne 3) { throw 'Expected exactly three release assets.' }
$checks = foreach ($name in $names) {
    $asset = @($release.assets | Where-Object name -CEQ $name)
    if ($asset.Count -ne 1) { throw "Missing or duplicate asset: $name" }
    $source = Join-Path $artifacts $name
    $download = Join-Path $destination $name
    $expected = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($asset[0].digest -cne "sha256:$expected") { throw "GitHub asset digest differs: $name" }
    $url = "https://github.com/$repository/releases/download/v$version/$name"
    Invoke-WebRequest $url -OutFile $download -Headers @{ 'User-Agent' = 'BiliSpeed-release-verification' }
    $actual = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -cne $expected -or (Get-Item -LiteralPath $download).Length -ne $asset[0].size) { throw "Anonymous download differs: $name" }
    [ordered]@{ name = $name; size = $asset[0].size; sha256 = $actual; url = $url }
}
$manifestPath = Join-Path $destination 'update.json'
$manifest = Get-Content -Raw -LiteralPath $manifestPath -Encoding UTF8 | ConvertFrom-Json
if ($manifest.versionName -cne $version -or $manifest.versionCode -ne [int]$properties.versionCode -or $manifest.packageName -cne 'app.bilispeed.browser' -or $manifest.sha256 -cne $checks[0].sha256 -or $manifest.size -ne $checks[0].size -or $manifest.apkUrl -cne $checks[0].url) { throw 'Public manifest does not describe the verified APK.' }
if ($manifest.schemaVersion -ne 1 -or $manifest.minSdk -ne 26 -or (Get-Content -Raw -LiteralPath (Join-Path $destination 'SHA256SUMS.txt') -Encoding UTF8).Trim() -cne "$($checks[0].sha256)  $apkName") { throw 'Public schema or checksum file differs.' }
$latestManifestPath = Join-Path $destination 'latest-update.json'
Invoke-WebRequest "https://github.com/$repository/releases/latest/download/update.json" -OutFile $latestManifestPath
if ((Get-FileHash -LiteralPath $latestManifestPath).Hash -cne (Get-FileHash -LiteralPath $manifestPath).Hash) { throw 'Latest channel differs from the tagged manifest.' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$signature = @(& (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs (Join-Path $destination $apkName))
if ($LASTEXITCODE -ne 0 -or ($signature -join "`n") -notmatch '(?i)e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3') { throw 'Public APK signature differs from the original signing identity.' }
$evidence = [ordered]@{ verifiedAt = (Get-Date).ToString('o'); tag = $release.tag_name; latest = $latest.tag_name; url = $release.html_url; target = $release.target_commitish; assets = @($checks); signature = $signature }
$evidence | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $artifacts "device-$version-public-verification.json") -Encoding utf8
$release | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $artifacts "device-$version-github-release.json") -Encoding utf8
Write-Output "Verified public release, latest channel, three asset hashes and original signature: $($release.html_url)"
