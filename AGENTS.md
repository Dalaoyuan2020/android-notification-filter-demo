# Agent rules (short)

- UI v0.4.0 redesign: start at `docs/agent_kit/ui_v040/00_INDEX.md`, then `STATE.md`, then only the current phase file. Do not open issue #1 in full; do not load several phase files at once.
- Keep file and directory names English (lowercase + underscores).
- Never commit API keys; keys stay in Android Keystore.
- Build check per phase: `.\gradlew.bat :filter:assembleDebug :filter:lintDebug` plus the pure-Java tests.
- Emulator-only verification must be reported as emulator-only.
