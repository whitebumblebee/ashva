---
seq: 43
agent: claude-code
date: "2026-10-06T17:16:36Z"
task: ux-polish-tactics
status: done
summary: "Android 0.18.0/code19: calmer course UI (course > chapter > variation tree > variation page, 3-line explanations), provenance only in off-by-default developer mode, outlined pieces, real Profile/Today routine, Woodpecker Tactics tab (13 sets, 6,117 CC0 puzzles), chess-core/startup speed-up (~2 CPU-min to ~10 s). Gates green, connected 87/87, installed in place on the owner emulator."
next: "Owner uses 0.18 daily per docs/GUIDE_0.18.md and flags wrong/unclear text; the next agent exports the flags (Developer mode > Content feedback) and fixes them, or extends club data so common mistakes get more coverage."
supersedes: []
git_head: ef7bab624746
---

# ux-polish-tactics

## What I did

Owner feedback after 0.17: too much text; hide provenance from users (it stays available to developers); white pieces are hard to see on light squares; polish Profile and add practice routines; add Woodpecker tactics like Disco Chess; gitignore caches; install the build and write a usage guide.

Claude Code designed the work, ran the pipelines and verified on device. Implementation went to Codex (`codex exec -m gpt-6.1-sol`, xhigh), one round per brief, A–H:

- **A – calmer UI.**
  - Course → chapter → variation tree → variation page: mini board, results bar with legend, rating bands, idea, how each side wins (written plan, key-move chips, model games), sub-variations, study and practice.
  - All-lines browser.
  - Developer mode (SharedPreferences, off by default) gates every provenance, source and coverage label.
  - Quiet flag dialog with an optional note; Sources & licences screen.
  - Outlined piece glyphs.
  - Pack model: `CourseSideIdeas.plan` and `patterns`.
- **B – Woodpecker tactics.**
  - `courseTools tactics` generator, run on the local CC0 puzzle prefix.
  - Shared session, Woodpecker and custom-set logic; Room migration 6→7 (`tactics_*` tables).
  - Tactics tab, set and puzzle screens.
- **C – Home and Profile.** Home order and the Today routine; real Profile (name, week, streak, stats, routine settings, preferences, About); Room migration 7→8 (activity table); disclaimers moved to ⓘ dialogs.
- **D – tactics difficulty.** Even rating spread in the theme and Ruy Lopez sets.
- **E and G – start-up performance.**
  - Root causes:
    1. `ChessMove` created two new `Regex` objects per construction.
    2. Legal-move generation copied a `LinkedHashMap` board for every candidate move.
    3. All packs were fully replayed on every launch.
  - Fixes: char checks instead of regexes; an array board; lazy per-chapter and per-course building; validation markers keyed by pack sha, versionCode and validator version; two-worker CPU limit.
  - Device result (debug build): first launch about 23 s CPU instead of more than 2 minutes, later launches about 14 s; Home with the course card in about 5 s.
- **F – visual-review fixes.**
  - SAN in tactics misses; palette for selected states; plain progress bars; chevrons; model-game rows.
  - Study and practice start at the variation anchor; `lineThrough` prefers lines that do not transpose.
  - Trainer header: course prefix and duplicate status line removed; no reach chip; friendly start card and branch card.
- **H – device tests.** Updated 12 failing device tests to the intentional UI changes. No app changes.

Claude Code also:
- Repacked the course (new fields, friendlier summary); course sha 7437b57e…, tactics sha 4cf96253….
- Bumped the app to 0.18.0/code19.
- Moved the writer sources into `content/courses/ruy-lopez/writer/`.
- Gitignored deep-course intermediates.
- Wrote docs/GUIDE_0.18.md and docs/COURSE_PROVENANCE.md; moved Codex handoff and performance reports to docs/dev/; updated README and USER_TESTING.
- Made public-audit treat `.py` as text.
- Saved memory notes: Codex as implementer, no provenance in the UI.

## Commands run and their outcomes

- Full gates: BUILD SUCCESSFUL. shared JVM 252/252, Android host 195/195, courseTools 45/45, contentTools 24/24. assembleDebug and assembleRelease, androidTest compile, lint (0 errors), iOS sim and device compiles all pass.
- `verify-engine-apk` on the debug and release APKs: verified. `public-audit`: 0 findings.
- `connectedDebugAndroidTest` on the isolated 4-core AVD Ashva_Course_Test (emulator-5556):
  1. First run: 87 tests, 12 failed. Eleven were outdated tests; one was a native libhwui RenderThread SIGSEGV, an emulator flake.
  2. After Codex H: 87/87 passed (BUILD SUCCESSFUL in 15m 42s).
- Owner emulator-5554: `adb install -r` from 0.17 to 0.18 with data preserved (saved lesson, studied lines and review cards intact); the app is open.

## External resources touched

None. The puzzle data was the already-downloaded CC0 Lichess prefix in .course-cache.

## Risks, warnings, and what is NOT done

- The owner already committed the 0.17 deep-course intermediates. The new gitignore rules do not untrack them; the owner can run `git rm -r --cached content/courses/*/{map,tree,extra,facts,checked,ideas}` if wanted.
- Common club mistakes are under-represented: one partial month of club data, and only club moves with ≥100 games and ≥10% share become lines.
- Move-by-move notes away from variation starts are still mostly board facts.
- First launch after an install still needs about 20 s CPU on the emulator (debug build) for one-time validation.
- A release APK signed with the local debug key is at `.course-cache/apk/` (0.18 pre-fix build, superseded); for a phone, use the debug APK or re-sign a fresh release build.
- No Git operations were done.

## Next

The owner uses 0.18 daily (docs/GUIDE_0.18.md) and flags text. The next agent exports the flags (Developer mode → Content feedback → Share) and fixes them, or extends club data to improve mistake coverage.
