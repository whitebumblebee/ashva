---
seq: 25
agent: codex
date: "2026-10-02T15:40:35Z"
task: grounded-teaching-content
status: done
summary: "Added Android 0.12 structured both-color position teaching and checked per-move engine explanations; 145 JVM, 116 Android-host, 24 importer and all 47 isolated UI cases pass, with retained lessons and learner identities unchanged."
next: "Claim gm-game-library and implement source-traceable full-score browsing, explicit player following, private PGN import and offline original-game replay with durable exact-source restoration."
supersedes: []
git_head: cba53d25fcf1
---

# grounded-teaching-content

## What I did

Implemented a shared, versioned position teacher and native Compose Understand this position panel for every lesson position in Study and for hypothetical engine previews. It separately explains the chosen side and opponent: exact material/pawn squares, doubled/isolated/passed pawns, chains/blocked centre, open/half-open files, home-minor occupancy, king/check/castling rights, geometric attackers/defenders, multiple attacks and king-ray pins. Legal current-turn central pawn contacts use exact SAN/UCI; no alternative turn is invented for the other color. Each statement distinguishes board fact from original conditional plan. Development/reduced-material/coordination are explicit teaching heuristics; check/terminal status takes precedence and terminal boards produce no move candidates.

Conditional teaching covers supported breaks, pawn support/blockade, development, rook coordination, king exposure, pawn races/simplification/stalemate and opponent counterplay, without winning/blunder/GM-intention claims. This supplements the existing family/branch guides across openings rather than changing their persisted content. Strategic prose is original and implementation-reviewed with semantic fixtures, not independent expert certification or imported historical commentary.

Extracted BoardPosition.attackersOf using the same blocker-aware geometric attack predicate as the existing rule. Pinned attacks are explicitly not legal captures. GroundedContinuation rechecks engine SAN/UCI and legality/history before attaching PositionCoach's actual move explanations. Candidate and preview panels explain exact board changes and allow both-color position ideas; scores/budget/provenance/near-equality remain separate. Lesson/source/policy/set IDs, fingerprints, schema4, old book content and learner data are unchanged.

Panel computation uses a cancellable worker only when expanded, keyed by FEN/history/POV with stale-result checks, explicit loading/error and disposal. Hidden Practice does not reveal this panel; retained automatic hints still work. Added seven common semantic cases and two native cases; extended the actual offline engine test to check every preview SAN/explanation correspondence and visible move idea. Android version is 0.12.0/code13; no library upgrades or XML screens. Updated README, testing/evidence/engine/privacy/coverage documents and Relay current state.

## Commands run and their outcomes

- First compile check failed in 7s: the extracted pawn-attack predicate retained an old local variable name. Corrected it to the piece's actual color; rerun builds/common/iOS/lint passed in 43s. Do not treat that first check as a success.
- Final command: `ANDROID_SERIAL=emulator-5556 ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest` — BUILD SUCCESSFUL in **10m 4s**. Reports: **145 JVM / 116 Android-host / 24 importer / 47 complete isolated Compose cases**, zero failures/errors/skips. Debug, unsigned release, lint and both shared iOS compilations succeed. This is not an iOS app/runtime.
- All four actual engine cases and two new position-teaching cases pass. Full regression preserves opening courses, source/legacy cold restoration, hints/branches, observed populations, immutable multi-family set queues and Room installation. Whole January timings from case start: taxonomy 29,819ms, January 124,185ms, initial observations 164,617ms, cold observations 208,888ms; each install/rebuild stays within its unchanged 180-second wait.
- Default pack verification reproduces 3,815 taxonomy / 79 April records unchanged; the separate January verification is repeated before completion, 857 accepted / 0 duplicate / 95 quarantine. No generated pack/source bytes are edited.
- Both final APKs verify exact Stockfish/source/NNUE/recipe hashes and notices. `node --test scripts/*.test.mjs`: **26 passed / 0 failed / 0 skipped**. Public audit before log: **227 candidates / 0 findings**; redacted pinned Gitleaks8.30.1:0 findings; pinned actionlint1.7.12:exit0/no diagnostics. Published context and strict doctor pass; repeat after the finalized log/index.
- Owner learning emulator-5554 updated with `install -r`, not uninstalled/cleared/instrumented. Confirm code13/version0.12 and launch after installation; isolated emulator-5556 alone ran tests.

## External resources touched

Read https://handbook.fide.com/chapter/E012023 (2026-10-02) for attack/king-safety semantics, especially articles3.1.3/3.9; those laws support rule distinctions, not Ashva's conditional strategic prose. No FIDE strategy recommendation or copied commentary. Existing source packs and prepared separate offline Stockfish19 are reused; no new chess acquisition, account, credentials, paid model, deployment, Git operation or publication.

## Risks, warnings, and what is NOT done

This completes the local structured-grounding milestone, not the entire teacher goal, every endgame, independent expert review, forced winning tactics or historical annotation. Geometric pins/forks/attack counts require legal exchange calculation; the UI says so. Pawn-contact candidates are a specific one-move central lens, not every preparable break or an engine ranking. Historical-game browsing/following/private import/full-score coaching remain next; recall/assisted analysis accounting, final board performance, local service and iOS client are also unfinished. Goal remains active. Retain package/database/schema/source identity and all existing learner revisions. Owner handles every Git/publication action and production compliance.

## Next

Claim gm-game-library and implement source-traceable full-score browsing, explicit player following, private PGN import and offline original-game replay with durable exact-source restoration. Start with an empty follow list so the user chooses players in the app; do not infer favorites or authenticate source-reported FIDE identities/titles. Use retained exact pack versions for bookmarked games, not whichever pack is active after updates. Continue immediately after the milestone notification.
