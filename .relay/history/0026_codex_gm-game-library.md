---
seq: 26
agent: codex
date: "2026-10-02T15:53:06Z"
task: gm-game-library
status: partial
summary: "Added source-traceable shared game-library metadata/filtering, private PGN validation and explicit local following with additive Room schema5; 152 JVM and 121 Android-host cases pass. Native library/replay and full Android verification are still unfinished."
next: "Implement native GM library and source-pinned full-game replay, wiring lazy cancellable library loading, explicit follows/private import and exact cold bookmarks; then run app/UI gates before completion."
supersedes: []
git_head: cba53d25fcf1
---

# gm-game-library

## What I did

Added shared GameLibrary metadata indexing and bounded cancellable filtering by query, exact player, event, year, opening/ECO, selected-player color, result, followed players and source-reported GM titles. Exact duplicate score IDs retain both source origins when metadata/moves agree; conflicts fail closed and different IDs are not guessed real-game duplicates. Required exact taxonomy names must be present. Aliases group by exact source/FIDE identity, not similar names; titles/FIDE IDs are still reported, not authenticated.

PrivateGameRecord accepts one bounded legal standard PGN, preserves original mainline/result/comments/RAVs, checks canonical SAN/UCI/FEN/result/metadata/hash and graph bounds, and uses a separate private-player namespace. It does not insert into licensed packs or public observed counts. Single-game import is bounded at256KiB/4096 mainline half-moves; archives are rejected explicitly rather than partially imported. Canonical duplicate imports preserve the first metadata row. Following starts empty; validation retains an unavailable player's names/identity without asserting game history exists.

Room schema5 adds followed_players and private_games only, with explicit4→5 registered on Android/JVM/iOS. Adds exact retained gameInPack lookup, follow/unfollow flows and private-import writer transactions; no old row/table/ID is rebound or pruned. Private storage is bounded at1000 records/32MiB payload, per-payload1MiB. Existing versions1–4 are tested as real SQLite fixtures, retaining old packs/notices/bookmarks/attempts/selections/policies/sets. Concurrent identical imports are idempotent. Private/follow APIs default to unavailable/empty for older fixture adapters, not fabricated persistence.

Five common library cases and two JVM persistence cases pass. This is **only the foundation**: no native GM shelf, follow/import controls, full-game player or game bookmarks yet. Development version has been advanced to0.13.0/code14 after the foundation gates; the owner's emulator stays on fully verified0.12.0/schema4. Do not install the unfinished0.13 build as a completed library.

## Commands run and their outcomes

- `./gradlew :shared:compileKotlinJvm`: BUILD SUCCESSFUL3s for initial library contracts.
- `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`: BUILD SUCCESSFUL33s after additive schema/repository implementation, existing cases pass.
- `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`: BUILD SUCCESSFUL28s with new cases: **152 JVM /121 Android-host**, zero failures/errors/skips. Debug/lint/shared iOS compile pass for foundation code before the subsequent0.13 version bump. No0.13 complete UI/unsigned-release/runtime/pack re-verification claim; those must follow the native implementation.
- Schema5 JSON generated and versions1–4 retained. Full previous0.12 gate evidence is log0025, not fresh0.13 runtime proof. Repeat public/context/doctor after finalizing this log; no new chess acquisition/provider call, Git action or publication.

## External resources touched

Existing local SDK/Gradle/Room fixtures only. No external archive, player API/FIDE verification, new account, paid service, secret, deployment, Git operation or publication. Synthetic names/PGNs are used in new tests. Owner emulator-5554 remains on verified0.12 with data preserved; isolated emulator-5556 remains available for forthcoming native tests.

## Risks, warnings, and what is NOT done

gm-game-library is IN_PROGRESS, not DONE. UI/replay/bookmarks/loading/error/withdrawn-source behavior and full app gates remain required. Lazy library loading must avoid duplicating full-score preparation on every launch or blocking Compose; exact source/manifest/taxonomy versions must travel with the selected game and its cold bookmark. Private PGNs/annotations stay learner-only and user-supplied, not publicly licensed or expert commentary. Source-reported GM identities/titles do not authenticate people or supply complete careers. Model/provider/cloud remain unauthorized. Schema5 is additive; do not clear the user's app or destructively migrate. Goal remains active and local work continues.

## Next

Implement native GM library and source-pinned full-game replay, wiring lazy cancellable library loading, explicit follows/private import and exact cold bookmarks; then run app/UI gates before completion. Read GameLibrary.kt, LearningStore/Room schema5 and current AppViewModel restoration. Start with an empty follow list and let the learner choose names in the app; do not infer favorites or require another optional roster approval.
