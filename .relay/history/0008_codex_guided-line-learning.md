---
seq: 8
agent: codex
date: "2026-10-01T10:02:13Z"
task: guided-line-learning
status: done
summary: "Shipped native Compose Study/Practice, Full idea, replay, automatic hints and confirmed branches for seeds; 57 shared and 12 emulator tests pass."
next: "Claim source-data-pipeline and audit collection-specific rights before building a local sourced game/opening importer with provenance and coverage counts."
supersedes: []
---

# guided-line-learning

## What I did

- Added shared `PracticeAttempt.kt` and six common tests: distinguish expected, legal off-line, matching available branch, and illegal attempts without mutating the replay. Promotion choice is exact, never silently substituted.
- Extended `Variation` and `LessonPath` with White/Black introductory plans; Ruy Morphy/Berlin/Exchange have authored color-specific summaries, other seeds summarize their existing move principles. Plans are explicitly seed content, not engine analysis or complete theory.
- Rebuilt `TrainerScreen.kt` entirely in native Compose. Full idea enters Study and reveals complete selected seed notation, chosen-color/opponent plans and per-move explanation. Read-only Study board supports first/previous/next/last/jump and autoplay/pause at 0.7/1.2/2.0s. Manual Next/jump follows the current route; autoplay pauses at branch choices.
- `AppViewModel.kt` now handles automatic hint text plus expected source/destination highlight after wrong/illegal moves, unchanged board, neutral legal off-line feedback, explicit Stay/Switch/Return, and four-piece promotion. A matching attempted alternative is applied only after Switch confirmation. Practice restarts the selected materialized route; progress is exercise completion with retries/assisted counts, not fabricated mastery.
- Added compact Bundle-backed SavedStateHandle restoration using stable content fingerprints and replay snapshots. Reconstructs cursor/color/mode/return stack, pending hint/alternative/promotion and counters; rejects changed/corrupt content. Saved-instance restoration is not a Room bookmark or guaranteed cold relaunch. Invalid saved lesson routes show a recovery message, not a blank screen.
- Autoplay/opponent jobs cancel on mode/navigation/restart/branch/background changes with revision guards. Lifecycle resumes a pending Practice reply but never auto-resumes Study playback. Navigation 3 saveable typed routes remain in use.
- ChessBoard gains coordinate/piece accessibility descriptions, expected/selected/legal-square semantics, test tags and a Study input-disabled state. Existing glyph-piece rendering/animation is retained; no visual-parity/performance claim.
- Added `GuidedLessonTest.kt` with current Compose JUnit4 v2 rule APIs, BOM-aligned UI test dependencies and stable AndroidX Test runner 1.7.0 / ext JUnit 1.3.0, pinned in the central catalog. Debug app bumped to 0.2.0/versionCode 2.
- Updated README, USER_TESTING, LEARNING_DESIGN, PROJECT and Relay gates for new behavior/current scope. No XML screens were added. Claim was renewed between stages; previous graph milestone 0007 completed earlier in this same user-authorized turn.

## Commands run and their outcomes

- Initial `:shared:testAndroidHostTest :androidApp:assembleDebug` failed at Kotlin compilation: PieceColor.opposite was invoked as a function and LifecycleEventEffect used the wrong positional parameter. Fixed both; later builds passed.
- First connected Compose run: 9 tests, 4 assertion failures, 0 errors/skips. XML showed correct app text; assertTextContains defaults to exact text, so assertions were corrected to substring=true.
- Second connected run: 10 tests, 1 assertion failure, 0 errors/skips. Completion/retry/assistance state was correct; the final explanatory paragraph was just outside the viewport. Added performScrollTo before the visibility assertion.
- Final command: `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug :androidApp:connectedDebugAndroidTest :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64` — BUILD SUCCESSFUL in 1m 9s, 107 tasks (19 executed, 88 up-to-date).
- Shared XML: 57 tests passed / 0 failed / 0 errors / 0 skipped (39 rules/notation/identification + 12 replay + 6 attempt/plan tests). Shared sources last executed earlier in this pass; final invocation reused unchanged results.
- Connected XML: 12 passed / 0 failed / 0 errors / 0 skipped on emulator-5554, Pixel_3a_API_33_arm64-v8a / Android 13. Covered wrong legal/illegal automatic hints and unchanged board; complete notation/plans/navigation; Black confirmed Berlin attempt; White branch reply pause/Stay; Study branch/return; autoplay/background cancellation; activity recreation; fresh ViewModel Parcel/Bundle restoration, stale-content rejection, pending alternative restoration; canceled queued opponent reply; full White practice with honest assisted-completion summary.
- Both shared iOS targets compiled successfully earlier in the pass and remained up-to-date in the final gate. No iOS app/UI was built.
- Lint: 0 errors / 4 existing dependency-update warnings: Gradle 9.7.1 → 9.8.0, core-ktx 1.19.0 → 1.19.1, Navigation3 runtime/UI 1.1.7 → 1.2.0. No claim that every configured dependency is currently latest; no unreviewed production dependency upgrade.
- Reinstalled latest debug APK with `adb -e install -r .../androidApp-debug.apk` — Success. `am start -W -n com.openinglab.app/.MainActivity` — Status ok, cold launch. A UI dump attempted during startup returned null root; a retry after launch succeeded. Emulator was not wiped or stopped.

## External resources touched

- Local Android emulator only: emulator-5554 / existing Pixel_3a_API_33_arm64-v8a. Installed debug app `com.openinglab.app` and instrumentation APK `com.openinglab.app.test`; no account, external messages, publication, spend, Git commit/push or backend changes.
- Official references checked for test setup/APIs: https://developer.android.com/develop/ui/compose/testing ; https://developer.android.com/develop/ui/compose/testing/migrate-v2 ; https://developer.android.com/jetpack/androidx/releases/test . Gradle resolved the pinned test artifacts through configured repositories. No chess corpus downloaded.

## Risks, warnings, and what is NOT done

- Still seven openings/thirteen short variations, zero full historical scores, seven unverified metadata cards; do not present the new teaching controls as full repertoire coverage or verified GM coaching.
- Authored plans/explanations are introductory and not engine-backed. Berlin seed stops after ...Nxe4; no forced recovery/win/complete Berlin ending is promised. Deeper curated/engine-grounded content is a separate milestone.
- PGN/FEN identifier still shows final main-line position; shared PGN graphs exist, but imported games are not wired to the teaching UI yet. Original-game coaching and GM library remain tasks.
- Saved-instance state is transient Android restoration; no database/download packs, durable learning history/review scheduler or cold-relaunch bookmarks. Store compact IDs/cursors, not graph payloads, when adding persistence.
- Runtime verification is on one API33 emulator. No physical-device haptic, frame benchmark, font-scale/tablet/orientation matrix, accessibility audit, distribution or iOS UI testing was claimed.
- Four pre-existing update warnings remain and need official compatibility review before upgrades. Test APIs use v2; do not regress to deprecated v1 rules.

## Next

Claim `source-data-pipeline` (`node .agents/skills/relay/bin/relay.mjs claim source-data-pipeline --agent <you>`). Read docs/LEARNING_DESIGN.md and tracker acceptance, audit separate opening-name/master-game/broadcast/annotation rights and current provider terms, then implement a repeatable local legal-score importer with source/version/license manifests, deduplication/quarantine and explicit coverage counts. Do not provision accounts, deploy, spend, silently download a whole corpus, or treat API default result caps as a full repertoire.
