# Task C — Home, Profile and daily practice

Implemented locally on 2026-10-06. This report is the Task C handoff; Relay is owned by Claude Code and was not changed.

## Behaviour and data rules

- Home begins with ASHVA and a local time-of-day greeting using the saved name (default “there”). Today shows attempted puzzles, completed opening-practice lines and enabled due reviews. All-done state and weekly study-day progress use stored data. Active tactics cycle progress appears in the first routine row, so there is no duplicate tactics card.
- Home shows seven main opening families in compact rows. Explore all retains access to the full catalogue. Secondary links are below those rows. Continue retains exact lesson/game bookmarks.
- Profile stores all routine/preferences settings locally and shows actual dates/days/streak, puzzle first-try statistics, completed Woodpecker cycles, per-set best completed-cycle time, opening lines, graded answers/accuracy and distinct active due cards. Zero-activity states remain zero; accuracy with no denominator is shown as “—”.
- A study day combines timestamped move attempts, recall answers, study views, tactics attempts and new daily activity. Local calendar conversion respects DST and the device time zone. Weeks start Monday. A streak includes consecutive days ending today, or yesterday until today is studied.
- Completed opening lines are recorded when actual practice reaches the end; opening a lesson or answering one review card does not create a completion. Repeating a completed line in a new session counts toward today’s target; all-time line totals count distinct lesson/path pairs.
- Historic exact-route study views contribute to lines studied and deep-course progress. Historic policy/set views do not prove which line was studied. Earlier move attempts do not prove a completed line, so completion counters start with the new events; all old move totals and activity dates remain visible.
- Attempting a partial puzzle or retrying a mistake creates activity without changing Task B’s first-try grades or cycle timing. Newly logged attempts are deduplicated against legacy tactics attempt rows for routine progress.
- Room 7→8 adds one table via CREATE TABLE IF NOT EXISTS. It changes no existing table definitions or identifiers and performs no learner-data deletion or backfill. New event saves use the existing retryable serial writer. Session identities survive bookmarks/recreation.
- Long caveats remain available through ⓘ dialogs. Sources/licences are always reachable from About; feedback/share, provenance and source micro-labels follow developer mode.

## Files changed

41 code/test/schema files, plus this handoff document:

