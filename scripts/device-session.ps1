# Loaded by build-apk.ps1. The durable device-side preference snapshot survives
# instrumentation/process/host interruption; the host record preserves system settings.
function Invoke-PreferenceProbe([string]$Method) {
    $result = @(& $adb -s $DeviceSerial shell am instrument -w -r -e class "app.bilispeed.browser.DevicePreferencesProbe#$Method" app.bilispeed.browser.test/androidx.test.runner.AndroidJUnitRunner)
    if ($LASTEXITCODE -ne 0 -or -not ($result -match '^OK \(1 test\)')) {
        throw "Preference $Method failed; keep the snapshot and test runner for recovery. $($result -join [Environment]::NewLine)"
    }
}

function Test-DeviceReady {
    $state = ([string](& $adb -s $DeviceSerial get-state 2>$null)).Trim()
    if ($state -ne 'device') { throw "Selected device $DeviceSerial is not online ($state)." }
    & $adb -s $DeviceSerial shell input keyevent KEYCODE_WAKEUP
    if ($LASTEXITCODE -ne 0) { throw 'Could not wake the test display.' }
    $policy = (& $adb -s $DeviceSerial shell dumpsys window policy) -join "`n"
    if ($policy -match '(?m)^\s*(showing|mIsShowing|inputRestricted)=true') {
        throw 'The selected phone is locked. Unlock it before UI tests; no fixture tests were started.'
    }
    $network = (& $adb -s $DeviceSerial shell dumpsys connectivity) -join "`n"
    if ($network -notmatch 'VALIDATED') { throw 'The device has no validated network. Check its current connection before tests.' }
    if ($DeviceSerial -notlike 'emulator-*') {
        $battery = (& $adb -s $DeviceSerial shell dumpsys battery) -join "`n"
        $level = [regex]::Match($battery, '(?m)^\s*level:\s*(\d+)')
        if ($level.Success -and [int]$level.Groups[1].Value -lt 10 -and $battery -notmatch '(?m)^\s*(?:AC|USB|Wireless|Dock) powered:\s*true') {
            throw "The phone battery is $($level.Groups[1].Value)% and is not charging. Connect power before continuous UI tests."
        }
    }
    $installed = (& $adb -s $DeviceSerial shell dumpsys package app.bilispeed.browser) -join "`n"
    if ($installed -notmatch "versionCode=$versionCode\b" -or $installed -notmatch "versionName=$([regex]::Escape($versionName))\b") {
        throw "Installed target is not $versionName / $versionCode."
    }
    Write-Output "Test device $DeviceSerial ready; installed $versionName / $versionCode."
}

function Restore-DeviceSystemSettings {
    if (Test-Path -LiteralPath $sessionRecord) {
        $saved = Get-Content -Raw -LiteralPath $sessionRecord | ConvertFrom-Json
        foreach ($key in @('screen_off_timeout', 'accelerometer_rotation', 'user_rotation', 'font_scale')) {
            if(-not $saved.PSObject.Properties[$key]){continue}
            $value = [string]$saved.$key
            if ($value -eq 'null') { & $adb -s $DeviceSerial shell settings delete system $key | Out-Null }
            elseif ($value -match '^\d+(?:\.\d+)?$') { & $adb -s $DeviceSerial shell settings put system $key $value | Out-Null }
            else { throw "Invalid saved device setting $key." }
            if ($LASTEXITCODE -ne 0) { throw 'Device settings restoration failed; retain the session record.' }
        }
        Remove-Item -LiteralPath $sessionRecord -Force
    }
}

function Start-DeviceSession {
    Test-DeviceReady
    Invoke-PreferenceProbe 'restoreInterruptedSession'
    Restore-DeviceSystemSettings
    $saved = [ordered]@{}
    foreach ($key in @('screen_off_timeout', 'accelerometer_rotation', 'user_rotation', 'font_scale')) {
        $saved[$key] = ([string](& $adb -s $DeviceSerial shell settings get system $key)).Trim()
    }
    $saved | ConvertTo-Json | Set-Content -LiteralPath ($sessionRecord+'.pending') -Encoding utf8
    Move-Item -LiteralPath ($sessionRecord+'.pending') -Destination $sessionRecord -Force
    Invoke-PreferenceProbe 'savePreferences'
    & $adb -s $DeviceSerial shell settings put system screen_off_timeout 1800000
    if ($LASTEXITCODE -ne 0) { throw 'Could not extend the test screen timeout.' }
    & $adb -s $DeviceSerial shell settings put system accelerometer_rotation 0
    & $adb -s $DeviceSerial shell settings put system user_rotation 0
    if ($LASTEXITCODE -ne 0) { throw 'Could not establish the test portrait rotation.' }
    if($FontScale){
        & $adb -s $DeviceSerial shell settings put system font_scale $FontScale
        if($LASTEXITCODE -ne 0){throw 'Could not establish the requested test font scale.'}
    }
}

function Stop-DeviceSession {
    & $adb -s $DeviceSerial shell am force-stop app.bilispeed.browser | Out-Null
    Invoke-PreferenceProbe 'restoreInterruptedSession'
    Restore-DeviceSystemSettings
    Write-Output 'Original preferences and temporary system settings restored.'
}
