---
seq: 28
agent: codex
date: "2026-10-02T20:27:29Z"
task: gm-game-library
status: done
summary: "Verified Android0.13 native source/private game library, exact-identity following/filtering, immutable full original replay and exact cold bookmarks; 155JVM/123Android-host/24importer/all52isolated UI cases pass. Learning emulator updated in place; historical-game coaching is next."
next: "Claim historical-game-coach and add per-move grounded original-game explanations plus bounded offline engine alternatives/preview/return, preserving exact original score and source bookmarks."
supersedes: []
git_head: cba53d25fcf1
---

# gm-game-library

## What I did

Completed gm-game-library on the foundation/native work in0026–0027. Android0.13/code14 has native Players & GM games on Learn: lazy cancellable source/hash-keyed metadata shelf, player/query/event/year/opening/color/result/reported-GM/followed filters, explicit user-selected local following and bounded private single-PGN import. Exact identities/aliases and conflicts are handled without guessing people or full careers. Full original mainline replay supports either POV, first/previous/next/last/full notation jumps/autoplay/speed/flip; original result/order stay immutable. User comments are visibly unverified, and position teaching is separate from source intent. Missing/error/zero/retained-history states stay explicit.

Exact GameStudyReference/bookmark fields pin public pack/manifest/record or canonical private identity; legal preparation, fingerprint/path/color/cursor restore and retained taxonomy checks reject substitution/stale games. Game study never writes an opening selection or recall mastery. Private imports/follows are schema5 learner tables only, with registered additive1→2→3→4→5 and real1–4 preservation fixtures; no public observed population/source bytes change. Private limits: one standard PGN256KiB/4096 mainline half-moves,1000 records/32MiB total,1MiB per JSON; canonical duplicates are idempotent and no pruning occurs.

Additional native checks exercise background pause and uninterrupted short original autoplay. Fixed the shared conflated bookmark writer to carry each request's revision with its payload, so an old receive cannot claim a later queued snapshot's Saved status; delayed-writer fixture verifies the exact final cursor/color/mode and intermediate Saving state. Older failure reporting is similarly guarded. Original/source/seed policies and learner IDs remain stable. Current README/USER_TESTING/GM_LIBRARY/OFFLINE_STORAGE/PRIVACY/PUBLIC_READINESS and Relay architecture/tracker are updated; earlier partial logs remain historical facts, not rewritten.

## Commands run and their outcomes

- After explicit continuation, no interrupted Gradle wrapper remained and reports were the old three-case checkpoint. Renewed the expired same-agent claim; no unproved complete pass was assumed.
- First complete0.13 command (shared JVM/Android-host, importer/default pack verify, debug/unsigned release/lint, both shared iOS targets, exact isolated connected suite): **BUILD SUCCESSFUL10m17s;155JVM/123Android-host/24importer/51UI**, zero failures/errors/skips. This preceded the subsequent writer fix/fifth native case.
- `ANDROID_SERIAL=<isolated-test-serial> ./gradlew :androidApp:connectedDebugAndroidTest :contentTools:run --args='verify broadcast-2020-01' -Pandroid.testInstrumentationRunnerArguments.class=com.openinglab.app.GameLibraryLearningTest`: **SUCCESS1m6s**, four focused cases after extra lifecycle/autoplay assertions, January byte-identical857accepted/0duplicates/95quarantined,1–253half-moves. Shared/source bytes did not change with the later Android-only actor fix.
- `ANDROID_SERIAL=<isolated-test-serial> ./gradlew :androidApp:assembleDebug :androidApp:lintDebug :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.openinglab.app.GameLibraryLearningTest`: **SUCCESS1m11s;5focused cases**, including controlled-delay revision-tagged writer.
- Final `ANDROID_SERIAL=<isolated-test-serial> ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest`: **BUILD SUCCESSFUL10m23s;155JVM/123Android-host/24importer/all52complete UI**, zero failures/errors/skips. Unchanged shared/importer artifacts were up-to-date; final Android writer/UI/release were checked. Default packs reproduce3815/79 with0duplicates/quarantine. Whole-month case finishes within unchanged per-stage180s waits: taxonomy33000ms, January128132ms, observations169501ms, cold215566ms measured cumulatively, not a raised timeout or phone performance claim.
- Both final APKs: `node scripts/verify-engine-apk.mjs <debug-or-unsigned-release-apk>` verify exact engine/source/NNUE/recipe hashes/notices.26Node cases pass; public surface241candidates/0findings; Gitleaks8.30.1zero; pinned actionlint1.7.12exit0. Final log/current docs require context/doctor/public recheck after finalization; those are local, not hosted CI/Git history/security proof.
- Owner learning emulator: `adb -s <learning-serial> install -r <debug-apk>` **Success**, MainActivity launched, package metadata confirms0.13.0/code14. No uninstall, clear, instrumentation, screenshots/private input or learner-row reading there. Isolated API33 emulator remains available for coach tests.

## External resources touched

Previously reviewed immutable Lichess opening and January/April broadcast assets and local SDK/Gradle/Room/emulators only. No new archive, player API/FIDE authentication, paid model/provider, cloud/account, deployment, Git command or publication. Test PGNs/names are synthetic. Owner-only publication and separately scoped data/GPL engine distribution boundaries remain intact.

## Risks, warnings, and what is NOT done

This completes the finite offline GM-library/player milestone, **not the whole goal**.936accepted broadcasts do not supply all GM careers/popularity/authenticated people. Following is user-selected; unknown history stays explicit. Private annotations are unverified learner data and preserved RAVs are not yet navigable in this mainline UI. The separate historical-game-coach must add per-move original facts, offline bounded engine alternatives with explicit hypothetical preview/return, provenance and cancellation/cache checks. A folded position panel alone is not that coach. Recall/demo statistics, frame/device quality, local service and an iOS client remain separate tasks; shared iOS compile is not runtime parity. No export/recovery UI exists; do not clear owner data or destructively change schema/version refs. Private histories beyond512half-moves need an explicit engine limit response rather than silently dropping repetition history. Continue sequentially; optional roster/course order is not an approval blocker.

## Next

Claim historical-game-coach and add per-move grounded original-game explanations plus bounded offline engine alternatives/preview/return, preserving exact original score and source bookmarks. Reuse GameLibraryController/GameReplayScreen and the shared legal PositionCoach/GroundedContinuation/ChessAnalysisEngine contracts; keep source mainline and hypothetical board/state visibly separate. Run actual engine/stale/background/both-color/cold/return tests, append truthful history, notify on completion and immediately move to the next tracker task.
