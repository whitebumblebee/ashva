# Task F — visual review fixes

Implemented locally on 2026-10-06. Task F changed **27 code/test files plus this handoff (28 files)**.

## Behaviour

All fourteen requested areas are addressed. Variation study opens after the defining move, on the most-reached non-TRANSPOSES line through that node; equal reach prefers the longer line, and all-transposition anchors fall back to any line with the same ranking. Variation practice retains weighted descendant selection and starts at the anchor immediately. The full approach remains in replay; restart and cold resume retain the anchor.

Move popularity is measured among siblings at the exact position: master-game share when available, otherwise club-game share. At least 50% shows **Main move**, at least 15% shows **Popular**, otherwise no chip. Cumulative reach is never used for these labels.

Starting-position cards show the retained line name and a short ending: **Ends equal (+0.1)**, **White ends better (+1.3)**, **Black ends better (-1.3)**, **Joins <short destination>**, or **Unclear**. For transpositions, the destination comes from the existing checked plan in legacy packs; absent a named destination the summary is **Joins another line**. Examples without chapter-ending metadata display **Original game**. The old line descriptions remain untouched for saved fingerprints and developer mode.

The whole-app review was a source review under the no-device rule. Determinate Material progress calls and Rare/Common move labels are gone; Material roles are fully mapped to the palette. Provenance labels stay behind developer mode, with legal attribution intentionally available on the explicit Sources & licences page. Longer learner prose found in analysis, position teaching, game coaching, identifier, repertoire and source pages now has Show more. Visual/device verification was not performed.

## Files changed by Task F

| File | Change |
| --- | --- |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/theme/Theme.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/theme/Theme.kt) | Maps every Material colour role, including fixed/surface roles, to the app palette. Selected containers use Leaf tint and dark text. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/components/RoundedProgressBar.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/components/RoundedProgressBar.kt) | New accessible continuous rounded determinate bar: empty at zero, full at one, with no stop dot or gap. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/components/DisclosureButton.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/components/DisclosureButton.kt) | New full-width disclosure button with animated chevron and expanded/collapsed semantics. |
| [androidApp/src/main/kotlin/com/openinglab/app/tactics/TacticsScreens.kt](../../androidApp/src/main/kotlin/com/openinglab/app/tactics/TacticsScreens.kt) | Wrong-move feedback uses legal SAN with capture/check/mate notation. SAN is computed only in the wrong phase so solution playback remains safe. Hides catalogue provenance outside developer mode; expands lengthy instructions. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/AppViewModel.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/AppViewModel.kt) | Starts lessons directly at an optional anchor cursor; variation practice jumps past the approach. Restart/mode switch and cold restoration retain the practice anchor. Restored deep practice retains automatic opponent replies. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/OpeningLabApp.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/OpeningLabApp.kt) | Launches variation study at its node ply and supplies display-only trainer title, ending, sibling-share and branch summaries. Passes developer mode to tactics and identifier. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/TrainerScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/TrainerScreen.kt) | Short deep-course title; hides repertoire editor for deep lessons; one role/move/turn row. Uses rounded progress, friendly start/branch summaries, position-relative move chips and rotating plan disclosures. Branch pause sentence is developer-only; long explanations expand. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseOverviewScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseOverviewScreen.kt) | Cream/grey/dark result bars with light dark-segment outline, compact shared result legend and rotating variation-tree chevrons. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseAllLinesScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseAllLinesScreen.kt) | Friendly line-ending summary outside developer mode; retained raw descriptions remain available in developer mode. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseVariationScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/CourseVariationScreen.kt) | Rounded rating-band bars, result legend for sub-variations, full-width rotating winning-plan disclosures, and left-aligned play-icon model-game rows with single-line ellipsis and muted event line. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/DeepCourseScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/DeepCourseScreen.kt) | Adds the result legend below Variations and the rotating Key ideas disclosure. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/HomeScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/HomeScreen.kt) | Uses continuous bars for Today/course progress, guards zero targets and shows completion check icons. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/ProfileScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/ProfileScreen.kt) | Uses the continuous weekly progress bar; selected routine chips inherit the corrected global palette. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/EngineAnalysisPanel.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/EngineAnalysisPanel.kt) | Long analysis help, SAN sequences, comparisons and move explanations use Show more. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/PositionTeachingPanel.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/PositionTeachingPanel.kt) | Long focus, candidate explanations, geometric motifs and teaching points use Show more. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/GameReplayScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/GameReplayScreen.kt) | Long move-coaching explanation and plan use Show more. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/IdentifierScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/IdentifierScreen.kt) | Long descriptions/possible labels expand; source metadata and source micro-labels are hidden outside developer mode. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/OpeningDetailScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/OpeningDetailScreen.kt) | Long historical-game lesson description uses Show more. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/SourcesLicencesScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/SourcesLicencesScreen.kt) | Long attribution/legal text and feedback notes use Show more; explicit Sources & licences remains available. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/GameLibraryScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/GameLibraryScreen.kt) | Long private-import help uses Show more. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/RepertoireScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/RepertoireScreen.kt) | Long observed/set/overview summaries and set-editor help use Show more. |
| [shared/src/commonMain/kotlin/com/openinglab/shared/course/DeepCourse.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/course/DeepCourse.kt) | Indexes anchor plies, regular-ending-first line selection with reach/length ranking, and sibling game shares. Exposes display-only ending summaries. |
| [shared/src/commonMain/kotlin/com/openinglab/shared/course/CourseNavigation.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/course/CourseNavigation.kt) | Friendly one-decimal ending summaries and legacy checked transposition-destination names; no mutation of retained lesson descriptions. |
| [shared/src/commonMain/kotlin/com/openinglab/shared/storage/LearningStore.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/storage/LearningStore.kt) | Adds default-zero optional practiceStartPly to serialized bookmarks. No Room schema or database identifier change. |
| [shared/src/commonTest/kotlin/com/openinglab/shared/course/DeepCourseTest.kt](../../shared/src/commonTest/kotlin/com/openinglab/shared/course/DeepCourseTest.kt) | 11 new cases for ranking/fallback/ties, anchor replay, sibling-share thresholds/no-data, ending summaries and old/new bookmark compatibility. Uses named checkpoint argument for the incoming validator API. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/DeepCourseLearningTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/DeepCourseLearningTest.kt) | 3 new compiled cases for study/practice anchor navigation, earlier replay/restart and real-pack line ranking. Existing cold-bookmark case also checks the retained anchor. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/tactics/TacticsLearningTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/tactics/TacticsLearningTest.kt) | 1 new compiled capture/check SAN case (Rxd8+) with unchanged wrong-move board and safe solution playback; updates existing miss-feedback assertion. |
| [docs/TASK_F_UI_HANDOFF.md](TASK_F_UI_HANDOFF.md) | This per-file handoff and exact verification results. |

