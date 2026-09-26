#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
src=filter/src/main/java/com/example/notificationdemo/filter
mkdir -p build/unit-tests
javac -encoding UTF-8 -d build/unit-tests \
  "$src"/{DecisionEngine,ModelConfig,StrictJson,SystemOneProtocol,RetryPolicy,AttentionMath,ShortTermMemory}.java \
  tests/{DecisionEngineTest,SystemOneProtocolTest,ShortTermMemoryTest}.java
for suite in DecisionEngineTest SystemOneProtocolTest ShortTermMemoryTest; do
  java -cp build/unit-tests "$suite"
done
