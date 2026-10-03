---
seq: 15
agent: codex
date: "2026-10-01T20:04:59Z"
task: repertoire-coverage
status: partial
summary: "Connected all 3815 installed taxonomy routes to 149 searchable source families, both-color replay/practice/branches, endpoint identification and cold resume in Android v0.4.0; full personalized repertoire policies and reviewed plans remain."
next: "Implement and test a versioned side-specific preferred-learner-move and covered-opponent-reply policy on the sourced graph, persisted separately from route bookmarks, with explicit uncovered replies."
supersedes: []
git_head: cba53d25fcf1
---

# repertoire-coverage

## What I did

After completing the interrupted readiness handoff in 0014, claimed repertoire-coverage. Implemented Android v0.4.0 (versionCode 5) without changing package/database identifiers, schemas, source-pack bytes or owner Git work.

Added portable SourcedOpeningCatalog: all 3,815 validated records remain separate routes in 149 name-derived families; source provenance/counts/depths, variant/ECO search with common spelling aliases, endpoint/transposition and explicit last-known identification. Opening historical metadata is now optional: sourced families have no fabricated historical cards. Lesson graphs distinguish SOURCED_OPENING paths. Source move labels state recorded facts; reviewed White/Black plans explicitly report unavailable.

AppViewModel loads the installed catalog and prepares family graphs off the UI thread, caches search labels/fingerprints and bounds sourced graph caches to eight. Async graph preparation has loading/error states. Cold restore waits for the source catalog and rebuilds the exact graph/content fingerprint; a revision guard prevents it overwriting a newly chosen lesson. Catalog notifications no longer cancel/strand a running identifier import; finished imports rematch current catalog state.

Compose Explore adds installed status, Sourced filtering and source counts; details expose all routes, search, provenance and White/Black selection. Authored starters link to sourced variations, so Ruy López can reach 235 routes. Trainer preserves study/practice/hints/branch-return behavior and explicitly paginates continuation cards twelve at a time without dropping graph paths. Unavailable source navigation is handled without a crash. Identifier ambiguous candidates have Explore actions. Updated README, privacy/library/storage/pipeline/testing documentation and added OPENING_COVERAGE.md; existing screenshots remain explicitly labeled v0.3.1 starter images. Relay tracks this as a delivered slice, not completed full repertoire coverage.

## Commands run and their outcomes

- Full final Gradle gate: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64` — **success**. Shared JVM **80 passed**, Android-host **70 passed**, importer **17 passed**; zero failures/errors/skips. Both iOS shared targets compile, not an iOS client/runtime. Pack verification reproduces **3,815 / 79 records**, zero duplicates/quarantine. Catalog regression enumerates every record ID and legally replays all 235 Ruy Lopez paths from both colors.
- Initial gate failed because a new synthetic ContentManifest fixture omitted limitations. Corrected it, then repeated successfully. No source model/data was changed to weaken validation.
- Isolated `ANDROID_SERIAL=emulator-5556 ./gradlew :androidApp:connectedDebugAndroidTest`: two initial runs were **21 passed / 1 failed** each. The new source UI test first targeted an uncomposed lazy-list row; fixed by scrolling to its stable key. The second assertion expected an exact text value instead of a substring in the longer source label; fixed explicit substring assertions. Final complete run: **22 passed / 0 failed / 0 errors / 0 skipped**, 4m18s, only Ashva_Source_Test API33 targeted. Includes starter/source navigation, 26-ply Black Berlin route, branch pagination/return, White flip, source hints, transposed Nimzo identification and fresh database/ViewModel cold restore without SavedStateHandle.
- Follow-up Compose ModifierParameter warning: moved Explore's modifier to its first optional parameter. Repeated debug/unsigned release/lint gate successfully; **0 lint errors / 4 pre-existing update warnings**. Final-APK focused source-navigation/branch test using `-Pandroid.testInstrumentationRunnerArguments.class=com.openinglab.app.SourcedLearningTest#installedCatalogSearchRouteSelectionBothColorReplayAndBranchReturn`: **1 passed / 0 failed**, 1m15s. This is an additional run of an existing case, not a 23rd unique test. Full-suite XML retained in ignored local audit state before the focused runner replaced generated results.
- `node --test scripts/*.test.mjs`: **20 passed / 0 failed / 0 skipped**. Directory Gitleaks 8.30.1: **0 findings**, repeated including this completed log. Public audit: **157 files / 0 findings** after this log. Final portable context/local doctor: **15 logs / 19 tasks / all checks passed**. actionlint 1.7.12: **exit 0 / no diagnostics**. No hosted CI or publication claim. Disposable headless test emulator shut down after verification; its reusable private AVD path is retained, with no owner learning-data changes.

## External resources touched

Existing local CC0 taxonomy pack lichess-openings-c67912be581f-import-v1, upstream revision c67912be581f0793dbaa776be5ccf111e01f88d9; existing CC-BY-SA broadcast pack only for unchanged verification/tests. No new chess data or provider was fetched. Official pinned Gitleaks download for directory checks; installed SDK API33 image for a newly created disposable AVD. Previous temporary isolated AVD was absent; new private runtime path/name are in ignored .public-audit/local-environment.json. Emulator serial 5556 only; the owner's emulator/data were not started, installed, instrumented, cleared or uninstalled. The disposable test runner can uninstall its own target. Unique test-only database files are cleaned up by tests; no learner files removed. Relay/AGP automatically read existing owner HEAD metadata; no direct Git command, mutation, account change, paid service, upload, commit, push or publication by the agent.

## Risks, warnings, and what is NOT done

Task remains IN_PROGRESS. This is the full delivered taxonomy snapshot, not full repertoires, all possible theory, popular-game reply populations, reviewed strategic plans, engine alternatives or GM-game coaching. London System has only four source routes (5–9 plies); do not generalize Ruy Lopez's 235 routes (5–36) to every family. Source names/identities are preserved; family grouping is mechanical and endpoints can have multiple names. Intermediate unnamed positions can be last-known rather than current named matches. FEN cannot supply absent history.

Current RepertoireSelection persists only the active route/side; it is NOT a repertoire policy of preferred learner moves and included opponent replies. Source lesson IDs include immutable pack IDs. Missing/inactive old source content reports unavailable and retains the bookmark; automatic retained-version lesson selection/upgrade mapping is not implemented. Oversized future families reject visibly rather than truncate. Initial source-route launch prepares a graph asynchronously; UI/large-catalog frame/memory/device benchmarks remain deferred. No engine, backend, GM shelf, recall scheduler or iOS UI has been added. Existing update warnings and Room expect/actual language warning remain unsuppressed. User distribution/Git history/hosted CI still belong to the owner; screenshots were not recaptured this slice.

## Next

Implement/test a versioned side-specific preferred-learner-move and covered-opponent-reply policy on the sourced graph, persisted separately from route bookmarks, with explicit uncovered replies. Start with shared catalog/graph contracts and synthetic transposition/branch-gap tests, then the non-destructive Room migration and Compose selection/coverage UI. Preserve broader exploration and both-color source labels; do not infer frequency/best-move/strategic explanations from taxonomy names. Renew the repertoire-coverage claim. Do not mark it DONE merely for this source explorer/player slice.
