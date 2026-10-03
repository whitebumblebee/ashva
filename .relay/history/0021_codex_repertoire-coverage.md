---
seq: 21
agent: codex
date: "2026-10-02T13:43:27Z"
task: repertoire-coverage
status: partial
summary: "Built Android 0.9.0 main-entry courses for all 149 source families with 3815 preserved named routes, 78 deeper study continuations and grounded both-color guidance; new teaching UI tests pass, startup regressions from full-suite testing are being corrected."
next: "Verify the corrected automatic-install and bookmark-loading flows on the isolated emulator, then continue coherent multi-family practice and the remaining teacher acceptance without another course-order approval gate."
supersedes: [20]
git_head: cba53d25fcf1
---

# repertoire-coverage

## What I did

The owner explicitly requested continuing across all openings until the teacher is testable, followed by remaining tracker work, with real Relay history. This supersedes only log 0020's assessment that an optional first-course question blocks local progress; its recorded 0.8.0 implementation/test results remain historical facts. No additional course-order approval is needed. The persistent goal remains active; repertoire-coverage stays IN_PROGRESS.

Built Android 0.9.0 / code10 main-entry TeachingCatalog: 149 source families, all 3,815 unchanged named source routes, and 78 original illustrative study continuations across 47 families, 15–33 half-moves. Ruy has 235 source + 9 authored routes, Sicilian 391 + 7, London System 4 + 4, French 212 + 5, Caro-Kann 110 + 4 and Italian 187 + 3. New OpeningGuides provide conditional both-color plans, structure/pitfalls and variation focus; PositionCoach legally derives captures, development, changed central attacks, checks, open files and pawn-structure facts. It does not invent engine evaluations, GM intentions or forced wins. Uncommon families visibly use general guidance. Illustrative routes are not historical games or identification records.

Main Learn/Explore and identifier actions prefer versioned course IDs; Starter and raw Sourced remain explicit separate views. Full idea, step controls, deliberate branching/return, both-color practice and automatic hints work on courses. Original source IDs/moves/SAN/fingerprints, legacy bookmarks/policies, schema3 and package/database names remain unchanged, not silently rebound. Course cards show actual route/depth counts, not demo percentages. Local reviewed opening assets install automatically. Teaching presentation/graph preparation is cancellable/off Main; exact-history prefix sharing reduces repeated work without reusing a different history, and a single manifest/teaching-version-keyed process cache retains immutable presentation only. No learner state is cached there.

Added three common PositionCoach cases, three importer/full-catalog cases (all routes legally validated, source identity unchanged and both-color/nested return), and three Compose teaching cases for main Ruy, other families and actual cold Room course restore. Existing legacy behavior tests explicitly choose Starter rather than accidentally switching their assertions to new course paths. Updated README, coverage/testing/privacy/public-readiness/pipeline/storage and Relay truth.

Full-suite testing then found startup regressions. Corrected the availability collector to launch installation separately instead of publishing a stale pre-install status, and restored no-bookmark/seed state without waiting for thousands of coached routes. Those corrections are pending complete-suite revalidation at the time of this checkpoint; do not treat earlier green build results as their final runtime proof.

## Commands run and their outcomes

- Earlier current-course shared/importer gates pass: 128 JVM / 101 Android-host / 20 importer cases, zero failures/errors/skips. All 78 study routes parse/replay legally; initially invalid SAN/move-order fixtures were corrected (London bishop before e3, legal unambiguous Ne7/Ne2 with pinned alternative knights), not accepted by weakening notation rules.
- Full command `ANDROID_SERIAL=emulator-5556 ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest` completed BUILD FAILED / 9m58s because instrumentation had **37 passed / 4 failed / 0 errors / 0 skipped**. All three new teaching UI cases passed; existing engine startup (1), observed startup (2) and offline installation (1) failed. Both shared iOS compiles, debug/release assembly and pack verification succeeded. Lint: 0 errors / 4 existing dependency-update warnings. Shared compilation is not an iOS client/runtime test.
- Earlier teaching-only runs: 2 passed / 1 failed twice due to a missing Home card test tag, corrected without changing behavior assertions; main Ruy focused repeat passed. The final 41-case run above includes all three passing together.
- Startup fix focused command with comma-separated class argument selected only **4 EngineAnalysisTest cases**, all passed / BUILD SUCCESSFUL 19s. It did not run the other requested classes; the complete command above is running again, not yet a claimed pass.
- Pack verify reproduces 3,815 taxonomy routes / 79 broadcast scores with 0 duplicates/quarantine and unchanged bytes. Both actual APK engine/source/NNUE/recipe hashes/notices verified before startup corrections; repeat if distributing a later build.
- `node --test scripts/*.test.mjs`: 24 passed / 0 failed / 0 skipped. Public audit: 200 candidate files / 0 findings. Pinned redacted Gitleaks 8.30.1: 0 findings. Pinned official actionlint 1.7.12: exit0 / no diagnostics. No hosted CI or publication. These checks precede this log; context/audit/doctor are repeated after filling it.

## External resources touched

Existing CC0 taxonomy pack lichess-openings-c67912be581f-import-v1, existing CC BY-SA 4.0 April 2020 broadcast pack, prepared Stockfish 19 assets, SDK and isolated AVD only. No new source collection, commentary corpus, provider account, paid model/service, deployment or Git mutation. Official pinned scanner was downloaded for local checks. Private runtime/scanner/AVD paths stay ignored; history commands use portable paths. The owner's learning emulator-5554 has not yet been updated this pass and has not been instrumented, uninstalled, cleared or inspected for learner rows.

## Risks, warnings, and what is NOT done

This is substantive broad teacher progress, not task/goal completion: 3,893 finite routes are not every theoretical continuation or representative master-population coverage. Only 47 families have longer authored additions; plans are original conditional Ashva guidance, not independent expert review. Coherent named multi-family membership/practice, broader cleared score populations, richer grounded strategy, GM library/game coach, recall, final board QA, local scale contracts and iOS client remain. Existing historical metadata demos and Review/Profile statistics remain prototype. The 79-score source sample must not be called a verified GM roster/career archive. No pending scope question is a reason to suspend safe local implementation.

Do not mark repertoire-coverage DONE based on this count increase or the new tests. Finish complete startup verification, record later actual outcomes in a new append-only log, and continue meaningful core teacher work. Preserve all legacy identities and learner state; use only isolated emulator-5556 for connected tests. Owner retains all Git and publication.

## Next

Verify the corrected automatic-install and bookmark-loading flows with the complete gate command above on isolated emulator-5556, then continue coherent multi-family practice and remaining teacher acceptance without another course-order approval gate. Keep the goal and coverage claim active, record actual outcomes and notify the owner at verified milestones; gates → log → done only for genuinely completed task acceptance.
