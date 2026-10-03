---
seq: 24
agent: codex
date: "2026-10-02T15:17:38Z"
task: repertoire-coverage
status: done
summary: "Verified Android 0.11 finite repertoire coverage across 149 families, guided deeper routes, named sets and two licensed score samples; all 45 isolated UI cases pass after legal-replay optimization. Richer teaching and GM coaching remain separate unfinished milestones."
next: "Claim grounded-teaching-content and add structured position-specific strategic and tactical teaching to current replay and engine alternatives, without changing retained lesson identities."
supersedes: []
git_head: cba53d25fcf1
---

# repertoire-coverage

## What I did

Completed the finite coverage milestone, not the entire repertoire-teacher goal: all 149 source families are reachable from the main Learn screen with 3,815 preserved named routes, 78 legal illustrative longer continuations across 47 families, conditional both-color guidance, automatic hints, full replay, explicit branching/return, source boundaries and exact cold restoration. Named revision-pinned multi-family sets and two manually installable historical score populations are verified. These user-visible behaviors, rather than taxonomy counts alone, support this milestone. Richer position-specific strategic teaching and original-game coaching remain separate unfinished tasks; no exhaustive-theory or independent-expert-review claim.

After log 0023's real installation timeout, removed redundant legal work without raising the 180-second mobile waits or weakening checks. SAN parsing now returns a checked move/notation/next-position transition; PGN keeps one immutable legal replay, canonical unannotated games reuse it, legal en-passant identity checks only adjacent candidate pawns, Room naming uses the same checked score positions, and observed aggregation reuses SAN transitions. Tests cover special moves, disambiguation, false check/mate suffixes and repetition history. Original taxonomy/April and new January pack bytes reproduce unchanged.

The optimized focused Android case installed within the original wait, then failed because its exact-text assertion expected only a prefix of a complete explanatory paragraph. Corrected the assertion to require the whole production text, not a shorter wait or a relaxed substring check. Added phase timing evidence. The final complete regression, including all existing cases, now passes.

## Commands run and their outcomes

- Final command: `ANDROID_SERIAL=emulator-5556 ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest` — BUILD SUCCESSFUL in 9m 6s. Reports: **138 JVM / 109 Android-host / 24 importer / 45 complete isolated Compose cases**, zero failures/errors/skips. Debug, unsigned release, lint and both shared iOS compilation targets succeed. Four pre-existing update lint warnings remain; no iOS client/runtime claim.
- Whole January case: 207.938 seconds total. Phase timestamps measured from its start: taxonomy installed 32,518 ms; January installed 126,701 ms; first observations ready 166,432 ms; cold observations ready 206,702 ms. Installation and cold indexing each pass their unchanged 180-second waits. The reopened database/index retains all 857 scores, exact source identity and the old learner selection; test deletes only its unique fixture database.
- `./gradlew :contentTools:run --args='verify broadcast-2020-01'` — BUILD SUCCESSFUL 13s, byte-identical 857 accepted / 0 duplicate / 95 quarantined. Default verification preserves 3,815 taxonomy / 79 April records. Combined sample: 936 score records / 82,679 half-moves, not master popularity.
- Earlier optimized focused command failed in 2m 48s, one case / one exact-text assertion failure (129.243 seconds total); this followed successful installation, unlike log 0023's timeout. The final complete result above verifies its correction.
- Both final APKs pass `node scripts/verify-engine-apk.mjs <APK>` with exact engine/source/NNUE/recipe hashes and required notices. Tooling tests: 26 passed / 0 failed / 0 skipped. Public audit before this log: 221 candidate files / 0 findings; redacted pinned Gitleaks 8.30.1: 0 findings; pinned actionlint 1.7.12: exit 0/no diagnostics. Repeat public/context/doctor after final documentation.

## External resources touched

Reused the separately reviewed Lichess January 2020 broadcast snapshot and its CC BY-SA 4.0 provenance/provider checksum from log 0023; no additional acquisition or provider request. Used isolated emulator-5556 for instrumentation and in-place installation on the owner's emulator-5554 only after the complete pass. No learner data reset, Git operation, account, spend, deployment or publication. Stockfish remains a separately approved UCI executable, not linked into Ashva.

## Risks, warnings, and what is NOT done

The finite coverage milestone is complete; the overarching teacher goal remains active. Source naming depth is 1–36 half-moves and deeper authored routes are illustrative 15–33 half-moves in 47 families, not all theory. Strategic guidance is original/conditional and implementation-tested, not independently expert-reviewed. The 936 accepted records include short reported scores and are not authenticated careers or representative popularity. GM browsing/full-game coaching, richer grounding, recall, board performance, local service contracts and iOS client remain unfinished. Shared iOS compilation does not deliver an iOS app. Keep stable package/database/schema4 and retained content/policy/bookmark/set revisions; no automatic rebinding, pruning, destructive migration or source-byte edits. Owner alone handles Git/publication/store compliance.

## Next

Claim grounded-teaching-content and add structured position-specific strategic and tactical teaching to current replay and engine alternatives, without changing retained lesson identities. Start from PositionCoach and TrainerScreen, use legal board facts and conditional original plans, and retain historical/engine/source distinctions. Continue immediately after recording the coverage completion; do not treat this checkpoint as the full goal achieved.
