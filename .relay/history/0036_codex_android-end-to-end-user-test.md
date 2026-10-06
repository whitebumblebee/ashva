---
seq: 36
agent: codex
date: "2026-10-03T19:17:08Z"
task: android-end-to-end-user-test
status: partial
summary: Started the retained Android13 learning emulator and reopened installed Ashva0.15/code16 for owner-led full-flow testing; no app changes or regression rerun.
next: Collect the owner's manual flow results from docs/USER_TESTING.md before returning to unfinished recall regression verification on an isolated device.
supersedes: []
git_head: a6820a3c24ee
---

# android-end-to-end-user-test

## What I did

Owner's2026-10-04 request prioritizes opening the emulator and explaining all built features for manual testing, then returning to unfinished regression verification. Claimed android-end-to-end-user-test, not the expired recall claim. Updated its tracker note to preserve this newer order. Strict doctor flagged the expired, owner-paused recall claim; administratively released it to unclaimed TODO, preserving all partial notes/history and without marking it done or taking a second claim. Started the configured retained learning AVD with a visible normal window/cold boot, no wipe. Existing installed version already matches current Android build configuration and retained debug APK metadata:0.15.0/code16. Reopened MainActivity without rebuilding/reinstalling or reading learner rows. Left emulator running and requested the existing full testing guide in the Codex panel (queued).

No app, source, database schema or test code changes. Manual testing is owner-led: the prepared walkthrough and successful launch do not establish that every flow passed.

## Commands run and their outcomes

- relay status:35history entries/21tasks, expired recall claim, manual testing next. relay claim android-end-to-end-user-test --agent codex succeeded; no other task claimed.
- adb devices -l initially no running devices; daemon started successfully. emulator -list-avds returned one configured learning AVD. emulator -avd <learning-avd> -port <learning-port> -no-snapshot-load launched normal graphics/window, using retained userdata; no wipe-data or test instrumentation.
- adb -s <learning-serial> shell getprop sys.boot_completed returned1. Installed package versionCode16/versionName0.15.0 matches androidApp/build.gradle.kts and existing output-metadata.json; no installation needed.
- adb -s <learning-serial> shell am start -W -n com.openinglab.app/.MainActivity:Status ok, LaunchState COLD, TotalTime2290ms, WaitTime2329ms. Foreground metadata confirms MainActivity.
- No Gradle build, connected tests, service/database harness or fresh regression acceptance in this session. Previous full61 result remains incomplete as recorded0035.
- Initial strict doctor:1error/0warnings for the expired paused recall claim. Released that stale claim to unclaimed TODO; final strict doctor PASS36history entries/21tasks. Published-context36logs/21tasks/0findings; public audit276candidatefiles/0findings. Final foreground metadata still confirms MainActivity. No Git command, staging, commit, remote or publication.

## External resources touched

Only the configured local Android13/API33 learning emulator, existing Android SDK/ADB and existing installed Ashva app. Actual local paths/AVD name/serial are not published here. No external data acquisition, account, backend, provider, paid service or deployment.

## Risks, warnings, and what is NOT done

0.15 is the built test version, not a fully accepted release. Recall remains unfinished/unclaimed TODO with full61 regression pending, and the development goal remains paused while the owner tests. Both board-quality/iOS stay deferred. Optional service is locally validated but not connected to this Android UI. User usability/flow outcomes have not been reported: keep android-end-to-end-user-test partial. Never instrument, wipe, uninstall, clear or query learner rows on the owner's emulator. Wait for Saved before cold-resume testing. Do not overwrite a newer owner build or substitute source versions silently.

## Next

Collect the owner's manual flow results from docs/USER_TESTING.md:course catalog/both POVs, Practice hints, Study replay/branches/position facts, saved policies/sets/conflicts, identifier PGN/FEN, licensed sources/observations, original GM/private game replay/follow/coaching, separate engine preview/cancellation, actual recall/Profile and exact cold resume. Record screen/route/color/actions/expected/actual outcome. Once the owner finishes this testing phase, return to unfinished recall regression verification on an isolated exact-serial device, not the learning emulator. No automatic board/iOS work or new goal completion claim.
