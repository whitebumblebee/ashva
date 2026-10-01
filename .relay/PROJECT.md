# Ashva — project invariants

## What this project is

Ashva (formerly Opening Lab) is a branching chess teacher, not just a fixed move-recall exercise. Build Android first with native Jetpack Compose, then iOS through Kotlin Multiplatform. Teach opening repertoires and complete historical games with replay, strategic explanations, guided practice, and selectable continuations for either color; recognize an unfamiliar opening from the opponent's moves or a supplied position/game. The board should feel as smooth and polished as leading chess apps.

Today this is an offline Android prototype, version 0.3.1: seven authored seed openings, thirteen short variations and seven unverified historical metadata cards. Shared legal rules/notation, normalized-position identification and the finite lesson graph/replay/branch-return model are implemented. The trainer has Study/Practice, Full idea, both-color plans, replay, automatic hints and confirmed branches. Room KMP now persists cold-relaunch bookmarks, branch/color/cursor/hint context, actual attempts and selections; Home Continue reflects the saved lesson rather than a sample position. Lifecycle cancels playback/replies. Identifier PGN/FEN imports still show final position/undo, not guided teaching. The licensed pipeline's reviewed packs are now APK assets installable into SQLite via **Offline library & sources**: 3,815 taxonomy sequences (149 name-derived families / 3,174 names, 1–36 half-moves) and 79 April 2020 original broadcast scores (7,606 half-moves, 31–205 per game). All were accepted, zero duplicates/quarantine. Installation is bounded/checksummed/legal/dependency-aware and atomic, with interruption recovery, retained versions/rollback and separate learner state. Source data is **not yet wired into broader teaching/search or a GM shelf**, not reviewed lessons; 15 player names remain unresolved. No live download provider, mastery/recall scheduler, engine, backend or iOS app exists. Shared iOS compilation is not an iOS client.

## Teaching behavior that must survive handoffs

- **Study a line:** a Full idea action shows the entire selected move sequence (both sides) and its purpose from the chosen White/Black perspective. Provide first/previous/next/last, move-list jump, and play/pause controls with per-move explanations. Explain plans, pawn breaks, threats, typical mistakes, and opponent counterplay where supported by the lesson.
- **Practice:** after an incorrect attempted move, automatically show the expected move in notation and on the board. The user explicitly chose to keep the lesson position unchanged. When a legal off-line move belongs to an available variation, offer Stay on this line or Switch to the named variation; switch only after confirmation. A legal different move is not automatically a chess mistake.
- **Branch deliberately:** pause at meaningful branch points, show named alternatives and their ideas, and let the learner stay, diverge, and return to the original line/branch point. Preserve the path, explanation context, chosen color, and progress.
- **Learn from GMs:** browse/search source-backed games, add/follow selected GMs progressively, and replay the original complete game by default. Explain each move and offer analyzed alternatives with continuations. Historical moves and hypothetical engine lines must remain visibly distinct; exploring a branch must never rewrite the original game.
- **Coverage and honesty:** target broad documented opening theory, major/minor named variations, popular game-observed replies, and on-demand off-book analysis. Publish source, version, sample counts, and coverage/depth limits. No finite database contains every possible continuation; teach winning plans and exploiting errors, never guaranteed wins or fabricated historical intent.

Detailed behavior, planned data/engine/backend boundaries, source candidates, and acceptance checks are in [docs/LEARNING_DESIGN.md](../docs/LEARNING_DESIGN.md). The tracker remains the authority for implementation order.

## Architecture you must not break

- `androidApp` owns native Compose screens and the board, Material 3/adaptive layouts, Navigation 3, and lifecycle-aware UI state. **No XML screen layouts, Views, Fragments, or WebView chessboard.** Manifest, launch-theme, and launcher-icon XML are Android platform resources, not screen implementations.
- Honor the user's latest-technology requirement: use the latest stable, compatible Jetpack Compose/AndroidX and Kotlin/KMP practices and libraries, checking official release notes before upgrades. Pin compatible versions centrally and verify builds; do not equate newest experimental releases with production readiness or claim checked-in versions are latest without checking. Discuss experimental tradeoffs before adopting a less-stable channel.
- Use immutable state, unidirectional data flow, screen-level state holders/ViewModels, coroutines/Flow, lifecycle-aware collection, repository boundaries, and constructor injection. Keep analysis/import work off the UI thread. Verify restoration, accessibility, and board animation/frame performance rather than promising visual parity from screenshots.
- `shared` owns legal chess rules, notation/PGN, position identification, lesson/branch models, replay, and repository contracts. Keep `commonMain` Android-free and compatible with Android/iOS. Engine execution, filesystem/database construction, and background scheduling need platform adapters.
- `contentTools` is a local JVM importer reusing shared KMP legal rules. Source configuration/lock/raw exports and versioned JSONL packs live under `content/`; do not edit generated bytes or overwrite different pack versions. Separate CC0 taxonomy and CC BY-SA 4.0 broadcast data rights/attribution. Preserve snapshot checksums and exact taxonomy dependency manifests. [Content pipeline](../docs/CONTENT_PIPELINE.md) records audited/blocked collections, commands, schema and measured scope; Masters statistics/PGNs and additional archives/evaluations are not ingested. Source scores/metadata are not reviewed plans or independent identity verification.
- Persistence uses Room KMP 2.8.5/bundled SQLite 2.7.1 with KSP 2.3.12; official stable releases checked 2026-10-01. `LearningStore` is the common repository, with platform builders and generated Room constructors. Kotlin warns that expect/actual classes are Beta even with stable Room. Schema 1 is a pre-release fixture; explicit 1→2 migration retains learner rows and adds notices. No destructive fallback or automatic pack pruning. Exact old taxonomy dependencies must remain available when a newer taxonomy is active. See [offline storage](../docs/OFFLINE_STORAGE.md). A service remains recommended for scale, not required for current offline installs.
- Position identity must support transpositions without losing move-path/history or lesson context. Preserve original game moves separately from generated variations. Source/license provenance and engine-analysis settings must travel with content.
- Treat existing statistics, historical cards, and progress as prototype data until validated. Opening confidence percentages have been removed; unknown, ambiguous, and out-of-book results must stay explicit. Chess engines establish bounded analysis, not human-readable teaching or certainty; explanations require grounded content and checks.
- Rules support standard chess, not Chess960/other variants. FEN has no prior repetition history; PGN reconstructs history from its declared initial position. Material-based dead-position detection is conservative, not an exhaustive solver for unusual locked positions. Keep these limits explicit and do not present the learning UI as a tournament arbiter.

