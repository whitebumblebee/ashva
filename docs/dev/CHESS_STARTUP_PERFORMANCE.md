# Task G: chess core and bundled startup performance

Measured locally on 2026-10-06 with the real bundled packs, offline Gradle and fresh JVM test workers. Device cold-launch/frame measurements remain for Claude Code. No Git, relay commands/edits, network, emulator or adb commands were used. Pack bytes, learner data, identifiers and saved lesson fingerprints were preserved. Task F's UI ownership was respected.

## Cause and change

`ChessMove` constructed two regular expressions per instance, and UCI/SAN parsing constructed more. Validation now uses length and character ranges; promotion validation no longer allocates a list. SAN strips annotation glyphs and locates destinations directly. Remaining PGN/FEN patterns are compiled once.

Every pseudo-legal candidate previously copied a board map, made a new position, then scanned it for check. Legal move generation now uses an immutable internal 64-square snapshot and one local scratch array per scan. Make/unmake covers captures, en passant, castling and king movement. Target-oriented attack checks use that array. A played move copies the public map once. Public map/data-class APIs, move ordering, position equality, clocks, castling rights and repetition semantics stay intact.

The actual Android loader constructs teaching summaries with `onDemand = true`. Course preparation and its temporary exact-history trie run only when that course is requested, on the shared worker dispatcher. Main-thread course getters request preparation and expose the existing loading state; they never return an incomplete summary to the trainer or a lesson graph. Search indexes include both source variation names and authored continuation titles. Tooling's explicit all-course API still constructs and checks every route. Study routes avoid a redundant SAN transition. Seed identification indexes are also lazy.

Tactics checksum/decode/structural checks publish cards quickly. A set is legally validated before a cycle or mistakes replay can start. Full-pack validation runs after the first frame and both catalogs are ready. Deep courses retain per-chapter preparation; background work finishes remaining legal chapter checks without building every presentation.

Validation proofs live in the app-private `bundled-validation` SharedPreferences file. Keys contain the exact trusted SHA-256, `BuildConfig.VERSION_CODE`, validator version and pack/chapter section. The current validator versions are both 1. Bump the relevant version whenever its validation rules change. Both bundled readers still verify their trusted checksum on every read, then JSON is decoded and structure checked. A missing/different key performs full legal validation. Proofs are persisted only after success; failed/cancelled work never authorizes its unfinished section. Cached chapters reconstruct exact history-aware boards without repeating legality/SAN checks. They retain the same presentation and lesson graph. Loss of the disposable marker file causes revalidation.

Installed taxonomy packs already checksum-check and legally validate their payloads in the atomic Room installation transaction. Startup decodes those retained records; it no longer eagerly builds all coached teaching routes. No source or learner database schema/cache was added.

Course/tactics/catalog workers share `Dispatchers.Default.limitedParallelism(2)`. Background validation uses a one-worker view within that budget and starts after a completed initial frame. The actual activity also defers broadcast observation replay until the repertoire editor requests it. Default injected test/tool ViewModels retain their existing eager observation behavior.

## Host timings

Milliseconds, reporting wall time / process CPU time. Process CPU includes JVM compilation/GC threads. These diagnostics do not impose flaky elapsed-time test assertions. The baseline and isolated final core runs use the same stage order and real inputs; the after constructor measures summaries, with full preparation separately below.

| Work | Before wall / CPU | After wall / CPU |
| --- | ---: | ---: |
| TeachingCatalog construction | 545.389 / 1342.506 | 14.684 / 34.476 |
| Full teaching routes, when explicitly requested | included in constructor | 281.414 / 714.300 |
| Legal moves: 10,000 position scans | 323.358 / 727.032 | 134.690 / 287.983 |
| SAN replay: all 3,815 bundled source routes, 36,997 plies | 1418.481 / 2528.310 | 350.616 / 727.480 |
| Tactics full validation | 418.028 / 907.382 | 254.240 / 530.276 |
| Deep-course full validation | 498.862 / 847.705 | 119.233 / 278.589 |

The isolated diagnostics can be reproduced with the same offline Gradle prefix and either
`--tests 'com.openinglab.shared.chess.ChessStartupBenchmarkTest.bundledStartupAndChessCore'` or
`--tests 'com.openinglab.shared.chess.ChessStartupBenchmarkTest.secondLaunchHomeWorkWithPersistedMarkers'`
on `:shared:jvmTest`.

The legal-move diagnostic cycles through the starting position, Kiwipete and a rook/pawn endgame, checks the aggregate move count, and warms 1,000 scans before measuring 10,000. SAN replay checks each parsed UCI against its source record. Timing varies with JVM warming and other host work: the final complete gate run measured legal scans at 39.275 ms, tactics full validation at 182.967 ms and deep-course full validation at 114.941 ms. The table retains the separate fresh-worker measurements.

