param([Parameter(Mandatory=$true)][string]$Device)
$ErrorActionPreference = 'Stop'
# This suite replaces model credentials/configuration with synthetic profiles and restores defaults.
# An emulator serial is required deliberately: never target a teammate's personal phone by mistake.
if ($Device -notmatch '^emulator-\d+$') { throw 'Model smoke tests require an emulator-* serial; this suite resets demo settings.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCommand) { $adbBinary = $adbCommand.Source }
elseif ($env:ANDROID_HOME) { $adbBinary = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' }
else { throw 'Set ANDROID_HOME or put adb on PATH.' }
function Run-Adb {
    param([string[]]$Arguments)
    & $adbBinary -s $Device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $LASTEXITCODE" }
}
Run-Adb -Arguments @('get-state')
# Clear previously installed configuration BEFORE an upgrade can restart its listener.
$installedFilter = & $adbBinary -s $Device shell pm path com.example.notificationdemo.filter
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect installed filter package.' }
if (($installedFilter -join '') -match 'package:') {
    Run-Adb -Arguments @('shell','am','force-stop','com.example.notificationdemo.filter')
    Run-Adb -Arguments @('shell','pm','clear','com.example.notificationdemo.filter')
}
Run-Adb -Arguments @('install','-r',"$projectRoot/filter/build/outputs/apk/debug/filter-debug.apk")
Run-Adb -Arguments @('install','-r',"$projectRoot/sender/build/outputs/apk/debug/sender-debug.apk")
Run-Adb -Arguments @('install','-r',"$projectRoot/filter/build/outputs/apk/androidTest/debug/filter-debug-androidTest.apk")
# Also reset a fresh install to deterministic defaults.
Run-Adb -Arguments @('shell','pm','clear','com.example.notificationdemo.filter')
$deviceApi = [int](& $adbBinary -s $Device shell getprop ro.build.version.sdk)
if ($LASTEXITCODE -ne 0) { throw 'Unable to read emulator Android API level.' }
if ($deviceApi -ge 33) {
    Run-Adb -Arguments @('shell','pm','grant','com.example.notificationdemo.sender','android.permission.POST_NOTIFICATIONS')
}
Run-Adb -Arguments @('shell','cmd','notification','allow_listener','com.example.notificationdemo.filter/com.example.notificationdemo.filter.FilterService')
$result = & $adbBinary -s $Device shell am instrument -w -e suite models com.example.notificationdemo.filter.test/com.example.notificationdemo.filter.SmokeInstrumentation
$result | Write-Output
if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'MODEL RESULT: \d+ checks passed, 0 failures') {
    throw 'Model integration test failed; inspect instrumentation output.'
}
