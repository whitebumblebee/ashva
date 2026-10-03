---
seq: 23
agent: codex
date: "2026-10-02T14:48:23Z"
task: repertoire-coverage
status: partial
summary: "Added a separately pinned whole January 2020 broadcast snapshot: 952 inputs, 857 accepted and 95 explicitly quarantined; importer/integrity and combined 936-score observations pass. Corrected PGN unknown-roster validation; Android installation rerun times out and replay performance needs correction."
next: "Finish isolated whole-archive installation and cold-observation validation, then the complete Android regression before closing the finite coverage milestone and continuing grounded teaching."
supersedes: []
git_head: cba53d25fcf1
---

# repertoire-coverage

## What I did

Added a separately reviewed snapshot namespace to the bounded Node downloader and JVM importer. New configuration/lock pins the provider compressed SHA-256 and an exact retained old taxonomy manifest; checked legal/hash-bounded dependency loading avoids regenerating old retrieval provenance. Default configuration, lock and original packs reproduce unchanged. Safe CLI names and bounded unique dependency/acquisition pins have regression tests.

Acquired the entire January 2020 CC BY-SA 4.0 broadcast export: 628,586 compressed bytes, 4,147,031 decoded bytes. All 952 frames have exactly one ordinal/hash-bound disposition: 857 accepted, zero duplicates, 95 quarantined (55 unfinished results, 40 invalid, including illegal moves and an empty score). Accepted scores have 75,073 half-moves, depth1–253, 66,046 normalized positions, 857 taxonomy matches and 328 unresolved source-scoped names. Combined with April there are 936 accepted score records / 82,679 half-moves, within unchanged mobile bounds. Not representative master popularity, complete careers or independently authenticated games.

Android 0.11.0 / code12 specifically allowlists the new immutable pack, with trusted manifest hash, optional manual native installation and explicit disposition display. Existing April UI tests now select their exact fixture rather than relying on the last catalog entry. Added whole-frame provenance/legal score and combined observation tests; real JVM/Android Room cold installation tests; strict supplied-tag tamper rejection. Expanded pipeline/coverage/testing/privacy/storage/README and owner continuation truth. No source/schema/learner IDs were migrated or bounds raised.

First Android run found a repository bug: 39 records omit optional Site/Date/Round tags, while canonical export supplies unknown roster placeholders. Added Pgn.canonicalTags and exact normalized comparison, preserving reported metadata and strict supplied-tag/result/FEN/SetUp/canonical-move checks. Missing metadata is not invented history; tampering is rejected. Pack bytes remain identical. The rerun then exceeded the existing 180-second installation wait; do not claim runtime success. Repeated legal replay/SAN construction needs profiling/efficiency work, not a weakened gate.

## Commands run and their outcomes

- `node scripts/fetch-content.mjs --snapshot=broadcast-2020-01 --record-lock`: acquired the bounded archive, matched provider SHA-256 `9461382d6933dedfbe7bb3de2ac139066d8d8657f2070cb11d59c7686f00f47e`, wrote only new snapshot/cache files. Locked reruns of both default and additional snapshot verify cached hashes without provider calls.
- `./gradlew :contentTools:run --args='import broadcast-2020-01'`: BUILD SUCCESSFUL27s; 857 accepted /0 duplicates /95 quarantined, depth1–253. `./gradlew :contentTools:test :contentTools:run --args='verify broadcast-2020-01'`: BUILD SUCCESSFUL47s,24 importer cases, byte-identical new pack. Default verify reproduces all original3815 taxonomy /79 April records unchanged.
- First isolated focused Android command failed2m49s: one test /one failure at generic source/canonical metadata equality; the initial focused JVM reproduction failed at RoomLearningStore line260, confirming the cause. After exact normalization, the two focused JVM cases passed32s.
- Corrected focused command with full common/build gates: `ANDROID_SERIAL=emulator-5556 ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.openinglab.app.WholeBroadcastInstallationTest` — BUILD FAILED5m50s, one Android case /one failure at the180-second installation wait (test elapsed292.444s including initial taxonomy preparation). **136 JVM /107 Android-host /24 importer cases pass**, zero failures/errors/skips on those reports. Shared iOS compilation succeeds; app/lint/release final task completion must be rerun because Gradle terminates after the connected failure. No complete Android0.11 regression pass is claimed.
- `node --test scripts/*.test.mjs`:26 passed /0 failed /0 skipped. Public audit219 candidate files /0 findings. Redacted pinned Gitleaks8.30.1:0 findings. Pinned actionlint1.7.12 exit0/no diagnostics. Both available APK engine/source/NNUE/recipe hashes verify, but final post-efficiency app assembly/runtime remains pending. Repeat context/public/doctor after log completion.

## External resources touched

Primary Lichess database broadcast license/table/checksum pages checked2026-10-02; downloaded only https://database.lichess.org/broadcast/lichess_db_broadcast_2020-01.pgn.zst. Exact source/game links and collection CC BY-SA terms remain in raw/derived data. Source-reported events include Tata Steel, Gibraltar and the Women's World Championship; no guessed GM alias or player favorite. Existing taxonomy, prepared Stockfish19, isolated emulator-5556 and local SDK are reused. No provider account, paid service, cloud/deploy, Git operation or publication. The owner learning emulator-5554 remains at verified0.10.0 with data preserved; the unverified0.11 build has not replaced it.

## Risks, warnings, and what is NOT done

Current Android whole-pack installation exceeds the existing180-second wait after the metadata fix. Improve repeated legal replay/export/notation work and rerun exactly that bound; do not silently increase it, omit invalid scores or bypass metadata validation. No runtime success or full teacher completion. Original source/pack bytes, policy/bookmark identities and schema4 stay stable. Rejecting incomplete/invalid input preserves full-record audit, not complete score authenticity. A known-result one-ply score remains as reported; do not assert every accepted score was fully played.

Finite coverage remains active pending runtime verification and the remaining teacher milestone. Grounded strategic content, GM library/original-game coach, recall, final board QA, local service contracts and iOS client remain. Connected tests only on isolated emulator-5556; never clear/instrument the owner's learning emulator. Owner manages all Git/publication. Persistent goal remains active, not paused or blocked by test duration.

## Next

Finish isolated whole-archive installation and cold-observation validation, then the complete Android regression before closing the finite coverage milestone and continuing grounded teaching. First eliminate repeated legal SAN/apply work in PGN parsing/export/observed aggregation without weakening legality; verify original/new packs remain byte-identical and all perft/special-move/history/notation cases pass. Keep one coverage claim and real append-only history.
