# Task A UI handoff

Task A implementation and offline compile/unit/lint gates are complete. This file is the handoff to Claude Code; no Relay commands or `.relay/` edits were made. No Git, network, emulator, adb, or connected-test operations were performed. No Task B tactics/storage files or content/cache files were edited. Learner identifiers and fingerprints remain unchanged; Task A makes no database schema changes.

## Files changed

Paths in the Android tables are relative to `androidApp/src/main/kotlin/com/openinglab/app/`.

| File | Change |
| --- | --- |
| `MainActivity.kt` | Injects the app-private persisted settings store into the ViewModel. |
| `content/AppPreferences.kt` (new) | SharedPreferences `developerMode`, default false, separate from learner storage. |
| `content/CourseFeedbackStore.kt` | Optional note with an empty default; old flag JSON remains readable. |
| `ui/AppViewModel.kt` | Exposes/updates developer mode; weighted variation-anchor practice; note-bearing explanation reports and snackbar messages. |
| `ui/OpeningLabApp.kt` | Local navigation entries and callbacks for overview, chapter, variation, all lines, sources/licences and developer feedback; passes developer mode to relevant screens. Preserves Task B's TACTICS entry. Uses already-loaded course state during route restoration. |
| `ui/components/ChessBoard.kt` | Glyph stroke behind the existing fill/shadow: dark outline on White, translucent light outline on Black. Includes a Studio preview of every glyph on both square colours. |
| `ui/components/Components.kt` | Opening cards gate attribution, source coverage and provenance identity behind developer mode. Preserves Task B's bottom-bar addition. |
| `ui/components/ExpandableText.kt` (new) | Three-line text with overflow-aware Show more/Show less, also used for More/Less chapter intros. |
| `ui/screens/HomeScreen.kt` | One deep-course card per pack, with title and data-derived chapter/side subtitle; provenance eyebrow only in developer mode. |
| `ui/screens/ExploreScreen.kt` | Gates source/authorship guidance and passes developer mode to opening cards. |
| `ui/screens/ProfileScreen.kt` | Minimal About additions: always-visible Sources & licences, developer-only Content feedback (N), Developer mode switch. |
| `ui/screens/OpeningDetailScreen.kt` | Gates provenance/coverage; clamps explanation text and presents moves instead of source micro-labels outside developer mode. |
| `ui/screens/DeepCourseScreen.kt` | Compact chapter header, clamped intro, segmented POV, primary study/practice actions, default-one-level-expanded variation tree, separate all-lines action and collapsed Key ideas. Developer metadata remains accessible when enabled. Game card retains players/event/date/result. |
| `ui/screens/CourseOverviewScreen.kt` (new) | Compact chapter rows and GM game at the end; shared tree rows, result bars, display names, move labels and unavailable/loading UI. |
| `ui/screens/CourseVariationScreen.kt` (new) | Short title, moves, read-only anchor board, result/rating-band bars, chosen-side-first plans/chips/model games, sub-variations and study/weighted practice. Legacy packs retain their prose fallback until re-packed. |
| `ui/screens/CourseAllLinesScreen.kt` (new) | Separate search/filter/count/line-card browser with retained test tags and study/practice actions. |
| `ui/screens/SourcesLicencesScreen.kt` (new) | Installed course and pack source/licence links, CC BY-SA adaptations, CC0 club/name-list credit, Stockfish and bundled notices. Also contains the developer feedback list and existing JSON share action. |
| `ui/screens/TrainerScreen.kt` | Compact role/move row, clamped move explanation, evidence evaluation/reach chips, muted players line, small flag dialog with optional note and snackbar; collapsed both-side/opponent plans. Gates provenance. |
| `ui/screens/PositionTeachingPanel.kt` | Developer-only evidence prefixes and provenance footer. |
| `ui/screens/EngineAnalysisPanel.kt` | Developer-only engine budget/provenance and evidence prefixes; retains analysis/preview behavior. |
| `ui/screens/GameReplayScreen.kt` | Passes developer mode through explanation/analysis panels and gates coaching provenance. |
| `ui/screens/GameLibraryScreen.kt` | Developer-only source/licence/record micro-labels on game cards. |
| `ui/screens/OfflineLibraryScreen.kt` | Developer-only licence/notices/disposition details; install actions remain available. |
| `ui/screens/RepertoireScreen.kt` | Developer-only observation source attribution/details. Learner choice/count behavior is retained. |

Other code/build files:

