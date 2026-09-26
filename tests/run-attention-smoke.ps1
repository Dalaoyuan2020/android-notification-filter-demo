param(
    [Parameter(Mandatory=$true)][string]$Device,
    [string]$Python = 'python',
    [string]$StateDirectory
)
$ErrorActionPreference = 'Stop'
if ($Device -notmatch '^emulator-\d+$') { throw 'Attention smoke tests require a disposable emulator-* serial; the suite resets demo settings.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $StateDirectory) {
    $StateDirectory = Join-Path (Split-Path $projectRoot -Parent) ('work\attention-https-' + [Guid]::NewGuid().ToString('N'))
}
$StateDirectory = [System.IO.Path]::GetFullPath($StateDirectory)
if ($StateDirectory.Equals($projectRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $StateDirectory.StartsWith($projectRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Choose a work directory outside the Git checkout: temporary TLS private keys must never enter the repository.'
}
New-Item -ItemType Directory -Force -Path $StateDirectory | Out-Null
$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCommand) { $adbBinary = $adbCommand.Source }
elseif ($env:ANDROID_HOME) { $adbBinary = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' }
else { throw 'Set ANDROID_HOME or put adb on PATH.' }
function Run-Adb {
    param([string[]]$Arguments)
    & $adbBinary -s $Device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $LASTEXITCODE" }
}
$server = $null
$forwardedPort = $null
try {
    Run-Adb -Arguments @('get-state')
    $serverScript = Join-Path $PSScriptRoot 'systemone_mock_server.py'
    $serverArguments = @(('"' + $serverScript + '"'), '--state-dir', ('"' + $StateDirectory + '"'))
    $server = Start-Process -FilePath $Python -ArgumentList $serverArguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $StateDirectory 'server.stdout.log') `
        -RedirectStandardError (Join-Path $StateDirectory 'server.stderr.log')
    $readyPath = Join-Path $StateDirectory 'ready.json'
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    while (-not (Test-Path -LiteralPath $readyPath)) {
        if ($server.HasExited) { throw "Local HTTPS fixture exited. Inspect $StateDirectory/server.stderr.log" }
        if ([DateTime]::UtcNow -ge $deadline) { throw 'Local HTTPS fixture readiness timeout.' }
        Start-Sleep -Milliseconds 200
    }
    $ready = Get-Content -LiteralPath $readyPath -Raw | ConvertFrom-Json
    $forwardedPort = [int]$ready.port
    Run-Adb -Arguments @('reverse', "tcp:$forwardedPort", "tcp:$forwardedPort")
    # Clear prior emulator model settings before an upgrade can restart an enabled listener.
    $installedFilter = & $adbBinary -s $Device shell pm path com.example.notificationdemo.filter
    if ($installedFilter -match 'package:') {
        Run-Adb -Arguments @('shell', 'am', 'force-stop', 'com.example.notificationdemo.filter')
        Run-Adb -Arguments @('shell', 'pm', 'clear', 'com.example.notificationdemo.filter')
    }
    Run-Adb -Arguments @('install', '-r', "$projectRoot/filter/build/outputs/apk/debug/filter-debug.apk")
    Run-Adb -Arguments @('install', '-r', "$projectRoot/sender/build/outputs/apk/debug/sender-debug.apk")
    Run-Adb -Arguments @('install', '-r', "$projectRoot/filter/build/outputs/apk/androidTest/debug/filter-debug-androidTest.apk")
    Run-Adb -Arguments @('shell', 'pm', 'clear', 'com.example.notificationdemo.filter')
    $deviceApi = [int](& $adbBinary -s $Device shell getprop ro.build.version.sdk)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read emulator API level.' }
    if ($deviceApi -ge 33) {
        Run-Adb -Arguments @('shell', 'pm', 'grant', 'com.example.notificationdemo.sender', 'android.permission.POST_NOTIFICATIONS')
    }
    Run-Adb -Arguments @('shell', 'cmd', 'notification', 'allow_listener', 'com.example.notificationdemo.filter/com.example.notificationdemo.filter.FilterService')
    $result = & $adbBinary -s $Device shell am instrument -w -e suite attention -e mock_origin "https://localhost:$forwardedPort" `
        -e ca_base64 $ready.ca_der_base64 com.example.notificationdemo.filter.test/com.example.notificationdemo.filter.SmokeInstrumentation
    $instrumentExit = $LASTEXITCODE
    $result | Set-Content -LiteralPath (Join-Path $StateDirectory 'attention-instrumentation.log') -Encoding utf8
    $result | Write-Output
    if ($instrumentExit -ne 0 -or ($result -join "`n") -notmatch 'ATTENTION RESULT: \d+ checks passed, 0 failures') {
        throw "Attention integration failed. No automatic retries. Evidence: $StateDirectory"
    }
    $requests = @(Get-Content -LiteralPath (Join-Path $StateDirectory 'requests.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
    foreach ($route in @('/official/v1/systemone', '/bocha/v1/systemone', '/relay/v1/systemone')) {
        if (-not ($requests | Where-Object { $_.path -eq $route -and $_.status -eq 200 })) { throw "No successful HTTPS request for $route" }
    }
    foreach ($model in @('local-systemone-ft', 'local-systemone-v1', 'typesafe-jev', 'bocha-jev')) {
        if (-not ($requests | Where-Object { $_.path -eq '/jev/v1/systemone' -and $_.status -eq 200 -and $_.body.model -eq $model })) {
            throw "No successful native HTTPS request body for 1052 model $model"
        }
    }
    if ($requests | Where-Object { $_.status -ne 200 -or $_.authorization_present }) { throw 'Fixture observed a rejected request or unexpected Authorization header.' }
    if (-not ($requests | Where-Object { $_.body.state.'近期行为' -match '划掉 5 次' })) { throw 'No HTTPS request carried the five real dismissals in recent behavior.' }
    Run-Adb -Arguments @('pull', '/sdcard/Android/data/com.example.notificationdemo.filter/files/attention-proof.png', (Join-Path $StateDirectory 'attention-proof.png'))
    Run-Adb -Arguments @('pull', '/sdcard/Android/data/com.example.notificationdemo.filter/files/comparison-proof.png', (Join-Path $StateDirectory 'comparison-proof.png'))
    "PASS: local HTTPS fixture validated $($requests.Count) requests across three comparison routes and all four 1052 model choices; only synthetic data used. Evidence: $StateDirectory"
} finally {
    if ($null -ne $forwardedPort) { & $adbBinary -s $Device reverse --remove "tcp:$forwardedPort" | Out-Null }
    if ($null -ne $server -and -not $server.HasExited) { Stop-Process -Id $server.Id }
}
