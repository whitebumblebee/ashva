---
seq: 4
agent: codex
date: "2026-09-30T16:22:51Z"
task: learning-product-plan
status: done
summary: "Recorded branching study/practice, automatic hints, sourced repertoires, GM-game coaching, Compose invariants, and a dependency-based architecture plan."
next: Claim chess-correctness; build tested legal rules and notation/PGN foundations with position-based opening recognition before changing the teaching player.
supersedes: []
---

# learning-product-plan

## What I did

Updated `.relay/PROJECT.md` to define a branching chess teacher with Study, Practice, automatic wrong-attempt hints, explicit variation choices/return, and complete GM-game coaching from either perspective. Recorded the user's explicit choice: keep the lesson position after a legal off-line attempt, immediately show the expected move, and offer a branch switch where available. Legal alternatives are not automatically chess mistakes. Historical game moves remain immutable and distinct from generated continuations.

Strengthened the existing Android-first Compose/KMP instruction: native Compose board/screens, no XML screen/View/Fragment/WebView implementation, current stable compatible libraries checked against official sources, and measured interaction/accessibility/performance. Platform manifest/theme/icon XML remains permitted. Installed dependency versions were not changed or freshly certified as latest.

Expanded `.relay/tasks.md` from 3 to 17 tasks, keeping the two previously completed tasks and the user-selected chess-correctness priority. Added this planning task and 13 implementation tasks with dependencies and acceptance criteria for graph/replay, guided teaching, sourced content, offline persistence, broad repertoires, engine alternatives, grounded explanations, GM library/coaching, optional service, real review progress, board quality, and later iOS. The subsequent ordering is a dependency-based recommendation, not a separately user-approved release schedule. No Git task was added.

Added `docs/LEARNING_DESIGN.md` as a subordinate specification, not a competing tracker. It details teaching interactions, realistic coverage boundaries, source/licensing distinctions, local database versus optional service, data contracts, analysis/explanation separation, and verification. Recommended Room KMP/SQLite offline storage; proposed optional Kotlin/Ktor-compatible service, PostgreSQL, object storage, and queued ingestion/analysis for scale. These are planned technologies, not provisioned components.

Used Relay claim/log/index/done protocol and preserved all existing history entries. No app code or harness pointer files needed modification; the existing four harnesses already read the central project/tracker files.

## Commands run and their outcomes

- Read the full Relay skill, central project/tracker/index, newest relevant log, AGENTS.md, and CLI guidance; inspected current opening catalog and prior verified code findings before planning.
- Initial `node .agents/skills/relay/bin/relay.mjs status`: 1 TODO, 0 in progress, 0 blocked, 2 DONE; 3 history entries; next chess-correctness; no Git repository.
- `node .agents/skills/relay/bin/relay.mjs claim learning-product-plan --agent codex`: claimed successfully before content edits; no takeover.
- Read-only `rg` checks in OpeningCatalog.kt: 7 Opening constructor entries, 13 variation call entries (plus helper declaration), and 7 historical metadata entries. No external games were imported.
- Node filesystem/Markdown check of `.relay/PROJECT.md`, `.relay/tasks.md`, and `docs/LEARNING_DESIGN.md`: all readable, 1 local Markdown link resolves, and all 6 checked command/harness paths exist (gradlew, Relay CLI, AGENTS.md, CLAUDE.md, Cursor rule, Kiro steering). Reviewed document/tracker content for agreement with the user's answer.
- `node .agents/skills/relay/bin/relay.mjs doctor --strict` before logging: exit 0, all checks passed, 3 history entries and 17 tasks.
- `node .agents/skills/relay/bin/relay.mjs log --agent codex --task learning-product-plan --summary ... --next ...`: created this sequence-4 log and regenerated history.md successfully.
- `node .agents/skills/relay/bin/relay.mjs done learning-product-plan`: DONE, citing log 0004. Post-handoff `doctor --strict`: exit 0, all checks passed, 4 history entries and 17 tasks. `status`: 14 TODO, 0 in progress, 0 blocked, 3 DONE; next chess-correctness.
- App source and dependencies are unchanged, so no fresh Gradle or emulator validation was performed for this documentation-only task. Prior five-test/build/emulator evidence is in log 0003, not new evidence for these planned features.

## External resources touched

Read-only research against official sources on 2026-09-30:

- https://github.com/lichess-org/chess-openings — names/canonical positions and CC0 dedication, not a ready-made instructional book.
- https://lichess.org/api and https://raw.githubusercontent.com/lichess-org/api/master/doc/specs/lichess-api.yaml — current API entry/schema.
- https://raw.githubusercontent.com/lichess-org/api/master/doc/specs/tags/openingexplorer/masters.yaml and https://raw.githubusercontent.com/lichess-org/api/master/doc/specs/tags/openingexplorer/masters-pgn-gameId.yaml — statistics/game references, current explorer.lichess.org host, default/capped results, PGN endpoint, and authentication schema to check at integration.
- https://github.com/lichess-org/lila-openingexplorer and https://lichess.org/page/api-tips — explorer roles and rate-limit handling.
- https://database.lichess.org/ — collection differences; broadcast games explicitly use Creative Commons Attribution-ShareAlike 4.0, unlike standard-game exports. No bulk download.
- https://stockfishchess.org/about/, https://official-stockfish.github.io/docs/stockfish-wiki/Developers.html, and https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html — bounded search, alternative analysis, commentary limitations, and GPL distribution considerations.
- https://developer.android.com/kotlin/multiplatform/room and https://developer.android.com/topic/architecture/recommendations — KMP storage/platform API differences and Compose architecture guidance.

No external writes, service accounts, deployments, paid integrations, commits/pushes, database provisioning, engine installation, or corpus downloads occurred.

Requested docs/LEARNING_DESIGN.md be opened in the current Codex panel; the app returned queued, not confirmed visible.

## Risks, warnings, and what is NOT done

The full requested app is NOT implemented by this task. Current APK, pseudo-legal chess logic, prefix-only identifier, seed content, sample progress, and unverified historical metadata remain unchanged. Existing metadata is not a valid game archive, and some cards may be inaccurate. Do not claim planned controls/automatic hints/data access already work.

No single researched source establishes all theoretical variations, every historical GM game, instructional explanations, or perpetual free/no-auth access. Masters statistics and PGN availability do not alone establish bulk redistribution rights. Additional older-game archive/annotation permissions need auditing. Modern broadcasts do not cover every GM's career.

Stockfish does not provide natural-language strategic teaching and cannot guarantee wins. Mobile engine adapters, especially iOS execution/distribution, need a feasibility/licensing spike. Optional AI prose must be grounded and tested, not trusted as a chess engine. Source scope, depth, storage/analysis budget, first GM roster, hosting/provider, and deployment decisions are still open. Safe local rules/lesson work can proceed without these unrelated decisions.

All generated index edits were made by Relay CLI. Prior logs remain historical snapshots; the newest user instructions and PROJECT/tasks now govern the expanded scope.

## Next

Run `node .agents/skills/relay/bin/relay.mjs claim chess-correctness --agent <harness-name>`. Read shared/src/commonMain/kotlin/com/openinglab/shared/chess/BoardPosition.kt, OpeningIdentifier.kt, model definitions, and existing tests. Implement legal rules and tested notation/PGN foundations with normalized position recognition/transpositions and honest unknown/ambiguous results. Do not start by downloading a huge corpus into the pseudo-legal model. Follow this with the lesson graph/replay and guided-line-learning slice, preserving the explicitly chosen wrong-attempt behavior.
