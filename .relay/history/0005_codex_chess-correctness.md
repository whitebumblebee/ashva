---
seq: 5
agent: codex
date: "2026-09-30T19:29:15Z"
task: chess-correctness
status: done
summary: "Implemented tested legal rules, notation/PGN, transposition-aware seed identification, and Compose PGN/FEN input; Android and iOS shared compilation verified."
next: Claim lesson-graph-replay and build the shared lesson path/branch cursor model on the validated BoardPosition and Pgn contracts before adding Study controls.
supersedes: []
---

# chess-correctness

## What I did

Implemented the user-selected rules/identification foundation, not the full teaching/data roadmap. Continued the same task after context compaction, read the Relay skill and current project/tracker/index/log 0004, inspected the leftover source, and renewed the expired same-harness claim. No other agent's live claim was taken.

- Replaced `shared/.../chess/BoardPosition.kt` with standard legal move generation and immutable state: king safety/check/checkmate/stalemate, permanent castling-right loss and attacked transit squares, en passant including discovered-check legality, explicit four-piece promotion, FEN clocks, history-dependent repetition claims/automatic draws, and conservative insufficient-material detection. Illegal moves throw rather than silently changing state. Position identity includes side, castling rights, and legally usable en passant while excluding clocks/history; game history stays separate.
- Added `ChessNotation.kt` for legal SAN formatting/parsing and strict UCI validation in `ChessModels.kt`. Added bounded `Pgn.kt` import/export for standard chess, setup FEN, tags/escapes, comments, NAGs, recursive annotation variations, archives, and explicit results/errors. Branches start at the position before the move they replace; the original main line remains unchanged. Imports enforce character/token/ply/depth limits; this is not a streaming bulk importer.
- Replaced prefix/percentage identification in `OpeningIdentifier.kt` with a normalized-position index and explicit known, ambiguous, unknown, invalid, empty, and last-known/out-of-book states. Added defining-ply metadata to seed models/catalog so an early Sicilian is not prematurely called Najdorf and a line without Bf4 is not identified as London. On resume, the actual source still retained unnamed early ambiguous candidates as last-known openings; fixed this with an explicit `isNamedPosition` distinction and the existing failing regression test. Source and fresh test results take precedence over the earlier context's reported completion of that fix.
- Updated Compose `IdentifierScreen.kt`, `OpeningLabApp.kt`, and `AppViewModel.kt` with single-game PGN/FEN text input, background validation, proper SAN/move numbering, explicit position/draw status, import errors that preserve the board, undo from the imported initial position, and Queen/Rook/Bishop/Knight selection. Cancellation/revision checks protect import state; pending opponent replies are canceled on trainer restart. Updated Explore copy to state seed coverage honestly. No XML screen implementation or dependency change.
- Added/expanded common rule, notation/PGN, identifier, and perft tests. Updated `README.md`, `docs/USER_TESTING.md`, `docs/LEARNING_DESIGN.md`, and `.relay/PROJECT.md` to describe what now works, repeatable import/promotion checks, and remaining limitations. Existing README screenshots are explicitly labeled earlier prototype captures.

Opening content remains seven families/thirteen authored short variations. No external opening corpus or historical game scores were bundled.

## Commands run and their outcomes

- Relay initial status on resume: 13 TODO, 1 IN_PROGRESS (expired codex chess-correctness claim), 0 blocked, 3 DONE; four history entries and no Git repository. `claim chess-correctness --agent codex` succeeded and was renewed between major steps.
- Gradle without escalation in the earlier part of this work could not access the wrapper lock under `~/.gradle`; running the requested gates with approved escalation succeeded. Initial expanded validation exposed the unnamed-candidate/out-of-book regression. A fresh resumed run of `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64` failed with **39 tests, 1 failure** (`noFalseLondonBeforeBishopDevelops`: expected UNKNOWN, got OUT_OF_BOOK). Corrected the source and reran the exact command: **BUILD SUCCESSFUL in 7s; 74 actionable tasks, 25 executed/49 up-to-date**.
- Final XML test reports: **39 passed, 0 failed, 0 errors, 0 skipped**: BoardPositionTest 3, ChessRulesTest 13, NotationPgnTest 8, OpeningIdentifierTest 9, PerftTest 6. All authored seed moves are legally replayed and their SAN validated. Perft covers eight reference positions, including start depths 1–4 (20/400/8902/197281), Kiwipete depths 1–3 (48/2039/97862), rook/pawn endgame depth 4 (43238), promotion/check fixtures (9467 and 62379 at depth 3), complex middlegame (89890), and two en-passant/check alignments (711/555).
- Android debug assembly and lint completed; lint has **0 errors and 4 update warnings**: Gradle 9.7.1 -> 9.8.0, core-ktx 1.19.0 -> 1.19.1, Navigation 3 runtime/UI 1.1.7 -> 1.2.0 (two warnings). No versions were changed; audit official compatibility/release notes before upgrading. The existing compose-board-quality task already includes that audit; these warnings do not create a new user-approved priority.
- Both `compileKotlinIosSimulatorArm64` and `compileKotlinIosArm64` succeeded. This is shared-code compilation only, not an iOS application/runtime test. The earlier simulator build downloaded Kotlin/Native compiler dependencies through Gradle; no mobile engine was installed.
- Reinstalled the final APK on API-33 `emulator-5554` with SDK `adb ... install -r`: **Success**. First launch/dump raced installation and returned a launch timeout/null root; retried launch and obtained fresh dumps before assertions. The app launched successfully. No unrelated foreground app was modified.
- Final-APK emulator checks: imported `1. f3 e5 2. g4 Qh4# 0-1`, confirmed all four SAN entries and White checkmate; Undo removed Qh4# and restored Black to move. Imported `7k/P7/8/8/8/8/8/7K w - - 0 1`, confirmed all four promotion buttons, chose Knight, observed `a8=N` and insufficient-material status; Undo restored the a7 pawn and the two kings at the imported FEN, not the standard start. Imported `not a FEN`, observed the six-field error while that board remained unchanged.
- Final-APK trainer smoke: attempted off-line d2d4 at the White start, observed unchanged position/0% and lesson feedback; then completed all eight White learner moves in the Ruy López/Morphy line with automatic replies, including both sides' castling, reaching **100% / LINE MASTERED** with one retry. Started Black, confirmed flipped board and automatic e4, then completed all eight Black learner moves, reaching **100% / LINE MASTERED / Perfect recall**. UI summary strings are uppercase; some ad-hoc case-sensitive probe booleans were false despite the visible matching text. Assertions were resolved against fresh visible text, not stale XML.
- Earlier pre-resume emulator interaction entered the transposed London move order manually and observed the known London family. Final shared tests independently verify this path, unknown/invalid separation, last-known continuation, genuine ambiguity, and agreement of FEN/PGN recognition. Do not describe that earlier manual interaction as a fresh final-APK check.
- Read-only Node checks: five updated documents readable, **6 local Markdown links and 6 required command/harness paths exist**. Final APK size **19,122,999 bytes**, at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- Before logging, `relay doctor --strict`: exit 0, all checks passed, four history entries/17 tasks. This log was created by Relay; index generation remains CLI-owned. `relay done chess-correctness`: DONE citing 0005. Post-handoff `doctor --strict`: exit 0, all checks passed, five history entries/17 tasks. `status`: 13 TODO, 0 IN_PROGRESS, 0 blocked, 4 DONE; next lesson-graph-replay.

