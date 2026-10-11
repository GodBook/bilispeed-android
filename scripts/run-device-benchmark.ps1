param(
    [Parameter(Mandatory)][string]$DeviceSerial,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9.-]+$')][string]$Label,
    [Parameter(Mandatory)][string]$ExpectedVersion,
    [switch]$SkipLaunch,
    [switch]$DiagnosticMemoryTrim,
    [string]$AndroidSdk = 'D:\dev\android-sdk'
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$outputFolder=Join-Path $projectRoot 'artifacts'
$adb=Join-Path $AndroidSdk 'platform-tools\adb.exe'
$versionCode=15
$versionName=$ExpectedVersion
$sessionRecord=Join-Path $outputFolder ('device-session-'+($DeviceSerial -replace '[^A-Za-z0-9.-]','_')+'.json')
. (Join-Path $PSScriptRoot 'device-session.ps1')
$inspectionProcess=$null
$forwarded=$false
$sessionStarted=$false
if($DiagnosticMemoryTrim -and $Label -notmatch 'diagnostic'){throw 'Memory-trim diagnostics require a diagnostic evidence label and must not count as final release verification.'}
Push-Location $projectRoot
try {
    $existing=(& $adb forward --list) -join "`n"
    if($existing -match '\btcp:9222\b'){throw 'Port 9222 already belongs to an inspection session. Finish that session first.'}
    $sessionStarted=$true
    Start-DeviceSession
    & $adb -s $DeviceSerial shell settings put system accelerometer_rotation 0
    & $adb -s $DeviceSerial shell settings put system user_rotation 0
    if($SkipLaunch){
        $launch=Get-Content -Raw -LiteralPath (Join-Path $outputFolder "device-$Label-native-launch.json") | ConvertFrom-Json
        if($launch.label -cne $Label -or $launch.coldSummary.n -ne 20 -or $launch.warmSummary.n -ne 20){throw 'Missing completed launch evidence.'}
    } else {
        & node scripts/device-launch-benchmark.mjs $DeviceSerial $Label
        if($LASTEXITCODE -ne 0){throw 'Launch benchmark failed.'}
    }
    $inspectionProcess=Start-Process -FilePath $adb -WindowStyle Hidden -PassThru -ArgumentList @(
        '-s',$DeviceSerial,'shell','am','instrument','-w','-r','-e','class',
        'app.bilispeed.browser.DeviceUiInspectionTest#inspectConnectedDevice','-e','inspectionSeconds','3600',
        '-e','inspectionMemoryTrim',([string][bool]$DiagnosticMemoryTrim).ToLowerInvariant(),
        'app.bilispeed.browser.test/androidx.test.runner.AndroidJUnitRunner') -RedirectStandardOutput (Join-Path $outputFolder "device-$Label-inspection.txt") -RedirectStandardError (Join-Path $outputFolder "device-$Label-inspection-error.txt")
    $deadline=(Get-Date).AddSeconds(30)
    $connected=$false
    while((Get-Date) -lt $deadline){
        if($inspectionProcess.HasExited){throw 'Inspection exited before connecting.'}
        $taskPid=(& $adb -s $DeviceSerial shell pidof app.bilispeed.browser | Out-String).Trim()
        if($taskPid -match '^\d+$'){
            & $adb -s $DeviceSerial forward tcp:9222 "localabstract:webview_devtools_remote_$taskPid" | Out-Null
            $forwarded=$true
            try { if(@(Invoke-RestMethod http://127.0.0.1:9222/json -TimeoutSec 2).Count -gt 0){$connected=$true;break} } catch { }
        }
        Start-Sleep -Milliseconds 250
    }
    if(-not $connected){throw 'Devtools did not become ready.'}
    & node scripts/device-journey-benchmark.mjs $DeviceSerial $Label
    if($LASTEXITCODE -ne 0){throw 'Journey benchmark failed; retain partial evidence.'}
} finally {
    if($inspectionProcess){
        & $adb -s $DeviceSerial shell touch /sdcard/Android/data/app.bilispeed.browser/files/stop-ui-inspection
        if(-not $inspectionProcess.WaitForExit(20000)){ & $adb -s $DeviceSerial shell am force-stop app.bilispeed.browser | Out-Null }
    }
    if($forwarded){ & $adb -s $DeviceSerial forward --remove tcp:9222 }
    if($sessionStarted){Stop-DeviceSession}
    Pop-Location
}
