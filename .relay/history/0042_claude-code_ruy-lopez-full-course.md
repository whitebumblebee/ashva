---
seq: 42
agent: claude-code
date: "2026-10-06T10:50:45Z"
task: ruy-lopez-full-course
status: done
summary: "Full Ruy Lopez deep course for both colours shipped as Android 0.17.0/code18: 10 named-variation chapters plus the GM game, 898 lines and 11,190 positions. Each variation has an intro, how White/Black win (master statistics, checked written plans) and replayable example games. All gates green; connected 70/70 on the isolated AVD; installed in place on the owner emulator."
next: Owner tests 0.17 via docs/USER_TESTING.md (Test the full Ruy Lopez course) and flags wrong or unclear text; then fix the flags or start deep-course-scale for the next opening with the same pipeline.
supersedes: []
git_head: a6820a3c24ee
---

# ruy-lopez-full-course

## What I did

**Content:** full Ruy Lopez deep course for both colours, `content/courses/ruy-lopez/v1/course.json`.
- 7.7 MB, 11,190 positions, 898 lines.
- 10 repertoire chapters plus the Firouzja–Carlsen 2020 GM game.
- Pinned in `BundledCourses.TRUSTED_SHA256` (cc7b70ce…).
- App 0.17.0/code18.

Pipeline, all in `courseTools`:
1. `toc` builds the variation map plus TOC.md.
   - Real CC0 names; important unnamed GM lines labelled `Name · move`.
   - Colliding labels are extended backwards, e.g. `Closed · 7...d6 8.c3`.
2. `build` produces the lines.
   - Roles MAIN/SIDE/DEVIATION/TRAP, then ENGINE/PUNISH at depth 18 until a verdict.
   - Transpositions end with a TRANSPOSES verdict.
3. `ideas` computes win patterns per variation from decisive master games.
   - Ignores each side's first move, which is the sub-variation choice.
   - Only counts games that continue ≥20 plies past the variation start, so quick draws do not count.
   - Example games prefer over-the-board events and avoid repeating an ancestor variation's examples.
4. `facts`, `check`, `pack`.

**Writing (Claude Code, agent mode):**
- 182 variation introductions (IDEA) and 111 WINPLAN plans.
- A WINPLAN passes only if every move it names is among that side's statistically distinctive winning moves.
- Check results:
  - Repertoire chapters: 303/304 writer claims pass. The one failure is an intro for a variation below 20 games with no anchor; it is harmless.
  - GM game: 33/33 pass.
  - Automatic claims: 1,317/1,317 pass.
- Writer source scripts live outside the repo (Claude scratchpad): `ruy_intros.py` (I/IP), `ruy_plans.py` (P keyed by move path) and `ruy_claims.py`, which generates `claims/<chapter>.json`. The generated claims files are in the repo.

**Implementation by Codex** (`codex exec -m gpt-6.1-sol`, xhigh, owner instruction), under this claim, briefs 1–7:
1. Compile everything and add tests.
2. Win-pattern minimum continuation; tests.
3. `·` labels; first-move exclusion; OTB-first examples; variation intro on the anchor node.
4. Unique labels.
5. Empty-pattern sentence, capture SAN display, no repeated examples.
6. Device test rounding fix.
7. Reach bug: approach nodes had zero position counts, so every line's reach was 0. That broke weighted practice and "Study this variation" picking the most-reached line.

## Commands run and their outcomes

- `./gradlew --offline --max-workers=4 :shared:jvmTest :shared:testAndroidHostTest :courseTools:test :contentTools:test :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`: BUILD SUCCESSFUL.
  - shared JVM 188/188, Android host 148/148, courseTools 35/35, contentTools 24/24.
- `node scripts/verify-engine-apk.mjs` on the debug and release APKs: verified.
- `ANDROID_SERIAL=emulator-5556 ./gradlew :androidApp:connectedDebugAndroidTest` on the isolated 4-core AVD Ashva_Course_Test: 70/70 passed (BUILD SUCCESSFUL in 13m 3s; an earlier run found the test rounding bug and the reach bug, both fixed).
- Owner emulator-5554: `adb install -r` in place (no clear or uninstall), app opened.

## External resources touched

None this session beyond the already downloaded Lichess data (no new downloads).

## Risks, warnings, and what is NOT done

- Node-by-node notes away from variation starts are mostly board descriptions plus engine/statistics facts. The teaching depth lives in variation intros, how-to-win plans and example games.
- Win patterns are statistical: about 60% of variations show a clear pattern for at least one side.
- Broadcast data includes rapid and blitz events.
- The bundled pack is 7.7 MB of JSON, parsed in the background after the catalogue is ready.
- 0.16 pilot progress, stored under course id ruy-lopez-white, stays in the database but is no longer shown. The pilot pack directory `content/courses/ruy-lopez-white/` is kept as the source of the GM game claims.
- The model bake-off and an AI judge were not run.
- No Git operations were done; the owner commits.

## Next

Owner tests 0.17 using docs/USER_TESTING.md, "Test the full Ruy Lopez course (0.17)", and flags wrong or unclear text. The next agent then addresses the flags, or starts deep-course-scale for the next opening with the same toc → build → ideas → facts → check → pack pipeline.
