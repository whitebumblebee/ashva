# Try Ashva as a user

Latest built emulator test version is **Android0.15.0/code16**, opened in place for manual testing. Development is owner-paused for credits; recall's final full regression run was interrupted, so0.15 is not fully accepted (Relay0035). Fully verified baseline0.14 includes the original-game library/coach and separate engine branches. The app works offline without an account on Android8.0+. No playable iPhone client exists. Learn/Explore opens149 courses with3815 named routes and78 longer study continuations across47 families; My repertoires adds named multi-opening practice queues. Never uninstall or clear learner data to test/update.

The opening pack installs automatically from reviewed APK assets; no network/account is needed. Courses have both-color conditional plans, branch focuses and board-derived explanations, not independently expert-reviewed theory. January's857 accepted scores and April's79 are manually installable. The new Players & GM games shelf exposes these source-reported identities/scores, not verified complete careers. See [coverage](OPENING_COVERAGE.md), [game library](GM_LIBRARY.md), [Content pipeline](CONTENT_PIPELINE.md) and [Offline storage](OFFLINE_STORAGE.md).

## Full-flow testing order

The owner has deferred board-quality and iOS implementation and paused development for manual testing. Test the built0.15 flows below in order; this does not substitute for its unfinished automated acceptance. The detailed sections give actions and expected results. Write down the version, chosen color/route, last successful action and any error. Wait for **Saved for offline resume** before closing; never uninstall or clear data to reproduce a problem.

1. **Learn / Explore:** open Ruy Lopez's main course, confirm235 named routes +9 study continuations, then search London, Sicilian, French, Caro-Kann and Italian. Starter's three Ruy demos are separate from the main course.
2. **Study / branches:** use Full idea, move list, First/Previous/Next/Last and Play/Pause from both POVs. Choose an offered variation, then Return to branch point; original path/color/position must return exactly. Leaving/backgrounding pauses autoplay.
3. **Practice:** try a legal off-line move. The board must stay put and automatically show the expected move. Confirm Stay or Switch when offered; only explicit switch changes the path. Study/hints/successful exposed engine output count as help, not unaided recall.
4. **My repertoires:** save family choices and included replies, then a named White Ruy+Sicilian set. Check membership, practice its routes and edit a family afterward: the set still pins its old revision until explicit membership save. Inspect conflicting/unavailable sets; unified practice must not silently omit them.
5. **Identify / offline sources:** enter moves or synthetic PGN/FEN; test known, transposed and unknown positions. Install the reviewed April/January scores, inspect attribution and observed replies. Counts describe the sample, not win odds; loading/error differs from zero.
6. **Players & GM games:** choose a shown identity yourself, Follow/filter, import the synthetic private PGN below and replay its unchanged original score as White/Black. Read original-move coaching, analyze the next recorded move, preview a hypothetical line and return. The source result/cursor must not be rewritten.
7. **Review / Profile:** practice a chosen route/policy/set, select its scope in Review and answer one due card. Next is enabled only after that answer saves. Test hint-assisted answers and separate Study counts; no fabricated streak/rating appears. Do not change the clock to manufacture spacing.
8. **Cold resume:** wait for Saved, force-stop/reopen and Continue a branched course, a pinned set, a hinted review card and an original game separately. Check exact route/revision/cursor/POV/hint context. Missing old content is reported, not substituted.

