---
seq: 41
agent: claude-code
date: "2026-10-06T08:31:18Z"
task: ruy-lopez-full-course
status: partial
summary: "Owner feedback reset the course structure. The pilot let a statistic pick 4.d3 and labelled lines by role, which hid the Berlin's named theory. Started the full Ruy Lopez course: named variations as the backbone, every master move for both sides, plus level stats, winning ideas from decisive games and example games. Owner approved downloading all broadcasts."
next: "Finish downloads, re-filter master games at 2200+, run courseTools toc ruy-lopez and show content/courses/ruy-lopez/TOC.md to the owner for approval before engine work and writing."
supersedes: []
git_head: a6820a3c24ee
---

# ruy-lopez-full-course

## What I did

**Owner feedback (2026-10-05/06) after testing the 0.16 pilot:**
- The owner asked why lines say "Opponent deviation" instead of variation names, and why named Berlin lines were missing.
- Diagnosis I gave the owner:
  1. The tree builder's learner-move rule chose 4.d3 (56.0% vs 55.7% for 4.O-O in club games) without asking, which removed nearly all named Berlin theory.
  2. The course pipeline never used the CC0 opening names.
  3. No early sample was shown to the owner.

**Owner's actual goal, restated and confirmed:**
- Like a strong course author, but on the board: the opening's variations under their real names, plus important unnamed lines GMs play constantly, labelled by moves.
- How players at different levels (GM/IM, master, club) actually play each line.
- The winning ideas for each side depending on the chosen point of view.
- Every deviation, as deep as possible.
- Data and analysis stay on the build side/backend; the app gets compact packs. Measured: 1.2 GB of inputs → a 191 KB / 27 KB gzipped course.

**Work started:**
- New task `ruy-lopez-full-course`; the owner approved downloading all broadcast months.
- Master filter lowered to both players 2200+, with bands 2600+, 2400–2599 and 2200–2399.
- New `VariationMap.kt`: taxonomy names by position (transpositions match) and named-route edges; `BandedIndex`; a data-only `VariationMapBuilder` that keeps every master move (≥8 games, ≥2% share) for both sides, every move along a named line, and frequent club moves.
- New `TableOfContents.kt` and the `courseTools toc` command; config `content/courses/ruy-lopez/course.config.json` with 10 chapters.

## Commands run and their outcomes

- Isolated AVD fix and 66/66 green: see log 0040.
- `node scripts/fetch-course-data.mjs broadcasts --months=200 --parallel=2`: running at the time of this log.
- `./gradlew :courseTools:compileKotlin`: success.

## External resources touched

database.lichess.org broadcast monthly exports (owner-approved, CC BY-SA 4.0), at ≤2 connections with the 429 cooldown.

## Risks, warnings, and what is NOT done

- Full engine analysis of 10k–20k positions will take on the order of a day or more on this Mac; build chapter by chapter.
- Hand-written prose cannot cover every position. Write variation intros, key moves and "how to win" sections; the rest is checked automatic text, or an API writer if the owner approves later.
- The 0.16 Berlin pilot stays installed until replaced.
- Lesson recorded for future agents: never let a statistic pick the learner's repertoire silently, and show the owner a table of contents plus one finished sample before generating at scale.

## Next

Finish the downloads, re-run `courseTools filter-master` (2200+), run `courseTools toc ruy-lopez`, and show TOC.md to the owner for approval.
