# Try Ashva as a user

This is the Android prototype, version 0.3.1. You can use it offline without an account. Android 8.0 or newer is required. An iPhone app has not been built yet. The lesson has **Full idea / Study**, replay controls, automatic hints and explicit variation choices. The new pass adds **Offline library & sources** and bookmarks that survive cold relaunch. If those controls are missing, install the newer APK again.

The library can install 3,815 opening taxonomy sequences and 79 archived scores from reviewed APK assets into SQLite. Opening teaching/search still uses seven authored openings/thirteen short seed lines; installing source data does not magically create full lessons or a verified GM shelf. See [Content pipeline](CONTENT_PIPELINE.md) and [Offline storage](OFFLINE_STORAGE.md).

## Build the local APK

Install JDK 21 and the Android SDK (platform 37/build tools 37.0.0), or configure them in Android Studio. From the project root run:

```bash
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

## A repeatable 10-minute walkthrough

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

### New in 0.3.1 — offline installation and cold resume

1. Return to **Learn**, scroll to **Offline library & sources** and open it.
2. Install **Opening names & routes**. Expect Installing, then **Installed for offline use** and 3,815 records with CC0 attribution.
3. Install **April 2020 broadcast scores**. Expect 79 records with **CC-BY-SA-4.0** attribution/version/changes. Installing this first should fail safely and offer Retry; install opening names, then retry it. Both installs work without internet because this build includes the reviewed data.
4. Return to Explore → Ruy López → Play White → Full idea. Jump to `3. Bb5`, explore Berlin, advance once to `3... Nf6` and flip to Black.
5. Go back to Learn and wait for **Saved for offline resume**. Its Continue learning card should name Berlin, show Black/study and move 6, rather than a sample lesson count.
6. Force-stop Ashva via Android Settings → Apps → Ashva → Force stop, then reopen from the app drawer. The command-line equivalents are `adb -s <your-device-serial> shell am force-stop com.openinglab.app` and `adb -s <your-device-serial> shell am start -W -n com.openinglab.app/.MainActivity`; substitute the exact serial.
7. Tap **Continue learning**. Expect Berlin at half-move 6, Black POV and Study mode. **Return to branch point** should restore Morphy at half-move 5. Playback must remain stopped.
8. Open Offline library again: both packs should still be installed. Review/Profile statistics are still samples; they do not yet display actual mastery or a recall schedule.

### 5. Practice as Black and confirm an alternate attempt

1. Go back to the opening details and tap **Play Black**.
2. Confirm the board faces Black and White makes the first automatic move.
3. Play `e7 → e5`; wait for White's `g1 → f3`. Play `b8 → c6`; wait for White's `f1 → b5`.
4. Instead of the Morphy move `a7 → a6`, try `g8 → f6`. Expect the unchanged board, the `a6` hint, and a **Switch to Berlin Defence** option.
5. Tap Switch: only now does your attempted `Nf6` move appear, followed by White's Berlin reply. **Return to branch point** restores the original Morphy position before Black's move, preserving Black POV.
6. Variation cards on opening details still start in the opening's default color; the trainer header lets you change color. Play White / Play Black on the details page start the main line.

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

3. The result should show **Ruy López / Morphy Defence**. Scroll below the board if the result is outside the screen.
4. Tap **Undo** and confirm the last move disappears and the board reverses it. Tap **Reset** and confirm the starting position returns.
5. Scroll to the example chips and tap **London System** or **Sicilian Defence** to try a seeded position.
6. Tap **Learn this repertoire** on a match to open its learning page.

### 7. Import a game or position

1. In **Opening identifier**, tap the import icon at the top right.
2. Select **PGN**, paste the following single game, and tap **Load**:

   ```text
   1. f3 e5 2. g4 Qh4# 0-1
   ```

3. Expect the final position, the move list ending in `Qh4#`, and **White is checkmated**. This input is outside the small opening book; an unknown-opening message is correct. Import currently opens the final position, not a guided replay.
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

## What this prototype cannot validate yet

- It contains seven openings with short seed lines, not the full repertoire of every opening.
- Home/Review/Profile statistics are sample values. Bookmarks, actual attempts and selections now persist, but no real mastery/spaced-repetition scheduler exists yet.
- Identification now matches normalized legal positions and handles transpositions, but only within the seven-opening seed book. A last-known opening does not mean the current continuation is covered.
- Legal moves, check/checkmate/stalemate, castling rights and attacked squares, en passant, all four promotions, and repetition/move-count draw state are tested. FEN supplies no prior repetition history; imported PGN reconstructs it from its starting position. Dead-position detection covers conservative material cases, not every unusual locked position. This learning app has no tournament clock or complete arbiter workflow.
- Study replay, Full idea, selectable branches, and automatic hints now work for the authored seeds. Plans/explanations are introductory authored content, not engine analysis or complete theory. Source-backed content and deeper teaching remain future work.
- Archive cards contain unverified game metadata and descriptions, not replayable, source-checked historical games.
- There is no Stockfish evaluation, sourced game library, cloud sync, or playable iOS client. PGN/FEN text import is available in the identifier; importing a game does not generate strategic explanations.

The rules, guided teaching, licensed import and offline storage slices are implemented. Next is sourced repertoire search/coverage and teaching integration, followed by engine-grounded explanations and GM-game learning. These limitations remain recorded so other agents do not mistake the prototype for a finished product.

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
