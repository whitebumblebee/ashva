# Ashva 0.18 — how to use it

*Updated for 0.18.1: branch choices along every line, and the Celtic piece set.*

A practical guide to the app as it is now: where things are, how to work through every Ruy Lopez variation, and how to train tactics with the Woodpecker method. Everything runs offline on your phone. There is no account and nothing is uploaded.

---

## 1. Install it on your phone

Step-by-step version with troubleshooting: [INSTALL_ON_PHONE.md](INSTALL_ON_PHONE.md).

You need the debug APK, which is signed with the local debug key and installs like any sideloaded app:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :androidApp:assembleDebug
```

The file is `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Install it in one of two ways:

- **USB:** turn on *Developer options → USB debugging* on the phone, connect it, then run:

  ```bash
  $HOME/Library/Android/sdk/platform-tools/adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
  ```

- **File copy:** copy the APK to the phone, open it, and allow "install unknown apps" for your file manager.

Android 8.0 or newer is required. To update later, install the new APK the same way. `-r` keeps your progress, so never uninstall or clear app data: that deletes your history.

The first start after an install or update needs a few seconds to prepare the courses and puzzles. A small progress bar shows while it works.

---

## 2. The five tabs

| Tab | What it is for |
| --- | --- |
| **Learn** | Your home: today's routine, continue where you left off, the Ruy Lopez course, your opening courses. |
| **Explore** | Browse every opening course in the app and search by name. |
| **Tactics** | Woodpecker puzzle training: fixed sets, timed cycles, history. |
| **Review** | Spaced repetition of the opening decisions you have practised. |
| **Profile** | Your week, statistics, routine settings, preferences, sources and licences. |

---

## 3. Learn: your daily routine

The **Today** card at the top is your practice routine. It has up to three rows; tap a row to jump straight into it:

1. **Tactics · 20 puzzles**: continues your active Woodpecker set.
2. **Openings · practise 3 lines**: practises course lines.
3. **Review · N due**: shown only when something is due.

A row gets a check mark when it is done. Below the rows you see how many study days you have this week against your goal. Change the targets in **Profile → Routine settings** (daily puzzles, daily opening lines, the review switch, weekly study-day goal).

Further down the Learn tab:
- **Continue** reopens the last lesson at the same move.
- **The Ruy Lopez · Complete course** opens the deep course (section 4).
- **Your opening courses** lists the shorter courses: Ruy Lopez routes, London, Sicilian, French, Caro-Kann, Italian, QGD.
- Links to **My repertoires**, **Players & GM games** and **Offline library** are at the bottom.

---

## 4. The Ruy Lopez course: exploring every variation

### 4.1 Course → chapter → variation

1. **Learn → The Ruy Lopez · Complete course.** You get the 10 chapters plus one annotated GM game:

   | Chapter | Variations | Lines |
   | --- | --- | --- |
   | Berlin Defence | 36 | 158 |
   | Exchange Variation | 8 | 25 |
   | Open Ruy Lopez | 11 | 54 |
   | Closed Ruy Lopez: main systems | 46 | 205 |
   | Marshall Attack and Anti-Marshall | 12 | 48 |
   | Morphy Defence: other systems | 51 | 293 |
   | Schliemann (Jaenisch) Gambit | 5 | 18 |
   | Classical Defence | 7 | 43 |
   | Old Steinitz Defence | — | 10 |
   | Other third moves for Black | 6 | 39 |
   | GM game: Firouzja–Carlsen, Tata Steel 2020 | | |

   The Closed chapter holds the Breyer, Chigorin, Zaitsev, Smyslov, Karpov, Flohr and Martinez systems. The "other systems" chapter holds the Møller, Arkhangelsk and Neo-Arkhangelsk, Modern Steinitz, Cozio, Wormald and Norwegian lines.

2. **Open a chapter.** At the top:
   - **Your side: White | Black.** Choose the side you want to play. All study and practice in the chapter then puts you on that side, and the winning plans for that side are shown first.
   - **Study main line** walks you through the most-played line of the chapter.
   - **Practice** plays a random line, picked as often as real players reach it. Common replies and common mistakes therefore come up most.

