---
seq: 40
agent: claude-code
date: "2026-10-05T07:50:56Z"
task: deep-course-pilot
status: done
summary: "Completed the deep course pilot in Android 0.16.0/code17. The Ruy Lopez for White Berlin chapter and the Firouzja–Carlsen 2020 game come from Lichess club/master data plus Stockfish, with machine-checked generated text. All 66 native tests pass on a 4-core isolated AVD, plus unit, iOS, release, engine-asset and public gates. Installed and opened in place on the owner's emulator."
next: Owner tests 0.16 using docs/USER_TESTING.md (Test the deep course pilot) and shares in-app flags. Then claim deep-course-scale to regenerate flagged text and add more Ruy chapters; deep-course-bakeoff waits on the owner's API decision.
supersedes: []
git_head: a6820a3c24ee
---

# deep-course-pilot

## What I did

Continued from partial log 0039 (read it for the full build description, data sources and claims). This session resolved the open blocker and finished acceptance.

- **Root cause of the 3 full-run-only timeouts:**
  - The isolated test AVD I recreated from the owner's learning AVD config had `hw.cpu.ncore = 1`.
  - Once earlier tests install the bundled packs into the test app, every later cold start builds the opening catalog and observed-replies index in the background. On one core that starves `PositionTeachingUiTest` / `OfflineLearningTest` past their 10 s waits.
  - Evidence: deep-course tests followed by `PositionTeachingUiTest` 7/7 pass. `OfflineLearningTest` + `PositionTeachingUiTest` fails 2/6 with deep-course loading enabled *and* with it disabled (A/B, `MainActivity` restored afterwards). With `hw.cpu.ncore = 4` the same reproduction passes.
  - No test waits were relaxed. Only the private test-AVD config changed.
- **Last night's app change kept:** the deep course is parsed off Main only after `catalogReady`, and restores use a suspend `openingFor` lookup.
- **Owner emulator:** the learning AVD (also 1 core) was updated in place to 0.16.0/code17 and opened. A UI dump then showed the deep-course main line in Study, consistent with the owner already using it; I stopped observing the device. Read only version/foreground metadata and that dump/screenshot (kept in the private scratchpad, not published). No instrumentation, clearing or learner-row reads.

## Commands run and their outcomes

- `ANDROID_SERIAL=emulator-5556 ./gradlew :androidApp:connectedDebugAndroidTest`:
  - `tests_regex` DeepCourse|PositionTeaching: 7/7 pass.
  - D..P classes: 39 cases, 2 failures (PositionTeaching).
  - OfflineLearning|PositionTeaching: 6 cases, 2 failures, both with and without deep-course loading.
  - After ncore 4: 6/6 pass.
  - **Complete suite: BUILD SUCCESSFUL in 10m53s, 66 cases, 0 failures** (only Ashva_Course_Test listed). This includes all recall-review cases.
- `:androidApp:assembleDebug :androidApp:assembleRelease`: success. `node scripts/verify-engine-apk.mjs` on both APKs: exact engine/source/NNUE/recipe hashes and notices verified. Both contain `assets/content/courses/ruy-lopez-white/v1/course.json`; release metadata versionCode 17 / 0.16.0.
- **Earlier this pilot (log 0039):**
  - shared JVM 181/0, Android-host 141/0, importer 24/0, courseTools 8/0
  - Node 32/32; both iOS shared targets compile
  - pack verify unchanged; lint 0 errors (4 existing update warnings); public audit 325 files / 0 findings
- **Owner emulator-5554:** `install -r` Success; `am start` Status ok / COLD / 2801 ms; versionCode 17 / 0.16.0; topResumedActivity MainActivity.

## External resources touched

Local isolated AVD (private path in ignored `.public-audit/local-environment.json`; now 4 cores) and the owner's learning emulator (in-place update only). No downloads, accounts, Git operations or publication this session.

## Risks, warnings, and what is NOT done

- The pilot covers one chapter and one game. Generated text is not human-reviewed; see 0039 for coverage and limits.
- The owner's learning AVD has 1 CPU core: Learn may show *Checking the bundled deep course…* until the opening catalog finishes. Raising its cores is the owner's choice (AVD settings); agents did not change the owner AVD.
- **Not done:** model bake-off / AI judge (blocked on an API decision); Gitleaks scan (requires an unapproved download).
- `persistent-recall-review` remains a separate TODO for the owner to accept; its cases passed in this run.

## Next

The owner tests 0.16 using docs/USER_TESTING.md ("Test the deep course pilot (0.16)") and uses *Share flags* to export feedback. Then claim `deep-course-scale` to regenerate flagged explanations and add the remaining Ruy chapters. `deep-course-bakeoff` waits on the owner's provider/budget decision.
