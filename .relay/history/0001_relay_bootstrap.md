---
seq: 1
agent: relay
date: "2026-09-30T11:22:48Z"
task: bootstrap
status: done
summary: "Adopted the Android Compose/KMP chess prototype into Relay for Codex, Claude Code, Cursor, and Kiro; chess correctness is the next development priority."
next: "Claim user-test-guide, verify the Android debug build, and document installation plus a repeatable user walkthrough."
supersedes: []
bootstrap: code
---

# Opening Lab — relay bootstrap

## Facts collected at adoption

Source: **the working tree only** — there is no usable git history. Dates below are file modification times, which copying or checking out resets; treat them as a guess.

- Files (excluding dependencies, build output, dotfiles): 42
- Where they live: androidApp/ 22, shared/ 8, (root) 6, gradle/ 3, docs/ 3
- Manifests: build.gradle.kts
- CI and deployment config: none found
- Docs: README.md, docs/ (1 entries)
- Existing agent instructions: none found
- Other trackers or plans: none found

### Last modified (modification times — a guess)

- `skills-lock.json` — 2026-09-30
- `docs/screenshots/identifier.png` — 2026-09-20
- `docs/screenshots/trainer.png` — 2026-09-20
- `docs/screenshots/home.png` — 2026-09-20
- `androidApp/src/main/kotlin/com/openinglab/app/ui/OpeningLabApp.kt` — 2026-09-20

Oldest:
- `settings.gradle.kts` — 2026-09-20
- `gradle.properties` — 2026-09-20
- `shared/build.gradle.kts` — 2026-09-20

## What this project is

Android-first chess opening repertoire learning app. The user's original request requires native Jetpack Compose, a KMP domain reusable by iOS, recognition of unfamiliar openings, major/minor lines for either color, historical-game teaching, and polished board interaction. Codex is adopting the existing implementation, not starting a new app.

## Where it stands

The code has Compose Learn/Explore/Review/Profile tabs, opening details, a tap-to-move trainer with scripted opponent replies and hints, and an opening identifier. A KMP shared module contains seven openings, move application, prefix-based identification, and a repository boundary. Android and shared iOS targets are configured, but no iOS application exists. A debug APK (version 0.1.0, package com.openinglab.app) is present; there is no production or staging distribution.

The previous session reported a successful build, five shared tests, zero lint issues, and an emulator walkthrough. These are historical reports, not fresh validation on 2026-09-30. The user-test-guide task will record current verification separately.

Relay is initialized in code mode because this folder is not a Git repository. All four requested harness pointer files were created, and project/task context now records the user's choices.

## Half-done work

Review/profile/home progress values are sample data. There is no durable repertoire storage, genuine review scheduler, cloud sync, PGN/FEN import, historical-game move viewer, or complete opening book. Historical games are metadata in the seed catalog; they have not been source-verified. There is no engine evaluation, drag-and-drop board, or performance benchmark evidence.

The interrupted continuation before Relay adoption only inspected code; it did not implement chess-rule changes. No branches or Git diff are available in this folder.

## Known broken or risky

BoardPosition.legalTargets is explicitly pseudo-legal: it does not reject self-check or maintain castling rights. En passant targeting contains a suspect rank comparison. OpeningIdentifier chooses a seeded prefix match, cannot recognize transpositions, and assigns heuristic percentage confidence that can overstate certainty. Keep these limitations visible when discussing what the app teaches.

The current sandbox prevents ADB from binding its local server socket; a device/emulator check requires the usual sandbox escalation or a user-run terminal. One installed AVD was listed: Pixel_3a_API_33_arm64-v8a. Android SDK, JDK 23, and Node 22 are installed. Android Studio was not found at /Applications/Android Studio.app; other installation paths were not conclusively checked.

There is no Git recovery/history. The user explicitly chose not to initialize Git for now. Preserve the working files; do not imply Relay replaces version control.

## Sources relied on

Adopting agent: codex. Sources: the user's original product request and Compose correction; the 2026-09-30 user testing/Relay request; structured answers selecting chess correctness and opening identification first, and Git not needed for now; README.md; gradle/libs.versions.toml; Android/shared build scripts; AndroidManifest.xml; OpeningLabApp and AppViewModel; screen implementations; ChessModels, BoardPosition, OpeningIdentifier, OpeningCatalog, OpeningRepository; existing shared tests and APK path; Relay skill/setup/harness/CLI/file-format documentation. Generated file counts and modification dates above were retained unchanged.

No commits, pushes, external accounts, paid services, deployments, or credentials were touched. This bootstrap's current app state is based on code inspection and prior-session reports until the next verification log records fresh outcomes.

## Next

Claim user-test-guide as codex. Re-run shared tests, assembleDebug, and lintDebug; inspect the result and APK; document real-phone and local-emulator installation and the exact Ruy López trainer/identifier walkthrough. Afterwards the next development task is chess-correctness. The order of other enhancements remains undecided.
