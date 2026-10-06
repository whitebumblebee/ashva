---
seq: 38
agent: claude-code
date: "2026-10-04T17:18:57Z"
task: deep-course-plan
status: done
summary: "Wrote docs/DEEP_COURSE_PLAN.md: 13 ordered, agent-implementable phases for deep generated repertoire courses and GM-game teaching with engine-checked claims, evals and app integration; documentation only."
next: "Owner reviews docs/DEEP_COURSE_PLAN.md and answers its Phase 0 decisions; development stays paused, and deep-course-pilot must not be claimed until the owner resumes it."
supersedes: []
git_head: a6820a3c24ee
---

# deep-course-plan

## What I did

Owner is unhappy with repertoire depth (97% of the 3,815 taxonomy routes end by move 10; teaching text is template-based). After discussion, the owner's goal is: Hanging-Pawns-style repertoire courses on the board (chapters by opponent choice, all relevant branches for both sides including club-level mistakes and punishments, every line played to an engine-checked verdict, per-move "why" explanations), plus a GM-game track (actual game as mainline, both-POV teaching, "better was"/"if instead" branches). No human reviewer is available, so explanations must be AI-generated.

- Added `docs/DEEP_COURSE_PLAN.md`: goal, finite/measurable definition of "all relevant variations" (club frequency, master practice, engine strength, tempting traps; one recommended learner move; stop at verdict or below 1/1000 reach probability), ground rules (moves/evals from data and engine only, claim-checked prose, labels, no copying of books/courses/videos, data rights via `content/sources.json`, existing invariants), and Phases 0–12: decisions/setup, course pack format mapped onto `LessonGraph`, club/master position indexes and Lichess eval cache, engine toolkit (eval/threat/prevents/compare/stable/verdict), gold set + evals before the writer, deterministic tree builder, retrieval (structure key, original principles glossary), typed claim checker, provider-agnostic writer (API mode or agent mode) with critic/reviser, model bake-off, Android integration (install, study, frequency-weighted practice, recall, off-course handling, feedback export), GM-game track, pilot review/regeneration/scale. Includes definition of done per chapter and a suggested Relay task split.
- Added two tracker tasks in `.relay/tasks.md`: `deep-course-plan` (this documentation task) and `deep-course-pilot` (TODO, explicitly gated on the owner resuming development and answering Phase 0 decisions).
- No app, shared, content, service, schema or test code changed.

## Commands run and their outcomes

- `relay status`: 37 history entries; `android-end-to-end-user-test` claim by codex EXPIRED (2026-10-03T20:53:13Z); next unblocked `persistent-recall-review`.
- `relay claim deep-course-plan --agent claude-code`: claimed, expires 2026-10-04T18:16:39Z.
- Read-only inspection of `PositionCoach.kt`, `OpeningGuides.kt`, `StudyRoutes.kt`, `LessonGraph.kt`, `ChessModels.kt`, `EngineAnalysis.kt`, `content/sources.json` and module layout to tie the plan to existing structures.
- One WebFetch of the owner's YouTube playlist URL returned only the page footer (no titles); the plan does not depend on its contents.
- No Gradle build, tests, emulator, Docker or network data acquisition. Plan thresholds (frequencies, eval cut-offs, budgets, rating band) are proposed defaults, not measured values.

## External resources touched

- YouTube playlist page (read-only fetch, failed to render content). No accounts, providers, downloads, Git operations or publication.

## Risks, warnings, and what is NOT done

- Nothing in the plan is implemented. Licenses for the Lichess standard-game, evaluation and puzzle databases are stated as "verify and record" — not re-verified in this session.
- Lichess monthly game files are very large; Phase 2 requires streaming and a measured scope. Sizes/run times are unmeasured.
- AI-generated explanations without a human reviewer remain the main quality risk; the plan relies on claim checking, evals and owner flags, and labels weaker evidence. Subtle positional judgement is the least verifiable part.
- Paid model/API use is not authorized; Phase 0 D1 must be answered first. Development remains owner-paused; `persistent-recall-review` full 61-case regression is still unfinished; board-quality/iOS remain deferred.
- `android-end-to-end-user-test` codex claim is expired and was left as-is (not taken over).

## Next

Owner reviews `docs/DEEP_COURSE_PLAN.md` and answers Phase 0 decisions D1–D5 (writer model/provider and budget, personal vs public data scope, target level/rating band, pilot chapter, pilot game). Do not claim `deep-course-pilot` until the owner explicitly resumes development; then split it per the plan's "Suggested Relay task split" and start with Phase 0 and Phase 1 (`course-format`).
