---
seq: 34
agent: codex
date: "2026-10-02T23:42:19Z"
task: persistent-recall-review
status: partial
summary: "Completed schema-six service/build/public regressions and added idempotent failed-save retry; orchestrated native run proves60/61, aggregate-flow timing assertion fixed and full rerun underway. Owner now defers board quality as well as iOS."
next: "Finish isolated full native rerun after the scope-flow wait fix, then complete recall history and stop implementation for the owner-selected full-flow testing phase; do not claim board/iOS."
supersedes: []
git_head: cba53d25fcf1
---

# persistent-recall-review

## What I did

Continued0033's Android0.15/code16/schema6 recall. Added a bounded in-session failed-write queue and explicit Retry pending saves in trainer/Review: retries reuse original event ID/time and exact closure, never new answers or doubled counts. Capacity refusal stays visibly unsaved; no unbounded queue/pruning. Saved review acknowledgement is dispatched after the answered state/event ID is installed, covering immediately completing stores. Added a fifth native synthetic failure/retry/Next-unlock case. Existing four cover real counts, both-color cold review, pinned sets and actual successful/failed/stopped Stockfish exposure.

Updated recall/storage/privacy/user guides and README implementation descriptions, while keeping the current verified-version banner0.14 until final gates. A new ordered full-flow checklist covers courses, study/branches/practice, policies/sets, identifier/sources, GM/private/original coaching, engine previews, real recall and cold resume. Owner's latest2026-10-03 instruction supersedes automatic board continuation: finish recall, stop implementation, defer compose-board-quality and ios-client, then full-flow testing. PROJECT/tasks record that stop point and a new unclaimed android-end-to-end-user-test TODO,21 tasks total. No board or iOS client code changed.

Long single-instrumentation runs terminated around43–44 cases amid guest low-memory process deaths. Adopted official stable Android Test Orchestrator1.6.1 as test-only dependency, one instrumentation process per case, explicit clearPackageData=false. Gradle connected tests still uninstall target packages; only the disposable device is used. No emulator RAM/heap increase or owner app manipulation. Orchestrator completed all61 cases, with60 passing and one async aggregate-Flow test assertion failing; added explicit observable scope/totals waits without increasing their limits. Full final rerun is now executing.

## Commands run and their outcomes

-0033's full gate session34806 completed12m34s:58/60 native cases pass. Studied completion now correctly counts8 assisted answers, not the old hint-only1; cold review acknowledgement also failed. Added deferred acknowledgement and corrected assistance expectations. An initial patch accidentally changed a different hint-only assertion to8; restored that one to1 and changed the intended Study assertion to8. Do not claim the erroneous patch was verified.
- Focused RecallLearningTest after retry/ack changes:5/5pass,0failures/errors/skips,17.068s test time; GradleSUCCESS29s.
- Another full run29489 ended4m41s with44 cases/4reported failures (two mistaken assertions, real engine timeout, final interrupted case). Corrected assertions; real-engine fixture exercises one deliberate retry after timeout and verifies no assistance from failure, native process count returns0 and no budget loosening. Two intervening test-import compilation failures (19s/2s) were corrected to the existing com.openinglab.app.analysis.ProcessUciTransport import. Subsequent65858 run stopped4m22s after43 cases/one interrupted-case failure. Guest events show low-memory/system-process deaths and target death, not a confirmed Ashva Java crash/OOM cause; no broad claim of production memory safety.
- Orchestrated42063 full native61:60pass/1fail/0errors/skips, GradleFAILED16m56s. Sole failure: scopes.single() read an empty aggregate Flow immediately after the enrollment transaction acknowledgement. All other cases including real engine/retry/Study/repertoire/library/source/whole-month pass. Added explicit10s waits for scope/totals emissions; no app runtime code or wait bounds changed after that full run.
- Final native command now running as unified exec_command session80642: ANDROID_SERIAL=<isolated-test-serial> ./gradlew --max-workers=2 :androidApp:connectedDebugAndroidTest. Only Ashva_Source_Test/API33 listed. Do not read the previous61/1 report as this run's result.
- Post-Orchestrator non-native gates94657: ./gradlew --max-workers=2 :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentService:test :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64; SUCCESS1m56s.176JVM/136Android-host/24importer/7service-unit XML totals,0failures/errors/skips; both shared-iOS targets compile, not client work. Expected Room expect/actual Beta and unstripped native library/multiple Kotlin-daemon warnings retained.
- node scripts/test-content-service.mjs: schema6 regression7unit/12real SQL-HTTP-UCI, actual loopback CIO process source import/search/real engine passed; GradleSUCCESS41s. Unique ephemeral test DB removed; Docker/unrelated containers untouched.
- Default pack verification:3815taxonomy/79April,0duplicates/quarantine. Separate verify broadcast-2020-01 SUCCESS19s:857accepted/0duplicates/95quarantined. Orchestrated whole-month native test passed with unchanged180s per-phase waits:taxonomy25.190s,January123.399s,observations164.046s,cold211.080s from start. No source bytes/counts/rights/bounds changed.
- Debug and release APK exact engine/source/network/recipe/notices verification pass after final builds.28Node tests pass (77.216ms); public surface272files/0findings, Gitleaks8.30.1/redacted0findings, actionlint exit0. Context33logs/21tasks/0findings and strictdoctor33/21 pass before this new partial log; final docs/history scans remain.

## External resources touched

Read-only official Android runner/isolation and stable test release documentation: https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner and https://developer.android.com/jetpack/androidx/releases/test#orchestrator-1.6.1 (stableJuly31,2025). Test-only Maven orchestrator1.6.1 acquired; runtime libraries stay pinned. Read-only preliminary Compose/benchmark guidance was inspected, but no board/benchmark task claimed or implemented. Existing approved local Docker/PostgreSQL/host engine and SDK/test emulator only; no new provider/source bytes/cloud/account/spend/deployment/publication or owner Git commands.

## Risks, warnings, and what is NOT done

Recall remains IN_PROGRESS; final80642 full native result must pass before DONE. Previous report is61/1, not green. Owner learning emulator retains0.14/code15; no install/clear/uninstall/instrumentation/learner-row read has occurred there this recall pass. Disposable test emulator is2GiB/API33 with256MiB Java heap; orchestrator changes process isolation, not production memory/performance certification. UI aggregate Flow is asynchronous independently of SQL commit/Saved status. Failed in-memory writes can be lost on process death and stay unconfirmed, not counted; wait for Saved. Original conservative schedule is not FSRS or an empirically fitted mastery probability. Legacy events stay ungraded; Study views are exposure counts, not comprehension/time. No notifications/cloud sync/export/account recovery. Board quality/iOS now explicitly deferred; overall project and owner full-flow usability testing are not complete. Never automatically claim board/iOS after recall.

## Next

Finish isolated full native rerun after the scope-flow wait fix, then complete recall history and stop implementation for the owner-selected full-flow testing phase; do not claim board/iOS. Resume exec_command80642 via write_stdin (not functions.wait); after exit inspect fresh aggregate testsuites count/timestamp/all failures. Update verified0.15 README/PROJECT/user/privacy/task notes only after gates, verify final public/secret/Relay checks, write new append-only completion log, done/doctor. Optional safe in-place owner update with exact verified APK and serial, no clearing/instrumentation/learner-row reads. Next unclaimed task is android-end-to-end-user-test. Respect the explicit stop point; do not mark deferred tasks/whole project complete or compact this unfinished round.
