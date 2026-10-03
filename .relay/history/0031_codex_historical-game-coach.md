---
seq: 31
agent: codex
date: "2026-10-02T21:16:29Z"
task: historical-game-coach
status: done
summary: "Verified Android0.14 original-move coaching, exact-history offline comparison and separate hypothetical preview/return;162JVM/128host/24importer/all56native cases and complete build/iOS gates pass. Owner updated in place; whole goal remains unfinished."
next: "Claim content-analysis-service and implement/test the local source-attributed API, versioned pagination/delta packs, bounded deduplicated analysis/cache/jobs and provider backoff without cloud/account/deployment changes."
supersedes: []
git_head: cba53d25fcf1
---

# historical-game-coach

## What I did

Completed historical-game-coach in Android0.14/code15, building on0029–0030. Shared OriginalGameCoach checks the actual original move’s SAN/UCI/before-after position/moving color and supplies original board-change facts plus a conditional principle, never source comments or guessed player intention. Native GameLibraryController prepares one explanation off Main with cancellation/stale checks and a bounded128-entry exact content/version/ply cache. Cold exact-source/private restore recomputes facts offline; no schema/source/legacy identifier changes.

The existing separate offline Stockfish adapter compares the next recorded move at the current original board with bounded candidates. Root/history/budget/side/terminal/candidate/original identities and PV legal notation are checked. GameReplayScreen and the reused EngineAnalysisPanel clearly separate original moves, Ashva facts, user-supplied unverified comments and hypothetical engine lines. Analysis/preview pause original autoplay; original controls disable during preview. Explicit return preserves cursor/score/result, and POV flip changes orientation/score perspective without rerooting or replacing a game. Endpoints do not invent a next recorded move. Engine history above512 is explicitly unavailable without discarding repetitions; private full replay/teaching keeps4096.

UciAnalysisEngine now reuses the immutable root already checked with full original history for all output lines/separate searches, and reuses sanAndPlay’s legal transition instead of replaying each move again. Repetition-dependent terminal, metrics/identity/coherent-batch and existing search/output limits remain. Five new common semantic cases, two JVM history/PV fixtures, four new native coach cases and an existing cold-bookmark assertion cover both POVs, actual Stockfish/provenance, near equality/malformed/timeout/unavailable output, late cancellation, background/process stop, full short autoplay/cache/branch return and long history.

README, USER_TESTING, GM_LIBRARY, new HISTORICAL_GAME_COACH, ENGINE_ANALYSIS, PRIVACY, PUBLIC_READINESS and current Relay architecture/tracker now describe the verified feature and limits. Owner emulator updated in place to0.14, MainActivity foreground verified; no uninstall/clear/instrumentation or learner-row inspection. Whole goal remains unfinished; next local service task is separate.

## Commands run and their outcomes

-0029 records initial compile repair,160JVM/128host/debug/lint success and four focused native cases.0030 records first full command **FAILED9m57s,55/56native**, only actual coach analysis timed out; no partial recommendation was accepted. The core preserves explicit retryable errors rather than pretending every finite search must complete on every device.
- After root/transition optimization, a full rerun **FAILED1m5s before instrumentation** in Kotlin Android-host incremental classpath compilation (`DirtyData` could not derive AnalysisPosition). No app assertion/source/legal bound was relaxed. Cleaned only generated shared build output through the normal Gradle clean task; no learner/source data or project settings removed.
- Final `ANDROID_SERIAL=<isolated-test-serial> ./gradlew :shared:clean :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest`: **BUILD SUCCESSFUL10m54s;162JVM/128Android-host/24importer/all56complete native cases**, zero failures/errors/skips. Gradle restored some exact-input artifacts/test results from cache; actual final host/native ran. All56 aggregate XML and both APK metadata0.14/code15 verified. Whole January native install/cold rebuild takes219.412s cumulatively within unchanged per-stage waits, not a raised timeout or physical-phone performance claim.
- `./gradlew :contentTools:run --args='verify broadcast-2020-01'`: **SUCCESS13s;857accepted/0duplicates/95quarantined,1–253plies**, byte-identical. Default verifier reproduces3815opening routes/79April scores with0duplicates/quarantine. Original/new reviewed pack bytes/dependencies stay unchanged.
- Both **final0.14** debug/unsigned-release APKs pass `verify-engine-apk.mjs`: exact engine/source/NNUE/recipe hashes and notices. An earlier release-asset check was still on0.13 while packaging was pending; final package metadata/hash checks are distinct and current.
-26Node cases pass; final-doc public surface248files/0findings; pinned actionlint1.7.12exit0. Redacted Gitleaks8.30.1 rerun/context/doctor/public recheck follow final log; only actual tool outcomes may be asserted.
- Owner learning install: exact-serial `install -r` **Success**, `am start` succeeds, package metadata0.14.0/code15/min26/target37 and topResumedActivity/MainActivity verified. No owner app clearing/uninstall/instrumentation/screen or learner-row read. All connected tests use the isolated API33 emulator only.

## External resources touched

Previously reviewed Lichess taxonomy/January-April broadcasts, pinned Stockfish19/NNUE/source/recipe and local Gradle/SDK/emulators only. Native/private fixtures are synthetic. No new archive/API/account/provider/paid service, cloud/deployment/publication or agent Git command. Original Apache-2.0 code, separately scoped dataset notices and approved standard-UCI GPL executable remain intact.

## Risks, warnings, and what is NOT done

This completes the finite offline historical-game coaching milestone, **not the whole goal**.936 accepted records do not establish full careers, authenticated identities or representative master popularity. Ashva facts/conditional principles are implementation-reviewed, not independently expert annotations or GM intentions. Bounded candidates are strongest found at a stated budget, not proven best/blunders/wins. Timeout/unavailable states remain possible and truthful; the native fixture permits one deliberate UI retry only on the expected timeout, without altering budgets or accepting fake output.

The checked-prose cache is bounded memory reuse, not persistent engine cache; cold explanations recompute locally. Source/private mainlines and results stay immutable; stored private RAVs are not yet a navigable annotated-variation course. No cloud sync/account/remote per-move narration/export/recovery or physical-device battery/frame/store-compliance proof. Recall/Profile demos, service, board quality and playable iOS remain separate tasks. Shared iOS compile is not a client. Continue sequentially without another course/roster approval gate, preserving owner-only Git/publication and cloud/budget/data-rights boundaries.

## Next

Claim content-analysis-service and implement/test the local source-attributed API, versioned pagination/delta packs, bounded deduplicated analysis/cache/jobs and provider backoff without cloud/account/deployment changes. Validate a local Kotlin/Ktor-compatible contract first; approved reviewed content and the shared legal/analysis contracts are inputs, not permission for new providers or automatic private-game uploads. Log actual results, finish that task only after its gates, notify the owner and immediately claim the next remaining milestone.
