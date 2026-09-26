# Pure Java rule checks

Run from the project root using JDK 17 (no Android device or dependency required):

```powershell
New-Item -ItemType Directory -Force -Path build/rule-tests | Out-Null
javac -encoding UTF-8 -d build/rule-tests filter/src/main/java/com/example/notificationdemo/filter/DecisionEngine.java tests/DecisionEngineTest.java
java -cp build/rule-tests DecisionEngineTest
```

The tests cover exact target-package matching, preservation priority, blank content, protected notification types, comma/newline parsing, defaults, and long notification text. These are rule-engine checks only. Reading other applications' notifications and confirming system removal require Android device/emulator tests with notification access granted.
