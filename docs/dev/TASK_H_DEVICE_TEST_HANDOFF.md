# Task H — device suite repair

The 0.18 connected report contains 87 tests, 12 failures, no errors or skips. All 87 instrumentation test methods remain. This change updates interactions and synchronization for the intentional UI and startup changes from Tasks A/C/E/F/G; it does not change app behavior or delete behavior assertions. The native crash classification remains provisional pending Claude Code's device rerun.

| Failing test | Decision and repair |
| --- | --- |
| `DailyRoutineLearningTest.developerModeRevealsFeedbackAndProvenanceAlongsideSources` | **(a)** Profile is lazy. Search inside `learner-profile-screen` for settings/About controls. Check default-hidden feedback and provenance after scrolling to About, then enable developer mode and retain the source/provenance assertions. |
| `DailyRoutineLearningTest.profilePuzzleTargetAndNamePersistAndUpdateHomeRoutine` | **(a)** Scroll the Profile list to the puzzle target and back to Edit name. Retain preference persistence, greeting and Home routine assertions. |
| `DeepCourseLearningTest.flagDialogSavesAnOptionalNoteLocally` | **(a)** The helper incorrectly applied `performScrollTo()` to radio/save controls in a non-scrollable dialog. Click displayed dialog controls directly; retain optional-note persistence and export assertions. |
| `DeepCourseLearningTest.replayExampleOpensTheOriginalGameLesson` | **(a), provisional test synchronization.** No definite app animation defect found. Settle finite Compose transitions and wait for Compose/Android UI idleness before navigation; wait for lesson loading to finish. Retain exact original score, path kind and POV assertions. See crash findings below. |
| `EngineAnalysisTest.actualOfflineEngineShowsCandidatesAndSeparatePreviewWithoutChangingLesson` | **(a)** `BOARD FACT` is a developer-only provenance label. Enable developer mode before its assertion. Retain actual engine identity, checked explanations, hypothetical preview, original replay and process cleanup assertions. |
| `ObservedRepliesTest.realSampleShowsCountsOutsideMovesAndSourceDetailsWithoutChangingChoices` | **(a)** MainActivity explicitly defers observations until `openRepertoire`. Move the existing 60-second readiness wait after opening the editor, and fail immediately on an explicit error. Retain all exact 79-score counts, outside-move non-editability, source hash and unchanged-policy assertions. |
| `PublicAlphaTest.publicScreensCanBeCapturedFromSyntheticLessonState` | **(a)** Teaching details now prepare asynchronously and use a lazy list. Wait for the detail screen, scroll its list to Play White, and scroll Explore to the identifier action on return. Retain all four screenshot flows and the trainer cursor assertion. |
| `RepertoireLearningTest.whiteEditorIncludesBerlinShowsGapAndPracticesOnlyChosenScope` | **(a)** Revision text is developer-only. Enable developer mode and additionally assert the actual policy revision is 3. Retain gap/adoption, admitted routes, fixed color and branch-return checks. |
| `RepertoireOverviewTest.loadingErrorAndRetryAreExplicitWithoutImplyingEmptyRepertoire` | **(a)** The full omission disclaimer moved to an info dialog. Assert the compact unavailable/saved message, open its accessible More information action, and retain the no-omissions assertion there plus retry/loading checks. |
| `SourcedLearningTest.installedCatalogSearchRouteSelectionBothColorReplayAndBranchReturn` | **(a)** The `explore-source-variations` action still exists. Wait for lazy teaching preparation and find the action in `opening-detail`; scroll the resulting source catalog to the raw Ruy entry. Retain catalog totals, selected route, both colors, branch return and honest source-plan limits. |
| `TeachingLearningTest.mainRuyEntryHasAllNamedRoutesAndDeeperBothColorTeaching` | **(a)** `learningOpenings()` intentionally returns summaries. Assert summary coverage, open the course and wait for preparation, then assert the full course contains all 235 source routes plus the advertised authored continuations. Retain Berlin-ending replay and both-color teaching assertions. |
| `WholeBroadcastInstallationTest.wholeMonthInstallsFromNativeScreenShowsDispositionsAndColdRebuildsObservations` | **(a)** Developer mode was already enabled, but revealing long pack notices changes lazy-list layout. Find `pack-dispositions-lichess-broadcast-2020-01` through `offline-library-list` after the toggle. Retain dispositions, 857 accepted records, exact hash, cold rebuild and unchanged learner data checks. |

The lazy teaching implementation does not drop authored routes: summaries advertise their counts, and full worker preparation combines authored continuations with every source route. Observation loading is deliberately deferred by MainActivity's `deferObservedReplies = true`; the earlier test waited before the request could happen. Neither failure supports undoing the startup optimizations.

## Native crash findings

The per-test log records SIGSEGV on the Android 13 emulator's RenderThread at `libhwui.so`, `AnimationContext::runRemainingAnimations`, null address `0x10`, with process uptime approximately 10 seconds. It provides no application stack identifying a faulty callback.

Inspected the navigation entries, chapter/variation screens, board animation and disclosure controls. Board `AnimatedContent`, navigation transitions and chevron animations are finite Compose-managed transitions. There is no custom Android View animation, `animateItemPlacement`, infinite/orphaned app animation loop or animation job retained beyond a disposed screen in this flow. Loading spinners are normal scoped Material components. No definite app cause was found, so app animation behavior is unchanged.

The replay test now advances the Compose clock by 1,000 ms to settle finite transitions, waits for Compose idleness, and calls `InstrumentationRegistry.waitForIdleSync()` at the navigation boundaries. Its click helper also waits for idleness before and after interactions. This is a timing mitigation, not proof that the native fault is fixed. Claude Code must re-run this case and the full suite.

## Verification

```sh
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew --offline --max-workers=4 :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug
```

**BUILD SUCCESSFUL**, 90 actionable tasks: 8 executed, 82 up-to-date.

- Shared JVM reports: **252 tests, 0 failures/errors/skips**.
- Shared Android-host reports: **195 tests, 0 failures/errors/skips**.
- Both shared test tasks were up-to-date; shared sources were unchanged.
- Debug assembly and instrumentation Kotlin compilation: passed.
- Lint: **0 errors, 1 existing `AndroidGradlePluginVersion` warning**.
- Static comparison confirms all test names in the 10 edited test files remain; the entire instrumentation suite still has **87 `@Test` methods**.
- Initial sandboxed Gradle startup failed because its local lock-coordination socket was denied. The same offline command succeeded with the approved sandbox exception.
- Device tests were not run. No Git, network, relay commands/edits, emulator/adb, content or course-cache writes were performed.

## Files changed

Ten instrumentation files under `androidApp/src/androidTest/kotlin/com/openinglab/app/`:

- `DailyRoutineLearningTest.kt`
- `DeepCourseLearningTest.kt`
- `EngineAnalysisTest.kt`
- `ObservedRepliesTest.kt`
- `PublicAlphaTest.kt`
- `RepertoireLearningTest.kt`
- `RepertoireOverviewTest.kt`
- `SourcedLearningTest.kt`
- `TeachingLearningTest.kt`
- `WholeBroadcastInstallationTest.kt`

Plus this report, `docs/dev/TASK_H_DEVICE_TEST_HANDOFF.md`: **11 files total**. No production app files changed.

Next: Claude Code re-runs `connectedDebugAndroidTest` on the isolated device and checks the original-game replay case for recurrence of the native crash.
