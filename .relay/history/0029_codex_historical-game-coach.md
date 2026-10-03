---
seq: 29
agent: codex
date: "2026-10-02T20:46:01Z"
task: historical-game-coach
status: partial
summary: Implemented in-progress Android0.14 original-move coaching and separate bounded engine preview/return; shared semantic tests and debug/lint pass. Native engine/replay tests are running; full task acceptance remains unfinished.
next: "Finish isolated historical-game coach tests, correct any failures, then run the complete domain/importer/iOS/Android/native/public gates before marking the task done."
supersedes: []
git_head: cba53d25fcf1
---

# historical-game-coach

## What I did

Added shared OriginalGameCoach/OriginalMoveTeaching: a checked original move’s SAN/UCI, before/after FEN, moving color, board-change explanation and conditional principle. Source/user comments and NAG/RAVs never become invented historical intention. Engine requests preserve the declared initial FEN/full ordered history, with an explicit512-half-move analysis limit rather than dropping repetitions; private full replay retains its4096 bound.

Android development version0.14/code15 adds automatic one-move preparation off Main, stale/cancellation guards and a bounded128-entry exact version/content/ply explanation cache. GameLibraryController now uses the existing separate offline engine and validates root/history/budget/terminal status/candidate and compared-move identities before legally rechecking each PV and attaching explanations. GameReplayScreen/EngineAnalysisPanel label original facts, next recorded comparison and hypothetical continuations separately; original playback pauses during analysis/preview and explicit return preserves the original cursor/result/score. POV flip preserves the analysis root; exit/background/position changes stop/cancel without stale publication. Private commentary remains visible as unverified learner input. No source/schema/learner identities change.

Added five common semantic cases and four isolated native coach cases, plus cold-explanation assertions in the existing game-library case. Corrected a nullable cross-module smart-cast compiler error. After the focused pass, changed manual Play→Pause to preserve its already-checked move caption; this final small change still needs full gates. Owner emulator remains verified0.13, not this development build.

## Commands run and their outcomes

- Initial `:shared:compileKotlinJvm :androidApp:assembleDebug`: shared compiled, Android failed at a cross-module nullable original search smart-cast; fixed using a local nullable binding.
- `./gradlew :shared:jvmTest :androidApp:assembleDebug`: SUCCESS23s before new semantic cases.
- `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug`: SUCCESS33s;160JVM/128Android-host, zero failures/errors/skips, including the five new coach cases.
- `ANDROID_SERIAL=<isolated-test-serial> ./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.openinglab.app.HistoricalGameCoachTest,com.openinglab.app.GameLibraryLearningTest`: SUCCESS27s, but XML proves only **4 historical coach cases** ran, not the requested combined9. Actual Stockfish/perspective/preview/return/cache/background/full short autoplay, malformed/near-equal/timeout/unavailable, late cancellation and long history pass. Do not assume the library regressions or cold assertion ran; the following unfiltered complete suite must prove them.
- Full importer/release/both shared-iOS/all native/asset/public/secret gates are not yet fresh for0.14. The previous complete0.13 results remain in0028, not substitutes.

## External resources touched

Only the existing reviewed local content, pinned Stockfish/NNUE assets and local SDK/isolated emulator. PGNs/names are synthetic fixtures. No new archive/API/provider/account, deployment/spend, publication or Git command. No owner emulator instrumentation, clear/uninstall or learner-row inspection.

## Risks, warnings, and what is NOT done

Historical-game-coach remains IN_PROGRESS. The final manual pause adjustment, existing library regression/cold checks and full complete gates remain pending. The128 checked explanations are ephemeral local reuse, not a persistent engine cache or expert annotations; cold resume recomputes offline. Long original games remain replayable, but engine history beyond512 is explicitly unavailable. No GM intention, proven-best/blunder/win or full-career claim is made. Owner remains on0.13 until0.14 is accepted. The overarching goal and service/recall/quality/iOS tasks remain unfinished; no broad permission boundary changes.

## Next

Finish isolated historical-game coach tests, correct any failures, then run the complete domain/importer/iOS/Android/native/public gates before marking the task done. Use an unfiltered connected suite with exact isolated ANDROID_SERIAL; verify aggregate XML counts. Keep source score/Room rows unchanged, append a new final history entry rather than rewriting this partial checkpoint, then notify completion and immediately claim the next tracker task.
