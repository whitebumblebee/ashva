---
seq: 17
agent: codex
date: "2026-10-02T08:55:38Z"
task: repertoire-coverage
status: partial
summary: "Added Android v0.6.0 observed-move counts from the installed licensed 79-score fixture, first-visit transposition-aware aggregation, source provenance and Compose coverage/unavailable states; broader repertoire teaching remains partial."
next: Claim engine-alternatives and start the official Stockfish mobile/distribution feasibility audit with a portable bounded-analysis contract; keep broader repertoire coverage partial.
supersedes: []
git_head: cba53d25fcf1
---

# repertoire-coverage

## What I did

Continued repertoire-coverage and shipped the local Android v0.6.0 / versionCode 7 slice. Package com.openinglab.app, database opening-lab.db, schema 3, policy revisions/bookmarks and immutable pack bytes remain unchanged. No owner Git work was performed.

Added common ObservedReplyIndex / ObservedGameSample and eight tests. Scores legally replay with SAN/UCI/final-FEN checks and worker cancellation checkpoints. Aggregation combines normalized transpositions and counts only the first visit per stable score ID/position. Scores ending on that first visit are outside the reply denominator. Matching IDs across packs count once with both provenances; conflicting scores reject the index. Different IDs are not guessed duplicate real games or verified player aliases. Source manifests, hashes, licenses, modifications, scope and dependencies remain attached. Input bounds are 32 packs / 10,000 rows / 500,000 half-moves; oversized input fails, never truncates.

Added LearningStore.games(packId?) using existing Room queries and worker JSON decoding, with no migration. Extended the real-pack cold-storage regression to check all 79 rows, retained reads, missing-pack behavior and the 79-reply starting-position denominator. Android's cancellable index is keyed by active broadcast pack IDs/hashes, separate from catalog/bookmark restoration. Missing/loading/error/ready-zero states are distinct. Version withdrawal cancels and clears observations; retries reread active versions, and generation guards prevent stale metadata reads from resurrecting old counts.

RepertoireScreen now shows position-specific denominators, selected/unselected/outside-family totals, per-option observations and read-only outside-snapshot moves. Neither frequency nor a source observation adds a lesson route or changes choices. Expandable source details retain source revisions, CC-BY-SA attribution, modification notices, manifest checksums and the exact CC0 taxonomy dependency. Library navigation and Retry are explicit actions. Offline-library wording now reflects the real editor rather than calling all repertoire choices future work. Three new Compose cases cover actual counts/provenance/non-mutating navigation, missing/loading/error/zero UI states, failed read/retry, failed active-version read and cancellation after sample withdrawal. Added the existing centrally pinned JSON library to androidTest only, not a new dependency version/channel.

Updated README, testing/coverage/storage/pipeline/privacy/readiness docs and Relay truth. No new chess source, engine, service, paid provider or dataset rights assumption. The 79-score fixture is explicitly not popular-master coverage, winning odds or reviewed strategic teaching. Broader coverage remains IN_PROGRESS. The next major implementation action is engine feasibility/contracts under its separate task.

## Commands run and their outcomes