A separate fresh-worker Home workload reads/checksums the source manifest and both bundled packs, decodes their JSON, constructs 149 teaching cards and 898 deep-course line summaries, and builds the tactics index. It writes real temporary marker files after successful full validation, then recreates marker readers and loaders for the second launch:

| Home work | Wall ms | Process CPU ms |
| --- | ---: | ---: |
| First launch, no markers; summaries ready before background validation | 395.630 | 923.744 |
| Second launch, persisted matching markers | 153.746 | 333.834 |
| Largest cached chapter (`morphy-other`), presentation + lesson graph | 70.315 | 154.696 |

The measured Home data path is below 2 seconds, and its second-launch CPU is below 1.5 seconds. This is host-equivalent shared data work, not an Android launch/frame measurement. Initial Room pack installation, Android lifecycle/rendering and an arbitrary populated learner database are outside that Home diagnostic. The final full-suite, warmed Home samples were 136.124 / 312.636 ms first launch and 98.330 / 223.580 ms second launch.

## Correctness and gates

The full teaching presentation SHA-256 stays `75bd02824b0bda934fa15fd2092a66ae4c68771f148af64254598316e9c206a2`. All 11 existing deep-course hashCode/SHA-256 fingerprint pairs are unchanged. Task E's benchmark checksum pin was updated to the current trusted course hash `7437b57ef044c4d3a42cbad45044095db291cf72b65cf803175b6c8b2243fa58`; its fingerprint expectations were retained.

New regressions cover independent SHA/app-version/validator-version invalidation, success-only proofs, cancellation/illegal content, cached graph/board/history equivalence and real persisted-marker reloads. Existing perft, notation and PGN tests pass, including starting depth 4 (197,281), Kiwipete depth 3 (97,862), en passant discovered checks, promotions and castling.

Final command:

```sh
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew --offline --max-workers=4 \
  :shared:jvmTest :shared:testAndroidHostTest :courseTools:test :contentTools:test \
  :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug \
  :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
```

BUILD SUCCESSFUL in 45 seconds: 103 actionable tasks, 36 executed / 67 up-to-date.

- Shared JVM: 252 passed, 0 failures/errors/skips.
- Shared Android host: 195 passed, 0 failures/errors/skips.
- courseTools: 45 passed, 0 failures/errors/skips.
- contentTools: 24 passed, 0 failures/errors/skips.
- Debug assembly, instrumentation-test Kotlin compilation and both shared iOS targets passed.
- Lint: 0 errors, 1 existing AndroidGradlePluginVersion warning. No new lint warnings.
- Device instrumentation was compiled, not executed, per the task's device-tooling restriction.

## Task G files changed

- `shared/src/commonMain/kotlin/com/openinglab/shared/model/ChessModels.kt`
- `shared/src/commonMain/kotlin/com/openinglab/shared/chess/BoardPosition.kt`
- `shared/src/commonMain/kotlin/com/openinglab/shared/chess/ChessNotation.kt`
- `shared/src/commonMain/kotlin/com/openinglab/shared/chess/Pgn.kt`
- `shared/src/commonMain/kotlin/com/openinglab/shared/chess/OpeningIdentifier.kt`
- `shared/src/commonMain/kotlin/com/openinglab/shared/data/TeachingCatalog.kt`
- `shared/src/commonMain/kotlin/com/openinglab/shared/data/StudyRoutes.kt`
- `shared/src/commonMain/kotlin/com/openinglab/shared/data/PackValidationCache.kt` (new)
- `shared/src/commonMain/kotlin/com/openinglab/shared/course/DeepCourse.kt` (validator/catalog caching only)
- `shared/src/commonMain/kotlin/com/openinglab/shared/tactics/TacticsPack.kt`
- `shared/src/commonTest/kotlin/com/openinglab/shared/course/ValidationCacheTest.kt` (new)
- `shared/src/jvmTest/kotlin/com/openinglab/shared/chess/ChessStartupBenchmarkTest.kt` (new)
- `shared/src/jvmTest/kotlin/com/openinglab/shared/course/StartupBenchmarkTest.kt` (current checksum pin)
- `androidApp/src/main/kotlin/com/openinglab/app/ui/AppViewModel.kt` (loading/startup)
- `androidApp/src/main/kotlin/com/openinglab/app/tactics/TacticsController.kt` (loading/validation gates)
- `androidApp/src/main/kotlin/com/openinglab/app/content/BundledValidationMarkers.kt` (new)
- `androidApp/src/main/kotlin/com/openinglab/app/content/StartupDispatchers.kt` (new)
- `androidApp/src/main/kotlin/com/openinglab/app/OpeningLabApplication.kt`
- `androidApp/src/main/kotlin/com/openinglab/app/MainActivity.kt`
- `docs/CHESS_STARTUP_PERFORMANCE.md` (this report)

Next: Claude Code measures retained-data device cold starts, first-frame/Home-card readiness, second-launch CPU with markers present, and a cached chapter/cold bookmark without clearing learner data.
