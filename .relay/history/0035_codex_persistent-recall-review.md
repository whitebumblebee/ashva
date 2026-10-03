---
seq: 35
agent: codex
date: "2026-10-03T00:07:01Z"
task: persistent-recall-review
status: partial
summary: "Owner paused development for credits and manual emulator testing; native fixture close/write race corrected and focused tests pass, full61 interrupted by request; recall remains unfinished."
next: "Help the owner test all built Android flows on0.15 using docs/USER_TESTING.md; resume recall full61 acceptance only when explicitly authorized, leaving board/iOS deferred."
supersedes: []
git_head: cba53d25fcf1
---

# persistent-recall-review

## What I did

Continued0034 verification, then stopped immediately when the owner requested a credit-saving pause and manual emulator testing. Goal status is explicitly paused. Recall stays IN_PROGRESS; no done command, board-quality work, iOS client work or new task claim.

Diagnosed the isolated full-run native SQLite crash at a cold fixture close while a ViewModel worker was still using its handle. Added androidTest-only TestViewModelCleanup.kt: capture owned scope jobs, clear stores on Main, then join cancelled jobs off Main with a10s timeout before closing/reopening UUID fixture databases. Applied it to recall, cold lesson/source/course/game/set/overview and whole-month fixtures. No production lifetime, source bytes, schema, board rendering, engine budget or memory limit changed. Production uses an application-scoped database. Added official lifecycle/coroutine references to docs/RECALL_REVIEW.md and removed stale sample-statistics wording in docs/USER_TESTING.md.

Installed the already-built0.15.0/code16 debug APK in place on the owner's emulator and launched MainActivity, at the owner's explicit request to test built features. Only version/foreground metadata inspected; no clearing, uninstalling, instrumentation or learner-row reading on that device. Baseline fully verified release remains0.14;0.15 is a test build with incomplete full acceptance.

## Commands run and their outcomes

-0034's full native session80642 ended FAILED9m:53cases/1failure/0errors/skips. All5 RecallLearningTest cases passed; RepertoireSetLearningTest cold resume crashed. Isolated crash buffer shows native SIGSEGV in libsqliteJni.so during LearningDao repertoire upsert and an orchestrator-service disconnection afterward. Earlier buffer also contains another SQLite native crash; do not infer all earlier interrupted tests were merely emulator memory failures. Fixture close/cancellation ordering was corrected, not claimed a general production memory-safety fix.
- ./gradlew --max-workers=2 :androidApp:compileDebugAndroidTestKotlin SUCCESS2s after cleanup helper.
- Focused connected command with a comma-separated class argument (session13490) SUCCESS22s, but actual XML contained only RepertoireSetLearningTest:3/3pass/0failures/errors/skips. Do NOT report8cases: the second requested class was not discovered by this harness invocation.
- Full unfiltered isolated command session99761 discovered61 and passed the formerly crashing cold-set case. Owner interrupted verification: Ctrl-C returned exit130; exact-serial orchestrator/test-app processes were stopped. There is no completed all61 result to cite. Resume a fresh full run only after owner authorization.
- ./gradlew --max-workers=2 :androidApp:lintDebug SUCCESS16s with the final test-only cleanup.
- Unchanged production non-native gates remain0034's verified176JVM/136Android-host/24importer/7service-unit/12real service integration cases, all0failures/errors/skips; debug/release/both shared-iOS compile success. Recounted current XML totals during this pass; no production code changed after those builds.
- node --test scripts/*.test.mjs:28/28pass/0failures/skips,77.98ms. Public audit274candidatefiles/0findings; pinned Gitleaks8.30.1 reports0findings. actionlint exit0. Debug and unsigned release APK engine/source/NNUE/recipe hashes and required notices both verified. Before this new log, Relay context/strict doctor34logs/21tasks/0findings; rerun after filling this handoff.
- Owner update: adb -s <owner-learning-serial> install -r <debug-apk> Success; am start -W reports Status ok/COLD/1101ms; package metadata0.15.0/code16, foreground MainActivity confirmed. Only isolated test device received instrumentation/stop commands. No Git operation or publication.

## External resources touched

Existing owner learning emulator and separate2GiB/API33 test emulator with256MiB Java heap. Exact serials/local paths remain local rather than public history. Official documentation: https://developer.android.com/reference/androidx/lifecycle/ViewModel and https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/. No new provider/network content, database container, account, deployment or paid service.

## Risks, warnings, and what is NOT done

The owner explicitly changed the instruction from finishing recall first to stopping NOW because credits were nearly exhausted. Do not resume development automatically. Current0.15 is built and available for manual testing, not fully accepted; no fresh full61 result exists after the final fixture cleanup. Keep recall unfinished and both board-quality/iOS deferred. The optional backend is only locally tested and not connected to Android; manual testing covers client features, not deployment. Actual chosen-scope recall is an original conservative deterministic schedule, not FSRS, expert theory, fitted retention or full-theory mastery. Failed in-memory saves remain unconfirmed and can be lost on process death: wait for Saved. No export/account recovery exists. Follow the guide without uninstalling/clearing app data.

## Next

Help the owner test all built Android flows on0.15 using docs/USER_TESTING.md. The app is installed and opened on the owner learning emulator; use the main149-course catalog, not just Starter. Record exact screen/route/color/actions/expected/actual outcome and wait for Saved before cold resume. Resume recall full61 acceptance only when explicitly authorized, using an isolated exact serial, then gates/log/done/doctor. Leave board/iOS deferred and the goal paused until the owner resumes development.