| File | Change |
| --- | --- |
| [androidApp/src/main/kotlin/com/openinglab/app/content/AppPreferences.kt](../../androidApp/src/main/kotlin/com/openinglab/app/content/AppPreferences.kt) | Stores display name, routine targets, weekly goal, board coordinates, autoplay speed and tactics auto-next alongside the retained developer-mode key. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/LearnerController.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/LearnerController.kt) | Combines stored activity and tactics history with injected clock/time zone, derives week/streak/routine progress, loads stats even if a content pack fails, and deduplicates legacy/new puzzle records. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/AppViewModel.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/AppViewModel.kt) | Wires preferences and daily navigation; records exact study and completed practice events; retains a session identity through cold bookmarks; shares autoplay/auto-next settings without changing the active learning context. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/RecallController.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/RecallController.kt) | Uses the existing serial, retryable writer for activity events, retaining event identities and timestamps on retries. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/GameLibraryController.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/GameLibraryController.kt) | Uses the stored autoplay preference for new games and shared speed changes; changing a preference does not reactivate a previously left game. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/OpeningLabApp.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/OpeningLabApp.kt) | Connects Home/Profile to learner state, supplies board coordinates, daily navigation, developer-only repertoire identity details and the shared feedback chooser. |
| [androidApp/src/main/kotlin/com/openinglab/app/tactics/TacticsController.kt](../../androidApp/src/main/kotlin/com/openinglab/app/tactics/TacticsController.kt) | Persists the shared auto-next choice, continues the latest set, and records a puzzle activity on the first attempted move per puzzle/day, including partial puzzles and mistakes practice. Cycle grades/time are unchanged. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/components/ChessBoard.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/components/ChessBoard.kt) | Honours the global coordinates preference; miniature boards can still explicitly hide coordinates. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/components/Components.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/components/Components.kt) | Uses the ASHVA wordmark and restricts source/guidance card micro-labels to developer mode. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/components/InfoNote.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/components/InfoNote.kt) | Adds accessible short notes with an ⓘ dialog containing the complete explanation. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/HomeScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/HomeScreen.kt) | Orders header/greeting, Today, compact Continue, real deep-course progress and compact opening rows; shows active tactics in Today, seven main opening families plus Explore all, and the relocated secondary links. Existing tags remain. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/ProfileScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/ProfileScreen.kt) | Adds an editable initial avatar/name, real learning-since date, week strip/bar/streak, tactics/opening stats, routine/preferences controls and compact About, sources, feedback/share and provenance dialog. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/ExploreScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/ExploreScreen.kt) | Shortens catalog/identifier wording and makes developer coverage explanations expandable through ⓘ. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/ReviewScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/ReviewScreen.kt) | Shortens empty/help/scheduler explanations while retaining full recall semantics in info dialogs and existing tags. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/RepertoireScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/RepertoireScreen.kt) | Compacts editor, observations, coverage and set explanations; keeps the full meaning in dialogs; gates raw saved identities behind developer mode. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/GameLibraryScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/GameLibraryScreen.kt) | Compacts archive/filter/following states and puts reported identity details behind info/developer controls without removing filters or game data. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/GameReplayScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/GameReplayScreen.kt) | Compacts original/private/preview explanations and analysis-root help; exact retained source metadata remains available in developer mode. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/IdentifierScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/IdentifierScreen.kt) | Shortens entry/import/matching help, retains endpoint/transposition limits in dialogs and points to the existing game library for full replay. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/OfflineLibraryScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/OfflineLibraryScreen.kt) | Shows compact pack summaries and installation help with full limitations available through info dialogs. |
| [androidApp/src/main/kotlin/com/openinglab/app/ui/screens/OpeningDetailScreen.kt](../../androidApp/src/main/kotlin/com/openinglab/app/ui/screens/OpeningDetailScreen.kt) | Moves lengthy plan/route/metadata limits behind info buttons and retains the study/practice/search/variation controls. |
| [shared/src/commonMain/kotlin/com/openinglab/shared/practice/DailyPractice.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/practice/DailyPractice.kt) | Adds platform-independent activity/routine models and calendar-day, Monday-week and consecutive-streak computation using injected clock/calendar conversion. |
| [shared/src/commonMain/kotlin/com/openinglab/shared/storage/LearningDatabase.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/storage/LearningDatabase.kt) | Adds only daily_activity and Room 7→8 CREATE TABLE IF NOT EXISTS migration; queries timestamped activity, exact historic route views and unique currently due cards. |
| [shared/src/commonMain/kotlin/com/openinglab/shared/storage/LearningStore.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/storage/LearningStore.kt) | Adds activity read/write contracts and an optional backward-compatible activitySessionId field in serialized bookmarks. |
| [shared/src/commonMain/kotlin/com/openinglab/shared/storage/RoomLearningStore.kt](../../shared/src/commonMain/kotlin/com/openinglab/shared/storage/RoomLearningStore.kt) | Stores immutable/idempotent activity events; preserves first puzzle activity timestamps; reads old route views without inventing lines from policy/set views. |
| [shared/src/androidMain/kotlin/com/openinglab/shared/storage/DatabaseBuilder.kt](../../shared/src/androidMain/kotlin/com/openinglab/shared/storage/DatabaseBuilder.kt) | Registers migration 7→8 in the existing Android migration chain; keeps opening-lab.db unchanged. |
| [shared/src/jvmMain/kotlin/com/openinglab/shared/storage/DatabaseBuilder.kt](../../shared/src/jvmMain/kotlin/com/openinglab/shared/storage/DatabaseBuilder.kt) | Registers migration 7→8 in the JVM migration chain. |
| [shared/src/iosMain/kotlin/com/openinglab/shared/storage/DatabaseBuilder.kt](../../shared/src/iosMain/kotlin/com/openinglab/shared/storage/DatabaseBuilder.kt) | Registers migration 7→8 in the iOS migration chain. |
| [shared/schemas/com.openinglab.shared.storage.LearningDatabase/8.json](../../shared/schemas/com.openinglab.shared.storage.LearningDatabase/8.json) | Exports Room schema 8: 25 tables, with all 24 schema-7 table definitions unchanged. |
| [shared/src/commonTest/kotlin/com/openinglab/shared/practice/DailyPracticeTest.kt](../../shared/src/commonTest/kotlin/com/openinglab/shared/practice/DailyPracticeTest.kt) | 12 tests for empty/duplicate/future activity, Monday weeks, streak gaps/yesterday, clock rollover, routine targets/review toggle and partial/retry puzzle activity. |
| [shared/src/jvmTest/kotlin/com/openinglab/shared/practice/StudyCalendarTimezoneTest.kt](../../shared/src/jvmTest/kotlin/com/openinglab/shared/practice/StudyCalendarTimezoneTest.kt) | 4 time-zone/calendar tests: Indian midnight, 23-hour spring day, repeated fall hour and a week crossing the year boundary. |
| [shared/src/jvmTest/kotlin/com/openinglab/shared/practice/ActivityStorageTest.kt](../../shared/src/jvmTest/kotlin/com/openinglab/shared/practice/ActivityStorageTest.kt) | 6 tests for immutable/cold activity, existing timestamp sources, overlapping due-card deduplication, puzzle resume, exact legacy route studies and preservation of every row in all 24 legacy tables through 7→8. |
| [shared/src/jvmTest/kotlin/com/openinglab/shared/tactics/TacticsStorageTest.kt](../../shared/src/jvmTest/kotlin/com/openinglab/shared/tactics/TacticsStorageTest.kt) | Updates the existing 6→current migration assertion to schema 8; retains the 21-table schema-6 preservation fixture. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/DailyRoutineLearningTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/DailyRoutineLearningTest.kt) | 5 compiled device cases: Today/zero progress, persisted puzzle target/name updating Home, developer sources/feedback/provenance, missed-puzzle activity and actual opening-study/practice completion recording. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/PublicAlphaTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/PublicAlphaTest.kt) | Updates branding/Profile assertions and scrolls to the relocated Home footer links. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/SourcedLearningTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/SourcedLearningTest.kt) | Updates catalog wording and provides navigation support for relocated Home links. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/ObservedRepliesTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/ObservedRepliesTest.kt) | Updates the compact observed-choice assertion and scrolls to Home’s relocated library link. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/GameLibraryLearningTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/GameLibraryLearningTest.kt) | Scrolls to relocated Home links and enables developer mode for exact source metadata assertions. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/RepertoireSetLearningTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/RepertoireSetLearningTest.kt) | Scrolls to the relocated My repertoires link before opening it. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/RepertoireOverviewTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/RepertoireOverviewTest.kt) | Scrolls to the relocated My repertoires link before opening it. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/HistoricalGameCoachTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/HistoricalGameCoachTest.kt) | Scrolls to the relocated Players & GM games link before opening it. |
| [androidApp/src/androidTest/kotlin/com/openinglab/app/OfflineLearningTest.kt](../../androidApp/src/androidTest/kotlin/com/openinglab/app/OfflineLearningTest.kt) | Scrolls to Home’s relocated Offline library link before opening it. |

