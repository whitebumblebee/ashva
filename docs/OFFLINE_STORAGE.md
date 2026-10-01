# Offline content and learner storage

Android v0.3.0 uses Room KMP 2.8.5, bundled SQLite 2.7.1, KSP 2.3.12 and coroutines 1.11.0. Versions were checked against official releases on 2026-10-01; Room 3's alpha documentation is not a reason to silently adopt an experimental library. Room's generated expect/actual constructor still produces Kotlin's expect/actual-class Beta warning. Both shared iOS targets must compile; there is no iOS app/runtime validation yet.

## What users can do

- On Learn, open **Offline library & sources**. Install the opening-name pack first, then the April 2020 broadcast pack. These reviewed packs are included in the APK and installed into SQLite locally, without a server, network or account. This is **not** a new live download provider.
- Each pack shows Not installed / Installing / Installed / Installation failed, source attribution, version, license and scope. An update failure can retain an older available pack. The app does not claim the data packs are finished teaching courses.
- Study/practice a seed lesson, choose a branch/color/cursor and return to Learn. Wait for **Saved for offline resume** before force-stopping. Relaunch and tap **Continue learning**: the selected line, branch-return stack, color, cursor, mode, hint and assistance context restore. Playback never resumes automatically.
- Actual attempted moves are recorded with outcome, color, route, cursor and assisted status. Home/Review/Profile mastery, streak and scheduling figures remain prototype values; a stored attempt is not a mastery score or spaced-recall scheduler.

The learning catalog is still seven authored openings/thirteen seed lines. SQLite can retrieve all 3,815 sourced taxonomy records and 79 complete original scores, including their exact source versions and position indexes. Wider opening search/teaching and GM-library entry points belong to the next tasks, not this storage milestone.

## Ownership and contracts

`shared/.../storage/LearningStore.kt` is the platform-neutral repository boundary. `RoomLearningStore` supplies the implementation; Android/iOS/JVM have database and SHA-256 adapters. Android owns a single store through `OpeningLabApplication` and injects it into the screen ViewModel. The existing SavedStateHandle remains a fast Android recreation path, while portable replay snapshots persist in SQLite for cold reopen. Writes are asynchronous and ordered/conflated for the latest bookmark; wait for the visible saved confirmation before terminating the process. An action killed before its save completes may restore the previous bookmark.

Tables separate immutable versioned packs/content/position indexes/notices/annotation slots from bookmarks, attempts and repertoire selections. Learner rows have **no foreign keys into replaceable content**. A changed lesson fingerprint is not resumed against incompatible moves; the bookmark/history are retained and the app reports changed/unavailable content. No old pack versions are automatically pruned. Annotation slots are empty because this snapshot supplies no reviewed teaching annotations or engine lines. Original source annotations remain in the raw pipeline inputs, not invented in the mobile store.

## Installation, interruption and rollback

1. The Android adapter reads bounded assets on an IO dispatcher. Only two reviewed pack directories are packaged through AGP's generated-source variant API; raw archives and arbitrary future packs are excluded.
2. The manifest SHA-256 must match the separately pinned app catalog. Schema/processor, exact filenames, collection license, byte/count bounds, every payload checksum, legal SAN/UCI/FEN replay, unique IDs, source locations and disposition/depth counts are validated before activation. A checksum pins integrity, not independent authenticity or a cryptographic signature.
3. Game naming requires the exact installed taxonomy pack ID **and manifest hash**. The consumer recomputes the last known taxonomy match against that retained dependency, not whichever newer taxonomy happens to be active.
4. One SQLite immediate transaction inserts all content/index rows and switches the source's active pointer plus installed status. Exceptions/cancellation roll back every partial row. Previously active content and learner state stay intact. Reinstalling identical bytes is idempotent; reusing an immutable pack ID with different bytes is rejected.
5. A persisted LOADING job found at startup becomes ERROR with a retry message. App-process death before commit leaves the old active version; after commit it leaves the new complete version. Library checks expose errors; no destructive reset is attempted.
6. `activatePreviousVersion(sourceId, packId)` atomically reactivates a retained version after checking its source and exact dependencies. The repository/tests support rollback; no user-facing version-management UI or remote pack transport is included yet. Keep dependencies when eventually implementing pruning.

Bounds: manifest 64 KiB; each payload 8 MiB; total declared payload 12 MiB; at most 50,000 opening/issue records and 10,000 games; 512 KiB per JSONL row. Existing source validation further limits moves/PGNs. Validation runs away from the main thread. This is a bounded installation slice, not a performance claim for a multi-million-game catalog.

## Database migration and verification

Exported schema 1 is a **pre-release fixture**, not a previously shipped Room database. Schema 2 adds retained attribution notices using explicit Migration(1, 2). All platform builders register it; no destructive-migration fallback is enabled. Tests create a real v1 SQLite file from its exported schema and reopen/migrate it with learner rows intact. Future schema versions need explicit reviewed migrations and new fixtures.

JVM repository tests cover empty/missing state, cold reopen, learner selections/attempts/bookmarks, idempotence, immutable-ID/schema/checksum rejection, missing files/dependencies, atomic failure/cancellation after row insertion, abandoned loading jobs, updates/rollback/progress retention, real pack installation and exact dependency retention. Compose instrumentation covers bundled installation/licensing and fresh database/ViewModel restoration without a SavedStateHandle bundle, plus actual assisted attempts.

**Run connected tests on an isolated emulator/device.** The Gradle test runner may uninstall its target APK and delete its data. With an isolated device already running, select its exact serial:

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew :androidApp:connectedDebugAndroidTest
./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
node --test scripts/fetch-content.test.mjs
./gradlew :contentTools:run --args=verify
```

Confirm the Gradle output names **only** the intended isolated device. Do not clear data or uninstall on the user's learning install. Use `adb -s <exact serial>` when more than one emulator is running.

Official release references: [Room](https://developer.android.com/jetpack/androidx/releases/room), [SQLite](https://developer.android.com/jetpack/androidx/releases/sqlite), [KSP](https://github.com/google/ksp/releases/tag/2.3.12), [coroutines](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0). See [Content pipeline](CONTENT_PIPELINE.md) for the separate CC0 taxonomy and CC BY-SA broadcast rights/provenance.