## External resources touched

Read-only reference research during this work (no corpus or engine binary download):

- https://raw.githubusercontent.com/official-stockfish/Stockfish/master/tests/perft.sh — reference move-enumeration fixtures.
- https://raw.githubusercontent.com/niklasf/python-chess/master/examples/perft/tricky.perft — lower-depth reference node counts.
- https://www.saremba.de/chessgml/standards/pgn/pgn-complete.htm — original PGN specification mirror for notation, FEN, tags, and recursive variations.
- https://python-chess.readthedocs.io/en/latest/core.html — legal-move/history/en-passant reference behavior; python-chess is not a runtime dependency and was not installed.
- https://handbook.fide.com/ — laws index; direct chapter retrieval was unreliable, so do not treat it as a fully fetched chapter citation.
- https://developers.openai.com/api/docs/guides/compaction — official OpenAI documentation used to answer the user's compaction-delay question. It describes context preservation, not telemetry explaining this particular pause.

Local Android SDK/emulator, Gradle caches, and Kotlin/Native tooling only. No accounts, paid provider, service/database provisioning, external messages, deployment, Git initialization, commit, or push.

## Risks, warnings, and what is NOT done

This is not the full repertoire teacher. Study/Full idea, replay controls, explicit branch choice/return, automatic wrong-attempt hints, durable progress, sourced repertoire coverage, and GM-game coaching remain TODO in their existing tasks. The current trainer still has manual Hint and prototype wording that can imply an off-line legal move is bad; replace that in guided-line-learning with the user's chosen unchanged-position/automatic-hint behavior. Imported PGN opens the original main line's final position and Undo, not annotations/variations or teaching. Import objects are transient; no database/library persistence yet.

Rules are standard chess only. FEN cannot reconstruct repetition before its supplied starting position. Insufficient-material detection is deliberately conservative and does not solve every locked dead position; no tournament clock, claim adjudication UI, or complete arbiter workflow. PGN character/token/ply/depth limits are safety bounds, not a large-archive throughput claim. Import UI is limited to one game, 65,536 characters, and 600 main-line half-moves; archive ingestion must be designed separately with quarantine/provenance/streaming needs. Canonical export preserves supported annotations but normalizes formatting rather than reproducing original text bytes.

Position keys share chess state, not lesson intent or history. Preserve path-specific annotations, repetition/history, chosen side, and original-versus-hypothetical status when building the graph. A FEN-only unknown has no move-path fallback; a PGN may retain a labeled last-known opening after leaving the seed book. Missing coverage is not a chess evaluation. All seven historical cards remain unverified metadata with zero bundled complete game scores; do not treat them as source-backed GM games.

No engine, server, storage layer, iOS UI, real-device frame benchmark, accessibility audit, automated Compose UI suite, or process-death restoration was added. Core tests use the Android host JVM; iOS targets were compiled only. Four dependency-update warnings remain explicitly unaddressed pending a compatibility audit. No promise of guaranteed wins or full opening coverage.

## Next

Run `node .agents/skills/relay/bin/relay.mjs claim lesson-graph-replay --agent <harness-name>`. Read `BoardPosition.kt`, `Pgn.kt`, `ChessModels.kt`, and the existing trainer state. Build/test a shared position/move graph with explicit immutable original paths, path-specific annotations, selected color, deterministic replay cursor, bounded nested branch navigation, and exact return restoration. Keep the user-facing Study/Full idea/automatic hint changes in the following guided-line-learning task, and do not start corpus ingestion before the graph/import contracts are ready.
