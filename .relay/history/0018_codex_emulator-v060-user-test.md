---
seq: 18
agent: codex
date: "2026-10-02T09:17:35Z"
task: emulator-v060-user-test
status: done
summary: "Opened the visible learning emulator with Ashva Android 0.6.0, verified build/version/foreground Home without clearing data or instrumentation, and prepared a feature-by-feature testing walkthrough."
next: Claim engine-alternatives and begin the official Stockfish mobile/distribution feasibility audit and portable bounded-analysis contract; leave the learning emulator running for the owner.
supersedes: []
git_head: cba53d25fcf1
---

# emulator-v060-user-test

## What I did

Honored the user's explicit first priority: open the latest app for manual testing before further implementation. Added/claimed emulator-v060-user-test, inspected current v0.6.0 code and the existing user guide, assembled the debug APK, started the configured visible Pixel_3a_API_33_arm64-v8a learning emulator on serial emulator-5554 and installed with adb install -r. Opened Ashva and verified versionCode 7 / versionName 0.6.0, a live process and foreground MainActivity. Inspected a private temporary screenshot of the Home screen. It shows No saved lesson yet; no claim of restoring an existing lesson is made. No app data was cleared or uninstalled, no learner database inspected/edited, and no instrumentation ran on this device. Leave the emulator/app running for the owner.

Prepared a step-by-step walkthrough of offline packs, White/Black practice and automatic hints, Full idea/replay/branch return, sourced family/variation search, separate preferred-move/reply policies and filtered practice, the new 79-score position observations/provenance, PGN/FEN identification and cold resume. Explain actual versus demo progress and remaining full-repertoire/GM/engine/recall/iOS limits. The detailed portable guide remains docs/USER_TESTING.md; no application source or content bytes changed in this turn. Engine-alternatives stays next development work, not silently completed by opening the emulator.

## Commands run and their outcomes

- `./gradlew :androidApp:assembleDebug` — **BUILD SUCCESSFUL**, 852ms, 50 actionable tasks (1 executed / 49 up-to-date). This is a fresh assembly check, not a rerun of the domain/connected suites recorded in 0017.
- `adb devices -l` — initially no connected devices. `emulator -list-avds` — configured Pixel_3a_API_33_arm64-v8a. Started it visibly using `emulator -avd Pixel_3a_API_33_arm64-v8a -port 5554 -no-snapshot-save -no-boot-anim`; no wipe/snapshot deletion. `adb -s emulator-5554 shell getprop sys.boot_completed` — **1**.
- Pre-install package-version filter returned no version/exit 1. `adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk` — **Success**. `adb -s emulator-5554 shell am start -W -n com.openinglab.app/.MainActivity` — **Status ok / COLD / complete**, total 3636ms, wait 3731ms.
- `adb -s emulator-5554 shell dumpsys package com.openinglab.app` — **versionCode 7 / versionName 0.6.0 / minSdk 26 / targetSdk 37**. `pidof` returned a live process. Private screenshot inspected: Ashva Home, Learn/Explore/Review/Profile, My repertoires, Offline library & sources and explicit alpha/demo labels. The first activity filter used the wrong Android field name and returned 1; corrected inspection verifies **topResumedActivity com.openinglab.app/.MainActivity, visible=true**.
- Read-only public audit passes with **173 candidate files / zero findings**; portable context and strict Relay doctor repeated after this completed log. No fresh broad UI/domain/iOS/secret-scan claim; previous implementation gate results remain in 0017.

## External resources touched

Existing local Android SDK/API33 image and the owner's configured learning AVD only. No chess source/provider/network fetch, paid service, new dependency, external account or cloud change. The emulator runtime and screenshot paths are private local artifacts, not copied into public assets/history. Relay automatically reads HEAD metadata; no direct Git operation/mutation, commit, staging, remote, push or publication by the agent.

## Risks, warnings, and what is NOT done

The owner is now using emulator-5554. Do not run connected instrumentation there, stop it, clear its data or uninstall to solve a future issue. Use the separate disposable test emulator/exact serial for automated gates. The agent did not install source packs through the UI or run every walkthrough interaction in this turn; those are manual checks for the owner. The two reviewed packs install from APK assets, taxonomy first then broadcasts. The 79-score fixture is not full popular-master coverage, a verified GM shelf or an engine. Home/Review/Profile statistics remain demos; bookmarks, attempts and repertoire-policy revisions are actual stored records. Source routes have factual move labels but lack reviewed strategic plans. Backend/iOS UI/engine/GM coach/recall/board performance work remains.

## Next

Claim engine-alternatives and begin the official Stockfish mobile/distribution feasibility audit and portable bounded-analysis contract, as specified in 0017. Preserve the owner's Apache-2.0 app-code decision and separate source licenses; no engine bundling/provider spend/deployment without resolved obligations/authority. Leave the learning emulator running for the owner while subsequent development uses isolated test targets. Broader repertoire-coverage stays partial; all Git work/publication remains owner-managed.
