---
seq: 44
agent: claude-code
date: "2026-10-06T18:12:07Z"
task: ux-polish-tactics
status: done
summary: "0.18.1 follow-up done by Claude Code directly. Study branching restored: main lines follow the most-played moves to the end (Closed 51 moves, Berlin 63) and one compact choice per alternative move at every branch point. Transposed lines continue. Celtic SVG piece set (MIT). Gates green, connected 87/87, installed in place on the owner emulator with data intact."
next: "Owner uses 0.18.1 (docs/GUIDE_0.18.md) and flags text; the next agent exports the flags and fixes them, or adds club data for common-mistake coverage."
supersedes: []
git_head: ef7bab624746
---

# ux-polish-tactics (0.18.1 follow-up)

## What I did

Owner: "There is no option to branch out inside the study now", and the pieces and board do not look polished. Owner allowed Claude Code to take over from Codex.

**Branching (diagnosed on device):** "Study main line" picked a 13-move stub. The main move order's 7.Bb3 position had been merged as a transposition, so all 102 lines that continue from that position were dumped at its end, with no branch offers along the way. Fixes:
- `DeepCourse.present`:
  - The main line follows the chapter's own first move (the child with most lines, up to `rootPly`), then MAIN and highest-weight children to the end.
  - A line ending in `TRANSPOSES` continues along the line that owns that position (spliced by board position key, at most 8 hops).
  - Line IDs stay keyed by the original leaf.
- `LessonReplay.branches()` returns one offer per distinct next move (first path in lesson order). `allBranches()` keeps every path for `diverge` and saved-route restore.
- Deep-course branch card: "Continue with ⟨move⟩" plus compact rows ("6.d3 · Martinez Variation — 1370 master games · Ends equal"), sorted by master games. New helpers `branchTitle`, `branchFacts`, `branchGames`.
- `AppViewModel.sameLessonContent`: deep-course bookmarks and review cards restore across content versions; the path, prefix and expected-move checks still apply. Verified on the owner emulator: the Continue card for a Berlin line restored.
- New `DeepCourseBranchingTest` on the real pack: main lines are long, offers are distinct per move, at most 15 per position, and branch points occur along the main line. Results: Closed 51 plies with 17 branch points; Berlin 63 with 17.
- `StartupBenchmarkTest` fingerprints re-pinned (intended content change; the GM game is unchanged).

**Board:**
- The owner approved downloading open-licensed piece sets. The two I first proposed (Cardinal and Maestro) turned out to be CC BY-NC-SA, so I substituted Fantasy and Celtic (MIT) alongside Chessnut (Apache-2.0).
- The owner chose **Celtic** (Maurizio Monge, MIT). The SVGs were rasterised to 256 px PNGs in `res/drawable-nodpi/piece_*.png`, and `ChessPiece` now draws them with `Image`.
- Licence text is in `LICENSES/Celtic-pieces-MIT.txt`, with a THIRD_PARTY_NOTICES entry.

Version 0.18.1/code20. The guide was updated with the branching section.

## Commands run and their outcomes

- Full gates: BUILD SUCCESSFUL. JVM 253/253, Android host 195/195, courseTools 45/45, contentTools 24/24. Lint and iOS compiles pass. verify-engine-apk passes on debug and release; public-audit has 0 findings.
- `connectedDebugAndroidTest` on emulator-5556:
  1. 87 tests, 1 failure. `SourcedLearningTest` expected 24 offers; with one offer per move there are 17. The test was updated.
  2. Re-run of `SourcedLearningTest`: 3/3.
- Owner emulator-5554: `adb install -r` to 0.18.1 with data intact.

## External resources touched

With owner approval, files from the lila repository (via raw.githubusercontent.com and cdn.jsdelivr.net): COPYING.md and the chessnut, fantasy and celtic SVG sets. Also the chess-art LICENSE file (MIT).

## Risks, warnings, and what is NOT done

- After switching to a branch, the role chip can still say "Main line" (cosmetic).
- Mid-line branch rows for regular (non-deep) courses still use the older card style.
- No Git operations were done.

## Next

The owner uses 0.18.1 daily and flags text. The next agent exports the flags and fixes them.