## Commands and exact final results

```sh
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew --offline --max-workers=4 \
  :shared:jvmTest :shared:testAndroidHostTest :courseTools:test :contentTools:test \
  :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug \
  :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
```

Final run: **BUILD SUCCESSFUL in 33s**, 103 actionable tasks (21 executed, 82 up-to-date).

| Gate | Exact result |
| --- | --- |
| Shared JVM | 226 passed, 0 failed, 0 errors, 0 skipped |
| Shared Android host | 173 passed, 0 failed, 0 errors, 0 skipped |
| courseTools | 45 passed, 0 failed, 0 errors, 0 skipped |
| contentTools | 24 passed, 0 failed, 0 errors, 0 skipped |
| Android assembleDebug | Passed |
| Android compileDebugAndroidTestKotlin | Passed; 5 new Task C cases compiled; 0 device tests executed |
| Android lintDebug | 0 errors, 1 existing AndroidGradlePluginVersion warning (Gradle 9.8.0 available versus pinned 9.7.1) |
| Shared iOS simulator ARM64 | Passed |
| Shared iOS device ARM64 | Passed |

An additional final-layout Android-only invocation of assembleDebug, compileDebugAndroidTestKotlin and lintDebug also passed (16s). The full command above subsequently covered the final legacy-study read path.

Read-only exported-schema comparison: all 24 old entity definitions are identical between 7.json and 8.json; daily_activity is the sole added entity. The migration preservation test also checks every retained row, an initially empty activity table and idempotent migration DDL.

Earlier checks caught an Int/Long mismatch in the shared game-speed callback and two test-fixture errors (a UTC clock falling on the next local Monday and a missing saved policy in the legacy-view fixture). All were corrected before the final successful gates. The first sandboxed Gradle invocation could not open its local file-lock socket; subsequent authorized runs stayed offline.

## Remaining

Claude Code should run the device suite, including DailyRoutineLearningTest, and visually inspect Home/Profile on the test device before the owner installs. Device execution and visual QA were explicitly excluded from Task C; no emulator, adb or connectedAndroidTest was run.

No Git commands, Relay commands/edits, network calls, publication or edits under content/, .course-cache/, .engine-cache/ or .public-audit/ were performed. Package/database identifiers and the version name/code are unchanged. No code work remains for Task C.