Existing test tags remain. Package/database/pack/lesson identifiers and stored line-description fingerprint inputs are unchanged by Task F. The bookmark addition has a default for older JSON and requires no database migration or learner-data rewrite.

## Final exact command and results

```sh
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew --offline --max-workers=4 :shared:jvmTest :shared:testAndroidHostTest :courseTools:test :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64
```

**BUILD SUCCESSFUL in 18s**; 97 actionable tasks: 24 executed, 73 up-to-date.

| Gate | Exact result |
| --- | --- |
| Shared JVM | **252 passed**, 0 failures/errors/skips |
| Shared Android host | **195 passed**, 0 failures/errors/skips |
| courseTools | **45 passed**, 0 failures/errors/skips |
| Debug assembly | Passed |
| Android instrumentation Kotlin compilation | Passed; **4 new Task F methods compiled**, **0 device tests executed** |
| Lint | **0 errors, 3 warnings** |
| Shared iOS simulator ARM64 compilation | Passed |

The affected shared DeepCourseTest suite has **32 passing methods on each host target**, including the 11 new Task F cases. The affected instrumentation classes contain **16 DeepCourseLearningTest methods** and **5 TacticsLearningTest methods**; these were compiled only.

Lint warnings are AndroidGradlePluginVersion in gradle-wrapper.properties, and ApplySharedPref/UseKtx in the incoming content/BundledValidationMarkers.kt. Task F does not edit that marker file.

Totals were counted from the final XML reports.

## Workspace integration and constraints

Unrelated startup/chess edits arrived while this turn was running, including new validation-cache APIs and tests. They were preserved; final gate counts describe the combined current workspace. The table above lists only files edited for Task F. An intermediate compilation encountered those changing APIs, and an intermediate test run encountered a changing pack-checksum expectation; the final command passed after integration settled.

No Git commands, relay commands or .relay edits, network calls, emulator/adb/device tests, publication, or Task F edits under content/ or .course-cache/ were performed. The sandbox initially blocked Gradle's local file-lock socket; the same offline command then ran with the required execution permission.

## Next

Claude Code should run the four new instrumentation cases plus the existing cold-bookmark case, then inspect zero/full bars, selected controls, result legend, disclosure chevrons, model-game truncation and anchor navigation on the emulator.

