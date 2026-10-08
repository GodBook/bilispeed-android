param(
    [switch]$RunTests,
    [switch]$RunLiveCheck,
    [switch]$RunUpdateCheck,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$DeviceSerial = $env:ANDROID_SERIAL
)
$ErrorActionPreference = 'Stop'
if ($RunLiveCheck) { $RunTests = $true }
if ($RunUpdateCheck) { $RunTests = $true }
$projectRoot = Split-Path -Parent $PSScriptRoot
$releaseProperties = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'version.properties') | ConvertFrom-StringData
$versionName = $releaseProperties.versionName
$versionCode = [int]$releaseProperties.versionCode
$repository = $releaseProperties.repository
if ($versionName -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$' -or $versionCode -lt 1) {
    throw 'Invalid version.properties.'
}
if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
if (-not $AndroidSdk -or -not (Test-Path -LiteralPath (Join-Path $AndroidSdk 'platforms\android-36\android.jar'))) {
    throw 'Install Android SDK Platform 36 and set ANDROID_HOME first.'
}
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk
if ($RunTests) {
    if (-not $DeviceSerial) {
        $devices = & (Join-Path $AndroidSdk 'platform-tools\adb.exe') devices
        $availableDevices = @($devices | Where-Object { $_ -match '^\S+\s+device$' })
        if ($availableDevices.Count -ne 1) { throw 'Select the test device explicitly with -DeviceSerial (adb devices).' }
        $DeviceSerial = ($availableDevices[0] -split '\s+')[0]
    }
    $env:ANDROID_SERIAL = $DeviceSerial
    $booted = ([string](& (Join-Path $AndroidSdk 'platform-tools\adb.exe') -s $DeviceSerial shell getprop sys.boot_completed)).Trim()
    if ($booted -ne '1') { throw 'The selected test device has not finished booting. Wait for sys.boot_completed=1.' }
}
$signingFolder = Join-Path $projectRoot '.signing'
$keyFile = Join-Path $signingFolder 'bilispeed-release.jks'
$credentialFile = Join-Path $signingFolder 'password.clixml'
New-Item -ItemType Directory -Path $signingFolder -Force | Out-Null
if (Test-Path -LiteralPath $credentialFile) {
    $credential = Import-Clixml -LiteralPath $credentialFile
    $env:BILISPEED_SIGNING_PASSWORD = $credential.GetNetworkCredential().Password
} else {
    if (Test-Path -LiteralPath $keyFile) { throw 'The signing key exists but its encrypted password is missing. Restore password.clixml.' }
    $random = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($random)
    $generator.Dispose()
    $env:BILISPEED_SIGNING_PASSWORD = [Convert]::ToBase64String($random)
    $secure = ConvertTo-SecureString $env:BILISPEED_SIGNING_PASSWORD -AsPlainText -Force
    $credential = New-Object System.Management.Automation.PSCredential('bilispeed', $secure)
    $credential | Export-Clixml -LiteralPath $credentialFile
}
Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath $keyFile)) {
        & keytool -genkeypair -keystore $keyFile -storetype JKS -alias bilispeed `
            -keyalg RSA -keysize 3072 -validity 10000 `
            -storepass:env BILISPEED_SIGNING_PASSWORD -keypass:env BILISPEED_SIGNING_PASSWORD `
            -dname 'CN=BiliSpeed Personal Browser, O=BiliSpeed, C=CN'
        if ($LASTEXITCODE -ne 0) { throw 'Could not generate the signing key.' }
    }
    $tasks = @('assembleRelease', 'lintRelease')
    if ($RunTests) { $tasks += @('assembleDebug', 'assembleDebugAndroidTest') }
    & .\gradlew.bat @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build or validation failed.' }
    $outputFolder = Join-Path $projectRoot 'artifacts'
    New-Item -ItemType Directory -Path $outputFolder -Force | Out-Null
    $apkName = "BiliSpeed-$versionName-Android16.apk"
    $outputApk = Join-Path $outputFolder $apkName
    Copy-Item -LiteralPath 'app\build\outputs\apk\release\app-release.apk' -Destination $outputApk -Force
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    $apkSigner = Join-Path $buildTools.FullName 'apksigner.bat'
    if ($RunTests) {
        # Some Android SDK native tools mishandle non-ASCII project paths on Windows.
        $testFolder = Join-Path ([System.IO.Path]::GetTempPath()) ('bilispeed-test-' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $testFolder | Out-Null
        $testApp = Join-Path $testFolder 'app-release.apk'
        $testRunner = Join-Path $testFolder 'app-test.apk'
        $adb = Join-Path $AndroidSdk 'platform-tools\adb.exe'
        try {
            Copy-Item -LiteralPath 'app\build\outputs\apk\release\app-release.apk' -Destination $testApp
            Copy-Item -LiteralPath 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk' -Destination $testRunner
            # Match the production signing identity so instrumentation can exercise the release APK.
            & $apkSigner sign --ks $keyFile --ks-key-alias bilispeed --ks-pass env:BILISPEED_SIGNING_PASSWORD --key-pass env:BILISPEED_SIGNING_PASSWORD --v4-signing-enabled false $testRunner
            if ($LASTEXITCODE -ne 0) { throw 'Could not sign the instrumentation APK.' }
            & $adb -s $DeviceSerial install -r $testApp
            if ($LASTEXITCODE -ne 0) { throw 'Could not install the release APK on the selected test device.' }
            # Only replace the disposable test runner, never uninstall the browser or its data.
            & $adb -s $DeviceSerial uninstall app.bilispeed.browser.test 2>&1 | Out-Null
            & $adb -s $DeviceSerial install -r $testRunner
            if ($LASTEXITCODE -ne 0) { throw 'Could not install the instrumentation test APK.' }
            if ($DeviceSerial -like 'emulator-*') {
                & $adb -s $DeviceSerial shell input keyevent KEYCODE_WAKEUP
                & $adb -s $DeviceSerial shell wm dismiss-keyguard
            }
            $testClasses = 'app.bilispeed.browser.PlaybackInstrumentationTest,app.bilispeed.browser.UpdateInstrumentationTest'
            if ($RunLiveCheck) { $testClasses += ',app.bilispeed.browser.OfficialBilibiliSmokeTest' }
            if ($RunUpdateCheck) { $testClasses += ',app.bilispeed.browser.PublishedUpdateSmokeTest' }
            & $adb -s $DeviceSerial shell am instrument -w -r -e class $testClasses `
                app.bilispeed.browser.test/androidx.test.runner.AndroidJUnitRunner |
                Tee-Object -FilePath (Join-Path $outputFolder 'instrumentation-tests.txt') |
                Tee-Object -Variable instrumentOutput
            if ($LASTEXITCODE -ne 0 -or -not ($instrumentOutput -match '^OK \([1-9][0-9]* tests?\)')) {
                throw 'Playback instrumentation tests did not pass. See artifacts/instrumentation-tests.txt.'
            }
        } finally {
            Remove-Item -LiteralPath @($testApp, $testRunner, ($testRunner + '.idsig')) -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $testFolder -Force -ErrorAction SilentlyContinue
        }
    }
    & $apkSigner verify --verbose $outputApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $hash = Get-FileHash -LiteralPath $outputApk -Algorithm SHA256
    $notes = [System.IO.File]::ReadAllText((Join-Path $projectRoot 'release-notes.md')).Trim()
    if ($notes.Length -gt 6000) { throw 'Release notes must contain at most 6000 characters.' }
    $manifest = [ordered]@{
        schemaVersion = 1
        packageName = 'app.bilispeed.browser'
        versionCode = $versionCode
        versionName = $versionName
        minSdk = 26
        apkUrl = "https://github.com/$repository/releases/download/v$versionName/$apkName"
        sha256 = $hash.Hash.ToLowerInvariant()
        size = (Get-Item -LiteralPath $outputApk).Length
        notes = $notes
    }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $outputFolder 'update.json'),
        ($manifest | ConvertTo-Json) + [Environment]::NewLine, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $outputFolder 'SHA256SUMS.txt'),
        "$($hash.Hash.ToLowerInvariant())  $apkName" + [Environment]::NewLine, $utf8)
    $hash
    Write-Output "APK: $outputApk"
    Write-Output "Update manifest: $(Join-Path $outputFolder 'update.json')"
} finally {
    Remove-Item Env:\BILISPEED_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
