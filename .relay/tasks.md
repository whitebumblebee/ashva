# Ashva — tasks

This is the authoritative tracker. One task, one owner. Use Relay claim/log/done commands; read current code before trusting history. File order is the recommended implementation sequence, subject to explicit dependencies and the user's newer instructions. Chess correctness first is user-selected; the subsequent order is derived from the new teaching/data requirements, not a separately approved release schedule. README roadmap items do not override this file. Detailed specification: `docs/LEARNING_DESIGN.md`.

## Now

- [~] `public-alpha-readiness` — IN_PROGRESS — codex — 2026-10-01T15:43:25Z — seen 2026-10-01T15:47:39Z — expires 2026-10-01T16:47:39Z
  - Explicit user priority (2026-10-01): prepare Ashva for public source exposure before returning to repertoire coverage. All Git initialization, staging, commits, remotes and pushes belong to the user; do none of them.
  - Owner decisions (2026-10-01): replace personal machine paths with placeholders, including historical logs, with original external backups; then explicitly apply Apache-2.0 to original Ashva code/documentation/assets. Keep collection-specific data, vendored Relay and dependency licenses separate.
  - Acceptance: user-selected app-code license with separate data/third-party notices, strengthened ignore rules, a redacted full-tree secret/privacy scan, current branding and screenshots, accurate alpha limitations, contributor/security/privacy documentation, and least-privilege pinned build/test CI. Preserve package/database identifiers and learner data. Run local build/domain/content/UI gates; do not claim hosted CI ran before publication.
- [x] `learning-product-plan` — DONE — 0004
  - Record the user's guided replay, automatic hints, selectable variations, broad sourced repertoires, and GM-game teaching requirements; document architecture and dependency order without changing the app yet.
- [x] `relay-setup` — DONE — 0002
  - Adopt existing code without Git history; wire Codex, Claude Code, Cursor, and Kiro; complete project context, bootstrap, and validation.
- [x] `user-test-guide` — DONE — 0003
  - Verify the Android debug build, write phone/emulator installation steps and a repeatable user walkthrough, and state prototype limitations accurately.
- [x] `emulator-user-test` — DONE — 0006
  - Explicit user request (2026-10-01): start the configured Android emulator, install/open the current build, and provide step-by-step checks for the new rules/identifier/import changes. This is a local testing handoff, not authorization to implement the next app milestone.
  - Acceptance: current debug assembly succeeds, emulator boot and app launch are verified, and the user receives concrete actions and expected results. Leave the emulator running; do not clear its data.

## Next

- [x] `chess-correctness` — DONE — 0005
  - User-selected next development priority (2026-09-30). Implement full legal moves, check/checkmate/stalemate, castling rights and attacked transit squares, en passant including discovered checks, promotion/underpromotion, and move-history-dependent draw handling. Introduce tested FEN/SAN/UCI and robust PGN parsing/export needed for real games, including tags/comments/recursive variations and unsupported-variant reporting.
  - Identify openings by normalized legal position with transpositions, preserving move history and reporting multiple plausible names or unknown without invented confidence. Support entered moves/position and imported PGN. Seed data is not evidence of full external coverage.
  - Acceptance: perft/reference fixtures, special-move/notation/PGN round trips, transposition and ambiguity tests; app build/lint and relevant Android smoke checks. Keep common rules compatible with iOS.

## Core teaching slice

- [x] `lesson-graph-replay` — DONE — 0007
  - Depends on `chess-correctness`. Replace flat scripted-line assumptions with position/move graph plus explicit lesson paths, annotations, branch navigation, selected-color state, and replay cursor. Keep original historical-game sequence immutable and alternate continuations separately labeled.
  - Acceptance: transposed paths share positions without losing context; nested branch -> return restores the exact position/path; cycles are bounded; first/previous/next/last and jump are deterministic for both perspectives.

