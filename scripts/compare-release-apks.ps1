param(
    [Parameter(Mandatory)][string]$TestedApk,
    [Parameter(Mandatory)][string]$ReleaseApk
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Read-ZipHashes([string]$Path) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
    $hashes = [System.Collections.Generic.Dictionary[string,string]]::new([System.StringComparer]::Ordinal)
    try {
        foreach ($entry in $archive.Entries) {
            $stream = $entry.Open()
            $digest = [System.Security.Cryptography.SHA256]::Create()
            try { $hashes.Add($entry.FullName, [BitConverter]::ToString($digest.ComputeHash($stream)).Replace('-', '')) }
            finally { $digest.Dispose(); $stream.Dispose() }
        }
    } finally { $archive.Dispose() }
    return ,$hashes
}
$tested = Read-ZipHashes $TestedApk
$release = Read-ZipHashes $ReleaseApk
if ($tested.Count -ne $release.Count -or @(Compare-Object @($tested.Keys) @($release.Keys)).Count) { throw 'APK entry names differ.' }
$differences = @($tested.Keys | Where-Object { $tested[$_] -cne $release[$_] })
if (@($differences | Where-Object { $_ -cne 'META-INF/version-control-info.textproto' }).Count) { throw "APK contents differ beyond VCS metadata: $($differences -join ', ')" }
[ordered]@{ entries = $tested.Count; differentEntries = $differences; testedSha256 = (Get-FileHash -LiteralPath $TestedApk).Hash; releaseSha256 = (Get-FileHash -LiteralPath $ReleaseApk).Hash } | ConvertTo-Json
