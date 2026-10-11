param(
    [Parameter(Mandatory)][string]$DeviceSerial,
    [switch]$ThemeOnly,
    [switch]$InspectOnly,
    [ValidateSet('home','course','single','boundaries','collection-fullscreen','new')][string]$FromStage='home',
    [string]$AndroidSdk = 'D:\dev\android-sdk'
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$properties=Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'version.properties') | ConvertFrom-StringData
$versionCode=[int]$properties.versionCode
$versionName=$properties.versionName
$outputFolder=Join-Path $projectRoot 'artifacts'
$adb=Join-Path $AndroidSdk 'platform-tools\adb.exe'
$sessionRecord=Join-Path $outputFolder ('device-session-'+($DeviceSerial -replace '[^A-Za-z0-9.-]','_')+'.json')
. (Join-Path $PSScriptRoot 'device-session.ps1')
$inspectionProcess=$null
$sessionStarted=$false
$forwarded=$false
$originalPrefix=$env:BILISPEED_EVIDENCE_PREFIX
function Invoke-LiveCheck([string]$Name,[string[]]$Arguments) {
    & node @Arguments | Tee-Object -FilePath (Join-Path $outputFolder "device-$versionName-$Name.txt")
    if($LASTEXITCODE -ne 0){throw "Official-site check failed: $Name"}
}
Push-Location $projectRoot
try {
    if(((& $adb forward --list) -join "`n") -match '\btcp:9222\b'){throw 'Port 9222 is already in use.'}
    $sessionStarted=$true
    Start-DeviceSession
    $env:BILISPEED_EVIDENCE_PREFIX="$versionName-"
    $inspectionProcess=Start-Process -FilePath $adb -WindowStyle Hidden -PassThru -ArgumentList @(
        '-s',$DeviceSerial,'shell','am','instrument','-w','-r','-e','class',
        'app.bilispeed.browser.DeviceUiInspectionTest#inspectConnectedDevice','-e','inspectionSeconds','3600',
        'app.bilispeed.browser.test/androidx.test.runner.AndroidJUnitRunner') -RedirectStandardOutput (Join-Path $outputFolder "device-$versionName-release-inspection.txt") -RedirectStandardError (Join-Path $outputFolder "device-$versionName-release-inspection-error.txt")
    $deadline=(Get-Date).AddSeconds(30)
    $connected=$false
    while((Get-Date) -lt $deadline){
        if($inspectionProcess.HasExited){throw 'Inspection exited before connecting.'}
        $taskPid=(& $adb -s $DeviceSerial shell pidof app.bilispeed.browser | Out-String).Trim()
        if($taskPid -match '^\d+$'){
            & $adb -s $DeviceSerial forward tcp:9222 "localabstract:webview_devtools_remote_$taskPid" | Out-Null
            $forwarded=$true
            try{if(@(Invoke-RestMethod http://127.0.0.1:9222/json -TimeoutSec 2).Count -gt 0){$connected=$true;break}}catch{}
        }
        Start-Sleep -Milliseconds 250
    }
    if(-not $connected){throw 'Devtools did not become ready.'}
    if($InspectOnly){
        Write-Output 'Protected inspection ready; finish with the stop-ui-inspection marker.'
        while(-not $inspectionProcess.HasExited){Start-Sleep -Milliseconds 500}
        return
    }
    if(-not $ThemeOnly){
        $checks=@(@{stage='home';name='home-live';arguments=@('scripts/device-home-smoke.mjs',$DeviceSerial)})
        foreach($stage in @('course','single','boundaries','collection-fullscreen')){
            $checks+=@{stage=$stage;name="player-$stage";arguments=@('scripts/device-player-smoke.mjs',$DeviceSerial,$stage)}
        }
        $checks+=@{stage='new';name='new-live';arguments=@('scripts/device-1.3-smoke.mjs',$DeviceSerial)}
        $start=@($checks.stage).IndexOf($FromStage)
        foreach($check in $checks[$start..($checks.Count-1)]){Invoke-LiveCheck $check.name $check.arguments}
    } else {
        Invoke-LiveCheck 'theme-prepare' @('scripts/device-theme-prepare.mjs',$DeviceSerial)
    }
    $env:BILISPEED_EVIDENCE_PREFIX="$versionName-dark-"
    foreach($stage in @('home','search','popular','dynamic','account-history','watchlater')){
        Invoke-LiveCheck "dark-$stage" @('scripts/device-ui-smoke.mjs',$stage,$DeviceSerial)
    }
} finally {
    if($inspectionProcess){
        & $adb -s $DeviceSerial shell touch /sdcard/Android/data/app.bilispeed.browser/files/stop-ui-inspection
        if(-not $inspectionProcess.WaitForExit(20000)){& $adb -s $DeviceSerial shell am force-stop app.bilispeed.browser | Out-Null}
    }
    if($forwarded){& $adb -s $DeviceSerial forward --remove tcp:9222}
    if($sessionStarted){Stop-DeviceSession}
    $env:BILISPEED_EVIDENCE_PREFIX=$originalPrefix
    Pop-Location
}