- [x] `guided-line-learning` — DONE — 0008
  - Depends on `lesson-graph-replay`. Add Study / Practice, Full idea with complete notation and chosen-color plans, replay controls including autoplay/pause/jump, per-move ideas, and Stay / Switch variation cards at branch points. Pause automatic replies when a branch choice is being offered.
  - After an incorrect attempt, preserve the lesson position and immediately display the expected move as text plus board highlight/arrow. Offer a matching available branch for a legal off-line move; never call it a blunder merely because it differs. Illegal moves remain blocked with a reason and hint.
  - Acceptance: Ruy López study and practice from White/Black, automatic hint without a Hint tap, explicit Morphy/Berlin branch choice and return, full sequence visible, restored cursor/color after recreation; Compose UI tests and device/emulator interaction. Authored seed content can validate behavior, but must remain labeled as seed coverage.

## Real content and offline access

- [x] `source-data-pipeline` — DONE — 0009
  - Depends on `chess-correctness` and the graph contract. Audit licenses/terms separately for opening names, master statistics/PGNs, historical archives, broadcasts, evaluations, and annotations before ingestion or redistribution. Build a repeatable local importer with source/version/license attribution, identities, deduplication, legal replay validation, quarantine/error reporting, and content manifests. Do not ship the current unverified historical cards as verified games.
  - Evaluate Lichess CC0 opening-name taxonomy, Masters explorer statistics plus individual PGNs, and permitted GM archives/broadcasts; broadcast exports are CC BY-SA 4.0, not the site's standard-game CC0 collection. Check API authentication, rate limits, and collection-specific rights; code licenses do not establish data rights.
  - Acceptance: a legally usable sourced vertical slice with full game scores, transpositions, published import counts/coverage and provenance. Define measurable opening-family/variation/depth targets before calling coverage complete. Do not confuse API default top-move/game limits with an exhaustive book.

- [x] `offline-content-progress` — DONE — 0010
  - Depends on `lesson-graph-replay` and a versioned import format. Add Room KMP/SQLite behind shared repositories for downloaded lessons/games, position indexes, sources/annotations, selected repertoires, saved branches, replay bookmarks, progress, and review history. Platform builders must support Android now and an iOS compile/check when feasible; do not assume Android-only prepackaged database APIs exist in common code.
  - Acceptance: offline study after download, process/relaunch restoration, atomic content updates, migrations/rollback and interrupted-download tests, no loss of learner progress during pack replacement; explicit downloaded/missing/loading/error states.

- [ ] `repertoire-coverage` — TODO
  - Depends on `source-data-pipeline`, `offline-content-progress`, and `guided-line-learning`. Replace tiny seed-only catalogs with downloadable, searchable opening families and documented major/minor/popular variations; expose White/Black plans for each. Build selected repertoires as preferred learner moves plus covered opponent replies, while retaining the broader opening explorer.
  - Acceptance: enumerate delivered families/variations, theoretical/game-observed/generated status, depths and sample populations. Import/update whole source snapshots without silently dropping branches; show uncovered/off-book states and incomplete coverage honestly. Wider sources and deeper release scope depend on rights, size, and budget decisions.

## Analysis and explanation

- [ ] `engine-alternatives` — TODO
  - Depends on `chess-correctness`, `lesson-graph-replay`, and cache contracts. Spike Stockfish through a shared engine interface with platform implementation or service fallback; resolve mobile execution feasibility and GPL distribution obligations before bundling. Request bounded MultiPV continuations and separately evaluate the played/lesson move with comparable settings, recording engine/NNUE version, depth/nodes/time, perspective, and status.
  - Acceptance: every suggested move/PV is legal; score signs work for White and Black; mate scores and near-equal choices are handled; stop/cancel/background/battery behavior tested. Label strongest found at this analysis budget rather than proven best. Do not derive a blunder or guaranteed win from move frequency or a shallow score alone.

- [ ] `grounded-teaching-content` — TODO
  - Depends on `repertoire-coverage` and `engine-alternatives`. Build structured, reviewed explanations for opening plans, pawn structures/breaks, threats, tactical motifs, alternatives, and typical middlegames/endgames. Combine permitted curated knowledge and deterministic chess/engine facts; optional language-model narration is grounded in those facts and not the move authority.
  - Acceptance: representative lessons for both colors teach more than move memorization; all narrated tactical lines pass legal/analysis checks; source and authored/engine/generated status are visible. No fabricated GM intention, invented historical annotation, copied copyrighted commentary without permission, or unsupported winning claim. Model/provider/budget remain undecided; paid integration is not authorized.