3. **The variation tree.** Each row is one variation and shows:
   - its name: a real name such as *Berlin Wall Defense* or *Breyer Defense*, or, for an important line with no name, its move, e.g. *4...Bc5*;
   - its last moves;
   - a result bar (White wins · draws · Black wins in master games);
   - the number of master games.

   Tap the chevron to unfold sub-variations, and tap a row to open it.

4. **The variation screen** is where you learn a variation:
   - **Board** showing the position where the variation starts.
   - **Results and rating bands**: how often White wins, draws or loses, and how many games come from 2600+, 2400–2599 and 2200–2399 players and from club players.
   - **The idea**: what the variation is about, in a few sentences. Tap *Show more* for the rest.
   - **How White wins / How Black wins** (your side first): a short written plan, then **key-move chips**. These are the moves the winners played much more often than other players, e.g. *Nc3 · 81% of wins*. Then **Model games**: tap one to replay a real master game from that position.
   - **Sub-variations**: deeper branches, each with its own screen.
   - **Study this variation / Practice this variation**: open the trainer at this variation's starting position.

5. **Browse all lines** (at the bottom of a chapter) lists every line with a search box. Try *Breyer*, *Zaitsev*, *Marshall*, *Berlin Wall* or a move like *Bxc6*. Filters: *All / Main / Alternatives / Mistakes*. Use *Mistakes* to drill the opponent errors that are punished in the course.

### 4.2 The trainer: Study and Practice

- **Study** shows the moves; step through them with the arrow buttons or tap any move in the move list. Each move has a short card:
  - the move and its role (*Most played*, *Alternative*, *Club move*, *Common mistake*, *Punish it*, *Engine line*);
  - an evaluation chip (e.g. *= 0.2*, *+1.3 White*);
  - who played it, when it is a master move;
  - the explanation, three lines at a time, with *Show more* for the rest.

  **Plans for both sides** (collapsed) holds the longer plan text, and **Understand this position** gives a board-based breakdown of the position.
- **Branch points:** whenever the course has other moves at the current position, a **Choose a continuation** card appears under the move card. It holds:
  - **Continue with ⟨move⟩**, which keeps you on your line;
  - one row per alternative move, sorted by how often masters play it. Each row shows the move and the variation it leads into, its master-game count and how the line ends, e.g. *6.d3 · Martinez Variation — 1,370 master games · Ends equal*.

  Tap **Explore ›** to follow that branch, then **Return to branch point** to come back. The main line follows the most-played moves to the end (e.g. 51 moves in the Closed chapter, through the Breyer/Zaitsev). When a line reaches a position that arises by another move order, it simply continues along that line.
- **Practice** asks you to find your moves while the app plays the opponent. If you play a wrong move, the board stays put and shows the expected move. Practised lines feed **Review**.
- **Flag icon** (top-right of a move card): tap it if an explanation is wrong or unclear, and add a note if you like. It is saved on the phone for later fixing (section 8).

### 4.3 A sensible way to work through the Ruy Lopez

1. Pick your side in each chapter: White, or Black if you defend against 1.e4 with 1...e5.
2. In each chapter, open the top-level variations one by one. Read *The idea* and *How <your side> wins*, replay one model game, then **Study this variation** and later **Practice this variation**.
3. Go one level deeper, into the sub-variations, only when the parent feels familiar.
4. Let the daily **Openings · practise N lines** row and **Review** bring old lines back.
5. A good order for White: Closed main systems (the classical heart) → Marshall/Anti-Marshall → Berlin → Open → Exchange → the rest.

---

## 5. Tactics: the Woodpecker method

### 5.1 What it is

The Woodpecker method (Smith & Tikkanen) works like this:
1. Solve one fixed set of puzzles.
2. Solve the *same set* again, faster.
3. Repeat until the patterns are automatic.

Each new cycle aims for about **half the time** of the previous one, and seven cycles is the classic plan. Accuracy matters more than speed in the first cycle.

### 5.2 The sets

All puzzles are real Lichess puzzles (CC0), filtered for quality: popular, at least 1,000 plays each, stable rating.

