# Task E: deep-course startup performance

Measured on the local host JVM on 2026-10-06 using the real checksum-pinned Ruy Lopez and tactics packs. No network or device commands were used. Timing is diagnostic, not a flaky test deadline. Missing packs skip the real-pack benchmark using JUnit assumptions.

The final fresh-worker measurement is **170.843 ms for Home course summaries** and **473.655 ms for opening `morphy-other`, including its first legal replay, presentation/index/tree construction, lesson graph and both saved-lesson fingerprint forms**. Both host targets pass. Device cold-start/frame measurements remain for Claude Code.

The original validator already replayed each node from its parent board once. The main startup costs were full-pack legal replay before publishing any course card, tactics validation, and waiting for the general catalog. Lesson graphs replayed shared prefixes again for every line. Line lookup scanned every line; variation ancestry/children scanned every variation; trainer node lookup scanned the whole chapter during recomposition. Custom-pool sorting repeatedly hashed the same IDs in its comparator.

Home and the overview now use structurally checked summaries, with real leaf counts. The Android loader reads/checksums/decodes/prepares summaries on Default without waiting for the general catalog or feedback storage. Only a requested chapter is legally replayed and presented; cold bookmark, saved session, policy, review and set lookups use the same demand path. Main-thread getters read prepared objects. Unopened illegal chapters cannot become playable: legal/canonical SAN checks run before publishing their views, with loading/error states and retained learner data.

Checked node positions supply lesson graphs and variation anchor boards, retaining exact clocks and repetition histories. Node-to-lines, most-reached-line, variation ancestry/children, and line-to-node indexes are prepared once per chapter. MoveStep text is shared by node, and label generation uses precomputed indices. All 11 old JVM hashCode and SHA-256 lesson fingerprints are asserted against the captured baseline. Synthetic tests also compare entire old/new graph paths/nodes and branch/cold snapshots, including repetition/clocks.

Tactics still validates the entire pinned pack once on Default, starting after course summaries publish. Checking legal `apply` plus insufficient-material/repetition/75-move endings avoids the redundant full-board `status()` legal-move scan; mate/stalemate remain rejected by legal application. Terminal fixtures guard the equivalence. History validation, Home/Profile tactics summaries and custom filter/selection work run on Default. Per-snapshot activity/history indexes remove repeated scans. Custom sorting hashes each eligible puzzle once while preserving the exact old seeded order.

## Host stages

All times are milliseconds. Each profile runs sequential stages; JIT/GC warming affects comparisons. The original eager constructor was separately measured at 629.042 ms after the first validation warmed the JVM. The Home baseline below is the sum of the original first validation and required read/hash/decode/presentation/progress/routine stages, not an emulator measurement. Original lesson graphs were on-demand already and are not included in the Home total.

Home required work: **1112.591 ms stage sum before → 170.843 ms measured total after**. The after Home total includes empty-history progress/routine computation; cold Room queries are separately timed below.

| Stage | Before | After |
| --- | ---: | ---: |
| Course read | 5.772 | 5.484 |
| Course checksum | 27.206 | 33.069 |
| JSON decode | 88.283 | 93.059 |
| Full course validation (deferred after) | 961.956 | 597.389 |
| All chapter presentation (deferred after) | 28.031 | 48.210 |
| All chapter lesson graphs (on demand) | 1118.279 | 86.657 |
| All variation trees (on demand) | 2.116 | 0.557 |
| All variation-row lineThrough lookups | 1.870 | 0.082 |
| Routine/activity computation, empty history | 1.067 | 0.182 |
| Tactics read | 1.235 | 1.702 |
| Tactics checksum | 0.581 | 0.634 |
| Tactics JSON decode | 17.559 | 27.497 |
| Tactics validation | 1198.260 | 353.092 |
| Tactics index | 0.425 | 0.455 |
| Custom pool filter | 0.224 | 0.236 |
| Custom pool selection | 888.519 | 96.169 |

After summary construction/structural checks alone: 31.302 ms. The largest chapter’s first demand lookup was 386.845 ms, checked graph 67.643 ms and fingerprint text/hash 18.219 ms; the 473.655 ms total includes measurement overhead. Later warm per-chapter measurements follow. Presentation after includes the new node/line indexes and variation tree; the separate tree column measures an independent diagnostic rebuild.

| Chapter | Legal replay after | Presentation before → after | Graph before → after | Fingerprint text/hash before → after | Tree before → after | Row lookups before → after |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| berlin | 77.436 | 8.092 → 7.023 | 205.549 → 16.273 | 12.018 → 5.834 | 0.770 → 0.102 | 0.503 → 0.016 |
| exchange | 15.243 | 5.731 → 6.499 | 26.815 → 2.312 | 0.907 → 0.916 | 0.060 → 0.027 | 0.032 → 0.005 |
| open | 30.682 | 1.798 → 2.141 | 75.995 → 5.020 | 2.195 → 1.760 | 0.040 → 0.034 | 0.081 → 0.004 |
| closed | 105.278 | 3.615 → 13.321 | 277.683 → 20.903 | 6.556 → 7.446 | 0.511 → 0.155 | 0.681 → 0.018 |
| marshall | 30.380 | 0.789 → 8.784 | 69.811 → 5.382 | 1.697 → 1.488 | 0.055 → 0.047 | 0.131 → 0.005 |
| morphy-other | 156.829 | 6.137 → 5.843 | 335.096 → 26.414 | 7.936 → 9.010 | 0.621 → 0.126 | 0.392 → 0.021 |
| schliemann | 16.427 | 0.384 → 0.896 | 25.284 → 1.993 | 0.671 → 0.538 | 0.016 → 0.015 | 0.011 → 0.004 |
| classical | 19.521 | 0.543 → 1.639 | 44.221 → 3.230 | 1.140 → 0.856 | 0.023 → 0.018 | 0.022 → 0.003 |
| steinitz | 5.434 | 0.158 → 0.224 | 8.868 → 0.771 | 0.328 → 0.259 | 0.002 → 0.012 | 0.001 → 0.001 |
| other | 21.948 | 0.395 → 0.736 | 33.703 → 3.180 | 0.923 → 0.779 | 0.016 → 0.018 | 0.015 → 0.004 |
| firouzja-carlsen-2020 | 3.672 | 0.389 → 1.104 | 15.254 → 1.179 | 0.335 → 0.273 | 0.002 → 0.003 | 0.001 → 0.001 |