## Environments

| Environment | Where | Notes |
| --- | --- | --- |
| production | None | No Play Store release, signing setup, backend, or cloud deployment is configured. |
| staging | None | Distribution method remains undecided. |
| local | This checkout; Android SDK and emulator | Debug package `com.openinglab.app`, minimum Android 8.0 / API 26. Configure the SDK locally through ANDROID_HOME or ignored local.properties. |

This folder has no Git repository. The owner's 2026-10-01 instruction assigns all Git initialization, staging, commits, remotes, pushes and publication to the owner; agents perform none of them. Node 22 runs the local Relay CLI; JDK 21 is the documented build/CI runtime, Java bytecode targets 17. SDK/local paths belong in ignored local configuration, not public context.

## Validation gates

For app/domain changes, run the following from the project root and record actual results. Runtime/UI changes also need a relevant emulator or device smoke test; distinguish previous-session evidence from current verification.

```bash
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
ANDROID_SERIAL=<isolated-test-device-serial> ./gradlew :androidApp:connectedDebugAndroidTest
./gradlew :shared:jvmTest :contentTools:test
node --test scripts/fetch-content.test.mjs
./gradlew :contentTools:run --args=verify
node .agents/skills/relay/bin/relay.mjs doctor --strict
```

Public exposure changes also require `node --test scripts/*.test.mjs`, `node scripts/public-audit.mjs`, a redacted `node scripts/run-secret-scan.mjs` and CI syntax validation. Neither scanner invokes Git or audits prior Git history. Preserve immutable source bytes and upstream licenses; include only synthetic app screenshots. The owner explicitly selected Apache-2.0 on 2026-10-01: root LICENSE and NOTICE cover original Ashva code and project-authored documentation/assets (Copyright 2026 Shishir Jha), not separately licensed datasets, vendored Relay or dependencies. Retain NOTICE and upstream attribution; new original source files should include an appropriate SPDX license comment. On 2026-10-01 the owner explicitly authorized replacing personal paths: four historical logs now use placeholders, preserving all technical history and an untouched external backup. This is a privacy-only exception to append-only history, not permission to rewrite other past facts. Actual local recovery/SDK/test paths are in ignored `.public-audit/local-environment.json`; never publish it. Replace angle-bracket path placeholders before running historical commands. See [public readiness](../docs/PUBLIC_READINESS.md).

For documentation or Relay-only changes, check referenced paths/commands and run Relay doctor; the app gates are needed only when claiming fresh app/build verification. Relay's `config.json` records gates but does not execute them. The CLI is not currently on PATH: use `node .agents/skills/relay/bin/relay.mjs`.

Content/import changes need the importer/Node/pack-verification gates above; Android/common dependency changes also need the app gates. Verify shared iOS compilation when common schemas/dependencies change. Pack integrity gates do not validate mobile atomic installation, progress persistence or source authenticity.

Connected instrumentation may uninstall the target app after tests. Durable learner data now exists: **always use an isolated emulator/device, set ANDROID_SERIAL, and verify Gradle lists only that target**. Never run it on the user's learning install. Use `adb -s <exact serial>` when multiple emulators run; keep the user's existing emulator/data untouched. Reinstall/open only where needed without uninstalling/clearing data.

## Actions that need the human

Continue authorized local implementation, documentation, tests, and reversible checks without repeatedly asking for approval. Obtain explicit authorization for commits/pushes/PR creation, publication or production deployment, paid services, destructive data changes, and account/console actions. Keep credentials out of Relay files. Respect task claims across harnesses; do not take another live claim without the user's authorization.

## Deferred and declined

- Git operations: exclusively owner-managed, explicitly reaffirmed 2026-10-01. Prepare local files/checks only; do not initialize, stage, commit, configure remotes or push.
- iOS application: after Android, per the original product request. Only shared iOS targets exist today.
- Guided study/practice is implemented for authored seeds; sourced opening coverage and GM teaching remain explicit requirements, not speculative enhancements. The tracker records the dependency-based implementation order; the README roadmap is background, not a competing tracker.
- First GM selection, release coverage/depth targets, content-provider terms, engine mobile distribution approach, hosting/budget, authentication/sync, monetization, analytics and distribution remain undecided. Public-alpha preparation authorizes local CI configuration, not hosted execution/publication. Research sources and build local slices; do not infer authorization for external account setup, spend, or deployment.

## Precedence

When sources disagree, this is the order:

1. The user's newest explicit instruction
2. This file
3. `.relay/tasks.md`
4. The current code, tests, and deployed state
5. `.relay/history.md` and the logs, as historical record

Plans and design documents are subordinate to `tasks.md`. If a plan says one
thing and `tasks.md` says another, `tasks.md` is what is actually happening.