| Group | Sets |
| --- | --- |
| **Woodpecker sets** | Easy: 222 puzzles, rated 1000–1500 · Intermediate: 762, 1500–2000 · Advanced: 144, 2000–2500 |
| **Themes** (100 each, 1100 → 2100, getting harder as you go) | Forks · Pins & skewers · Discovered attacks · Mate in 2 · Mate in 3 · Back rank & exposed king · Sacrifices · Endgame tactics · Defensive moves |
| **From your openings** | Ruy Lopez tactics: 150 puzzles from real Ruy Lopez games, 900 → 2400 |
| **My sets** | Your own sets: **Create a set**, then choose a rating range, themes and a size (25/50/100/200) from a pool of about 6,000 puzzles. |

### 5.3 Doing a cycle

1. **Tactics → a set → Start cycle 1.**
2. The opponent's last move is shown first. Then it is your move: *White to move* or *Black to move*. Find the best move by tapping the piece, then the target square.
3. **Right move:** the board flashes green, the opponent replies, and you continue until the puzzle is solved. The next puzzle then opens automatically; you can turn *Auto-next* off in the ⋮ menu.
4. **Wrong move:** the board flashes red, the puzzle counts as a **miss** for this cycle, and the correct move is shown. Tap **Show solution** to see the rest, or **Next**.
5. The timer at the top counts only the time you spend on the puzzle screen. It pauses when you leave or lock the phone. You can stop at any time; **Resume cycle** continues at the same puzzle.
6. **When the cycle is done** you see its time, accuracy, seconds per puzzle, the comparison with your last cycle, and the **target for the next cycle** (about half the time).

### 5.4 Between cycles

- The set screen keeps a **Cycle history** table: cycle, time, accuracy, and the result against the target.
- **Retry mistakes from cycle N** drills only the puzzles you missed. It does not change the cycle statistics.
- Rest a day between cycles. The first cycle of Intermediate (762 puzzles) takes several sessions, which is normal; the Today row's daily target spreads it out.
- **Reset set history** starts a set from scratch (after a confirmation).

### 5.5 Suggested plan

- **Daily:** your 20 puzzles (adjust in Profile), on **Woodpecker · Easy** first.
- When Easy feels quick, do **Ruy Lopez tactics** next: they train exactly the patterns from your opening.
- Then **Intermediate**, with a theme set (e.g. *Forks* or *Back rank*) in between when one motif keeps tripping you up.

---

## 6. Review

**Review** schedules the opening decisions you have practised (spaced repetition):
1. Choose a scope: a course route, a repertoire family or a named set.
2. Answer the due positions.

Hinted answers count separately from unaided ones. The *Review* row on Learn tells you when something is due.

---

## 7. Profile

| Section | What it contains |
| --- | --- |
| **This week** | Study days against your weekly goal, a 7-day strip and your streak. A study day is any day with puzzles, course study or practice, or review answers. |
| **Stats** | Tactics: puzzles solved, first-try accuracy, cycles completed. Openings: lines studied and practised, review answers and accuracy, cards due. |
| **Routine settings** | Daily puzzles, daily opening lines, review on/off, weekly goal. |
| **Preferences** | Board coordinates, autoplay speed, puzzle auto-next. |
| **About** | Version, **Sources & licences**, privacy note. |

Tap **Edit name** to set the name used in the greeting.

---

## 8. Developer mode (optional)

**Profile → About → Developer mode** shows what learners do not normally see:
- how each piece of course text was generated and checked;
- engine and data sources;
- course coverage;
- the **Content feedback** list from your flags, with **Share** to export it as JSON.

The same information is documented in [COURSE_PROVENANCE.md](COURSE_PROVENANCE.md). Leave developer mode off for normal studying.

---

## 9. If something looks wrong

- **A wrong or unclear explanation:** tap the flag icon on that move card. Later, export the flags with Developer mode → Content feedback → Share.
- **A puzzle that seems wrong:** use the *Lichess puzzle ↗* link to check it on Lichess.
- **The app shows "failed its checks" for the course or the puzzles:** the bundled file did not match its checksum. Reinstall the APK; your progress is kept with `install -r`.
