---
seq: 6
agent: codex
date: "2026-10-01T08:17:45Z"
task: emulator-user-test
status: done
summary: "Started the configured Android emulator, installed the current debug APK, verified the Learn screen, and prepared the user walkthrough without app changes."
next: "Collect the user test results; if no corrective work is requested, claim lesson-graph-replay and implement its shared replay/branch model."
supersedes: []
---

# emulator-user-test

## What I did

Handled the user's explicit request to run the emulator for testing the newly completed foundation. Read the Relay skill, current project/tracker/index, log 0005, and `docs/USER_TESTING.md`. Added/claimed this requested local testing task; no app source, dependencies, or emulator data were changed. Started the existing Pixel AVD, assembled/installed the current APK, opened Opening Lab, and verified the Learn screen. Left the emulator running for user interaction and prepared concrete manual checks for legal input, seed recognition/transpositions, PGN/FEN input, promotion, error recovery, and the existing trainer.

## Commands run and their outcomes

- Initial SDK `adb devices`: daemon started successfully; zero connected devices. `emulator -list-avds` returned `Pixel_3a_API_33_arm64-v8a`, with sandbox crashpad permission warnings. An optional `ps` inventory was denied by the sandbox; did not require it to proceed.
- Started `<android-sdk>/emulator/emulator -avd Pixel_3a_API_33_arm64-v8a -no-snapshot-save -no-boot-anim` with approved escalation. Emulator 36.1.9.0 passed disk/GPU/system checks and remains running in exec session 31197. No wipe/reset or second AVD was used.
- `./gradlew :androidApp:assembleDebug` with approved escalation: **BUILD SUCCESSFUL in 6s; 48 actionable tasks, 1 executed/47 up-to-date**. APK **19,122,999 bytes** at the documented path. No source edit prompted rebuilding; this confirms the current artifact is up-to-date.
- SDK `adb devices`: `emulator-5554 device`. `adb -s emulator-5554 shell getprop sys.boot_completed`: **1**.
- `adb -s emulator-5554 install -r <project-root>/androidApp/build/outputs/apk/debug/androidApp-debug.apk`: **Success**.
- `adb -s emulator-5554 shell am start -W -n com.openinglab.app/.MainActivity`: **Status ok / COLD**, activity MainActivity, total 4157 ms. Fresh UIAutomator dump succeeded at `/sdcard/openinglab-user-testing-ui.xml`; reading it confirmed OPENING LAB, Ruy López/Morphy card, and Learn/Explore/Review/Profile. No board input or user progress was changed in this session.
- Optional native window lookup via `cua.getApp("Android Emulator")` could not resolve an app with that display name; the native app inventory did not expose the emulator. Did not force macOS UI access or launch another emulator. Android app state was verified via its dedicated SDK interface; bringing its window to the front via native automation was not verified.
- Before the operational handoff, Relay doctor --strict passed with five logs/17 tasks. No full test/lint rerun was necessary for source-unchanged launch work; the **39-test and lint/iOS compilation evidence remains the prior session's log 0005**, not new evidence here.

## External resources touched

Only local Gradle tooling, Android SDK, configured API-33 AVD, and app package `com.openinglab.app`. No web research, accounts, uploads, third-party messages, signing/distribution, production services, Git initialization, commits, or pushes.

## Risks, warnings, and what is NOT done

The emulator is intentionally left running for the user; do not close/wipe it during their test. App is on Learn. This session verifies boot/install/launch, not fresh completion of every manual walkthrough scenario. Use `docs/USER_TESTING.md` for the full existing guide. The emulator's host window can be behind another Mac window; it was started with the normal visible-window defaults, not `-no-window`, but native foregrounding was not verified.

Seven seed openings/thirteen short variations remain the entire bundled coverage. No Full idea/branch replay, automatic wrong-attempt hint, persistent learning store, engine, sourced GM-game library, or iOS client was added. Import opens a PGN's final position; FEN undo starts at the supplied FEN. Keep these boundaries explicit when gathering feedback. Four dependency-update warnings from log 0005 are still known; no dependency audit was performed in this launch-only session.

## Next

Collect the user's test results, with screen/color, exact moves, expected/actual behavior, and screenshots where useful. If no corrective work is requested, run `node .agents/skills/relay/bin/relay.mjs claim lesson-graph-replay --agent <harness-name>` and build its shared graph/path/cursor contract. Do not infer permission to wipe the emulator, provision data services, or begin a different milestone from this operational request.
