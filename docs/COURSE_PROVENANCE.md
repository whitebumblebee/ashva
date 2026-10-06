# Course provenance (for developers)

The app hides this information from learners by default. It is shown in the app only with **Profile → About → Developer mode**. This file records how the bundled content was made, so anyone working on the open-source project can audit or rebuild it.

## The Ruy Lopez deep course (`content/courses/ruy-lopez/v1/course.json`)

**Text and labels**
- Every sentence is generated. A sentence is shown only if its claim passed an automatic check. Each check gives the sentence one of these labels:
  - **engine-checked**: Stockfish confirmed it.
  - **game statistics**: club or master data supports it.
  - **board fact**: read directly from the position.
  - **general idea** (`IDEA`): names no move or square, so it cannot be engine-checked.
- No human coach reviewed the text.
- Writer: Claude Code (claude-opus-5-5) in agent mode. Sources are in `content/courses/ruy-lopez/writer/`, and the generated claims in `claims/`.
- Check results: 303 of 304 writer claims pass in the 10 repertoire chapters, and 33 of 33 in the GM game. All 1,317 automatic claims pass. The single failure is an introduction for a variation with fewer than 20 games; that variation has no anchor in the course.

**Engine:** Stockfish 19 on the build host. Depth 18, at most 30 s per search. Verdicts are confirmed at depths 14 and 18.

**Data**

| Source | Licence | Games | Use |
| --- | --- | --- | --- |
| Lichess rated games, September 2026, first 1 GiB of the export | CC0-1.0 | 8,905 Ruy Lopez games (3,284,678 scanned) | Both players 1600–2200, 5+0 or slower. Club move frequencies and scores. |
| Lichess official broadcasts, 83 monthly exports | CC BY-SA 4.0 | 18,044 Ruy Lopez games | Both players 2200+, in bands 2600+, 2400–2599 and 2200–2399. Master moves, statistics, win patterns, example games. |
| Lichess opening names (`chess-openings`) | CC0-1.0 | — | Variation names. |

The course data is CC BY-SA 4.0 because it adapts the broadcast data.

**Coverage**

| Chapter | Lines | Half-moves | Line endings |
| --- | --- | --- | --- |
| Berlin | 158 | 8–82 | 100 equal, 47 transpose, 3 White better, 2 Black better, 6 unclear |
| Exchange | 25 | 12–42 | 20 equal, 2 transpose, 3 unclear |
| Open | 54 | 13–51 | 39 equal, 6 transpose, 4 White better, 4 Black better, 1 unclear |
| Closed main systems | 205 | 11–54 | 126 equal, 52 transpose, 5 White better, 1 Black better, 21 unclear |
| Marshall / Anti-Marshall | 48 | 19–58 | 43 equal, 3 transpose, 1 White better, 1 Black better |
| Other Morphy systems | 293 | 10–57 | 173 equal, 90 transpose, 3 White better, 1 Black better, 26 unclear |
| Schliemann | 18 | 11–49 | 9 equal, 1 White better, 8 unclear |
| Classical | 43 | 9–53 | 23 equal, 10 transpose, 6 White better, 4 unclear |
| Old Steinitz | 10 | 9–27 | 5 equal, 1 transpose, 1 White better, 3 unclear |
| Other third moves | 39 | 7–41 | 13 equal, 8 transpose, 4 White better, 1 Black better, 13 unclear |

**Limitations**
- Lines follow the course thresholds, not every possible continuation. See [DEEP_COURSE_PLAN.md](DEEP_COURSE_PLAN.md), "Full course implementation (0.17)".
- Engine verdicts hold at the stated depth; they are not proof.
- The club data is one partial month, so club-only mistakes are under-represented. Only club moves with ≥100 games and ≥10% share become lines.
- The broadcasts include rapid and blitz events.

## Tactics pack (`content/tactics/v1/tactics.json`)

Puzzles come from the Lichess puzzle database (CC0-1.0), taken from the first 16 MiB of the export. They are filtered to Popularity ≥ 90, at least 1,000 plays and rating deviation ≤ 80. Each puzzle is replayed legally with the app's chess rules. Set composition is deterministic and is produced by `courseTools tactics`.

## Content feedback

The flag icon on an explanation saves *Wrong / Unclear / Something else*, plus an optional note, in app-private storage. Developers export it from Profile → About → Developer mode → Content feedback → Share. Nothing is uploaded automatically.