| File | Change |
| --- | --- |
| `androidApp/build.gradle.kts` | Packages the existing root third-party notices and NOTICE as legal assets for the sources screen. |
| `shared/src/commonMain/kotlin/com/openinglab/shared/course/DeepCourse.kt` | Adds defaulted `CourseSideIdeas.plan`/`patterns` and serializable `CoursePattern`; validates size, nonblank SAN and finite shares in [0,1]. Existing `text` is retained. |
| `shared/src/commonMain/kotlin/com/openinglab/shared/course/CourseNavigation.kt` (new) | Longest strict path-prefix hierarchy, relative child names, display-only chapter title shortening and weighted anchor-line selection. |
| `courseTools/src/main/kotlin/com/openinglab/course/CoursePacker.kt` | Populates plan from passed WINPLAN claims and patterns from display SAN/shares; existing text rendering stays intact. |

Tests:

| File | Change |
| --- | --- |
| `shared/src/commonTest/kotlin/com/openinglab/shared/course/DeepCourseTest.kt` | Legacy defaults/round-trip, invalid structured data, weighted anchor selection, tree ancestry/relative naming and unchanged title identities. 17 cases pass on JVM and host. |
| `courseTools/src/test/kotlin/com/openinglab/course/CoursePackerTest.kt` | Passed-WINPLAN-only plan and SAN/share pattern regression. 6 cases pass. |
| `androidApp/src/androidTest/kotlin/com/openinglab/app/DeepCourseLearningTest.kt` | Home → overview → chapter → variation/all-lines navigation; default-hidden/developer-visible provenance; note-bearing flag dialog; settings/old-flag compatibility; Profile sources and developer feedback; retained POV/example/bookmark/practice/player checks. 13 test methods compiled; not run. Handles both old and re-packed structured ideas. |
| `androidApp/src/androidTest/kotlin/com/openinglab/app/GuidedLessonTest.kt` | Expands the renamed plans section before assertions. |
| `androidApp/src/androidTest/kotlin/com/openinglab/app/TeachingLearningTest.kt` | Enables developer coverage and expands plans for existing assertions. |
| `androidApp/src/androidTest/kotlin/com/openinglab/app/SourcedLearningTest.kt` | Explicit developer coverage and collapsed-plan assertions. |
| `androidApp/src/androidTest/kotlin/com/openinglab/app/EngineAnalysisTest.kt` | Explicit developer mode before engine metadata assertions. |
| `androidApp/src/androidTest/kotlin/com/openinglab/app/PositionTeachingUiTest.kt` | Explicit developer mode before provenance assertion. |
| `androidApp/src/androidTest/kotlin/com/openinglab/app/HistoricalGameCoachTest.kt` | Explicit developer mode before coach/engine provenance assertions. |
| `androidApp/src/androidTest/kotlin/com/openinglab/app/WholeBroadcastInstallationTest.kt` | Enables developer mode in its disposable fixture before disposition assertions. |
| `androidApp/src/androidTest/kotlin/com/openinglab/app/ObservedRepliesTest.kt` | Explicit developer mode before source-detail assertions. |

Developer-mode test fixtures restore the setting after affected MainActivity tests.

## Verification

Final exact requested command:

```sh
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew --offline --max-workers=4 :shared:jvmTest :shared:testAndroidHostTest :courseTools:test :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug
```

BUILD SUCCESSFUL in 19 seconds; 94 actionable tasks (16 executed, 78 up-to-date).

| Gate | Exact result |
| --- | --- |
| `:shared:jvmTest` | 204 passed, 0 failed/errors/skipped |
| `:shared:testAndroidHostTest` | 161 passed, 0 failed/errors/skipped |
| `:courseTools:test` | 40 passed, 0 failed/errors/skipped |
| `:androidApp:assembleDebug` | Passed |
| `:androidApp:compileDebugAndroidTestKotlin` | Passed; no device tests run |
| `:androidApp:lintDebug` | Passed; 0 errors, 1 pre-existing AndroidGradlePluginVersion warning |

An earlier complete run with `--continue` also passed. Transient concurrent Task B failures were not edited: the first run had 198/199 JVM cases pass, failing `TacticsStorageTest.migrationSixToSevenPreservesEveryLegacyTableRow`; a retry hit `TacticsController.kt:77` suspension compilation; another run hit a concurrent JVM test-output file collision after 203/204 cases. All cleared on subsequent retries. The initial sandboxed attempt could not open Gradle's local file-lock socket; offline Gradle gates then ran with the required sandbox escalation.

No course bytes or trusted SHA were changed by Task A.

## Remaining owner/Claude steps

1. Re-pack the course with the updated CoursePacker and update `BundledCourses.TRUSTED_SHA256`. Current packs still parse through the default fields; structured chips appear after re-packing.
2. Run device tests and visually inspect piece outlines at normal/mini-board sizes on both square colours. The contrast preview compiles, but it was not rendered or device-verified in Task A.
3. Task C can restyle Profile around the existing `developerMode`, `onDeveloperMode`, `onSources`, `feedbackCount` and `onFeedback` parameters.
