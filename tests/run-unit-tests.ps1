$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$source = Join-Path $projectRoot 'filter/src/main/java/com/example/notificationdemo/filter'
$output = Join-Path $projectRoot 'build/unit-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$javacBinary = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac' }
$javaBinary = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$sources = @('DecisionEngine','ModelConfig','StrictJson','SystemOneProtocol','RetryPolicy','AttentionMath','ShortTermMemory') |
    ForEach-Object { Join-Path $source "$_.java" }
$testFiles = @('DecisionEngineTest','SystemOneProtocolTest','ShortTermMemoryTest','p0_api_config_test')
$sources += $testFiles | ForEach-Object { Join-Path $PSScriptRoot "$_.java" }
& $javacBinary -encoding UTF-8 -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Pure Java test compilation failed.' }
foreach ($test in $testFiles) {
    & $javaBinary -cp $output $test
    if ($LASTEXITCODE -ne 0) { throw "Pure Java test failed: $test" }
}