## GM game learning and scale

- [ ] `gm-game-library` — TODO
  - Depends on `source-data-pipeline` and `offline-content-progress`. Add a GM section with search/add/follow players, reliable identity/alias handling, and filters for event/year/opening/color/result. Fetch or import full game scores, not metadata-only cards; support user PGNs. Start with selected GMs and expandable packs rather than bundling every available game.
  - Acceptance: original game can be replayed from start to finish offline, metadata and source are traceable, duplicates/aliases handled, and unavailable history is explicit. A Lichess username's games are not the player's entire over-the-board career; recent broadcasts are not the whole historical archive. First GM roster is a user decision, not an inferred favorite.

- [ ] `historical-game-coach` — TODO
  - Depends on `gm-game-library`, `guided-line-learning`, `engine-alternatives`, and `grounded-teaching-content`. Reuse the teaching player for full games: original moves by default, explanations for each move, useful alternative candidates with bounded continuations, and a clearly labeled hypothetical branch with a return-to-game action.
  - Acceptance: original move order/result never change; playback pauses for branch decisions; alternatives can be near-equivalent rather than always better; White/Black POV, offline/cached explanations, branch return and uninterrupted full-game playback all tested.

- [ ] `content-analysis-service` — TODO
  - Recommended for a large continuously updated catalog; not a prerequisite for the first offline teaching slice. Depends on proven import/schema/analysis contracts and an explicit provider/hosting/budget decision before deployment or account setup. Design a Kotlin/Ktor-compatible API with PostgreSQL search/metadata, object storage/versioned downloadable packs, and queued import/analysis jobs; validate locally first.
  - Acceptance: cached/source-attributed responses, pagination and delta/pack updates, provider-compliant throttling/backoff, deduplicated bounded engine work, retries, observability, and secrets remaining server-side. Cloud explanations and cross-device sync are optional additions, not mandatory per-move network calls. No live deployment, spend, or accounts authorized by this plan.

## Product quality and later platform work

- [ ] `persistent-recall-review` — TODO
  - Depends on `offline-content-progress` and `guided-line-learning`. Replace sample progress/review/profile with actual attempts, branch-aware repertoire mastery and spaced recall; distinguish studying a line from demonstrating recall. Define completion from the user's chosen repertoire, not the entire theoretical opening tree.
  - Acceptance: real progress survives relaunch and content version changes, review scheduling is deterministic/tested, automatic hints count as assisted rather than unaided recall.

- [ ] `compose-board-quality` — TODO
  - Applies throughout; final acceptance after the new teaching screens exist. Keep Compose-only rendering/interaction; add smooth moves/captures/promotion/branch transitions, drag and tap input, arrows/highlights, board flip, accessibility, adaptive layouts, and correct state restoration. Audit current stable compatible Compose/AndroidX releases against official sources before dependency changes.
  - Acceptance: representative-device frame/macrobenchmark evidence, no analysis/import work on UI thread, semantic/UI tests for both orientations and key controls, accessible move list and descriptions. Visual parity with leading apps is a quality target, not an unverified claim.

- [ ] `ios-client` — TODO
  - Deferred until Android's core teaching/data contracts are validated. Reuse KMP domain, persistence, and source contracts; decide native/Compose Multiplatform iOS UI and platform engine adapter with the user at that stage. Acceptance includes iOS build/runtime, persistence/replay parity, legal moves, and engine integration feasibility.

## Decisions not tasks to execute without permission

All Git work is explicitly owner-managed (2026-10-01); agents must not initialize, stage, commit, configure remotes or push. Local public-alpha preparation and CI configuration are authorized, but hosting/account changes, spend/deployment, actual publication/signing/distribution, first GM roster, numerical release coverage, and new sync/auth/model-provider setup still need decisions or specific authorization.