- Final-source gate: `ANDROID_SERIAL=emulator-5556 ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.openinglab.app.ObservedRepliesTest` — **BUILD SUCCESSFUL**, 2m21s. **99 JVM / 86 Android-host / 17 importer / 3 focused Compose tests passed**, zero failures/errors/skips. Both shared iOS targets compile, not an iOS client/runtime. Debug/unsigned release metadata both show versionCode 7 / 0.6.0. Lint has **0 errors / 4 existing update warnings**; Room expect/actual-class warning remains unsuppressed.
- Initial build failed on a missing BoardPosition test import; corrected and repeated JVM/debug successfully. Initial cross-platform gate failed because the Android test classpath lacked JSON; added the already-pinned androidTest dependency. No validation was weakened.
- First complete isolated 31-case regression: **30 passed / 1 failed / 0 errors / 0 skipped**, 8m41s. All **28 pre-existing UI cases** passed. The new actual-sample test selected the wrong library Back label. Corrected the label and lazy scroll. First focused repeat: **2 passed / 1 failed**, because the observation label is inside a merged selectable row. Corrected the test to inspect its unmerged child semantics. Final focused gate above passes all three new cases, including the subsequent active-version retry hardening. This is **not a claim that a second complete 31-case suite passed on the final APK**; final changes are covered by the focused source/library/retry cases.
- Pack verification reproduced **3,815 taxonomy / 79 broadcast records**, zero duplicates/quarantine, unchanged bytes. Actual first-move counts: e4 26, d4 45, Nf3 3, c4 4, e3 1. Repeated visits never inflate votes. Counts describe the current position, not repertoire mastery or an opening-specific population.
- `node --test scripts/*.test.mjs` — **20 passed / 0 failed / 0 skipped**. Public audit and portable Relay context pass with zero findings. Redacted Gitleaks **8.30.1 / 0 findings**. Official actionlint **1.7.12 / exit 0 / no diagnostics**, with release archive SHA checked against the official checksum file before execution. Repeated public/secret/context/strict-doctor checks after this log; see terminal outcomes. No hosted CI/publication claim.

## External resources touched

Only the existing local CC0 taxonomy pack lichess-openings-c67912be581f-import-v1 (upstream c67912be581f0793dbaa776be5ccf111e01f88d9) and CC-BY-SA-4.0 broadcast pack lichess-broadcast-2020-04-2020-04-snap-import-v1 were used. No chess data/provider was fetched or altered. Downloaded the pinned official Gitleaks release and official actionlint v1.7.12 archive/checksums for local checks. Previous temporary AVD was absent; created a new disposable API33 AVD using the installed SDK image, with its private path recorded only in ignored local audit configuration. Ran instrumentation solely on emulator-5556, then shut it down; the private AVD is retained. Owner emulator/data were not started, installed, instrumented, cleared or removed. Relay/AGP automatically read HEAD metadata; no direct Git operation, commit, remote, push, upload, account change, paid service or publication by the agent.

## Risks, warnings, and what is NOT done

This is a source-attributed fixture observation slice, not completion of repertoire-coverage. The sample is one April 2020 broadcast export, with 79 score IDs / 15 unresolved source-scoped player names. IDs can distinguish two records of the same real game; no guessed cross-ID matching. Normalized positions omit clocks/repetition history, so counts are not game-state/engine evaluations. The first visit rule intentionally ignores later visits with different replies. A source endpoint with no reply is not a played-move vote. Zero observations do not establish move quality, rarity in all chess, or an impossible continuation.

The index is one bounded ephemeral cache, rebuilt on cold launch; large-corpus memory/performance/indexing remains unbenchmarked and needs a scale strategy rather than blindly raising limits. Reads of retained exact packs do not automatically select those versions for the active sample. No new preferred moves, learner attempts, policy revisions or source routes are inferred from counts. Choices remain one family/color/snapshot, and source-prefix fitting is not all-theory coverage. Broader cleared populations, deeper release targets, reviewed both-color plans and multi-opening aggregation remain, along with engine alternatives, GM shelf/coach, recall scheduling, board performance/accessibility QA and iOS UI.

No engine binary/NNUE, new distribution license, remote explanation provider or hosted service was added. Stockfish execution/distribution and GPL interaction must be audited before deciding how to bundle/link/serve it; do not silently change the owner's Apache-2.0 app license. No representative-device frame/memory/visual parity claim, new screenshot audit, Git-history scan or hosted CI success. Instrumentation can uninstall its own target; continue exact-serial isolated tests only.

## Next

Claim `engine-alternatives` and start the official Stockfish mobile/distribution feasibility audit with a portable bounded-analysis contract. Define legal MultiPV/original-move comparison, explicit budget/version/NNUE provenance, White/Black score perspective, mate/status and cancellation semantics; begin tests with deterministic fake engine output before an audited platform spike. Keep broader repertoire-coverage partial, and preserve original games/pack licenses/policies. Do not bundle a GPL engine or provision a paid/remote provider until distribution/authority choices are resolved. No Git work or actual publication: the owner handles those.
