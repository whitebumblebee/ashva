---
seq: 7
agent: codex
date: "2026-10-01T09:32:59Z"
task: lesson-graph-replay
status: done
summary: "Added finite legal lesson paths, transposition-shared nodes, nested branch return and replay snapshots; integrated trainer and verified 51 shared tests."
next: "Claim guided-line-learning and add Study/Practice, Full idea, replay controls, immediate hints, and confirmed branch choices using LessonReplay."
supersedes: []
---

# lesson-graph-replay

## What I did

Claimed the next unblocked task using Relay. Added `shared/.../lesson/LessonGraph.kt`: legal finite lesson paths, normalized nodes/edges, path-specific annotations and history, authored-opening/PGN adapters, immutable original-game sequence versus annotated alternatives, replay first/previous/next/last/jump, bounded branch navigation, exact nested return frames, and compact ID/cursor snapshots with validation. Transposed branches keep the learner's actual played prefix/clocks/repetition rather than replacing it with the source path's history.

Added 12 common `LessonReplayTest` checks. Integrated the existing Android trainer with `LessonReplay` as the position/cursor authority and changed its move strip to use the active route. This task deliberately retains the old UI until guided-line-learning; no source corpus/dependency/engine change.

## Commands run and their outcomes

- First shared test run: **51 tests, 2 failures**. Nested PGN descendants were advertised at their borrowed prefix before their actual branch origin, causing duplicate root choices. Fixed branch eligibility to start at the annotated origin. Regression tests then passed.
- `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`: **BUILD SUCCESSFUL in 19s; 74 tasks, 28 executed/46 up-to-date**. **51 passed, 0 failures/errors/skips**: previous 39 plus 12 replay tests. Lint: 0 errors, same four dependency-update warnings from log 0005. Both iOS shared targets compile; no iOS UI/runtime claim.
- On running API-33 emulator-5554, installed APK with `adb install -r`: Success. First launch timed out and UI dump returned null; second launch returned Status ok, fresh dump showed Learn. Opened Ruy López/Play White, entered e2e4, and observed e4/e5 with 12% progress. This validates trainer integration only, not new teaching controls.
- Relay log created 0007 and regenerated index. Gates passed before DONE; no Git repository or commits.

## External resources touched

Local Gradle/SDK/emulator only for this milestone. Read-only official Android testing documentation was searched for the upcoming UI-test setup; no libraries were added in this task. No corpus import, external writes, accounts, spending, deployment, Git initialization, or user-data clearing.

## Risks, warnings, and what is NOT done

The UI still looks like the old trainer at this intermediate milestone. Full idea/Study/replay/automatic hints/branch controls are the following task, not implemented by this log. Snapshots require a platform persistence adapter. PGN graph safety limits are 4096 plies per materialized route, 50,000 total plies, 1000 paths, source nesting 64, active branch stack 16; finite routes can revisit nodes without recursive graph traversal. Metadata/source/analysis/persistent storage and GM library remain future tasks. Read-only Kotlin collection contracts are used, not a new persistent-collections dependency.

## Next

Run `node .agents/skills/relay/bin/relay.mjs claim guided-line-learning --agent codex`. Implement visible Study/Practice and Full idea, full notation/jump/navigation/autoplay with branch pauses, automatic unchanged-position hints and confirmed variation switch/return. Reuse LessonReplay, preserve selected color and cursor through recreation, and add Compose interaction tests plus current emulator evidence. Keep seven/thirteen seed coverage honest.
