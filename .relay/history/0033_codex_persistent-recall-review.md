---
seq: 33
agent: codex
date: "2026-10-02T22:54:58Z"
task: persistent-recall-review
status: partial
summary: "Implemented in-progress Android0.15 chosen-scope recall, additive schema6 and real Home/Review/Profile;176JVM/136host and four focused native cases pass. Full60-case regression is running, not complete."
next: "Finish the running isolated full recall regression, resolve any failures, then verify service/schema and public gates before completing persistent-recall-review; keep iOS client deferred."
supersedes: []
git_head: cba53d25fcf1
---

# persistent-recall-review

## What I did

Added shared review/Recall.kt: exact-history/color/moves-only snapshot identities, shared-prefix deduplication within a course, chosen admitted-policy/set/route denominators and original deterministic schedule version1. Unaided due answers progress1/3/7/14/30/60/120/240days; assisted/unsuccessful answers retry after10min. Early drills cannot advance spacing; out-of-order/backwards timestamps do not roll state back. Established is three separated due successes and not overdue, not calibrated mastery. No imported scheduler/model code or copied data.

Additive Room6 cards/scopes/membership/pointers/events/Study views, all five migrations on Android/JVM/shared-iOS builders. Exact events/attempts/scheduling commit atomically, retries idempotent, conflicts fail, previous revisions/legacy attempts retained without backfill. Bounds and SQL aggregate flows avoid reading unbounded old-attempt JSON. Version/failure/cold/idempotence/migration fixtures added;1–5 include synthetic retained follows/private rows. Existing source/private/learner identifiers unchanged.

Native RecallController supplies a bounded serial writer, worker preparation/validation, actual totals/scopes and one-position due queue. Review retains exact policy/context and waits for the actual answer ledger before Next; cold checks use exact event ID rather than an old timestamp. Matching successful engine output marks Practice assistance and persists it; failed/stopped/stale requests do not. Explicit/automatic hints, branch adoption and ten-minute Study exposure are separate help context. Original-game/hypothetical previews never generate grades. Full idea is a separate Study view. Existing bookmarks gain defaulted fields, not rewritten old JSON.

Replaced demo Review/Profile and Home due/streak/percentage displays; existing opening.progress sample field remains in the domain for compatibility but is no longer displayed or used to choose Continue. Added four unique-DB isolated recall tests including actual Stockfish and cold assistance, both-color queue and pinned-set denominator. Version0.15/code16 builds locally; owner emulator remains0.14, not installed/cleared/instrumented/read for learner rows. New RECALL_REVIEW and updated storage/user instructions document measured semantics and limits. Current verified project version remains0.14 until this milestone's final gates pass.

## Commands run and their outcomes

- Initial shared compilation/existing suites SUCCESS31s; new common/store fixtures then SUCCESS26s. One native compile failed on nullable cross-module bookmark smart cast; fixed with a local nullable let, subsequent debug/test APKs/lint/domain SUCCESS32s.
- Current XML:176JVM/136Android-host/24importer/7service-unit,0failures/errors/skips. Includes8 new common recall and6 repository cases; migrated1–5 and future-schema retention fixtures pass. Some final full-run build steps were still pending when logged.
- Focused isolated RecallLearningTest:4cases/0failures/errors/skips, native actual engine/cold help/real UI/set-denominator checks; SUCCESS25s. The comma-separated class argument only produced the four recall cases, not the requested PublicAlpha cases; no seven-case claim.
- Separate PublicAlphaTest:2/3 pass, one failed because performScrollTo targeted an uncomposed final lazy-list item. Added app-version key and explicit lazy-key scroll; full rerun, including this case, is underway. Public synthetic screenshot case passed30.285s. No owner screenshots/learner rows captured.
- Running full gate command: ANDROID_SERIAL=<isolated-test-serial> ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :contentService:test :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest. Unified exec34806 still running; output lists only Ashva_Source_Test/API33. Shared compiles/default3815+79 pack verification passed; no full native/release/final completion claim yet.
-28Node tests pass; public surface271candidatefiles/0findings; context32logs/20tasks/0findings before this new log. Final public/secret/Relay checks remain.

## External resources touched

Read-only official Anki manual and open-spaced-repetition repositories/spec/license references. FSRS Kotlin has no standalone LICENSE at the inspected main URL; FSRS Rust license is BSD3, not presumed MIT, and main also contains newer model research. No dependency/code/dataset adopted; Ashva's bounded original schedule is explicitly not FSRS or a fitted recall probability. Existing Maven/SDK/emulator/Stockfish inputs only. No source-data bytes/rights expansion, Git/publication, account/provider/cloud/spend or new iOS client. Owner-approved Docker is available for service/schema regressions; no new test container was started in this partial pass.

## Risks, warnings, and what is NOT done

Task is IN_PROGRESS, not DONE; final native60-case/full build/service-schema/public gates and owner in-place update remain. Full native output may take several minutes on whole-January installs/cold rebuilding; do not treat a stale XML from the earlier failed PublicAlpha run as the running suite's result. Only test serial5556 is authorized for connected tests;5554 retains0.14 learner data. No destructive migration/fallback/pruning. Review exposes last-practiced immutable scope revisions; missing inactive course versions remain explicit/unavailable, not silently adopted. Study view is an exposure count, not time spent/each-move comprehension. Original conservative scheduling is not empirically calibrated, expert pedagogy, notifications, cloud sync or a rating. Failed/refused writes remain visibly unsaved; cold interruption can lose an unconfirmed action, just as bookmarks require waiting for Saved. UI/error recovery and full-regression outcomes still need inspection before acceptance. Board-quality and the overarching goal remain unfinished; ios-client is owner-deferred and must not be claimed/implemented.

## Next

Finish the running isolated full recall regression, resolve any failures, then verify service/schema and public gates before completing persistent-recall-review; keep iOS client deferred. Resume unified exec34806 with write_stdin, not functions.wait; it is an exec_command session. Read the completed aggregate testsuites count and current timestamps, not only the first suite or older reports. After final gates write a new append-only completion log, then done/doctor, notify owner, immediately claim compose-board-quality. Do not edit this finalized partial history later or compact the round.