The separate disposable Room fixture contains 3,000 activity events. Its latest full-suite run measured:

- Room cold learnerActivity queries (3,000 events): 15.982 ms.
- progress index (3,000 events): 3.905 ms.
- legacy progress 1,000 recompositions: 177.493 ms.
- indexed progress 1,000 recompositions: 0.865 ms.
- routine/week (3,000 events): 1.631 ms.
- legacy tactics Home history queries: 6.688 ms.
- indexed tactics Home history queries: 1.453 ms.

## Verification

The final complete command passed:

```sh
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew --offline --max-workers=4 :shared:jvmTest :shared:testAndroidHostTest :courseTools:test :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64
```

- Shared JVM: 235 passed, 0 failures, 0 errors, 0 skipped.
- Shared Android host: 180 passed, 0 failures, 0 errors, 0 skipped.
- Course tools: 45 passed, 0 failures, 0 errors, 0 skipped.
- Android debug assembly, instrumentation Kotlin compilation, and shared iOS simulator compilation passed.
- Lint: 0 errors, 1 existing Gradle-version warning.
- Final build: 39 s; 97 actionable tasks (15 executed, 1 from cache, 81 up-to-date).
- Both bundled checksums and all 11 original lesson-fingerprint pairs pass. No database/package/lesson identifiers, schema, pack format or pack bytes were changed.
- Instrumentation was compiled only; no emulator/adb/device measurements or learner database access. No Git, relay commands/edits, network, publication, or content/cache edits.

Run the fresh-worker diagnostic with `:shared:jvmTest --tests '*StartupBenchmarkTest.realPackStartupStages'`; run both benchmark cases with `--tests '*StartupBenchmarkTest'`. Output is in the Gradle test XML/report under `shared/build/test-results/jvmTest/`.

## Files changed

- [shared/src/commonMain/kotlin/com/openinglab/shared/course/DeepCourse.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/course/DeepCourse.kt)
- [shared/src/commonMain/kotlin/com/openinglab/shared/course/CourseNavigation.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/course/CourseNavigation.kt)
- [shared/src/commonMain/kotlin/com/openinglab/shared/lesson/LessonGraph.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/lesson/LessonGraph.kt)
- [shared/src/commonMain/kotlin/com/openinglab/shared/tactics/TacticsPack.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/tactics/TacticsPack.kt)
- [shared/src/commonMain/kotlin/com/openinglab/shared/tactics/Woodpecker.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/tactics/Woodpecker.kt)
- [shared/src/commonMain/kotlin/com/openinglab/shared/practice/DailyPractice.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/practice/DailyPractice.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/ui/AppViewModel.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/AppViewModel.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/ui/OpeningLabApp.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/OpeningLabApp.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/ui/LearnerController.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/LearnerController.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/HomeScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/HomeScreen.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseOverviewScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseOverviewScreen.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/DeepCourseScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/DeepCourseScreen.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseVariationScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseVariationScreen.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/SourcesLicencesScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/SourcesLicencesScreen.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/tactics/TacticsController.kt](../../androidApp/src/main/kotlin/com/openinglab/app/tactics/TacticsController.kt)
- [androidApp/src/main/kotlin/com/openinglab/app/tactics/TacticsScreens.kt](../../androidApp/src/main/kotlin/com/openinglab/app/tactics/TacticsScreens.kt)
- [shared/src/commonTest/kotlin/com/openinglab/shared/course/DeepCourseTest.kt](../../shared/src/commonTest/kotlin/com/openinglab/shared/course/DeepCourseTest.kt)
- [shared/src/commonTest/kotlin/com/openinglab/shared/tactics/TacticsTest.kt](../../shared/src/commonTest/kotlin/com/openinglab/shared/tactics/TacticsTest.kt)
- [shared/src/commonTest/kotlin/com/openinglab/shared/practice/DailyPracticeTest.kt](../../shared/src/commonTest/kotlin/com/openinglab/shared/practice/DailyPracticeTest.kt)
- [shared/src/jvmTest/kotlin/com/openinglab/shared/course/StartupBenchmarkTest.kt](../../shared/src/jvmTest/kotlin/com/openinglab/shared/course/StartupBenchmarkTest.kt)
- [androidApp/src/androidTest/kotlin/com/openinglab/app/DeepCourseLearningTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/DeepCourseLearningTest.kt)
- [docs/STARTUP_PERFORMANCE.md](STARTUP_PERFORMANCE.md)
