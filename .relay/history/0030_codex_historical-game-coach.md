---
seq: 30
agent: codex
date: "2026-10-02T20:58:01Z"
task: historical-game-coach
status: partial
summary: Full Android0.14 native run proves55/56 cases; only new actual-coach analysis timed out. Reduced redundant full-history/PV transitions without loosening checks or budgets; added repetition fixtures and a deliberate retry test. Complete rerun is underway.
next: "Finish the unfiltered full0.14 rerun after UCI root/transition reuse, inspect exact aggregate results, and only then complete coach docs/history and install the accepted build in place."
supersedes: []
git_head: cba53d25fcf1
---

# historical-game-coach

## What I did

Continued the0.14 historical coach in0029. The unfiltered full native suite found one actual-engine timeout; all existing game-library, opening, persistence and installation regressions passed. The timeout remained a retryable Error with no fabricated partial recommendation. It does not prove a particular scheduling cause or that every bounded search will finish on every device.

Inspection found avoidable UciAnalysisEngine work: position.board() reconstructed the complete original history for every scored info line/bestmove, and board.san(move) then board.apply(move) duplicated an already-checked transition. Search now reuses the immutable root checked once by analyze, and sanAndPlay’s checked result for each PV move. Original clocks/repetitions/status/legality/metrics/coherent candidate batches/identity checks remain; no search/output/wait bound changes. Added two JVM fixtures checking independent PVs and restricted searches preserve repetition history and still reject continuations past a history-dependent terminal draw.

The actual native coach fixture no longer leaves autoplay racing a scroll-to-Analyze before its root capture. It allows at most one deliberate UI retry **only** for the expected timeout error, asserting source/cursor preservation and process cleanup; repeated or other errors still fail. Playback→analysis pause is separately exercised atomically before backgrounding. No silent retry, budget increase or accepting fake output was added to the app. Full unchanged search ceilings remain.

Added docs/HISTORICAL_GAME_COACH.md and a concrete USER_TESTING development walkthrough; PROJECT/tasks distinguish implementation from full acceptance and keep owner install0.13. Previous partial checkpoints remain append-only facts.

## Commands run and their outcomes

- First unfiltered complete0.14 command (shared JVM/host, importer/default pack verify, debug/release/lint, both shared iOS targets, exact isolated native suite) **FAILED9m57s** at connected instrumentation: aggregate XML **56cases/1failure/0errors/0skips**,55pass. The failing actualOfflineOriginalCoachCacheBothPovPreviewReturnAndBackground returned the explicit analysis timeout at its recommendation assertion. Four focused coach cases had passed earlier; that is not a full pass.
- All five existing game-library cases, including new cold explanation assertions, pass in that run. Whole January native install/cold rebuild completes in207.027s within unchanged per-stage bounds; all other native suites pass. Prior shared artifacts160JVM/128host and24importer remain; this failed command is not final accepted0.14 evidence.
- Before the optimization, both APK engine/source/NNUE/recipe assets verified;26Node cases, public surface247files/0findings, Gitleaks8.30.1zero, actionlint1.7.12exit0, published context29logs/20tasks and strictdoctor pass. Final assets/context/scans must be repeated after accepted rebuild/final log.
- Fresh full command after UCI root reuse/two new JVM fixtures/retry test is running on the isolated emulator. No fresh162JVM or final native result is asserted yet. Owner emulator remains verified0.13/code14, untouched by instrumentation.

## External resources touched

Existing reviewed local datasets/Stockfish19/NNUE, local Gradle/SDK and isolated emulator only. Synthetic private PGNs/test identities. No new source acquisition/account/provider/deploy/spend/publication or agent Git operation; no owner clearing/uninstalling/learner-row reads.

## Risks, warnings, and what is NOT done

Historical-game-coach remains IN_PROGRESS. Root/transition reuse and new tests require their actual complete rerun; do not turn55/56 or the four focused cases into DONE. Defaults staydepth16/50k nodes/1000ms/MultiPV3/one thread; timeout/error remains possible and truthful. Cache is bounded ephemeral checked prose, not persistent engine cache or expert GM intentions. Original/private refs/schema/source bytes remain unchanged; engine histories over512 stay explicitly unsupported while full replay/teaching retains4096. Final0.14 docs, owner in-place install and final acceptance history are pending. Whole goal and all remaining tracker tasks remain unfinished.

## Next

Finish the unfiltered full0.14 rerun after UCI root/transition reuse, inspect exact aggregate results, and only then complete coach docs/history and install the accepted build in place. If another failure occurs, diagnose and append real outcomes; do not enlarge bounds or mark partial work done. On acceptance append the next final log, run Relay done/doctor, notify the owner and immediately claim content-analysis-service.