Keep larger source installs open while validation finishes. Record failed analysis as a timeout/error and try the explicit Retry; an incomplete search must not appear as a recommendation. See [recall's shorter walkthrough](RECALL_REVIEW.md#user-check).

## Test actual recall (0.15)

The0.15 implementation is undergoing complete regression verification; see Relay for the current gate status. Once updated in place, follow the [recall walkthrough](RECALL_REVIEW.md#user-check): practice either-color routes/pinned repertoires to enroll real decisions; choose their exact scope in Review; answer one position, wait for its ledger save and use Next due position. Hint/automatic hint/successful engine/confirmed branch/recent Study help are assisted, failed/stopped analysis is not. Profile separates actual attempts, new grades, Study views and retained legacy/ungraded history. No sample streak/percentage or guaranteed mastery remains. Do not change the device clock to manufacture due grades; spaced intervals are tested with injected times in synthetic fixtures.

## Test players and full original games (0.13)

1. Learn → **Players & GM games** → **Offline sources**. Install April2020 for79 accepted scores; optionally January2020 for857 more. Return to the library; with both sources it has936 public score records, plus any private imports. This is not every historical GM match.
2. Search a name shown in the installed sample. Tap **Follow**, then **Followed players** to restrict games to your chosen identities. Following initially has no favorites. **Show games** selects one exact identity; similar/private names are not silently merged.
3. Open **Filters**. Try event, year, opening/ECO, result and reported-GM title. Select a player before filtering their White/Black games. Zero means no match in this installed sample, not no career history. Clear filters to restore the list.
4. Open **Replay as black**. Test Next/Previous, Last/First, the complete move list, Play/Pause, speed and Flip POV. Original move order/result must never change. Leaving/backgrounding pauses playback; restart deliberately.
5. Read the automatic move explanation, then expand **Understand this position** for current-board facts and conditional plans. Read the exact retained pack/manifest/record. These are not inferred GM intentions. Test analyzed alternatives using the walkthrough below.
6. Return to Learn. **Continue original game** resumes cursor/color. Wait for saved status before force-stopping/relaunching. Cold resume checks the exact source; an unavailable version is not replaced by a newer score.

### Test move coaching and analyzed branches (0.14)

Complete regression gates pass, including real offline engine, both POVs, cold explanations, original replay and branch return. See [coaching contracts and limits](HISTORICAL_GAME_COACH.md).

1. Open an installed score or your private synthetic game below as Black. Tap **Next**. Expect **ASHVA BOARD FACT**, the color that actually moved, an explanation of the board change and a separate conditional plan. This is not the player’s verified intention. User comments remain separately labeled unverified.
2. Go Next, then Previous. The same move explanation is reused from the bounded local cache. Flip POV: the original move/position stays unchanged, while the position report and engine-score perspective use your selected color.
3. Tap **Analyze this position**. Original autoplay pauses. Read **Analysis root**: this compares the **next** recorded move against candidates at the current position. To evaluate a move just played, go Previous before analyzing. Expect offline Stockfish19, search ceilings, actual depth/nodes/time, next recorded original move and binary/NNUE provenance.
4. Tap **Explore this analyzed continuation**. Expect a separate **HYPOTHETICAL ENGINE LINE**, checked move explanations and first/previous/next/last controls. Original navigation/Play is disabled; the original score, result and cursor must not change. Flip POV can still change orientation/score perspective.
5. Tap **Return to unchanged original game**. You return to exactly the saved original ply and chosen POV; playback does not automatically restart. Play deliberately to continue the complete recorded mainline. An engine continuation is not a historical variation or proven winning line.
6. Start analysis and background the app, or leave the replay. It must stop without publishing a stale recommendation; reopening retains the original cursor and recomputes its move explanation offline. Cold resume does not automatically restart analysis. Never uninstall/clear data to test this.
7. At Last, analyze the final recorded board. There is no invented next original move and the reported source result is unchanged. A board-terminal position reports its rules status without an engine search. Private games longer than512 half-moves still replay/explain fully, but analysis beyond that history has an explicit limit message rather than silently discarding repetitions.

### Import and follow your own game

1. Players & GM games → **Import private PGN**. Paste this synthetic game, then **Import locally**:

```pgn
[White "Synthetic Learner"]
[Black "Synthetic Opponent"]
[Event "Private example"]
[Date "2026.10.03"]

1. e4 {User-supplied example, not expert commentary} e5 2. Nf3 Nc6 *
```

2. Search `Synthetic Learner`, Follow the private identity and open its four-half-move score. At `e4`, the comment is visibly user-supplied/unverified. Private identities stay separate from similarly named source players.
3. Unfollow. The game remains; **Followed players** may now show no matching private score. Clear that filter to find it again. Private games never change public observed counts or pack licensing.
4. Try `1. e5 *` or two games in one archive. Expect rejection with existing games unchanged. Limits: one standard PGN,256KiB UTF-8/4096 mainline half-moves; private storage1000 records/32MiB. Oversized pasted replacements are rejected, not truncated. Drafts are not guaranteed to survive activity destruction; successful imports are.
5. You need permission for supplied annotations. These remain local data, not uploads/public content. Clearing/uninstalling loses imports/follows/progress; no export/recovery UI exists yet.

## Test the expanded observed-game sample

1. Open Learn → Offline library & sources. Opening names should already be installed; install January 2020 broadcast scores, then optionally April 2020.
2. January must show **857 records stored**, **952 inputs attempted / 95 quarantined**, CC BY-SA 4.0 and exact version/attribution. Installation never adds rejected records or changes saved repertoire choices.
3. Open any course → Build / edit my repertoire. At its starting position, the observed sample should show 857 accepted scores with January alone, or 936 with both game packs. Position-specific counts become smaller farther into a line. Counts are observed replies, not engine evaluations or master popularity.
4. Open source details to inspect exact pack hashes/dependencies/license. Force-stop/reopen without clearing data: installation and the observed sample should restore/rebuild offline. A larger sample can take longer to validate; loading is not zero coverage.

## Test position-specific teaching

1. Open any course → Full idea, then **Understand this position** below the board's replay controls. Loading should resolve into your side and the opponent's counterplay, with separate BOARD FACT / CONDITIONAL PLAN labels.
2. Read exact pawn squares, isolated/doubled/passed pawns, files, attackers/defenders and king safety. Flip White/Black in the header; those two reports swap perspective without moving the board. Candidate pawn contacts stay attached to the actual side to move, not your POV.
3. In a French Advance route, jump after `e4 e6 d4 d5 e5`. Black's legal contact candidates include `c5` and `f6`. They are not ranked recommendations: compare the resulting captures and king exposure, or analyze separately.
4. Step farther into the line. The expanded report should update to the current position; branch and return should restore its exact facts. In queenless/reduced material, read the heuristic/endgame limits rather than interpreting the lens as a win assessment.
5. Tap Analyze this position → Explore this analyzed continuation. Each hypothetical move now has a checked explanation. Expand its separate Understand this position panel, use Next/Previous, and Return to unchanged lesson. The original path/cursor and saved repertoire must not change.
6. Switch to Practice from start. The position-ideas panel is not automatically exposed during recall; wrong moves still retain the board and show the next-move hint immediately. No study or analyzed line proves mastery. See [teaching evidence and limits](GROUNDED_TEACHING.md).

## Test the main opening courses first

1. Open Learn and wait for course preparation to finish. The family catalog should contain 149 courses. If an install/error is shown, open Offline library and retry Opening names & routes; do not clear data.
2. Open Ruy Lopez from Learn, or search `Ruy` under Explore → All. Expect **235 named source routes + 9 authored study continuations**, not three starter lines. Course details publish actual half-move depth and provenance.
3. Search its routes for `Berlin`. Choose Black POV, then the authored **Berlin · queenless middlegame** route. Tap Full idea. Jump to half-move 16 (`Kxd8`); read the move explanation, both-color plans and line focus. Flip the header color; the selected plan changes without losing the position.
4. Jump to half-move 5 (`Bb5`). Select a named alternative, advance once, then Return to branch point. Check the exact position and original path are restored. Stay on a line to resume autoplay; it must pause for branch decisions.
5. Tap Practice from start as White and try `d2 → d4` instead of `e2 → e4`. The board stays unchanged and automatically shows the e4 hint; this is not a claim that d4 is a blunder.
6. Repeat with **London System** (4 source + 4 study routes), **Sicilian Defense** (391 + 7), **French Defense** (212 + 5), **Caro-Kann Defense** (110 + 4), **Italian Game** (187 + 3), then another family. Both colors, Full idea and replay apply throughout the catalog. Source routes retain their original lengths; not every endpoint has a deeper authored continuation.
7. Build/edit a repertoire from a course and deliberately include a reply or adopt a route. Study switches alone must not change saved choices. Policies bind to that course version; old source/Starter policies stay separate.
8. Wait for Saved for offline resume. Force-stop/reopen without uninstalling or clearing data; Continue should retain the course, route, color, cursor and branch return. Existing legacy bookmarks should continue to open their original lesson.
9. Under Explore → Sourced, compare the raw 235-route Ruy family: it still explicitly lacks strategic plans. Under Starter, the original three Ruy demos remain available. These filters separate raw source data/legacy demos from the default courses.
10. At a route endpoint, read the continuation boundary and use offline analysis to explore hypothetical alternatives. Engine lines remain separate from authored/source routes; no course ending proves mastery or a guaranteed win.

## Test the combined repertoire overview

1. Open an authored or sourced lesson, tap **Build / edit my repertoire**, and wait for Choices saved. Repeat for another opening, optionally also the other color. No source pack is needed for the authored seeds.
2. Go to **Home → My repertoires**. Switch White/Black in Combined repertoire overview. Counts union validated reachable choices, not all theory or mastery; source endpoints/gaps remain per family.
3. To see a conflict, save White policies for Ruy López and London. Their first moves e4/d4 differ. Both remain listed—no winner is chosen and nothing is overwritten. These may intentionally stay independent.
4. Tap a conflict's **Edit … here** link. The editor opens that exact position, even if its remembered cursor was elsewhere. Inspection alone changes no preferences. Deliberate edits still create immutable new revisions.
5. Expand **Show exact saved identity** to inspect lesson/policy/snapshot/revision. Missing/changed families remain listed, excluded from checked counts; Practice is unavailable when no fitting source route can be validated. Loading is not zero coverage, and error offers Retry.
6. Close/reopen the app. The overview rebuilds from retained Room policies. This overview is read-only; the separate named-set feature below creates multi-family practice queues.

## Test a named multi-opening repertoire

1. Save White family choices for Ruy Lopez and Sicilian Defense (or other compatible families) in their lesson editors. Keep the individual policies; a set groups them without rewriting them.
2. Open Home → My repertoires. Choose White in the color selector, then New white repertoire. Enter a name such as `White e4`, check both saved families and Save membership. Each displayed revision is pinned.
3. Tap Check set. Expect fitting route entries, conflicts and unavailable/changed member counts. Practice is enabled only when every exact member can be checked, has fitting routes and no preferred-move conflict.
4. Tap Practice this repertoire. The header shows the set name and current queue entry. Next/Previous route traverses its different families; each retains its own original path and policy-filtered branches. Color is fixed. Full idea, practice, hints and return remain available. Visiting an entry does not prove recall/mastery.
5. Study an entry, jump several moves and wait for Saved for offline resume. Force-stop/reopen without uninstalling or clearing data. Continue must retain the exact set revision, queue index, family, cursor, color and branch context.
6. Edit a family's choices to create a newer revision. The existing set still lists its old pinned revision. Use Edit membership and explicitly Save membership to adopt current displayed revisions; old bookmarked sets remain restorable.
7. Create a separate White set containing Ruy Lopez and London. Check set should expose the e4/d4 preferred-first-move conflict and disable unified practice. Keep these in separate named sets or edit compatible choices deliberately; neither policy is automatically overwritten.

## Build the local APK

Install JDK 21 and the Android SDK (platform 37/build tools 37.0.0), or configure them in Android Studio. From the project root run:

```bash
export ASHVA_NDK_DIR="$ANDROID_HOME/ndk/30.0.16248370"
node scripts/prepare-stockfish.mjs
./gradlew :androidApp:assembleDebug
```

Find `androidApp/build/outputs/apk/debug/androidApp-debug.apk` relative to the project folder. No prebuilt public release is promised.

## Option A — install on an Android phone

1. Copy the generated APK to your Android phone using USB or a private transfer method.
2. Open Files/Downloads and tap the APK.
3. If Android asks, allow that file app to install apps from this source, then return and tap Install. See [Android's installation guidance](https://support.google.com/android/answer/9457058?hl=en).
4. Open **Ashva**. Expect Learn, Explore, Review and Profile tabs.
5. You can turn off that file app's installation permission afterwards.

This is a debug build, not a Play Store release. To update, install the newer APK with the same signing key; do not uninstall or clear data to resolve an error, because it deletes local progress.

## Option B — use an Android emulator

In Android Studio → Device Manager, create a virtual phone with API 33 or newer, choose the architecture matching your host, and start it. You can drag the APK onto the emulator to install it, as described in [Android's guide](https://developer.android.com/studio/run/emulator-install-add-files).

For command-line use, put your SDK's platform-tools and emulator directories on PATH; then run from the project root:

```bash
emulator -list-avds
emulator -avd <your-avd-name> -no-snapshot-save -no-boot-anim
```

Skip the second command if the emulator is running. Replace angle-bracket placeholders with actual names; do not paste them unchanged. In another terminal:

```bash
adb devices -l
adb -s <your-device-serial> install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s <your-device-serial> shell am start -W -n com.openinglab.app/.MainActivity
```

Expected installation output: Success. Select the exact serial, especially with multiple devices. **Ashva** is the new launcher name; the package remains `com.openinglab.app` for upgrade/data compatibility.

## Legacy starter walkthrough (select Explore → Starter)

The following original short-fixture checks use **Starter**, not the current default All course catalog. Select Starter before searching/opening Ruy López in these checks. Historical v0.4–0.8 sections describe their raw-source/seed behavior; the current course walkthrough above takes priority.

### 1. Browse an opening

1. Open **Explore** from the bottom bar.
2. Search `Ruy` and confirm Ruy López is shown.
3. Clear the search; try the difficulty filters and confirm the displayed list changes.
4. Open **Ruy López**. Inspect the core ideas and scroll to **Lines in this repertoire**. Its seeded lines are Morphy Defence, Berlin Defence, and Exchange Variation.

### 2. Learn the main line as White

1. Return to the upper part of the Ruy López page and tap **Play White**.
2. Make moves by tapping the source square, then the destination. The board currently uses taps, not drag-and-drop. Coordinates appear along its edges.
3. Play `e2 → e4`. Wait for Black's automatic `e7 → e5` reply.
4. Play `g1 → f3`, then wait for Black's `b8 → c6`.
5. Play `f1 → b5`. The automatic reply pauses: **Choose a continuation** offers Berlin Defence. Tap **Stay on Morphy Defence** to receive `a7 → a6`. White's Exchange Variation is then offered; choose **Stay on Morphy Defence** again (or simply play the expected `b5 → a4`).
6. Continue with the White moves below, waiting for each scripted Black reply:

   | Your move | Black's reply |
   | --- | --- |
   | `b5 → a4` | `g8 → f6` |
   | `e1 → g1` (castle) | `f8 → e7` |
   | `f1 → e1` | `b7 → b5` |
   | `a4 → b3` | `d7 → d6` |
   | `c2 → c3` | `e8 → g8` (castle) |

7. Read the explanation after each move. At the end, expect **Practice complete**, retry and assisted-move counts. The bookmark and attempts are stored, but completion covers this seed line only; it is not a mastery score or chess-strength rating.

### 3. Check help and correction

1. Tap **Restart practice**.
2. Try `d2 → d4` as the first White move. Expect **Legal move, but outside this lesson. Play e4.** The position stays unchanged; `e2` and `e4` are highlighted automatically, and **Expected: e4 · e2 → e4** appears without a Hint tap. This does not claim `d4` is a chess blunder.
3. Try `e2 → e5`: expect an illegal-move message and the same automatic hint. Neither attempt moves the board.
4. Play `e2 → e4`; the move is counted as assisted. **Hint** remains available before making an attempt.

### 4. Study the whole line and explore a variation

1. In the Ruy López trainer, tap **Full idea**. You are now in Study mode; the board is read-only.
2. Scroll below the board to the chosen-color plan, opponent's plan, and **Full line**. All 16 half-moves of the Morphy seed are shown, including both colors. This is not the entire Ruy López repertoire.
3. Tap any move to jump to its resulting position. Try `2... Nc6`, then use **First position**, **Previous move**, **Next move**, and **Last position** under the board. Read the explanation for the currently displayed move.
4. Tap `3. Bb5` (half-move 5). Expect **Choose a continuation**. Tap **Explore Berlin Defence**: the board remains at the branch point; **Next move** plays `3... Nf6`.
5. Tap **Return to branch point**. The Morphy path and position after `Bb5` return exactly. Select **Stay on Morphy Defence** to continue it.
6. Return to the first position and tap **Play replay**. Playback pauses at the Berlin choice. Select Stay, then tap Play again. Use the speed button to cycle 0.7 / 1.2 / 2.0 seconds per move. Manual Next or a move-list jump follows the current line without requiring Stay.
7. Tap **White ⇄** in the header: the board flips and Full idea changes to Black's plan. Cursor and branch-return history are preserved. Tap **Practice from start** to practice the active route in the chosen color.
8. In Study, jump to a move, background and reopen the app, then rotate the emulator (or phone). The chosen color, cursor and branch-return context should remain. Autoplay stops on backgrounding and does not restart by itself. For a cold relaunch check, follow the next section; never clear app data or uninstall, since those erase local progress.

### Offline installation and cold resume

1. Return to **Learn**, scroll to **Offline library & sources** and open it.
2. Install **Opening names & routes**. Expect Installing, then **Installed for offline use** and 3,815 records with CC0 attribution.
3. Install **April 2020 broadcast scores**. Expect 79 records with **CC-BY-SA-4.0** attribution/version/changes. Installing this first should fail safely and offer Retry; install opening names, then retry it. Both installs work without internet because this build includes the reviewed data.
4. Return to Explore → Ruy López → Play White → Full idea. Jump to `3. Bb5`, explore Berlin, advance once to `3... Nf6` and flip to Black.
5. Go back to Learn and wait for **Saved for offline resume**. Its Continue learning card should name Berlin, show Black/study and move 6, rather than a sample lesson count.
6. Force-stop Ashva via Android Settings → Apps → Ashva → Force stop, then reopen from the app drawer. The command-line equivalents are `adb -s <your-device-serial> shell am force-stop com.openinglab.app` and `adb -s <your-device-serial> shell am start -W -n com.openinglab.app/.MainActivity`; substitute the exact serial.
7. Tap **Continue learning**. Expect Berlin at half-move 6, Black POV and Study mode. **Return to branch point** should restore Morphy at half-move 5. Playback must remain stopped.
8. Open Offline library again: both packs should still be installed. In0.15, follow the actual-recall walkthrough above: Review/Profile use stored events and chosen-scope schedules, not the old demo values. Replaying this Study line does not itself prove recall.

### New in 0.4.0 — explore the full delivered taxonomy

1. Install **Opening names & routes** as above, then return to **Explore**. Wait for **149 source families · 3815 routes** and select the **Sourced** filter. The authored starter lessons remain separately available under All/difficulty filters and on Learn.
2. Search `Ruy Lopez` or `Ruy López`. Open its sourced family, not the authored **Ruy López** starter. Alternatively, the starter page's **Explore sourced variations →** action searches the sourced catalog for you. Expect **235 routes**, CC0 provenance, the pinned revision and a published depth range—not three starter lines. Search also accepts Defence/Defense spelling aliases; source names stay unchanged.
3. Scroll to **Source routes**, search `Berlin`, select **Black POV**, and tap a named route. Different records may have the same name but different move orders; all source routes are retained. The Play White / Play Black cards start the family's shortest base route instead.
4. Wait for lesson preparation. Tap **Full idea**: the entire selected route is shown, with first/previous/next/last and move-list jumps. Expect **SOURCED · CC0-1.0**. Per-move labels state recorded moves; both strategic plans explicitly say they are unavailable. This is sequence study, not a reviewed course or engine advice.
5. Jump to `3. Bb5`. Where alternatives exist, playback pauses and offers named continuations. Use **Show more continuations** if there are over twelve; pagination affects the screen, not stored coverage. Explore one, advance, and use **Return to branch point**. Confirm route/cursor/color restore.
6. Flip to White and select **Practice from start**. Try `d2 → d4` instead of the route's `e4`. The board should remain unchanged and immediately hint `e2 → e4`; no Hint tap is required. A legal off-line move is not proof of a blunder.
7. Wait for **Saved for offline resume**, force-stop/reopen, and tap Continue. The sourced route, chosen color, cursor, hint and branch-return history should survive. No autoplay resumes. Missing/changed content reports unavailable while retaining the saved record.
8. Test source-based identification: import `1. c4 Nf6 2. d4 e6 3. Nc3 Bb4 *` as PGN. Expect a sourced **Nimzo-Indian Defense** candidate at half-move 6 despite the transposed move order. A FEN has no move history, so only the supplied current position can match a source endpoint. Ambiguous and last-known results remain explicit.

This snapshot contains 391 Sicilian Defense routes, 235 Ruy Lopez routes, 212 French Defense routes and other documented families. It is all of this delivered taxonomy, not all possible theory or all popular replies from historical games. Reviewed source explanations are still upcoming.

### New in 0.5.0 — build and practice your own choices

Use the authored Ruy López for this short reproducible check; the same builder works on installed sourced families.

1. Open **Explore → Ruy López → Play White**, then **Full idea** and jump to `3. Bb5` (half-move 5).
2. Tap **Build / edit my repertoire** above the board. A new White policy is seeded from Morphy, with one fitting recorded route. If you edited it previously, existing choices load instead; do not clear data to repeat the check.
3. At position 5, include **Nf6** under opponent replies. Wait for the saved revision. Expect an unanswered learner branch at position 6; adding an opponent reply does not silently pick your response.
4. Tap that gap. The board shows the Berlin position. Choose **O-O** as your preferred move. More unanswered replies/positions may appear: fill them individually, or tap **Add this route to my choices** and confirm **Use route choices**. The confirmation explains that conflicting learner preferences will change. Morphy and Berlin now fit; Exchange remains outside White's chosen scope.
5. Tap **Practice my repertoire**. It starts on the longest fitting recorded route; its banner names the policy/revision, and only fitting routes are present. Study/jump to position 5: Berlin is available; explore it and return. The color button is disabled because Black needs its own policy. A legal different move still gets a hint and leaves the board unchanged.
6. Return to **Learn → My repertoires**. Reopen White's editor; confirm choices/revision persist. Open the normal Ruy lesson separately: all three seed variations remain available in the broad explorer.
7. Start a **Black** Ruy lesson and build its separate policy. Initially its preferred move at position 5 is **a6**. Practice this policy: Berlin is not offered as an included learner move. Trying **Nf6** still reports a legal off-line move and hints **a6**, without moving the board or calling it a blunder. Edit Black's preference and included White replies deliberately to choose Berlin instead.
8. Save a policy practice at a known cursor, wait for **Saved for offline resume**, then edit the current policy to a new revision. Force-stop/reopen and Continue: the bookmarked older policy revision restores its exact route/color/cursor. My repertoires still shows the latest choices; editing is not a destructive upgrade of prior bookmarks.
9. Repeat with a named **sourced** route after installing the taxonomy. Browse routes in the editor. Counts cover only that family/color/snapshot. Inspect source endpoints, excluded replies and legal moves absent at the current board; none claim exhaustive coverage, popularity, mastery or an engine evaluation.

### 5. Practice as Black and confirm an alternate attempt

1. Go back to the opening details and tap **Play Black**.
2. Confirm the board faces Black and White makes the first automatic move.
3. Play `e7 → e5`; wait for White's `g1 → f3`. Play `b8 → c6`; wait for White's `f1 → b5`.
4. Instead of the Morphy move `a7 → a6`, try `g8 → f6`. Expect the unchanged board, the `a6` hint, and a **Switch to Berlin Defence** option.
5. Tap Switch: only now does your attempted `Nf6` move appear, followed by White's Berlin reply. **Return to branch point** restores the original Morphy position before Black's move, preserving Black POV.
6. Authored variation cards start in the opening's default color; the trainer header lets you change color. Sourced details also let you choose White/Black POV before selecting a route. Play White / Play Black starts the main line.

### Check the new observed-move counts

1. From Learn → **Offline library & sources**, install **Opening names & routes**, then **April 2020 broadcast scores**. Nothing is fetched over the network.
2. Open the authored **Ruy López → Play White**, enter **Study**, jump to **First**, then tap **Build / edit my repertoire**.
3. Scroll below the board to **Moves observed in the game sample**. At the starting position expect **79 / 79 score records**, all with replies; **26 replies match your choices**. The selected `e4` row shows **26 / 79**.
4. Below the selectable route moves, `d4` shows **45 / 79** and is labeled outside the family snapshot. It has no selection control; it does not generate a Ruy López lesson.
5. Open **Source details & attribution**. Expect April 2020, CC-BY-SA-4.0, exact pack/version/checksum, contributors, changes and the original taxonomy dependency. Tap **Offline library & sources**, then Back: your choices remain unchanged.
6. Step through the editor's board and observe counts changing by position. Zero means the sample never reached that position, not that the move is bad. A reached endpoint without a reply is excluded from the move-count denominator. The sample is not all master games, a win forecast or an engine.
7. On an installation without the broadcast pack, the card instead says **No installed game sample**, not zero. During indexing it shows loading; a read/validation failure shows an error and Retry. Existing source packs and choices are retained.

### 6. Identify a game you recreate

1. Return to **Explore** and tap the **Don't know the name?** card to open **Opening identifier**.
2. Enter both players' moves by tapping squares in this order:

   ```text
   e2 → e4
   e7 → e5
   g1 → f3
   b8 → c6
   f1 → b5
   a7 → a6
   ```

3. Without the source pack, expect **Ruy López / Morphy Defence**. With it installed, expect the sourced **Ruy Lopez** labels for that named position. Scroll below the board if the result is outside the screen.
4. Tap **Undo** and confirm the last move disappears and the board reverses it. Tap **Reset** and confirm the starting position returns.
5. Scroll to the example chips and tap **London System** or **Sicilian Defence** to try a seeded position.
6. Tap **Learn this repertoire** on an authored match or **Explore source routes** on a sourced match. Ambiguous candidates have separate Explore buttons.

### 7. Import a game or position

1. In **Opening identifier**, tap the import icon at the top right.
2. Select **PGN**, paste the following single game, and tap **Load**:

   ```text
   1. f3 e5 2. g4 Qh4# 0-1
   ```

3. Expect the final position, the move list ending in `Qh4#`, and **White is checkmated**. Catalog availability can produce unknown or last-known opening information; it does not establish a course covering the mate. Import currently opens the final position, not a guided replay.
4. Tap **Undo** to step back one half-move. **Reset** returns to the standard starting position.
5. Open import again, select **FEN**, replace the text with this position, and tap **Load**:

   ```text
   7k/P7/8/8/8/8/8/7K w - - 0 1
   ```

6. Tap `a7 → a8`. Expect a choice of Queen, Rook, Bishop, or Knight. Choose **Knight**: the move list should show `a8=N`, with insufficient-material draw status. **Undo** returns to the imported FEN, not the standard starting position.
7. Try importing `not a FEN`. Expect an error while the previous board remains unchanged. Import accepts standard chess only, one PGN game at a time, at most 65,536 characters and 600 main-line half-moves. PGN tags, comments, and nested variations are validated by the shared parser, but imported games are not yet connected to the guided lesson screen.

To check transpositions, reset and enter `d2d4 g8f6 c1f4 d7d5 g1f3` by tapping the corresponding squares. Expect **London System**, even though this is a different move order from the authored seed line. Match labels distinguish known positions, early ambiguity, unknown positions, and a last-known opening after leaving the book; there is no confidence percentage.

### 8. Check the overall experience

Browse London System and Sicilian Defence, switch all four bottom tabs, send the app to the background and reopen it, and check the board on your phone. Note any clipped text, difficult taps, slow animation, confusing feedback, or lost state. Emulator haptics cannot tell you how vibration feels on a real phone.

## Test offline analyzed alternatives

1. Open **Ruy López → Play White → Full idea**, step to the position after **3. Bb5**, then scroll to **Analyze alternatives**.
2. Tap **Analyze this position**. First use prepares verified local NNUE data; no network/account is needed. Expect Stockfish 19, White score perspective, limits and actual depth/nodes/time. The next lesson move appears separately as Compared move.
3. Tap **Explore this analyzed continuation**. Use First/Previous/Next/Last on the hypothetical board, then **Return to unchanged lesson**. The lesson board/cursor/policy must remain unchanged.
4. Flip to Black and analyze again; the score perspective must flip. Mate is separate from CP; bounds retain ≥/≤. Strongest found at this budget does not mean proven best.
5. Start analysis, then Stop or leave/background the trainer. No stale result should appear after changing lesson position; retry deliberately rather than expecting automatic background analysis.
6. In Practice, attempt a legal off-line move: board preservation/automatic hint still work. Analyze to compare the attempted move; no automatic policy adoption or blunder label.
7. Read binary/NNUE provenance. Source, license, network and build recipe are in APK engine assets. Unsupported ABIs/errors/timeouts do not produce fake recommendations or indefinite Loading.

These are bounded continuations, not reviewed courses. [Engine setup/limits](ENGINE_ANALYSIS.md) explains build and distribution details.

## What this prototype cannot validate yet

- It contains149 guided families with3815 source routes and78 longer illustrative study continuations in47 families, plus retained legacy starters—not every possible line or independent expert-reviewed theory.
- Android0.15 Home/Review/Profile use local actual events and chosen-scope schedules, not sample statistics. Established means three separated due unaided successes and not overdue; this is an original deterministic rule, not a validated rating/mastery probability. Legacy attempts remain ungraded and Study is separate from recall.
- Identification matches normalized legal positions from the authored book and installed taxonomy endpoints, with transpositions. Intermediate unnamed positions can report the last known name; a last-known opening does not mean the current continuation is covered.
- Legal moves, check/checkmate/stalemate, castling rights and attacked squares, en passant, all four promotions, and repetition/move-count draw state are tested. FEN supplies no prior repetition history; imported PGN reconstructs it from its starting position. Dead-position detection covers conservative material cases, not every unusual locked position. This learning app has no tournament clock or complete arbiter workflow.
- Study replay, Full idea, branches/return and automatic hints work across courses and retained raw routes. Both-color policies and named multi-family queues are real. Conditional guides/board facts are original Ashva content; raw taxonomy has no historical annotations or independently reviewed strategy.
- Legacy opening-detail archive cards remain unverified metadata. Use Players & GM games for checked full source/private scores;936 broadcast records do not establish full careers or authenticated identities.
- Offline Stockfish candidates/continuations and original-game move coaching are implemented, not expert intention or guaranteed wins. The0.15 recall implementation has its final regression status in Relay. The optional content/analysis service is locally tested but not deployed or connected to Android; cloud sync is absent. Final board/device quality and playable iOS client are owner-deferred. Identifier PGN/FEN input is still a separate final-position tool.

Rules, finite guided courses/position teaching, licensed imports, offline storage, selected family/multi-family policies and original-score browsing/replay are implemented. The whole goal remains unfinished; verified milestones belong in Relay, not inferred from catalog counts.

## Troubleshooting and bug reports

- **No emulator detected:** wait until Android finishes starting, then run `adb devices -l`. There should be a device such as `emulator-5554` with state `device`.
- **Phone cannot install:** check Android is at least 8.0 and that the APK fully transferred. If updating an app installed with a different signing key, report the installer message; do not uninstall automatically, since that can remove app data.
- **Want to install by USB from your computer?** Enable Developer options and USB debugging on the phone, connect it, and accept the computer authorization prompt. Then use `adb -d install -r` with the same APK path. See [Android's physical-device setup guide](https://developer.android.com/studio/run/device).
- **Crash or UI issue:** record phone model, Android version, screen/line/color, exact moves/actions, expected result, actual result, and a screenshot or screen recording if useful. Never include passwords or private unrelated screens.

## Rebuild after app code changes

From the project root:

```bash
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
```

Install the updated APK again. If SDK location is missing, configure `ANDROID_HOME` or Android Studio's SDK settings locally; do not commit a host-specific `local.properties`.

Run automated Compose checks only with a **separate test emulator** running. The test runner may uninstall its APK and delete its data. Select the isolated serial explicitly; confirm the output names only that test device:

```bash
ANDROID_SERIAL=<isolated-test-serial> ./gradlew :androidApp:connectedDebugAndroidTest
```


## Alpha privacy and demo labels

Home repertoire percentages, Review counts/dates and Profile goals are labeled as demo data. They are not your measured mastery, rating or account. The neutral Learner profile requires no sign-in. Cold-resume bookmarks and actual attempts are local records.

Automatic cloud/device-transfer backups are excluded in this alpha. There is no learner export/recovery yet; uninstalling or clearing app data loses progress. Read [privacy notes](PRIVACY.md) before using private game input or sharing bug evidence.
