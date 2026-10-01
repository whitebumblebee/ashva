---
seq: 3
agent: codex
date: "2026-09-30T11:45:05Z"
task: user-test-guide
status: done
summary: "Documented phone/emulator testing and verified the Android build, five shared tests, and Ruy López trainer/identifier flows."
next: Claim chess-correctness and replace pseudo-legal moves and prefix-only identification with tested legal rules and position-based recognition.
supersedes: []
---

# user-test-guide

## What I did

Added docs/USER_TESTING.md with APK sideload steps, exact commands for this Mac's existing Pixel emulator, a full Ruy López White exercise, help/correction and Black/variation checks, manual opening identification, troubleshooting, and rebuild instructions. Updated README.md to link the guide, explain Relay usage, and distinguish sample progress/archive metadata from implemented product features. Changed the stack heading to configured versions rather than an unverified latest/stable assertion.

Installed the existing debug APK into the local emulator, launched the app, verified the documented Ruy López trainer and identifier moves, and returned it to Learn for the user. App source was not changed. The user selected chess correctness next and declined Git initialization; those decisions are already in PROJECT.md and tasks.md.

## Commands run and their outcomes

- `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug`: initial sandbox attempt could not write the Gradle wrapper lock under the user's cache. Repeated with normal sandbox escalation: BUILD SUCCESSFUL in 32s, 71 actionable tasks (2 executed, 69 up-to-date). This run reused the cached test task and APK.
- `./gradlew :shared:testAndroidHostTest --rerun-tasks`: BUILD SUCCESSFUL in 4s, all 8 actionable tasks executed. Fresh XML reports dated 2026-09-30: BoardPositionTest 3 passed and OpeningIdentifierTest 2 passed; 0 failures, 0 errors, 0 skipped total.
- Inspected androidApp/build/reports/lint-results-debug.xml: 0 issue entries.
- `<android-sdk>/build-tools/36.0.0/aapt dump badging androidApp/build/outputs/apk/debug/androidApp-debug.apk`: package com.openinglab.app, version 0.1.0, min SDK 26, target SDK 37, label Opening Lab. APK exists, approximately 18 MiB; it was reused because app source was unchanged.
- ADB initially failed to start its local socket inside the sandbox; escalation started it successfully. `adb devices -l` initially found no connected device. No physical phone was tested.
- `<android-sdk>/emulator/emulator -avd Pixel_3a_API_33_arm64-v8a -no-snapshot-save -no-boot-anim`: existing AVD booted; `adb -s emulator-5554 shell getprop sys.boot_completed` returned 1.
- `adb -s emulator-5554 install -r <absolute-debug-APK-path>`: Success. `adb -s emulator-5554 shell am start -W -n com.openinglab.app/.MainActivity`: Status ok, COLD launch, TotalTime 2521 ms. ADB commands used the absolute SDK platform-tools path throughout.
- ADB input plus `uiautomator dump /sdcard/openinglab-test-ui.xml` verified Learn -> Ruy López -> Play White. First move d2d4 was rejected, pieces stayed at the start, and the correction said to think about space before tactics. Then the full documented White sequence e4, Nf3, Bb5, Ba4, O-O, Re1, Bb3, c3 with scripted Black replies reached 100% and LINE MASTERED; the completion recorded the one deliberate retry. Both castling positions showed king and rook relocated.
- Explore -> Don't know the name? -> manual e4 e5 Nf3 Nc6 Bb5 a6 yielded Ruy López, Morphy Defence, and the current prototype's 90% MATCH. The match's percentage remains heuristic, not calibrated confidence.
- Checked README/guide local Markdown targets with a Node filesystem check: all 4 local documentation links exist. Inspected source labels for Play White, Play Black, Hint, Restart, and Train it again.
- `node .agents/skills/relay/bin/relay.mjs doctor --strict`: exit 0 before the final log, all checks passed (2 entries/3 tasks). Final done/doctor/status are run after this body is completed.

## External resources touched

Local emulator emulator-5554 / Pixel_3a_API_33_arm64-v8a and package com.openinglab.app. No device data was wiped; installation used -r and the emulator was started without snapshot saving. A temporary UI hierarchy is on the emulator at /sdcard/openinglab-test-ui.xml. The emulator remains running for the user.

Read official Android installation/emulator guidance at https://developer.android.com/studio/run/device, https://developer.android.com/studio/run/emulator, https://developer.android.com/studio/run/emulator-install-add-files, and https://support.google.com/android/answer/9457058?hl=en. No external writes, account setup, paid services, commits, pushes, or publication occurred. Requested the guide be opened in Codex; the panel tool returned queued.

## Risks, warnings, and what is NOT done

Current runtime evidence is from one Android 13/API 33 emulator. Phone sideload steps, Black-side/other-variation checks, hint highlighting, and undo/reset are documented from the implementation but not all were re-exercised in this session. No iOS app exists. There are five shared tests, not an exhaustive rules suite or Compose UI test suite.

The original prototype remains unchanged: pseudo-legal targeting, incomplete castling rights/check/en passant handling, no transposition recognition, heuristic match confidence, sample review/profile/progress, unverified historical-game metadata, and no persistent learning or full opening database. The user-test-guide task is complete; the original full product vision is not.

## Next

Claim chess-correctness through `node .agents/skills/relay/bin/relay.mjs claim chess-correctness --agent <harness-name>`. Start with shared/src/commonMain/kotlin/com/openinglab/shared/chess/BoardPosition.kt and its tests: enforce check legality, preserve castling rights, correct en passant, and handle promotion explicitly. Then replace OpeningIdentifier's prefix-only heuristic with position-based recognition and honest ambiguity, testing transposed move orders and unknown positions. Read the actual current code before editing.
