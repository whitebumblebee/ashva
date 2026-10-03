---
seq: 27
agent: codex
date: "2026-10-02T19:57:19Z"
task: gm-game-library
status: partial
summary: "Implemented Android0.13 native player library, local follow/private PGN controls and exact-source original-game replay/bookmarks; 155JVM/123Android-host and three focused UI cases pass. Full regression including a fourth retained-source case is running after interruption."
next: "Finish the complete isolated0.13 regression and pack/APK/public checks, repair any actual failures, then finalize the GM-library milestone and begin historical-game coaching."
supersedes: []
git_head: cba53d25fcf1
---

# gm-game-library

## What I did

Added GameLibraryController and immutable screen states, native GameLibraryScreen/GameReplayScreen, Home/navigation entries and AppViewModel's shared serial bookmark queue. The lazy source/hash-keyed metadata index, cancellable filters, explicit local follows/private import and source-pinned full original score preparation run away from Main. The shelf exposes source-reported identities/titles, installed-sample limits, exact provenance, filter counts and retained unavailable followers; favorites are not inferred.

GameStudyReference and optional default-null LessonBookmark.gameReference pin exact public pack/manifest/record or canonical private identity. Room checks retained exact content and refuses practice/branched original-game bookmarks; replay restore verifies content fingerprint, original path and saved color/cursor. Game study does not mutate opening selections or recall attempts. Source versions, private comments and original result remain unchanged. Native controls cover full mainline jumps/autoplay/speed/flip; source comments are unverified, not GM intention. Added the already centrally pinned serialization JSON runtime to Android for small SavedState references, not large PGN Bundles.

Corrected failed-load stale-game display, restored-library lazy reload, navigation cancellation of pending game preparation, and opening-start cancellation before asynchronous graph work. New common/reference and JVM retained-public/private bookmark cases supplement foundation tests. Three focused native cases verify shelf/search/event filter, full replay/both colors/recreation, private import/reject/follow/unfollow, and cold private bookmarks with missing-content fail-closed behavior. A fourth source-retention/bad-hash native case was added afterwards and is in the complete regression, not included in the three-case success claim. docs/GM_LIBRARY.md records the implementation and limits; general docs still await final0.13 verification.

## Commands run and their outcomes

- Initial controller compile failed on missing Android serialization JSON runtime and a nullable cross-module reference; fixed with the existing pinned dependency and checked local values. Subsequent shared/app/lint/iOS check succeeded46s (foundation152JVM/121Android-host). Native compile then failed on LifecycleEventEffect's positional callback; named onEvent fixed it. Debug/shared/lint verification succeeded36s before navigation regressions were added.
- First three-case isolated run failed48s: an outdated synthetic fixture used nonexistent seed ID `london-system`, a substring assertion used exact matching, and a dialog button tried scrolling without a scroll parent. Corrected fixture ID to `london`, substring flags and dialog clicks; no failures were hidden/skipped.
- `ANDROID_SERIAL=<isolated-test-serial> ./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.openinglab.app.GameLibraryLearningTest`: **BUILD SUCCESSFUL57s;155JVM/123Android-host/3focused native cases**, zero failures/errors/skips. This includes new exact-reference/common and retained-public/private Room tests, but not the fourth native case added afterwards.
- A planned full command was interrupted before a usable process/result checkpoint. On explicit user continuation, read-only process/report checks found no running Gradle wrapper and only the prior three-case XML; no complete-pass claim. Renewed the expired same-agent claim. Restarted full shared/importer/pack/debug/release/lint/iOS/isolated UI command; current unified session is20729 and lists only the isolated test AVD. Results are pending, not yet a task completion.

## External resources touched

Existing local SDK/Gradle/Room and immutable previously reviewed packs only. No new chess archive, network player API/authentication, account, paid provider, cloud, Git operation, publication or learner-data clearing. Synthetic private names/PGNs in tests. Owner learning emulator remains0.12/schema4; isolated API33 test emulator is the only instrumentation target. Local environment paths remain in ignored configuration.

## Risks, warnings, and what is NOT done

GM library is IN_PROGRESS. The full regression/fourth native test, unsigned release, both shared iOS targets, January byte-identical verification, engine assets and public/secret/context/doctor must be confirmed before DONE or owner update. Native library/full original replay are implemented, but per-move game coaching, engine alternatives/return and cached game explanations belong to the next historical-game-coach milestone. A position panel is not that coach. Private import mainlines replay; preserved user RAVs are not yet navigable in this UI. Neither reported GM tags nor936 accepted broadcasts establish full careers/identity authenticity/popularity. Schema5 is additive; preserve old schemas/rows/bookmarks and never instrument the learning install. Do not rewrite log0026's earlier foundation-only facts. Overall goal remains unfinished; user asked continued sequential work, not another optional roster-order approval gate.

## Next

Finish the complete isolated0.13 regression and pack/APK/public checks, repair any actual failures, then finalize the GM-library milestone and begin historical-game coaching. Poll session20729 while it exists; on interruption inspect actual XML/process state before starting another run. Update current docs and owner emulator only after complete acceptance. Append a new final verified log, mark gm-game-library done, notify the owner and immediately claim historical-game-coach.
