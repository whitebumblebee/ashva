---
seq: 37
agent: codex
date: "2026-10-03T19:53:13Z"
task: android-end-to-end-user-test
status: partial
summary: "Measured existing 0.15 APK size and public content depth during owner testing: 102.2 MiB debug/93.7 MiB unsigned release; NNUE dominates, named-route coverage remains shallow and finite."
next: Collect the owner's remaining manual flow results before resuming unfinished recall regression verification on an isolated device.
supersedes: []
git_head: a6820a3c24ee
---

# android-end-to-end-user-test

## What I did

Answered the owner's offline size/exhaustiveness question using existing APK ZIP central-directory measurements, output metadata, public reviewed pack records and current code/docs. No rebuilding, implementation, new data acquisition or emulator interaction. Renewed only the manual-testing task; goal remains paused and task acceptance is partial. Added this append-only history entry, not an optimization task or completion claim.

Both retained artifacts are version0.15.0/code16. Debug APK:107,139,036 bytes (102.1757 MiB); unsigned release:98,216,909 bytes (93.6669 MiB). Compressed entries, rounded in MiB:

| Category | Debug | Unsigned release |
| --- | ---: | ---: |
| One NNUE asset | 76.8961 | 75.4119 |
| DEX app/dependencies | 18.7563 | 12.4132 |
| Native binaries, all packaged ABIs | 3.5106 | 3.3088 |
| Three reviewed chess packs | 2.0682 | 1.6736 |
| Engine corresponding source tar | 0.3114 | 0.2551 |
| Engine notices/recipe | 0.0992 | 0.0685 |
| Other resources/metadata | 0.5016 | 0.5116 |

ZIP headers/signature/alignment overhead accounts for the remainder; category sums are compressed entry bytes, not installed usage. NNUE is75.26% of debug/80.51% of release. Its exact uncompressed size is98,511,183 bytes (93.9476 MiB); AndroidStockfish copies verified network data from APK assets to private files for analysis. This copy adds disk use beyond the APK. Full private/installed disk and RAM/frame/battery usage were NOT measured, and no learner rows were read. Native entries include Stockfish ARM64/x86_64 plus SQLite's four packaged ABIs; do not imply every native binary is Stockfish. Engine source/notices are small, not the primary size driver. Release shrinking is not explicitly configured in androidApp/build.gradle.kts; current unsigned artifact is not a store-optimized/signed production release.

Read all3815 public taxonomy JSONL records:3174 names/149 families; source depth1–36 half-moves, median9. Distribution:2337 at1–10 half-moves (61.258% end by White/Black move5),1372 at11–20,105 at21–30,1 at31–36. Thus3709/3815 (97.221%) end by move10. These prefixes overlap and do not represent3815 independent deep repertoire courses. Current teaching adds78 original illustrative15–33-half-move routes across47 families, not independently expert-reviewed theory. Ruy235+9, Sicilian391+7, French212+5, Caro-Kann110+4, Italian187+3, London4+4. The game shelf offers936 accepted January/April2020 source records after local installation, not every famous GM game/full careers/representative master statistics. Original move coaching and bounded engine alternatives do not turn those samples into exhaustive opening theory or authentic historical intent.

Potential future design only: retain small bundled opening content, optional downloaded/cached engine and larger game/course packs, release shrinking and device-specific ABI delivery. Offline usability after installation is compatible with optional first-time downloads, but none of this is currently implemented or newly authorized by a size discussion. Preserve engine/source/network/notices distribution obligations; no license materials removed.

## Commands run and their outcomes

- Read Relay context/status/latest history and docs/OPENING_COVERAGE.md, docs/GM_LIBRARY.md, docs/ENGINE_ANALYSIS.md, docs/OFFLINE_STORAGE.md; inspected build config and AndroidStockfish adapter.
- ls/unzip and read-only inline Node filesystem/ZIP analysis on the two existing APKs:success. All178 debug entries inspected; raw entry sums180,788,493 debug/163,274,246 release. Output metadata confirms version16/0.15.0. Do not equate those raw sums to Android installed size.
- Read-only Node analysis of public openings.jsonl:3815 records, exact depth distribution/counts above; no learner database queried.
- Initial rg against a guessed androidApp/.../engine directory returned exit2 because it does not exist; searched actual sources and found analysis/AndroidStockfish.kt. APK and lock agree on one NNUE asset.
- No Gradle build, native regression, service harness, new installed-storage benchmark, Git command or publication.

## External resources touched

Read https://developer.android.com/topic/performance/reduce-apk-size for primary-source guidance on App Bundle device-specific delivery and R8/resource shrinking. No external accounts, providers, dataset acquisition or mutations. Owner emulator remains untouched/running.

## Risks, warnings, and what is NOT done

Offline functionality is not content exhaustiveness. An empty game shelf still ships the pack bytes; Install means importing bundled assets into SQLite, not downloading. A percentage of all opening theory cannot be derived from this snapshot. No installed total, physical-device performance, optimized size target or full goal completion is claimed. Development remains paused;0.15 full61 native acceptance/recall is still unfinished and0.14 is the latest complete verified baseline. Board quality/iOS remain owner-deferred. Manual usability results are still pending.

## Next

Collect the owner's remaining manual flow results from docs/USER_TESTING.md before resuming unfinished recall regression verification on an isolated exact-serial device. Do not turn the size discussion into permission to change engine/content delivery or resume deferred development.
