param([Parameter(Mandatory=$true)][string]$Device)
$ErrorActionPreference = 'Stop'
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
Run-Adb -Arguments @('install','-r',"$projectRoot/filter/build/outputs/apk/debug/filter-debug.apk")
Run-Adb -Arguments @('install','-r',"$projectRoot/sender/build/outputs/apk/debug/sender-debug.apk")
Run-Adb -Arguments @('install','-r',"$projectRoot/filter/build/outputs/apk/androidTest/debug/filter-debug-androidTest.apk")
$deviceApi = [int](& $adbBinary -s $Device shell getprop ro.build.version.sdk)
if ($deviceApi -ge 33) {
    Run-Adb -Arguments @('shell','pm','grant','com.example.notificationdemo.sender','android.permission.POST_NOTIFICATIONS')
}
Run-Adb -Arguments @('shell','cmd','notification','allow_listener','com.example.notificationdemo.filter/com.example.notificationdemo.filter.FilterService')
$result = & $adbBinary -s $Device shell am instrument -w com.example.notificationdemo.filter.test/com.example.notificationdemo.filter.SmokeInstrumentation
$result | Write-Output
if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'RESULT: \d+ checks passed, 0 failures') {
    throw 'Device integration test failed; inspect instrumentation output.'
}
